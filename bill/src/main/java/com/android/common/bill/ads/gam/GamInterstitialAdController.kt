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
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
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
 * GAM 插页广告控制器
 */
class GamInterstitialAdController private constructor() {

    private var totalClickCount by DataStoreIntDelegate("gam_interstitial_ad_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("gam_interstitial_ad_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("gam_interstitial_ad_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("gam_interstitial_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("gam_interstitial_ad_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("gam_interstitial_ad_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("gam_interstitial_ad_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("gam_interstitial_ad_total_shows", 0)

    private var currentAdValue: AdValue? = null
    private var isShowing: Boolean = false
    private var currentShowingAd: InterstitialAd? = null
    private var showContinuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null

    companion object {
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: GamInterstitialAdController? = null

        fun getInstance(): GamInterstitialAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GamInterstitialAdController().also { INSTANCE = it }
            }
        }
    }

    private val adCachePool = mutableListOf<CachedInterstitialAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT

    data class CachedInterstitialAd(
        val ad: InterstitialAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > 1 * 60 * 60 * 1000L
        }
    }

    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.interstitialId
        return loadAdToCache(context, finalAdUnitId)
    }

    suspend fun showAd(
        activity: FragmentActivity,
        adUnitId: String? = null,
        ignoreFullNative: Boolean = false,
        sessionId: String = ""
    ): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.interstitialId

        totalShowTriggerCount++
        AdLogger.d("GAM 插页广告累积触发展示次数: $totalShowTriggerCount")

        val lifecycleGuard = AdLifecycleGuard.instance
        when (val lifecycleResult = lifecycleGuard.awaitResumeOrCancel(activity)) {
            is AdResult.Failure -> {
                AdLogger.w("GAM 插页广告展示被取消：Activity 生命周期不满足")
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.INTERSTITIAL,
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
            AdLogger.d("GAM 插页广告: Activity 销毁，清理展示资源")
            destroyShowingAd()
            showContinuation?.let {
                if (it.isActive) {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(
                        AdType.INTERSTITIAL,
                        AdPlatform.GAM,
                        finalAdUnitId,
                        totalShowFailCount,
                        "Activity destroyed",
                        sessionId = sessionId,
                        isPreload = false
                    )
                    it.resume(AdResult.Failure(createAdException("GAM interstitial: Activity destroyed")))
                }
            }
            showContinuation = null
            isShowing = false
        }

        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.INTERSTITIAL, AdPlatform.GAM, finalAdUnitId)
            }
        }

        return try {
            var cachedAd = getCachedAd(finalAdUnitId)
            var isPreload = cachedAd != null
            if (cachedAd == null) {
                AdLogger.d("GAM 缓存为空，立即加载插页广告，广告位ID: %s", finalAdUnitId)
                loadAdToCache(activity, finalAdUnitId)
                cachedAd = getCachedAd(finalAdUnitId)
                isPreload = false
            }

            if (cachedAd != null) {
                AdLogger.d("GAM 使用缓存中的插页广告，广告位ID: %s", finalAdUnitId)
                showAdInternal(activity, cachedAd.ad, finalAdUnitId, sessionId = sessionId, isPreload = isPreload)
            } else {
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.INTERSTITIAL,
                    AdPlatform.GAM,
                    finalAdUnitId,
                    totalShowFailCount,
                    "No ad available",
                    sessionId = sessionId,
                    isPreload = false
                )
                AdResult.Failure(createAdException("GAM interstitial ad no available ad"))
            }
        } catch (e: Exception) {
            AdLogger.e("GAM 显示插页广告异常", e)
            totalShowFailCount++
            AdEventReporter.reportShowFail(
                AdType.INTERSTITIAL,
                AdPlatform.GAM,
                finalAdUnitId,
                totalShowFailCount,
                e.message.orEmpty(),
                sessionId = sessionId,
                isPreload = false
            )
            AdResult.Failure(createAdException("GAM interstitial: show exception: ${e.message}", e))
        }
    }

    private suspend fun loadAd(context: Context, adUnitId: String): InterstitialAd? {
        totalLoadCount++
        AdLogger.d("GAM 插页广告累积加载次数: $totalLoadCount")
        val requestId = AdEventReporter.reportStartLoad(AdType.INTERSTITIAL, AdPlatform.GAM, adUnitId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            val adRequest = AdRequest.Builder(adUnitId).build()

            InterstitialAd.load(adRequest, object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.d("GAM 插页广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    AdEventReporter.reportLoaded(
                        AdType.INTERSTITIAL,
                        AdPlatform.GAM,
                        adUnitId,
                        totalLoadSucCount,
                        ad.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        ceil(loadTime / 1000.0).toInt(),
                        requestId
                    )
                    continuation.resume(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("GAM interstitial ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, loadTime, adError.message)
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(
                        AdType.INTERSTITIAL,
                        AdPlatform.GAM,
                        adUnitId,
                        totalLoadFailCount,
                        adError.responseInfo?.loadedAdSourceResponseInfo?.name.orEmpty(),
                        ceil(loadTime / 1000.0).toInt(),
                        adError.message,
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

            val interstitialAd = loadAd(context, adUnitId)
            if (interstitialAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedInterstitialAd(interstitialAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("GAM 插页广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("GAM interstitial ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("GAM 插页 loadAdToCache 异常", e)
            AdResult.Failure(AdException(0, "GAM interstitial ad loadAdToCache exception: ${e.message}", e))
        }
    }

    private fun getCachedAd(adUnitId: String): CachedInterstitialAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool.removeAt(index) else null
        }
    }

    fun getCachedAdPeek(adUnitId: String): CachedInterstitialAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool[index] else null
        }
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
        interstitialAd: InterstitialAd,
        adUnitId: String,
        sessionId: String = "",
        isPreload: Boolean = false
    ): AdResult<Unit> {
        currentShowingAd = interstitialAd

        return suspendCancellableCoroutine { continuation ->
            showContinuation = continuation
            continuation.invokeOnCancellation {
                showContinuation = null
                isShowing = false
            }

            interstitialAd.adEventCallback = object : InterstitialAdEventCallback {
                var currentAdUniqueId = ""

                override fun onAdPaid(value: AdValue) {
                    AdLogger.d("GAM 插页广告收益回调: value=${value.valueMicros}, currency=${value.currencyCode}")
                    val uuid = java.util.UUID.randomUUID().toString()
                    val creativeId = interstitialAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceId.orEmpty()
                    currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"
                    currentAdValue = value

                    AdEventReporter.reportImpression(
                        AdType.INTERSTITIAL,
                        AdPlatform.GAM,
                        adUnitId,
                        currentAdUniqueId,
                        totalShowCount,
                        interstitialAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                        currentAdValue?.currencyCode ?: "",
                        sessionId = sessionId,
                        isPreload = isPreload
                    )
                    AdRevenueReporter.reportRevenue(
                        AdType.INTERSTITIAL,
                        AdPlatform.GAM,
                        adUnitId,
                        value.valueMicros / 1_000_000.0,
                        value.currencyCode,
                        interstitialAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "GAM",
                        interstitialAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceName.orEmpty()
                    )
                }

                override fun onAdDismissedFullScreenContent() {
                    AdLogger.d("GAM 插页广告关闭")
                    isShowing = false
                    totalCloseCount++
                    AdEventReporter.reportClose(
                        AdType.INTERSTITIAL,
                        AdPlatform.GAM,
                        adUnitId,
                        totalCloseCount,
                        interstitialAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                        currentAdValue?.currencyCode ?: "",
                        sessionId = sessionId
                    )
                    interstitialAd.adEventCallback = null

                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            try {
                                AdLogger.d("GAM 插页广告关闭，开始重新预缓存，广告位ID: %s", adUnitId)
                                AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(sessionId)
                                ) {
                                    preloadAd(activity.applicationContext, adUnitId)
                                }
                            } catch (e: Exception) {
                                AdLogger.e("GAM 插页广告重新预缓存失败", e)
                            }
                        }
                    }

                    if (continuation.isActive) {
                        continuation.resume(AdResult.Success(Unit))
                    }
                }

                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                    AdLogger.w("GAM 插页广告 show failed: %s", fullScreenContentError.message)
                    totalShowFailCount++
                    AdLogger.d("GAM 插页广告累积展示失败次数: $totalShowFailCount")
                    AdEventReporter.reportShowFail(
                        AdType.INTERSTITIAL,
                        AdPlatform.GAM,
                        adUnitId,
                        totalShowFailCount,
                        fullScreenContentError.message,
                        sessionId = sessionId,
                        isPreload = isPreload
                    )
                    interstitialAd.adEventCallback = null

                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            try {
                                AdLogger.d("GAM 插页广告展示失败，开始重新预缓存，广告位ID: %s", adUnitId)
                                AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(sessionId)
                                ) {
                                    preloadAd(activity.applicationContext, adUnitId)
                                }
                            } catch (e: Exception) {
                                AdLogger.e("GAM 插页广告重新预缓存失败", e)
                            }
                        }
                    }

                    if (continuation.isActive) {
                        continuation.resume(AdResult.Failure(createAdException("GAM interstitial: show callback failed: ${fullScreenContentError.message}")))
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    AdLogger.d("GAM 插页广告开始显示")
                    AdConfigManager.recordShow(AdType.INTERSTITIAL, AdPlatform.GAM)
                }

                override fun onAdClicked() {
                    AdLogger.d("GAM 插页广告被点击")
                    totalClickCount++
                    AdLogger.d("GAM 插页广告累积点击次数: $totalClickCount")
                    AdConfigManager.recordClick(AdType.INTERSTITIAL, AdPlatform.GAM)
                    AdClickProtectionController.recordClick(currentAdUniqueId)
                    AdEventReporter.reportClick(
                        AdType.INTERSTITIAL,
                        AdPlatform.GAM,
                        adUnitId,
                        currentAdUniqueId,
                        totalClickCount,
                        interstitialAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                        currentAdValue?.currencyCode ?: "",
                        sessionId = sessionId
                    )
                }

                override fun onAdImpression() {
                    AdLogger.d("GAM 插页广告展示完成")
                    isShowing = true
                    totalShowCount++
                    AdLogger.d("GAM 插页广告累积展示次数: $totalShowCount")
                }
            }

            interstitialAd.show(activity)
        }
    }

    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.clear()
        }
        AdLogger.d("GAM 插页广告已销毁")
    }

    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.adEventCallback = null
            AdLogger.d("GAM 插页广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    fun destroy() {
        destroyAd()
        AdLogger.d("GAM 插页广告控制器已清理")
    }

    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(
            code = 0,
            message = message,
            cause = cause
        )
    }

    fun isAdShowing(): Boolean {
        return isShowing
    }
}
