package com.android.common.bill.ads.gam

import android.content.Context
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
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
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ui.gam.GamFullScreenNativeAdView
import com.blankj.utilcode.util.ActivityUtils
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesPlacement
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * GAM 全屏原生广告控制器
 */
class GamFullScreenNativeAdController private constructor() {

    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false

    @Volatile
    private var onAdDisplayedCallback: (() -> Unit)? = null

    private var totalClickCount by DataStoreIntDelegate("gam_fullscreen_native_ad_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("gam_fullscreen_native_ad_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("gam_fullscreen_native_ad_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("gam_fullscreen_native_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("gam_fullscreen_native_ad_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("gam_fullscreen_native_ad_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("gam_fullscreen_native_ad_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("gam_fullscreen_native_ad_total_shows", 0)

    private var currentAdValue: AdValue? = null
    private var isShowing: Boolean = false

    companion object {
        private const val TAG = "GamFullScreenNativeAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: GamFullScreenNativeAdController? = null

        fun getInstance(): GamFullScreenNativeAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GamFullScreenNativeAdController().also { INSTANCE = it }
            }
        }
    }

    private val adCachePool = mutableListOf<CachedFullScreenNativeAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT
    private var fullScreenNativeAd: NativeAd? = null
    private var loadTime: Long = 0L
    private val fullScreenAdView = GamFullScreenNativeAdView()
    private var currentShowingAd: NativeAd? = null
    var nativeAds: NativeAd? = null

    private data class CachedFullScreenNativeAd(
        val ad: NativeAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }

    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.fullNativeId
        val cached = synchronized(adCachePool) {
            adCachePool.firstOrNull { it.adUnitId == finalAdUnitId && !it.isExpired() }
        }
        if (cached != null) {
            AdLogger.d("GAM 全屏原生广告已有有效缓存，广告位ID: %s", finalAdUnitId)
            return AdResult.Success(Unit)
        }
        return loadAdToCache(context, finalAdUnitId)
    }

    fun closeEvent(adUnitId: String = "") {
        totalCloseCount++
        AdEventReporter.reportClose(
            AdType.FULL_SCREEN_NATIVE,
            AdPlatform.GAM,
            adUnitId,
            totalCloseCount,
            nativeAds?.getResponseInfo()?.loadedAdSourceResponseInfo?.name.orEmpty(),
            (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
            currentAdValue?.currencyCode ?: "",
            sessionId = currentSessionId
        )
        isShowing = false
        onAdDisplayedCallback = null
    }

    suspend fun getAd(context: Context, adUnitId: String? = null): AdResult<NativeAd> {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.fullNativeId
        var cachedAd = getCachedAd(finalAdUnitId)
        if (cachedAd == null) {
            AdLogger.d("GAM 缓存为空，立即加载全屏原生广告，广告位ID: %s", finalAdUnitId)
            AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(currentSessionId)
            ) {
                loadAdToCache(context, finalAdUnitId)
            }
            cachedAd = getCachedAd(finalAdUnitId)
        }
        return if (cachedAd != null) {
            AdLogger.d("GAM 使用缓存中的全屏原生广告，广告位ID: %s", finalAdUnitId)
            AdResult.Success(cachedAd.ad)
        } else {
            AdResult.Failure(createAdException("GAM full-screen native ad no cached ad available"))
        }
    }

    suspend fun showAdInContainer(
        context: Context,
        container: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        adUnitId: String? = null,
        sessionId: String = "",
        onAdDisplayed: (() -> Unit)? = null
    ): AdResult<Unit> {
        totalShowTriggerCount++
        (context as? FragmentActivity)?.let { activity ->
            AdDestroyManager.instance.register(activity) {
                AdLogger.d("GAM 全屏原生广告: Activity 销毁，清理展示资源")
                destroyShowingAd()
                container.removeAllViews()
                isShowing = false
            }
        }

        currentSessionId = sessionId
        currentIsPreload = hasCachedAd(adUnitId ?: BillConfig.gam.fullNativeId)
        onAdDisplayedCallback = onAdDisplayed

        synchronized(adCachePool) {
            val finalId = adUnitId ?: BillConfig.gam.fullNativeId
            if (adCachePool.any { it.adUnitId == finalId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.FULL_SCREEN_NATIVE, AdPlatform.GAM, finalId)
            }
        }

        return try {
            fullScreenAdView.createFullScreenLoadingView(context, container)
            when (val result = getAd(context, adUnitId)) {
                is AdResult.Success -> {
                    val success = fullScreenAdView.bindFullScreenNativeAdToContainer(context, container, result.data, lifecycleOwner)
                    if (success) {
                        currentShowingAd = result.data
                        AdResult.Success(Unit)
                    } else {
                        onAdDisplayedCallback = null
                        totalShowFailCount++
                        AdEventReporter.reportShowFail(
                            AdType.FULL_SCREEN_NATIVE,
                            AdPlatform.GAM,
                            adUnitId.orEmpty(),
                            totalShowFailCount,
                            "GAM full-screen native: ad bindView failed",
                            sessionId = currentSessionId,
                            isPreload = currentIsPreload
                        )
                        AdResult.Failure(AdException(code = -1, message = "GAM full-screen native: ad bindView failed"))
                    }
                }

                is AdResult.Failure -> {
                    onAdDisplayedCallback = null
                    totalShowFailCount++
                    AdLogger.e("GAM 全屏原生 ad load failed: %s", result.error.message)
                    AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.GAM, adUnitId.orEmpty(), totalShowFailCount, result.error.message, sessionId = currentSessionId, isPreload = currentIsPreload)
                    AdResult.Failure(result.error)
                }
            }
        } catch (e: Exception) {
            onAdDisplayedCallback = null
            totalShowFailCount++
            AdLogger.e("GAM 显示全屏原生广告失败", e)
            AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.GAM, adUnitId.orEmpty(), totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
            AdResult.Failure(AdException(code = -2, message = "show full-screen native ad exception: ${e.message}", cause = e))
        }
    }

    private fun getCachedAd(adUnitId: String): CachedFullScreenNativeAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool.removeAt(index) else null
        }
    }

    private fun peekCachedAd(adUnitId: String): CachedFullScreenNativeAd? {
        synchronized(adCachePool) {
            return adCachePool.firstOrNull { it.adUnitId == adUnitId && !it.isExpired() }
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

    fun hasCachedAd(adUnitId: String? = null): Boolean {
        synchronized(adCachePool) {
            return if (adUnitId != null) {
                adCachePool.any { it.adUnitId == adUnitId && !it.isExpired() }
            } else {
                adCachePool.any { !it.isExpired() }
            }
        }
    }

    private suspend fun loadAdToCache(context: Context, adUnitId: String): AdResult<Unit> {
        return try {
            val currentAdUnitCount = getCachedAdCount(adUnitId)
            if (currentAdUnitCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("GAM 广告位 %s 缓存已满，当前缓存: %d/%d", adUnitId, currentAdUnitCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }
            val nativeAd = loadAd(context, adUnitId)
            if (nativeAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedFullScreenNativeAd(nativeAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("GAM 全屏原生广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("GAM full-screen native ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("GAM 全屏原生 loadAdToCache 异常", e)
            AdResult.Failure(AdException(0, "GAM full-screen native ad loadAdToCache exception: ${e.message}", e))
        }
    }

    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(code = -1, message = message, cause = cause)
    }

    private suspend fun loadAd(context: Context, adUnitId: String): NativeAd? {
        totalLoadCount++
        AdLogger.d("GAM 全屏原生广告累积加载次数: $totalLoadCount")
        val requestId = AdEventReporter.reportStartLoad(AdType.FULL_SCREEN_NATIVE, AdPlatform.GAM, adUnitId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            val videoOptions = VideoOptions.Builder().setStartMuted(true).build()
            val adRequest = NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE))
                .setAdChoicesPlacement(AdChoicesPlacement.TOP_RIGHT)
                .setMediaAspectRatio(NativeAd.NativeMediaAspectRatio.LANDSCAPE)
                .setVideoOptions(videoOptions)
                .build()

            NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {
                var currentAdUniqueId = ""

                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    nativeAds = nativeAd
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.d("GAM 全屏原生广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    AdEventReporter.reportLoaded(AdType.FULL_SCREEN_NATIVE, AdPlatform.GAM, adUnitId, totalLoadSucCount, nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(), ceil(loadTime / 1000.0).toInt(), requestId)

                    nativeAd.adEventCallback = object : NativeAdEventCallback {
                        var hasImpressionCounted = false

                        override fun onAdPaid(adValue: AdValue) {
                            AdLogger.d("GAM 全屏原生广告收益回调: value=${adValue.valueMicros}, currency=${adValue.currencyCode}")
                            val uuid = java.util.UUID.randomUUID().toString()
                            val creativeId = nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceId.orEmpty()
                            currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"
                            currentAdValue = adValue

                            if (!hasImpressionCounted) {
                                totalShowCount++
                                hasImpressionCounted = true
                                AdLogger.d("GAM 全屏原生广告累积展示次数: $totalShowCount")
                                AdConfigManager.recordShow(AdType.FULL_SCREEN_NATIVE, AdPlatform.GAM)
                            }

                            AdEventReporter.reportImpression(
                                AdType.FULL_SCREEN_NATIVE,
                                AdPlatform.GAM,
                                adUnitId,
                                currentAdUniqueId,
                                totalShowCount,
                                nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                                currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                                currentAdValue?.currencyCode ?: "",
                                sessionId = currentSessionId,
                                isPreload = currentIsPreload
                            )
                            AdRevenueReporter.reportRevenue(
                                AdType.FULL_SCREEN_NATIVE,
                                AdPlatform.GAM,
                                adUnitId,
                                adValue.valueMicros / 1_000_000.0,
                                adValue.currencyCode,
                                nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "GAM",
                                nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceName.orEmpty()
                            )
                        }

                        override fun onAdClicked() {
                            AdLogger.d("GAM 全屏原生广告被点击")
                            totalClickCount++
                            AdLogger.d("GAM 全屏原生广告累积点击次数: $totalClickCount")
                            AdConfigManager.recordClick(AdType.FULL_SCREEN_NATIVE, AdPlatform.GAM)
                            AdClickProtectionController.recordClick(currentAdUniqueId)
                            AdEventReporter.reportClick(
                                AdType.FULL_SCREEN_NATIVE,
                                AdPlatform.GAM,
                                adUnitId,
                                currentAdUniqueId,
                                totalClickCount,
                                nativeAds?.getResponseInfo()?.loadedAdSourceResponseInfo?.name.orEmpty(),
                                currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                                currentAdValue?.currencyCode ?: "",
                                sessionId = currentSessionId
                            )
                        }

                        override fun onAdImpression() {
                            AdLogger.d("GAM 全屏原生广告展示完成")
                            isShowing = true
                            if (!isCacheFull(adUnitId)) {
                                GlobalScope.launch {
                                    try {
                                        AdLogger.d("GAM 全屏原生广告曝光，开始预缓存，广告位ID: %s", adUnitId)
                                        AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(currentSessionId)
                                        ) {
                                            preloadAd(context, adUnitId)
                                        }
                                    } catch (e: Exception) {
                                        AdLogger.e("GAM 全屏原生广告预缓存失败", e)
                                    }
                                }
                            }
                            AdLogger.d("GAM 全屏原生广告显示成功")
                            notifyAdDisplayedIfNeeded()
                        }

                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            onAdDisplayedCallback = null
                            totalCloseCount++
                            AdEventReporter.reportClose(
                                AdType.FULL_SCREEN_NATIVE,
                                AdPlatform.GAM,
                                adUnitId,
                                totalCloseCount,
                                nativeAds?.getResponseInfo()?.loadedAdSourceResponseInfo?.name.orEmpty(),
                                (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
                                currentAdValue?.currencyCode ?: "",
                                sessionId = currentSessionId
                            )
                        }
                    }

                    continuation.resume(nativeAd)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("GAM 全屏原生 ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, loadTime, loadAdError.message)
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.GAM, adUnitId, totalLoadFailCount, loadAdError.responseInfo?.loadedAdSourceResponseInfo?.name.orEmpty(), ceil(loadTime / 1000.0).toInt(), loadAdError.message, requestId)
                    continuation.resume(null)
                }
            })
        }
    }

    private fun notifyAdDisplayedIfNeeded() {
        val callback = onAdDisplayedCallback ?: return
        onAdDisplayedCallback = null
        try {
            callback.invoke()
        } catch (e: Exception) {
            AdLogger.e("GAM 全屏原生 onAdDisplayed 回调异常", e)
        }
    }

    fun getCachedAdPeek(adUnitId: String? = null): NativeAd? {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.fullNativeId
        return peekCachedAd(finalAdUnitId)?.ad
    }

    fun getCurrentAd(adUnitId: String? = null): NativeAd? {
        return getCachedAdPeek(adUnitId)
    }

    fun isAdLoaded(adUnitId: String? = null): Boolean {
        return getCachedAdPeek(adUnitId) != null
    }

    fun isAdExpired(adUnitId: String? = null): Boolean {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.fullNativeId
        val expired = synchronized(adCachePool) {
            adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }
        }
        if (expired) {
            AdLogger.d("GAM 全屏原生广告已过期")
        }
        return expired
    }

    fun getRemainingTime(adUnitId: String? = null): Long {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.fullNativeId
        val cachedAd = peekCachedAd(finalAdUnitId) ?: return 0L
        val remaining = AD_TIMEOUT - (System.currentTimeMillis() - cachedAd.loadTime)
        return if (remaining > 0) remaining else 0L
    }

    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.forEach { cachedAd -> cachedAd.ad.destroy() }
            adCachePool.clear()
        }
        fullScreenNativeAd = null
        loadTime = 0L
        AdLogger.d("GAM 全屏原生广告已销毁")
    }

    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.destroy()
            AdLogger.d("GAM 全屏原生广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    fun destroy() {
        destroyAd()
        AdLogger.d("GAM 全屏原生广告控制器已清理")
    }

    fun isAdShowing(): Boolean {
        return isShowing
    }
}
