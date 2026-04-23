package com.android.common.bill.ads.gam

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.tracker.AdRevenueReporter
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.AdLifecycleGuard
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ui.dialog.ADLoadingDialog
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * GAM 激励广告控制器
 */
class GamRewardedAdController private constructor() {

    private var totalClickCount by DataStoreIntDelegate("gam_rewarded_ad_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("gam_rewarded_ad_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("gam_rewarded_ad_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("gam_rewarded_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("gam_rewarded_ad_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("gam_rewarded_ad_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("gam_rewarded_ad_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("gam_rewarded_ad_total_shows", 0)
    private var totalRewardEarnedCount by DataStoreIntDelegate("gam_rewarded_ad_total_reward_earned", 0)

    private var currentAdValue: AdValue? = null
    private var isShowing: Boolean = false
    private var currentShowingAd: RewardedAd? = null
    private var showContinuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null
    private var rewardCallback: ((RewardItem) -> Unit)? = null

    companion object {
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: GamRewardedAdController? = null

        fun getInstance(): GamRewardedAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GamRewardedAdController().also { INSTANCE = it }
            }
        }
    }

    private val adCachePool = mutableListOf<CachedRewardedAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT

    private data class CachedRewardedAd(
        val ad: RewardedAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > 1 * 60 * 60 * 1000L
        }
    }

    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.rewardedId
        return loadAdToCache(context, finalAdUnitId)
    }

    suspend fun showAd(
        activity: FragmentActivity,
        adUnitId: String? = null,
        onRewardEarned: ((RewardItem) -> Unit)? = null,
        sessionId: String = ""
    ): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.rewardedId
        totalShowTriggerCount++
        AdLogger.d("GAM 激励广告累积触发展示次数: $totalShowTriggerCount")

        val lifecycleGuard = AdLifecycleGuard.instance
        when (val lifecycleResult = lifecycleGuard.awaitResumeOrCancel(activity)) {
            is AdResult.Failure -> {
                AdLogger.w("GAM 激励广告展示被取消：Activity 生命周期不满足")
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.REWARDED,
                    AdPlatform.GAM,
                    finalAdUnitId,
                    totalShowFailCount,
                    lifecycleResult.error.message,
                    sessionId = sessionId,
                    isPreload = false
                )
                return lifecycleResult
            }

            else -> Unit
        }

        AdDestroyManager.instance.register(activity) {
            AdLogger.d("GAM 激励广告: Activity 销毁，清理展示资源")
            destroyShowingAd()
            showContinuation?.let {
                if (it.isActive) {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(
                        AdType.REWARDED,
                        AdPlatform.GAM,
                        finalAdUnitId,
                        totalShowFailCount,
                        "Activity destroyed",
                        sessionId = sessionId,
                        isPreload = false
                    )
                    it.resume(AdResult.Failure(createAdException("GAM rewarded: Activity destroyed")))
                }
            }
            showContinuation = null
            rewardCallback = null
            isShowing = false
        }

        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.REWARDED, AdPlatform.GAM, finalAdUnitId)
            }
        }

        return try {
            var cachedAd = getCachedAd(finalAdUnitId)
            var isPreload = cachedAd != null
            if (cachedAd == null) {
                AdLogger.d("GAM 缓存为空，立即加载激励广告，广告位ID: %s", finalAdUnitId)
                loadAdToCache(activity, finalAdUnitId)
                cachedAd = getCachedAd(finalAdUnitId)
                isPreload = false
            }

            if (cachedAd != null) {
                AdLogger.d("GAM 使用缓存中的激励广告，广告位ID: %s", finalAdUnitId)
                showAdInternal(activity, cachedAd.ad, finalAdUnitId, sessionId = sessionId, isPreload = isPreload, onRewardEarned = onRewardEarned)
            } else {
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.REWARDED,
                    AdPlatform.GAM,
                    finalAdUnitId,
                    totalShowFailCount,
                    "No ad available",
                    sessionId = sessionId,
                    isPreload = false
                )
                AdResult.Failure(createAdException("GAM rewarded ad no available ad"))
            }
        } catch (e: Exception) {
            AdLogger.e("GAM 显示激励广告异常", e)
            totalShowFailCount++
            AdEventReporter.reportShowFail(
                AdType.REWARDED,
                AdPlatform.GAM,
                finalAdUnitId,
                totalShowFailCount,
                e.message.orEmpty(),
                sessionId = sessionId,
                isPreload = false
            )
            AdResult.Failure(createAdException("GAM rewarded: show exception: ${e.message}", e))
        }
    }

    private suspend fun loadAd(context: Context, adUnitId: String): RewardedAd? {
        totalLoadCount++
        AdLogger.d("GAM 激励广告累积加载次数: $totalLoadCount")
        val requestId = AdEventReporter.reportStartLoad(AdType.REWARDED, AdPlatform.GAM, adUnitId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            val adRequest = AdRequest.Builder(adUnitId).build()

            RewardedAd.load(adRequest, object : AdLoadCallback<RewardedAd> {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.d("GAM 激励广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    AdEventReporter.reportLoaded(
                        AdType.REWARDED,
                        AdPlatform.GAM,
                        adUnitId,
                        totalLoadSucCount,
                        rewardedAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        ceil(loadTime / 1000.0).toInt(),
                        requestId
                    )
                    continuation.resume(rewardedAd)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("GAM rewarded ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, loadTime, loadAdError.message)
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(
                        AdType.REWARDED,
                        AdPlatform.GAM,
                        adUnitId,
                        totalLoadFailCount,
                        loadAdError.responseInfo?.loadedAdSourceResponseInfo?.name.orEmpty(),
                        ceil(loadTime / 1000.0).toInt(),
                        loadAdError.message,
                        requestId
                    )
                    continuation.resume(null)
                }
            })
        }
    }

    suspend fun loadAdToCache(context: Context, adUnitId: String): AdResult<Unit> {
        return try {
            val currentAdUnitCount = getCachedAdCount(adUnitId)
            if (currentAdUnitCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("GAM 广告位 %s 缓存已满，当前缓存: %d/%d", adUnitId, currentAdUnitCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }

            val rewardedAd = loadAd(context, adUnitId)
            if (rewardedAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedRewardedAd(rewardedAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("GAM 激励广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("GAM rewarded ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("GAM 激励 loadAdToCache 异常", e)
            AdResult.Failure(AdException(0, "GAM rewarded ad loadAdToCache exception: ${e.message}", e))
        }
    }

    private fun getCachedAd(adUnitId: String): CachedRewardedAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool.removeAt(index) else null
        }
    }

    fun peekCachedAd(adUnitId: String = BillConfig.gam.rewardedId): RewardedAd? {
        return synchronized(adCachePool) {
            adCachePool.firstOrNull { it.adUnitId == adUnitId && !it.isExpired() }?.ad
        }
    }

    fun getCachedAdPeek(adUnitId: String? = null): RewardedAd? {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.rewardedId
        return peekCachedAd(finalAdUnitId)
    }

    private fun getCachedAdCount(adUnitId: String): Int {
        synchronized(adCachePool) {
            return adCachePool.count { it.adUnitId == adUnitId && !it.isExpired() }
        }
    }

    private fun isCacheFull(adUnitId: String): Boolean {
        return getCachedAdCount(adUnitId) >= maxCacheSizePerAdUnit
    }

    private suspend fun showAdInternal(
        activity: FragmentActivity,
        rewardedAd: RewardedAd,
        adUnitId: String,
        sessionId: String = "",
        isPreload: Boolean = false,
        onRewardEarned: ((RewardItem) -> Unit)?
    ): AdResult<Unit> {
        currentShowingAd = rewardedAd

        return suspendCancellableCoroutine { continuation ->
            var hasRewarded = false
            rewardedAd.adEventCallback = object : RewardedAdEventCallback {
                var currentAdUniqueId = ""

                override fun onAdPaid(adValue: AdValue) {
                    AdLogger.d("GAM 激励广告收益回调: value=${adValue.valueMicros}, currency=${adValue.currencyCode}")
                    val uuid = java.util.UUID.randomUUID().toString()
                    val creativeId = rewardedAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceId.orEmpty()
                    currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"
                    currentAdValue = adValue

                    AdEventReporter.reportImpression(
                        AdType.REWARDED,
                        AdPlatform.GAM,
                        adUnitId,
                        currentAdUniqueId,
                        totalShowCount,
                        rewardedAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                        currentAdValue?.currencyCode ?: "",
                        sessionId = sessionId,
                        isPreload = isPreload
                    )
                    AdRevenueReporter.reportRevenue(
                        AdType.REWARDED,
                        AdPlatform.GAM,
                        adUnitId,
                        adValue.valueMicros / 1_000_000.0,
                        adValue.currencyCode,
                        rewardedAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "GAM",
                        rewardedAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceName.orEmpty()
                    )
                }

                override fun onAdDismissedFullScreenContent() {
                    AdLogger.d("GAM 激励广告关闭")
                    isShowing = false
                    totalCloseCount++

                    AdEventReporter.builder(com.android.common.bill.ads.tracker.AdEventType.CLOSE)
                        .adType(AdType.REWARDED)
                        .platform(AdPlatform.GAM)
                        .adUnitId(adUnitId)
                        .number(totalCloseCount)
                        .adSource(rewardedAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty())
                        .value(currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0)
                        .currency(currentAdValue?.currencyCode ?: "")
                        .param("isended", if (hasRewarded) "true" else "")
                        .report()

                    rewardedAd.adEventCallback = null

                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            try {
                                AdLogger.d("GAM 激励广告关闭，开始重新预缓存，广告位ID: %s", adUnitId)
                                AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(sessionId)
                                ) {
                                    preloadAd(activity.applicationContext, adUnitId)
                                }
                            } catch (e: Exception) {
                                AdLogger.e("GAM 激励广告重新预缓存失败", e)
                            }
                        }
                    }

                    if (continuation.isActive) {
                        continuation.resume(AdResult.Success(Unit))
                    }
                }

                override fun onAdFailedToShowFullScreenContent(adError: FullScreenContentError) {
                    AdLogger.w("GAM 激励广告 show failed: %s", adError.message)
                    totalShowFailCount++
                    AdLogger.d("GAM 激励广告累积展示失败次数: $totalShowFailCount")
                    AdEventReporter.reportShowFail(
                        AdType.REWARDED,
                        AdPlatform.GAM,
                        adUnitId,
                        totalShowFailCount,
                        adError.message,
                        sessionId = sessionId,
                        isPreload = isPreload
                    )
                    rewardedAd.adEventCallback = null

                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            try {
                                AdLogger.d("GAM 激励广告展示失败，开始重新预缓存，广告位ID: %s", adUnitId)
                                AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(sessionId)
                                ) {
                                    preloadAd(activity.applicationContext, adUnitId)
                                }
                            } catch (e: Exception) {
                                AdLogger.e("GAM 激励广告重新预缓存失败", e)
                            }
                        }
                    }

                    if (continuation.isActive) {
                        continuation.resume(AdResult.Failure(createAdException("GAM rewarded: show callback failed: ${adError.message}")))
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    AdLogger.d("GAM 激励广告开始显示")
                    AdConfigManager.recordShow(AdType.REWARDED, AdPlatform.GAM)
                }

                override fun onAdClicked() {
                    AdLogger.d("GAM 激励广告被点击")
                    totalClickCount++
                    AdLogger.d("GAM 激励广告累积点击次数: $totalClickCount")
                    AdConfigManager.recordClick(AdType.REWARDED, AdPlatform.GAM)
                    AdClickProtectionController.recordClick(currentAdUniqueId)
                    AdEventReporter.reportClick(
                        AdType.REWARDED,
                        AdPlatform.GAM,
                        adUnitId,
                        currentAdUniqueId,
                        totalClickCount,
                        rewardedAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                        currentAdValue?.currencyCode ?: "",
                        sessionId = sessionId
                    )
                }

                override fun onAdImpression() {
                    AdLogger.d("GAM 激励广告展示完成")
                    isShowing = true
                    totalShowCount++
                    AdLogger.d("GAM 激励广告累积展示次数: $totalShowCount")
                }
            }

            rewardedAd.show(activity) { rewardItem ->
                AdLogger.d("GAM 用户获得奖励: type=${rewardItem.type}, amount=${rewardItem.amount}")
                totalRewardEarnedCount++
                AdLogger.d("GAM 激励广告累积奖励获得次数: $totalRewardEarnedCount")
                hasRewarded = true
                AdEventReporter.reportRewardEarned(
                    AdType.REWARDED,
                    AdPlatform.GAM,
                    adUnitId,
                    totalRewardEarnedCount,
                    rewardedAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                    rewardItem.type,
                    rewardItem.amount,
                    sessionId = sessionId
                )
                onRewardEarned?.invoke(rewardItem)
            }
        }
    }

    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.clear()
        }
        AdLogger.d("GAM 激励广告已销毁")
    }

    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.adEventCallback = null
            AdLogger.d("GAM 激励广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    fun destroy() {
        destroyAd()
        AdLogger.d("GAM 激励广告控制器已清理")
    }

    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(code = 0, message = message, cause = cause)
    }

    fun isAdShowing(): Boolean {
        return isShowing
    }
}
