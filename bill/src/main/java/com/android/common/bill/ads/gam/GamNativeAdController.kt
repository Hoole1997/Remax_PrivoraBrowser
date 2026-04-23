package com.android.common.bill.ads.gam

import android.content.Context
import android.view.ViewGroup
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
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ui.NativeAdStyle
import com.android.common.bill.ui.gam.GamNativeAdView
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
 * GAM 原生广告控制器
 */
class GamNativeAdController private constructor() {

    private var totalClickCount by DataStoreIntDelegate("gam_native_ad_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("gam_native_ad_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("gam_native_ad_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("gam_native_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("gam_native_ad_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("gam_native_ad_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("gam_native_ad_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("gam_native_ad_total_shows", 0)

    private var currentAdValue: AdValue? = null

    companion object {
        private const val TAG = "GamNativeAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: GamNativeAdController? = null

        fun getInstance(): GamNativeAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GamNativeAdController().also { INSTANCE = it }
            }
        }
    }

    private val adCachePool = mutableListOf<CachedNativeAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT
    private val nativeAdView = GamNativeAdView()
    private var currentShowingAd: NativeAd? = null

    private data class CachedNativeAd(
        val ad: NativeAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }

    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.nativeId
        val cached = synchronized(adCachePool) {
            adCachePool.firstOrNull { it.adUnitId == finalAdUnitId && !it.isExpired() }
        }
        if (cached != null) {
            AdLogger.d("GAM 原生广告已有有效缓存，广告位ID: %s", finalAdUnitId)
            return AdResult.Success(Unit)
        }
        return loadAdToCache(context, finalAdUnitId)
    }

    suspend fun getAd(context: Context, adUnitId: String? = null): AdResult<NativeAd> {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.nativeId
        var cachedAd = pollCachedAd(finalAdUnitId)
        if (cachedAd == null) {
            AdLogger.d("GAM 缓存为空，立即加载原生广告，广告位ID: %s", finalAdUnitId)
            loadAdToCache(context, finalAdUnitId)
            cachedAd = pollCachedAd(finalAdUnitId)
        }
        return if (cachedAd != null) {
            AdLogger.d("GAM 使用缓存中的原生广告，广告位ID: %s", finalAdUnitId)
            AdResult.Success(cachedAd.ad)
        } else {
            AdResult.Failure(createAdException("GAM native ad no cached ad available"))
        }
    }

    suspend fun showAdInContainer(
        context: Context,
        container: ViewGroup,
        style: NativeAdStyle = BillConfig.gam.nativeStyleStandard,
        adUnitId: String? = null,
        sessionId: String = ""
    ): Boolean {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.nativeId
        val showSessionId = sessionId
        val showIsPreload = synchronized(adCachePool) { adCachePool.any { it.adUnitId == finalAdUnitId && !it.isExpired() } }

        totalShowTriggerCount++
        AdLogger.d("GAM 原生广告累积触发展示次数: $totalShowTriggerCount")

        (context as? FragmentActivity)?.let { activity ->
            AdDestroyManager.instance.register(activity) {
                AdLogger.d("GAM 原生广告: Activity 销毁，清理展示资源")
                destroyShowingAd()
                container.removeAllViews()
            }
        }

        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.NATIVE, AdPlatform.GAM, finalAdUnitId)
            }
        }

        return try {
            when (val result = getAd(context, adUnitId)) {
                is AdResult.Success -> {
                    val nativeAd = result.data
                    currentShowingAd = nativeAd
                    nativeAd.adEventCallback = createNativeAdEventCallback(
                        context = context,
                        adUnitId = finalAdUnitId,
                        nativeAd = nativeAd,
                        showSessionId = showSessionId,
                        showIsPreload = showIsPreload
                    )
                    val bindSuccess = nativeAdView.bindNativeAdToContainer(context, container, nativeAd, style)
                    if (bindSuccess) {
                        true
                    } else {
                        totalShowFailCount++
                        AdLogger.d("GAM 原生广告累积展示失败次数: $totalShowFailCount")
                        if (currentShowingAd == nativeAd) {
                            currentShowingAd = null
                        }
                        AdEventReporter.reportShowFail(
                            AdType.NATIVE,
                            AdPlatform.GAM,
                            finalAdUnitId,
                            totalShowFailCount,
                            "bind_failed",
                            sessionId = showSessionId,
                            isPreload = showIsPreload
                        )
                        false
                    }
                }

                is AdResult.Failure -> {
                    totalShowFailCount++
                    AdLogger.d("GAM 原生广告累积展示失败次数: $totalShowFailCount")
                    AdEventReporter.reportShowFail(AdType.NATIVE, AdPlatform.GAM, finalAdUnitId, totalShowFailCount, result.error.message, sessionId = showSessionId, isPreload = showIsPreload)
                    false
                }
            }
        } catch (e: Exception) {
            totalShowFailCount++
            AdLogger.d("GAM 原生广告累积展示失败次数: $totalShowFailCount")
            AdEventReporter.reportShowFail(AdType.NATIVE, AdPlatform.GAM, finalAdUnitId, totalShowFailCount, e.message.orEmpty(), sessionId = showSessionId, isPreload = showIsPreload)
            AdLogger.e("GAM 显示原生广告失败", e)
            false
        }
    }

    private suspend fun loadAd(context: Context, adUnitId: String): NativeAd? {
        totalLoadCount++
        AdLogger.d("GAM 原生广告累积加载次数: $totalLoadCount")
        val requestId = AdEventReporter.reportStartLoad(AdType.NATIVE, AdPlatform.GAM, adUnitId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            val videoOptions = VideoOptions.Builder().setStartMuted(true).build()
            val adRequest = NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE))
                .setAdChoicesPlacement(AdChoicesPlacement.TOP_RIGHT)
                .setMediaAspectRatio(NativeAd.NativeMediaAspectRatio.LANDSCAPE)
                .setVideoOptions(videoOptions)
                .build()

            NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.d("GAM 原生广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    AdEventReporter.reportLoaded(AdType.NATIVE, AdPlatform.GAM, adUnitId, totalLoadSucCount, nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(), ceil(loadTime / 1000.0).toInt(), requestId)
                    continuation.resume(nativeAd)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("GAM 原生 ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, loadTime, loadAdError.message)
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(AdType.NATIVE, AdPlatform.GAM, adUnitId, totalLoadFailCount, loadAdError.responseInfo?.loadedAdSourceResponseInfo?.name.orEmpty(), ceil(loadTime / 1000.0).toInt(), loadAdError.message, requestId)
                    continuation.resume(null)
                }
            })
        }
    }

    private fun createNativeAdEventCallback(
        context: Context,
        adUnitId: String,
        nativeAd: NativeAd,
        showSessionId: String,
        showIsPreload: Boolean
    ): NativeAdEventCallback {
        return object : NativeAdEventCallback {
            var currentAdUniqueId = ""

            override fun onAdPaid(adValue: AdValue) {
                AdLogger.d("GAM 原生广告收益回调: value=${adValue.valueMicros}, currency=${adValue.currencyCode}")
                val uuid = java.util.UUID.randomUUID().toString()
                val creativeId = nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceId.orEmpty()
                currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"
                currentAdValue = adValue

                AdEventReporter.reportImpression(
                    AdType.NATIVE,
                    AdPlatform.GAM,
                    adUnitId,
                    currentAdUniqueId,
                    totalShowCount,
                    nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                    currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                    currentAdValue?.currencyCode ?: "",
                    sessionId = showSessionId,
                    isPreload = showIsPreload
                )
                AdRevenueReporter.reportRevenue(
                    AdType.NATIVE,
                    AdPlatform.GAM,
                    adUnitId,
                    adValue.valueMicros / 1_000_000.0,
                    adValue.currencyCode,
                    nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "GAM",
                    nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceName.orEmpty()
                )
            }

            override fun onAdClicked() {
                AdLogger.d("GAM 原生广告被点击")
                totalClickCount++
                AdLogger.d("GAM 原生广告累积点击次数: $totalClickCount")
                AdConfigManager.recordClick(AdType.NATIVE, AdPlatform.GAM)
                AdClickProtectionController.recordClick(currentAdUniqueId)
                AdEventReporter.reportClick(
                    AdType.NATIVE,
                    AdPlatform.GAM,
                    adUnitId,
                    currentAdUniqueId,
                    totalClickCount,
                    nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                    currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                    currentAdValue?.currencyCode ?: "",
                    sessionId = showSessionId
                )
            }

            override fun onAdImpression() {
                AdLogger.d("GAM 原生广告展示完成")
                totalShowCount++
                AdLogger.d("GAM 原生广告累积展示次数: $totalShowCount")
                AdConfigManager.recordShow(AdType.NATIVE, AdPlatform.GAM)
                if (!isCacheFull(adUnitId)) {
                    GlobalScope.launch {
                        try {
                            AdLogger.d("GAM 原生广告曝光，开始预缓存，广告位ID: %s", adUnitId)
                            AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(showSessionId)
                            ) {
                                preloadAd(context, adUnitId)
                            }
                        } catch (e: Exception) {
                            AdLogger.e("GAM 原生广告预缓存失败", e)
                        }
                    }
                }
            }

            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                totalCloseCount++
                AdEventReporter.reportClose(
                    AdType.NATIVE,
                    AdPlatform.GAM,
                    adUnitId,
                    totalCloseCount,
                    nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                    currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                    currentAdValue?.currencyCode ?: "",
                    sessionId = showSessionId
                )
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
                    adCachePool.add(CachedNativeAd(nativeAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("GAM 原生广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("GAM native ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("GAM 原生 loadAdToCache 异常", e)
            AdResult.Failure(AdException(0, "GAM native ad loadAdToCache exception: ${e.message}", e))
        }
    }

    private fun pollCachedAd(adUnitId: String): CachedNativeAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool.removeAt(index) else null
        }
    }

    /**
     * 只读取缓存广告，不移除缓存池中的对象。
     */
    private fun peekCachedAd(adUnitId: String): CachedNativeAd? {
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

    fun getCachedAdPeek(adUnitId: String? = null): NativeAd? {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.nativeId
        return peekCachedAd(finalAdUnitId)?.ad
    }

    fun getCurrentAd(adUnitId: String? = null): NativeAd? {
        return getCachedAdPeek(adUnitId)
    }

    fun isAdLoaded(adUnitId: String? = null): Boolean {
        return getCachedAdPeek(adUnitId) != null
    }

    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.forEach { cachedAd -> cachedAd.ad.destroy() }
            adCachePool.clear()
        }
        AdLogger.d("GAM 原生广告已销毁")
    }

    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.destroy()
            AdLogger.d("GAM 原生广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    fun destroy() {
        destroyAd()
        AdLogger.d("GAM 原生广告控制器已清理")
    }

    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(code = 0, message = message, cause = cause)
    }
}
