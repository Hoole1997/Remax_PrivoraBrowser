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
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
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
 * GAM 开屏广告控制器
 */
class GamAppOpenAdController private constructor() {

    private var totalClickCount by DataStoreIntDelegate("gam_app_open_ad_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("gam_app_open_ad_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("gam_app_open_ad_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("gam_app_open_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("gam_app_open_ad_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("gam_app_open_ad_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("gam_app_open_ad_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("gam_app_open_ad_total_shows", 0)

    private var currentShowingAd: AppOpenAd? = null
    private var showContinuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null

    companion object {
        private const val TAG = "GamAppOpenAdController"
        private const val AD_TIMEOUT = 4 * 60 * 60 * 1000L
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 2

        @Volatile
        private var INSTANCE: GamAppOpenAdController? = null

        fun getInstance(): GamAppOpenAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GamAppOpenAdController().also { INSTANCE = it }
            }
        }
    }

    private val adCachePool = mutableListOf<CachedAppOpenAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT

    data class CachedAppOpenAd(
        val ad: AppOpenAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }

    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.splashId
        return loadAdToCache(context, finalAdUnitId)
    }

    private suspend fun loadAd(context: Context, adUnitId: String): AppOpenAd? {
        totalLoadCount++
        AdLogger.d("GAM 开屏广告累积加载次数: $totalLoadCount")
        val requestId = AdEventReporter.reportStartLoad(AdType.APP_OPEN, AdPlatform.GAM, adUnitId, totalLoadCount)
        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            val adRequest = AdRequest.Builder(adUnitId).build()
            val loadCallback = object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.d("GAM 开屏广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    AdEventReporter.reportLoaded(
                        adType = AdType.APP_OPEN,
                        platform = AdPlatform.GAM,
                        adUnitId = adUnitId,
                        number = totalLoadSucCount,
                        adSource = ad.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        passTime = ceil(loadTime / 1000.0).toInt(),
                        requestId = requestId
                    )
                    continuation.resume(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("GAM app open ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, loadTime, adError.message)
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(
                        adType = AdType.APP_OPEN,
                        platform = AdPlatform.GAM,
                        adUnitId = adUnitId,
                        number = totalLoadFailCount,
                        adSource = adError.responseInfo?.loadedAdSourceResponseInfo?.name.orEmpty(),
                        passTime = ceil(loadTime / 1000.0).toInt(),
                        reason = adError.message,
                        requestId = requestId
                    )
                    continuation.resume(null)
                }
            }
            AppOpenAd.load(adRequest, loadCallback)
        }
    }

    private suspend fun loadAdToCache(context: Context, adUnitId: String): AdResult<Unit> {
        return try {
            val currentAdUnitCount = getCachedAdCount(adUnitId)
            if (currentAdUnitCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("GAM 开屏广告位 %s 缓存已满，当前缓存: %d/%d", adUnitId, currentAdUnitCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }
            val appOpenAd = loadAd(context, adUnitId)
            if (appOpenAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedAppOpenAd(appOpenAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("GAM 开屏广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("GAM app open ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("GAM 开屏 loadAdToCache 异常", e)
            AdResult.Failure(AdException(0, "GAM app open ad loadAdToCache exception: ${e.message}", e))
        }
    }

    suspend fun showAd(
        activity: FragmentActivity,
        adUnitId: String? = null,
        onLoaded: ((isSuc: Boolean) -> Unit)? = null,
        sessionId: String = ""
    ): AdResult<Unit> {
        totalShowTriggerCount++
        AdLogger.d("GAM 开屏广告累积触发展示次数: $totalShowTriggerCount")

        val lifecycleGuard = AdLifecycleGuard.instance
        when (val lifecycleResult = lifecycleGuard.awaitResumeOrCancel(activity)) {
            is AdResult.Failure -> {
                AdLogger.w("GAM 开屏广告展示被取消：Activity 生命周期不满足")
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.APP_OPEN,
                    AdPlatform.GAM,
                    adUnitId ?: BillConfig.gam.splashId,
                    totalShowFailCount,
                    lifecycleResult.error.message,
                    sessionId = sessionId,
                    isPreload = false
                )
                onLoaded?.invoke(false)
                return lifecycleResult
            }

            else -> Unit
        }

        AdDestroyManager.instance.register(activity) {
            AdLogger.d("GAM 开屏广告: Activity 销毁，清理展示资源")
            destroyShowingAd()
            showContinuation?.let {
                if (it.isActive) {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(
                        AdType.APP_OPEN,
                        AdPlatform.GAM,
                        adUnitId ?: BillConfig.gam.splashId,
                        totalShowFailCount,
                        "Activity destroyed",
                        sessionId = sessionId,
                        isPreload = false
                    )
                    it.resume(AdResult.Failure(createAdException("GAM app open: Activity destroyed")))
                }
            }
            showContinuation = null
        }

        val finalAdUnitId = adUnitId ?: BillConfig.gam.splashId
        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.APP_OPEN, AdPlatform.GAM, finalAdUnitId)
            }
        }

        return try {
            var cachedAd = getCachedAd(finalAdUnitId)
            var isPreload = cachedAd != null
            if (cachedAd == null) {
                AdLogger.d("GAM 缓存为空，立即加载开屏广告，广告位ID: %s", finalAdUnitId)
                loadAdToCache(activity, finalAdUnitId)
                cachedAd = getCachedAd(finalAdUnitId)
                isPreload = false
            }

            if (cachedAd != null) {
                AdLogger.d("GAM 使用缓存中的开屏广告，广告位ID: %s", finalAdUnitId)
                onLoaded?.invoke(true)
                showAdInternal(activity, cachedAd.ad, finalAdUnitId, sessionId = sessionId, isPreload = isPreload)
            } else {
                onLoaded?.invoke(false)
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.APP_OPEN, AdPlatform.GAM, finalAdUnitId, totalShowFailCount, "No ad available", sessionId = sessionId, isPreload = false)
                AdResult.Failure(createAdException("GAM app open ad no available ad"))
            }
        } catch (e: Exception) {
            AdLogger.e("GAM 显示开屏广告异常", e)
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.APP_OPEN, AdPlatform.GAM, finalAdUnitId, totalShowFailCount, e.message.orEmpty(), sessionId = sessionId, isPreload = false)
            AdResult.Failure(createAdException("GAM app open: show exception: ${e.message}", e))
        }
    }

    private fun getCachedAd(adUnitId: String): CachedAppOpenAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool.removeAt(index) else null
        }
    }

    fun getCachedAdPeek(adUnitId: String): CachedAppOpenAd? {
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
        appOpenAd: AppOpenAd,
        adUnitId: String,
        sessionId: String = "",
        isPreload: Boolean = false
    ): AdResult<Unit> {
        currentShowingAd = appOpenAd
        return suspendCancellableCoroutine { continuation ->
            showContinuation = continuation
            continuation.invokeOnCancellation {
                showContinuation = null
            }
            var currentAdValue: AdValue? = null

            appOpenAd.adEventCallback = object : AppOpenAdEventCallback {
                var currentAdUniqueId = ""

                override fun onAdPaid(value: AdValue) {
                    AdLogger.d("GAM 开屏广告收益回调: value=${value.valueMicros}, currency=${value.currencyCode}")
                    val uuid = java.util.UUID.randomUUID().toString()
                    val creativeId = appOpenAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceId.orEmpty()
                    currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"
                    currentAdValue = value

                    AdEventReporter.reportImpression(
                        adType = AdType.APP_OPEN,
                        platform = AdPlatform.GAM,
                        adUnitId = adUnitId,
                        adUniqueId = currentAdUniqueId,
                        number = totalShowCount,
                        adSource = appOpenAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        value = (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
                        currency = currentAdValue?.currencyCode ?: "",
                        sessionId = sessionId,
                        isPreload = isPreload
                    )
                    AdRevenueReporter.reportRevenue(
                        AdType.APP_OPEN,
                        AdPlatform.GAM,
                        adUnitId,
                        value.valueMicros / 1_000_000.0,
                        value.currencyCode,
                        appOpenAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "GAM",
                        appOpenAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceName.orEmpty()
                    )
                }

                override fun onAdDismissedFullScreenContent() {
                    totalCloseCount++
                    AdLogger.d("GAM 开屏广告关闭")
                    AdEventReporter.reportClose(
                        adType = AdType.APP_OPEN,
                        platform = AdPlatform.GAM,
                        adUnitId = adUnitId,
                        number = totalCloseCount,
                        adSource = appOpenAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        value = (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
                        currency = currentAdValue?.currencyCode ?: "",
                        sessionId = sessionId
                    )
                    appOpenAd.adEventCallback = null

                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                            try {
                                AdLogger.d("GAM 开屏广告关闭，开始重新预缓存，广告位ID: %s", adUnitId)
                                AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(sessionId)
                                ) {
                                    preloadAd(activity.applicationContext, adUnitId)
                                }
                            } catch (e: Exception) {
                                AdLogger.e("GAM 开屏广告重新预缓存失败", e)
                            }
                        }
                    }

                    if (continuation.isActive) {
                        continuation.resume(AdResult.Success(Unit))
                    }
                }

                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                    AdLogger.w("GAM 开屏广告 show failed: %s", fullScreenContentError.message)
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(
                        adType = AdType.APP_OPEN,
                        platform = AdPlatform.GAM,
                        adUnitId = adUnitId,
                        number = totalShowFailCount,
                        reason = fullScreenContentError.message,
                        sessionId = sessionId,
                        isPreload = isPreload
                    )
                    appOpenAd.adEventCallback = null

                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                            try {
                                AdLogger.d("GAM 开屏广告展示失败，开始重新预缓存，广告位ID: %s", adUnitId)
                                AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(sessionId)
                                ) {
                                    preloadAd(activity.applicationContext, adUnitId)
                                }
                            } catch (e: Exception) {
                                AdLogger.e("GAM 开屏广告重新预缓存失败", e)
                            }
                        }
                    }

                    if (continuation.isActive) {
                        continuation.resume(AdResult.Failure(createAdException("GAM app open: show callback failed: ${fullScreenContentError.message}")))
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    AdLogger.d("GAM 开屏广告开始显示")
                    totalShowCount++
                    AdLogger.d("GAM 开屏广告累积展示次数: $totalShowCount")
                    AdConfigManager.recordShow(AdType.APP_OPEN, AdPlatform.GAM)
                }

                override fun onAdClicked() {
                    AdLogger.d("GAM 开屏广告被点击")
                    totalClickCount++
                    AdLogger.d("GAM 开屏广告累积点击次数: $totalClickCount")
                    AdConfigManager.recordClick(AdType.APP_OPEN, AdPlatform.GAM)
                    AdClickProtectionController.recordClick(currentAdUniqueId)
                    AdEventReporter.reportClick(
                        adType = AdType.APP_OPEN,
                        platform = AdPlatform.GAM,
                        adUnitId = adUnitId,
                        adUniqueId = currentAdUniqueId,
                        number = totalClickCount,
                        adSource = appOpenAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        value = (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
                        currency = currentAdValue?.currencyCode ?: "",
                        sessionId = sessionId
                    )
                }

                override fun onAdImpression() {
                    AdLogger.d("GAM 开屏广告展示完成")
                }
            }

            appOpenAd.show(activity)
        }
    }

    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.clear()
        }
        AdLogger.d("GAM 开屏广告已销毁")
    }

    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.adEventCallback = null
            AdLogger.d("GAM 开屏广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    fun destroy() {
        destroyAd()
        AdLogger.d("GAM 开屏广告控制器已清理")
    }

    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(code = 0, message = message, cause = cause)
    }
}
