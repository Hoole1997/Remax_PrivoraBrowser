package com.android.common.bill.ads.admob

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.blankj.utilcode.util.ScreenUtils
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.tracker.AdRevenueReporter
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.PositionGet
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * Banner广告控制器
 * 提供标准Banner广告显示功能
 */
class AdmobBannerAdController private constructor() {
    
    // 当前展示的sessionId和isPreload（用于传递到回调）
    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false

    // 累积点击统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("banner_ad_total_clicks", 0)

    // 累积关闭统计（持久化）
    private var totalCloseCount by DataStoreIntDelegate("banner_ad_total_close", 0)
    
    // 累积加载次数统计（持久化）
    private var totalLoadCount by DataStoreIntDelegate("banner_ad_total_loads", 0)

    // 累积加载成功次数统计（持久化）
    private var totalLoadSucCount by DataStoreIntDelegate("banner_ad_total_load_suc", 0)

    // 累积加载失败次数统计（持久化）
    private var totalLoadFailCount by DataStoreIntDelegate("banner_ad_total_load_fails", 0)
    
    // 累积展示失败次数统计（持久化）
    private var totalShowFailCount by DataStoreIntDelegate("banner_ad_total_show_fails", 0)
    
    // 累积触发统计（持久化）
    private var totalShowTriggerCount by DataStoreIntDelegate("banner_ad_total_show_triggers", 0)
    
    // 累积展示统计（持久化）
    private var totalShowCount by DataStoreIntDelegate("banner_ad_total_shows", 0)
    
    // 当前广告的收益信息（临时存储）
    private var currentAdValue: AdValue? = null
    
    companion object {
        private const val TAG = "AdmobBannerAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1
        
        @Volatile
        private var INSTANCE: AdmobBannerAdController? = null
        
        fun getInstance(): AdmobBannerAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobBannerAdController().also { INSTANCE = it }
            }
        }
    }
    
    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedBannerAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT
    
    /**
     * 缓存的Banner广告数据类
     */
    private data class CachedBannerAd(
        val bannerAd: BannerAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: BannerAd? = null

    
    /**
     * 获取自适应Banner广告尺寸
     */
    private fun getAdSize(context: Context): AdSize {
        val widthPixels = ScreenUtils.getScreenWidth()
        val density = Resources.getSystem().displayMetrics.density
        val adWidth = (widthPixels / density).toInt()
        val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context,adWidth)

        return adSize
    }
    
    /**
     * 从缓存获取广告
     */
    private fun getCachedAd(adUnitId: String): CachedBannerAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) {
                adCachePool.removeAt(index)
            } else {
                null
            }
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
    
    /**
     * 获取指定广告位的缓存数量
     */
    private fun getCachedAdCount(adUnitId: String): Int {
        synchronized(adCachePool) {
            return adCachePool.count { it.adUnitId == adUnitId && !it.isExpired() }
        }
    }
    
    /**
     * 检查指定广告位的缓存是否已满
     */
    private fun isCacheFull(adUnitId: String): Boolean {
        return getCachedAdCount(adUnitId) >= maxCacheSizePerAdUnit
    }
    
    /**
     * 创建广告异常
     */
    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(
            code = -1,
            message = message,
            cause = cause
        )
    }
    
    /**
     * 加载Banner广告
     * @param context 上下文
     * @param adUnitId 广告位ID
     */
    private suspend fun loadAdInternal(context: Context, adUnitId: String): BannerAd? {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("AdmobBanner广告累积加载次数: $totalLoadCount")
        
        val requestId = AdEventReporter.reportStartLoad(AdType.BANNER, AdPlatform.ADMOB, adUnitId, totalLoadCount)
        
        return suspendCancellableCoroutine { continuation ->
            val loadStartTime = System.currentTimeMillis()
            
            val adRequest = BannerAdRequest.Builder(adUnitId, getAdSize(context))
                .setGoogleExtrasBundle(Bundle())
                .build()
            
            BannerAd.load(adRequest, object : AdLoadCallback<BannerAd> {
                // 每次展示生成唯一 adUniqueId（用于点击保护）
                var currentAdUniqueId = ""
                override fun onAdLoaded(ad: BannerAd) {
                    val loadTime = System.currentTimeMillis() - loadStartTime
                    AdLogger.d("AdmobBanner广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    AdEventReporter.reportLoaded(AdType.BANNER, AdPlatform.ADMOB, adUnitId, totalLoadSucCount, ad.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(), ceil(loadTime / 1000.0).toInt(), requestId)

                    // 设置事件回调
                    ad.adEventCallback = object : BannerAdEventCallback {
                        override fun onAdPaid(adValue: AdValue) {
                            AdLogger.d("AdmobBanner广告收益回调: value=${adValue.valueMicros}, currency=${adValue.currencyCode}")

                            // adUniqueId = uuid + 广告位id + 创意id
                            val uuid = java.util.UUID.randomUUID().toString()
                            val creativeId = ad.getResponseInfo().loadedAdSourceResponseInfo?.instanceId.orEmpty()
                            currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"

                            // 存储当前广告的收益信息
                            currentAdValue = adValue

                            AdEventReporter.reportImpression(AdType.BANNER, AdPlatform.ADMOB, adUnitId, currentAdUniqueId, totalShowCount, ad.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(), currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0, currentAdValue?.currencyCode ?: "", sessionId = currentSessionId, isPreload = currentIsPreload)

                            // 上报真实的广告收益数据
                            AdRevenueReporter.reportRevenue(AdType.BANNER, AdPlatform.ADMOB, adUnitId, adValue.valueMicros / 1_000_000.0, adValue.currencyCode, ad.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "Admob", ad.getResponseInfo().loadedAdSourceResponseInfo?.instanceName.orEmpty())
                        }
                        
                        override fun onAdClicked() {
                            AdLogger.d("AdmobBanner广告被点击")
                            
                            // 累积点击统计
                            totalClickCount++
                            AdLogger.d("AdmobBanner广告累积点击次数: $totalClickCount")
                            
                            AdConfigManager.recordClick(AdType.BANNER, AdPlatform.ADMOB)

                            // 记录点击用于重复点击保护
                            AdClickProtectionController.recordClick(currentAdUniqueId)
                            
                            AdEventReporter.reportClick(AdType.BANNER, AdPlatform.ADMOB, adUnitId, currentAdUniqueId, totalClickCount, ad.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(), currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0, currentAdValue?.currencyCode ?: "", sessionId = currentSessionId)
                        }
                        
                        override fun onAdImpression() {
                            AdLogger.d("AdmobBanner广告展示完成")
                            
                            // 累积展示统计
                            totalShowCount++
                            AdLogger.d("AdmobBanner广告累积展示次数: $totalShowCount")

                            // 记录展示（在SDK回调中）
                            AdConfigManager.recordShow(AdType.BANNER, AdPlatform.ADMOB)
                        }

                        override fun onAdDismissedFullScreenContent() {
                            AdLogger.d("AdmobBanner广告关闭")
                            totalCloseCount++
                            AdEventReporter.reportClose(AdType.BANNER, AdPlatform.ADMOB, adUnitId, totalCloseCount, ad.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(), (currentAdValue?.valueMicros ?: 0) / 1_000_000.0, currentAdValue?.currencyCode ?: "", sessionId = currentSessionId)
                        }
                    }
                    
                    continuation.resume(ad)
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    val loadTime = System.currentTimeMillis() - loadStartTime
                    AdLogger.e("AdmobBannerad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, loadTime, error.message)
                    
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(AdType.BANNER, AdPlatform.ADMOB, adUnitId, totalLoadFailCount, error.responseInfo?.loadedAdSourceResponseInfo?.name.orEmpty(), ceil(loadTime / 1000.0).toInt(), error.message, requestId)
                    
                    continuation.resume(null)
                }
            })
        }
    }
    
    /**
     * 加载广告到缓存
     */
    private suspend fun loadAdToCache(context: Context, adUnitId: String): AdResult<Unit> {
        return try {
            // 检查缓存是否已满
            val currentAdUnitCount = getCachedAdCount(adUnitId)
            if (currentAdUnitCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("Admob广告位 %s 缓存已满，当前缓存: %d/%d", adUnitId, currentAdUnitCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }
            val loadedBannerAd = loadAdInternal(context, adUnitId)
            if (loadedBannerAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedBannerAd(loadedBannerAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("AdmobBanner广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Admob banner ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("AdmobBanner loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "Admob banner ad loadAdToCache exception: ${e.message}", e))
        }
    }
    
    /**
     * 预加载Banner广告（可选，用于提前准备）
     * @param context 上下文
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     */
    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.bannerId
        return loadAdToCache(context, finalAdUnitId)
    }
    
    /**
     * 显示Banner广告（自动处理加载）
     * @param context 上下文
     * @param container 目标容器
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     */
    suspend fun showAd(context: FragmentActivity, container: ViewGroup, adUnitId: String? = null, sessionId: String = ""): AdResult<View> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.bannerId
        
        // 累积触发统计
        totalShowTriggerCount++
        AdLogger.d("AdmobBanner广告累积触发展示次数: $totalShowTriggerCount")

        // 拦截器检查
// 注册 Activity 销毁时的清理回调
        AdDestroyManager.instance.register(context) {
            AdLogger.d("AdmobBanner广告: Activity销毁，清理展示资源")
            // 先销毁正在显示的广告对象
            destroyShowingAd()
            // 再移除已添加的广告 View
            container.removeAllViews()
        }
        
        // 设置sessionId和isPreload
        currentSessionId = sessionId
        currentIsPreload = synchronized(adCachePool) { adCachePool.any { it.adUnitId == finalAdUnitId && !it.isExpired() } }

        // 检查缓存过期
        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.BANNER, AdPlatform.ADMOB, finalAdUnitId)
            }
        }

        return try {
            // 1. 尝试从缓存获取广告
            var cachedAd = getCachedAd(finalAdUnitId)
            if (cachedAd == null) {
                AdLogger.d("Admob缓存为空，立即加载Banner广告，广告位ID: %s", finalAdUnitId)
                loadAdToCache(context, finalAdUnitId)
                cachedAd = getCachedAd(finalAdUnitId)
            }
            
            if (cachedAd != null) {
                AdLogger.d("Admob使用缓存中的Banner广告，广告位ID: %s", finalAdUnitId)
                
                // 显示加载视图
                container.removeAllViews()
                
                // 获取 BannerAd 的 View 并添加到容器
                val bannerAdView = cachedAd.bannerAd.getView(context)
                if (bannerAdView != null) {
                    // 从父容器移除（如果有）
                    (bannerAdView.parent as? ViewGroup)?.removeView(bannerAdView)
                    container.addView(bannerAdView)
                    // 记录当前显示的广告（用于资源释放）
                    currentShowingAd = cachedAd.bannerAd
                    // recordShow 已移到 onAdImpression 回调中
                    AdResult.Success(bannerAdView)
                } else {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, "Admob banner: ad view is null", sessionId = currentSessionId, isPreload = currentIsPreload)
                    AdResult.Failure(createAdException("Admob banner: ad view is null"))
                }
            } else {
                // 累积展示失败次数统计
                totalShowFailCount++
                AdLogger.d("AdmobBanner广告累积展示失败次数: $totalShowFailCount")

                AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, "No fill", sessionId = currentSessionId, isPreload = currentIsPreload)

                AdResult.Failure(createAdException("Admob banner ad no cached ad available"))
            }
        } catch (e: Exception) {
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
            AdLogger.e("Admob显示Banner广告失败", e)
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
    

    
    /**
     * 获取当前广告视图
     */
    fun getCurrentAdView(): BannerAd? {
        return getCachedAdPeek()
    }

    /**
     * 只读查看缓存中的广告，不移除缓存。
     */
    fun getCachedAdPeek(adUnitId: String? = null): BannerAd? {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.bannerId
        return peekCachedAd(finalAdUnitId)?.bannerAd
    }
    
    /**
     * 检查是否有可用的广告
     */
    fun isAdLoaded(): Boolean {
        return getCachedAdPeek() != null
    }
    
    /**
     * 检查广告是否已过期
     */
    fun isAdExpired(adUnitId: String? = null): Boolean {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.bannerId
        val expired = synchronized(adCachePool) {
            adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }
        }
        if (expired) {
            AdLogger.d("Admob Banner广告已过期")
        }
        return expired
    }
    

    
    /**
     * 获取剩余有效时间（毫秒）
     */
    fun getRemainingTime(adUnitId: String? = null): Long {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.bannerId
        val cachedAd = peekCachedAd(finalAdUnitId) ?: return 0L
        val remaining = AD_TIMEOUT - (System.currentTimeMillis() - cachedAd.loadTime)
        return if (remaining > 0) remaining else 0L
    }
    
    /**
     * 暂停广告
     */
    fun pauseAd() {
        // 新版 SDK 不需要手动暂停
        AdLogger.d("Admob Banner广告已暂停")
    }
    
    
    /**
     * 恢复广告
     */
    fun resumeAd() {
        // 新版 SDK 不需要手动恢复
        AdLogger.d("Admob Banner广告已恢复")
    }
    
    /**
     * 销毁广告
     */
    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.forEach { cachedAd -> cachedAd.bannerAd.destroy() }
            adCachePool.clear()
        }
        AdLogger.d("Admob Banner广告已销毁")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或容器被移除时调用，释放正在显示的Banner广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.destroy()
            AdLogger.d("Admob Banner广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }
    
    /**
     * 清理资源
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("Admob Banner广告控制器已清理")
    }
} 
