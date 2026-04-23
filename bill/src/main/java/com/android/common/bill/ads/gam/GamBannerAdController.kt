package com.android.common.bill.ads.gam

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.View
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
import com.blankj.utilcode.util.ScreenUtils
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlinx.coroutines.suspendCancellableCoroutine
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * GAM Banner 广告控制器
 */
class GamBannerAdController private constructor() {

    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false
    private var totalClickCount by DataStoreIntDelegate("gam_banner_ad_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("gam_banner_ad_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("gam_banner_ad_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("gam_banner_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("gam_banner_ad_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("gam_banner_ad_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("gam_banner_ad_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("gam_banner_ad_total_shows", 0)
    private var currentAdValue: AdValue? = null

    companion object {
        private const val TAG = "GamBannerAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: GamBannerAdController? = null

        fun getInstance(): GamBannerAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GamBannerAdController().also { INSTANCE = it }
            }
        }
    }

    private val adCachePool = mutableListOf<CachedBannerAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT
    private var currentShowingAd: BannerAd? = null

    private data class CachedBannerAd(
        val bannerAd: BannerAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }

    private fun getAdSize(context: Context): AdSize {
        val widthPixels = ScreenUtils.getScreenWidth()
        val density = Resources.getSystem().displayMetrics.density
        val adWidth = (widthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
    }

    private fun getCachedAd(adUnitId: String): CachedBannerAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool.removeAt(index) else null
        }
    }

    /**
     * 仅查看缓存（不移除）以获取指定广告位的一个广告。
     */
    private fun peekCachedAd(adUnitId: String): CachedBannerAd? {
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

    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(code = -1, message = message, cause = cause)
    }

    private suspend fun loadAdInternal(context: Context, adUnitId: String): BannerAd? {
        totalLoadCount++
        AdLogger.d("GAM Banner 广告累积加载次数: $totalLoadCount")
        val requestId = AdEventReporter.reportStartLoad(AdType.BANNER, AdPlatform.GAM, adUnitId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            val loadStartTime = System.currentTimeMillis()
            val adRequest = BannerAdRequest.Builder(adUnitId, getAdSize(context))
                .setGoogleExtrasBundle(Bundle())
                .build()

            BannerAd.load(adRequest, object : AdLoadCallback<BannerAd> {
                var currentAdUniqueId = ""

                override fun onAdLoaded(ad: BannerAd) {
                    val loadTime = System.currentTimeMillis() - loadStartTime
                    AdLogger.d("GAM Banner 广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    AdEventReporter.reportLoaded(
                        AdType.BANNER,
                        AdPlatform.GAM,
                        adUnitId,
                        totalLoadSucCount,
                        ad.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                        ceil(loadTime / 1000.0).toInt(),
                        requestId
                    )

                    ad.adEventCallback = object : BannerAdEventCallback {
                        override fun onAdPaid(adValue: AdValue) {
                            AdLogger.d("GAM Banner 广告收益回调: value=${adValue.valueMicros}, currency=${adValue.currencyCode}")
                            val uuid = java.util.UUID.randomUUID().toString()
                            val creativeId = ad.getResponseInfo().loadedAdSourceResponseInfo?.instanceId.orEmpty()
                            currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"
                            currentAdValue = adValue
                            AdEventReporter.reportImpression(
                                AdType.BANNER,
                                AdPlatform.GAM,
                                adUnitId,
                                currentAdUniqueId,
                                totalShowCount,
                                ad.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                                currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                                currentAdValue?.currencyCode ?: "",
                                sessionId = currentSessionId,
                                isPreload = currentIsPreload
                            )
                            AdRevenueReporter.reportRevenue(
                                AdType.BANNER,
                                AdPlatform.GAM,
                                adUnitId,
                                adValue.valueMicros / 1_000_000.0,
                                adValue.currencyCode,
                                ad.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "GAM",
                                ad.getResponseInfo().loadedAdSourceResponseInfo?.instanceName.orEmpty()
                            )
                        }

                        override fun onAdClicked() {
                            AdLogger.d("GAM Banner 广告被点击")
                            totalClickCount++
                            AdLogger.d("GAM Banner 广告累积点击次数: $totalClickCount")
                            AdConfigManager.recordClick(AdType.BANNER, AdPlatform.GAM)
                            AdClickProtectionController.recordClick(currentAdUniqueId)
                            AdEventReporter.reportClick(
                                AdType.BANNER,
                                AdPlatform.GAM,
                                adUnitId,
                                currentAdUniqueId,
                                totalClickCount,
                                ad.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                                currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                                currentAdValue?.currencyCode ?: "",
                                sessionId = currentSessionId
                            )
                        }

                        override fun onAdImpression() {
                            AdLogger.d("GAM Banner 广告展示完成")
                            totalShowCount++
                            AdLogger.d("GAM Banner 广告累积展示次数: $totalShowCount")
                            AdConfigManager.recordShow(AdType.BANNER, AdPlatform.GAM)
                        }

                        override fun onAdDismissedFullScreenContent() {
                            AdLogger.d("GAM Banner 广告关闭")
                            totalCloseCount++
                            AdEventReporter.reportClose(
                                AdType.BANNER,
                                AdPlatform.GAM,
                                adUnitId,
                                totalCloseCount,
                                ad.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                                (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
                                currentAdValue?.currencyCode ?: "",
                                sessionId = currentSessionId
                            )
                        }
                    }

                    continuation.resume(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    val loadTime = System.currentTimeMillis() - loadStartTime
                    AdLogger.e("GAM Banner ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, loadTime, error.message)
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(
                        AdType.BANNER,
                        AdPlatform.GAM,
                        adUnitId,
                        totalLoadFailCount,
                        error.responseInfo?.loadedAdSourceResponseInfo?.name.orEmpty(),
                        ceil(loadTime / 1000.0).toInt(),
                        error.message,
                        requestId
                    )
                    continuation.resume(null)
                }
            })
        }
    }

    private suspend fun loadAdToCache(context: Context, adUnitId: String): AdResult<Unit> {
        return try {
            val currentAdUnitCount = getCachedAdCount(adUnitId)
            if (currentAdUnitCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("GAM 广告位 %s 缓存已满，当前缓存: %d/%d", adUnitId, currentAdUnitCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }
            val loadedBannerAd = loadAdInternal(context, adUnitId)
            if (loadedBannerAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedBannerAd(loadedBannerAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("GAM Banner 广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("GAM banner ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("GAM Banner loadAdToCache 异常", e)
            AdResult.Failure(AdException(0, "GAM banner ad loadAdToCache exception: ${e.message}", e))
        }
    }

    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.bannerId
        return loadAdToCache(context, finalAdUnitId)
    }

    suspend fun showAd(
        context: FragmentActivity,
        container: ViewGroup,
        adUnitId: String? = null,
        sessionId: String = ""
    ): AdResult<View> {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.bannerId
        totalShowTriggerCount++
        AdLogger.d("GAM Banner 广告累积触发展示次数: $totalShowTriggerCount")

        AdDestroyManager.instance.register(context) {
            AdLogger.d("GAM Banner 广告: Activity 销毁，清理展示资源")
            destroyShowingAd()
            container.removeAllViews()
        }

        currentSessionId = sessionId
        currentIsPreload = synchronized(adCachePool) { adCachePool.any { it.adUnitId == finalAdUnitId && !it.isExpired() } }

        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.BANNER, AdPlatform.GAM, finalAdUnitId)
            }
        }

        return try {
            var cachedAd = getCachedAd(finalAdUnitId)
            if (cachedAd == null) {
                AdLogger.d("GAM 缓存为空，立即加载 Banner 广告，广告位ID: %s", finalAdUnitId)
                loadAdToCache(context, finalAdUnitId)
                cachedAd = getCachedAd(finalAdUnitId)
            }

            if (cachedAd != null) {
                AdLogger.d("GAM 使用缓存中的 Banner 广告，广告位ID: %s", finalAdUnitId)
                container.removeAllViews()
                val bannerAdView = cachedAd.bannerAd.getView(context)
                if (bannerAdView != null) {
                    (bannerAdView.parent as? ViewGroup)?.removeView(bannerAdView)
                    container.addView(bannerAdView)
                    currentShowingAd = cachedAd.bannerAd
                    AdResult.Success(bannerAdView)
                } else {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(
                        AdType.BANNER,
                        AdPlatform.GAM,
                        finalAdUnitId,
                        totalShowFailCount,
                        "GAM banner: ad view is null",
                        sessionId = currentSessionId,
                        isPreload = currentIsPreload
                    )
                    AdResult.Failure(createAdException("GAM banner: ad view is null"))
                }
            } else {
                totalShowFailCount++
                AdLogger.d("GAM Banner 广告累积展示失败次数: $totalShowFailCount")
                AdEventReporter.reportShowFail(
                    AdType.BANNER,
                    AdPlatform.GAM,
                    finalAdUnitId,
                    totalShowFailCount,
                    "No fill",
                    sessionId = currentSessionId,
                    isPreload = currentIsPreload
                )
                AdResult.Failure(createAdException("GAM banner ad no cached ad available"))
            }
        } catch (e: Exception) {
            totalShowFailCount++
            AdEventReporter.reportShowFail(
                AdType.BANNER,
                AdPlatform.GAM,
                finalAdUnitId,
                totalShowFailCount,
                e.message.orEmpty(),
                sessionId = currentSessionId,
                isPreload = currentIsPreload
            )
            AdLogger.e("GAM 显示 Banner 广告失败", e)
            container.removeAllViews()
            AdResult.Failure(
                AdException(
                    code = -1,
                    message = "show banner ad exception: ${e.message}",
                    cause = e
                )
            )
        }
    }

    fun getCurrentAdView(): BannerAd? {
        return getCachedAdPeek()
    }

    fun getCachedAdPeek(adUnitId: String? = null): BannerAd? {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.bannerId
        return peekCachedAd(finalAdUnitId)?.bannerAd
    }

    fun isAdLoaded(): Boolean {
        return getCachedAdPeek() != null
    }

    fun isAdExpired(adUnitId: String? = null): Boolean {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.bannerId
        val expired = synchronized(adCachePool) {
            adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }
        }
        if (expired) {
            AdLogger.d("GAM Banner 广告已过期")
        }
        return expired
    }

    fun getRemainingTime(adUnitId: String? = null): Long {
        val finalAdUnitId = adUnitId ?: BillConfig.gam.bannerId
        val cachedAd = peekCachedAd(finalAdUnitId) ?: return 0L
        val remaining = AD_TIMEOUT - (System.currentTimeMillis() - cachedAd.loadTime)
        return if (remaining > 0) remaining else 0L
    }

    fun pauseAd() {
        AdLogger.d("GAM Banner 广告已暂停")
    }

    fun resumeAd() {
        AdLogger.d("GAM Banner 广告已恢复")
    }

    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.forEach { cachedAd -> cachedAd.bannerAd.destroy() }
            adCachePool.clear()
        }
        AdLogger.d("GAM Banner 广告已销毁")
    }

    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.destroy()
            AdLogger.d("GAM Banner 广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    fun destroy() {
        destroyAd()
        AdLogger.d("GAM Banner 广告控制器已清理")
    }
}
