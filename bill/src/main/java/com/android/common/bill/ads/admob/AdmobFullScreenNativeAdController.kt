package com.android.common.bill.ads.admob

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
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
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.android.common.bill.ads.tracker.AdRevenueReporter
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.util.PositionGet
import kotlin.math.ceil
import com.android.common.bill.ui.FullScreenNativeAdView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.fragment.app.FragmentActivity
import com.android.common.bill.ads.util.AdDestroyManager

/**
 * 全屏原生广告控制器
 * 专门处理全屏展示的原生广告，通常用于应用启动、页面切换等场景
 */
class AdmobFullScreenNativeAdController private constructor() {
    
    // 当前展示的sessionId和isPreload（用于传递到回调）
    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false
    @Volatile
    private var onAdDisplayedCallback: (() -> Unit)? = null

    // 累积点击统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("fullscreen_native_ad_total_clicks", 0)

    // 累积关闭统计（持久化）
    private var totalCloseCount by DataStoreIntDelegate("fullscreen_native_ad_total_close", 0)
    
    // 累积加载次数统计（持久化）
    private var totalLoadCount by DataStoreIntDelegate("fullscreen_native_ad_total_loads", 0)

    // 累积加载成功次数统计（持久化）
    private var totalLoadSucCount by DataStoreIntDelegate("fullscreen_native_ad_total_load_suc", 0)

    // 累积加载失败次数统计（持久化）
    private var totalLoadFailCount by DataStoreIntDelegate("fullscreen_native_ad_total_load_fails", 0)
    
    // 累积展示失败次数统计（持久化）
    private var totalShowFailCount by DataStoreIntDelegate("fullscreen_native_ad_total_show_fails", 0)
    
    // 累积触发统计（持久化）
    private var totalShowTriggerCount by DataStoreIntDelegate("fullscreen_native_ad_total_show_triggers", 0)
    
    // 累积展示统计（持久化）
    private var totalShowCount by DataStoreIntDelegate("fullscreen_native_ad_total_shows", 0)
    
    // 当前广告的收益信息（临时存储）
    private var currentAdValue: AdValue? = null
    
    // 全屏原生广告是否正在显示的标识
    private var isShowing: Boolean = false
    
    companion object {
        private const val TAG = "AdmobFullScreenNativeAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1
        
        @Volatile
        private var INSTANCE: AdmobFullScreenNativeAdController? = null
        
        fun getInstance(): AdmobFullScreenNativeAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobFullScreenNativeAdController().also { INSTANCE = it }
            }
        }
    }
    
    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedFullScreenNativeAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT
    
    private var fullScreenNativeAd: NativeAd? = null
    private var loadTime: Long = 0L
    private val fullScreenAdView = FullScreenNativeAdView()
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: NativeAd? = null
    
    /**
     * 缓存的全屏原生广告数据类
     */
    private data class CachedFullScreenNativeAd(
        val ad: NativeAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }


    var nativeAds :NativeAd ?=null
    
    /**
     * 预加载全屏原生广告（可选，用于提前准备）
     * @param context 上下文
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     */
    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.fullNativeId
        
        // 检查缓存是否有效
        val cached = synchronized(adCachePool) {
            adCachePool.firstOrNull { it.adUnitId == finalAdUnitId && !it.isExpired() }
        }
        if (cached != null) {
            AdLogger.d("Admob全屏原生广告已有有效缓存，广告位ID: %s", finalAdUnitId)
            return AdResult.Success(Unit)
        }
        
        return loadAdToCache(context, finalAdUnitId)
    }

    fun closeEvent(adUnitId: String = ""){
        totalCloseCount++

        AdEventReporter.reportClose(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId, totalCloseCount, nativeAds?.getResponseInfo()?.loadedAdSourceResponseInfo?.name.orEmpty(), (currentAdValue?.valueMicros ?: 0) / 1_000_000.0, currentAdValue?.currencyCode ?: "", sessionId = currentSessionId)
        // 设置广告不再显示标识
        isShowing = false
        onAdDisplayedCallback = null
    }
    
    /**
     * 获取全屏原生广告（自动处理加载）
     * @param context 上下文
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     */
    suspend fun getAd(context: Context, adUnitId: String? = null): AdResult<NativeAd> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.fullNativeId
        
        // 1. 尝试从缓存获取广告
        var cachedAd = getCachedAd(finalAdUnitId)
        if (cachedAd == null) {
            AdLogger.d("Admob缓存为空，立即加载全屏原生广告，广告位ID: %s", finalAdUnitId)
            AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(currentSessionId)
            ) {
                loadAdToCache(context, finalAdUnitId)
            }
            cachedAd = getCachedAd(finalAdUnitId)
        }
        
        return if (cachedAd != null) {
            AdLogger.d("Admob使用缓存中的全屏原生广告，广告位ID: %s", finalAdUnitId)
            AdResult.Success(cachedAd.ad)
        } else {
            AdResult.Failure(createAdException("Admob full-screen native ad no cached ad available"))
        }
    }
    
    /**
     * 显示全屏原生广告到指定容器（简化版接口）
     * @param context 上下文
     * @param container 目标容器
     * @param lifecycleOwner 生命周期所有者
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     * @return AdResult<Unit> 广告显示结果
     */
    suspend fun showAdInContainer(
        context: Context,
        container: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        adUnitId: String? = null,
        sessionId: String = "",
        onAdDisplayed: (() -> Unit)? = null
    ): AdResult<Unit> {
        totalShowTriggerCount++
        // 拦截器检查
// 注册 Activity 销毁时的清理回调
        (context as? FragmentActivity)?.let { activity ->
            AdDestroyManager.instance.register(activity) {
                AdLogger.d("Admob全屏原生广告: Activity销毁，清理展示资源")
                // 先销毁正在显示的广告对象
                destroyShowingAd()
                // 再移除已添加的广告 View
                container.removeAllViews()
                isShowing = false
            }
        }
        
        // 设置sessionId和isPreload
        currentSessionId = sessionId
        currentIsPreload = hasCachedAd(adUnitId ?: BillConfig.admob.fullNativeId)
        onAdDisplayedCallback = onAdDisplayed

        // 检查缓存过期
        synchronized(adCachePool) {
            val finalId = adUnitId ?: BillConfig.admob.fullNativeId
            if (adCachePool.any { it.adUnitId == finalId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, finalId)
            }
        }

        return try {
            // 显示加载视图
            fullScreenAdView.createFullScreenLoadingView(context, container)
            
            when (val result = getAd(context, adUnitId)) {
                is AdResult.Success -> {
                    // 绑定广告到容器
                    val success = fullScreenAdView.bindFullScreenNativeAdToContainer(
                        context, container, result.data, lifecycleOwner
                    )
                    
                    if (success) {
                        // 记录当前显示的广告（用于资源释放）
                        currentShowingAd = result.data
                        AdResult.Success(Unit)
                    } else {
                        onAdDisplayedCallback = null
                        totalShowFailCount++
                        AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId.orEmpty(), totalShowFailCount, "Admob full-screen native: ad bindView failed", sessionId = currentSessionId, isPreload = currentIsPreload)
                        val error = AdException(code = -1, message = "Admob full-screen native: ad bindView failed")
                        AdResult.Failure(error)
                    }
                }
                is AdResult.Failure -> {
                    onAdDisplayedCallback = null
                    totalShowFailCount++
                    AdLogger.e("Admob全屏原生ad load failed: %s", result.error.message)
                    AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId.orEmpty(), totalShowFailCount, result.error.message, sessionId = currentSessionId, isPreload = currentIsPreload)
                    AdResult.Failure(result.error)
                }
            }
        } catch (e: Exception) {
            onAdDisplayedCallback = null
            totalShowFailCount++
            AdLogger.e("Admob显示全屏原生广告失败", e)
            AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId.orEmpty(), totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
            AdResult.Failure(AdException(code = -2, message = "show full-screen native ad exception: ${e.message}", cause = e))
        }
    }
    
    /**
     * 从缓存获取广告
     */
    private fun getCachedAd(adUnitId: String): CachedFullScreenNativeAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) {
                adCachePool.removeAt(index)
            } else {
                null
            }
        }
    }

    private fun peekCachedAd(adUnitId: String): CachedFullScreenNativeAd? {
        synchronized(adCachePool) {
            return adCachePool.firstOrNull { it.adUnitId == adUnitId && !it.isExpired() }
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
     * 检查缓存池是否存在元素
     * @param adUnitId 广告位ID，如果为空则检查所有广告位
     * @return 如果缓存池中存在有效广告则返回true，否则返回false
     */
    fun hasCachedAd(adUnitId: String? = null): Boolean {
        synchronized(adCachePool) {
            return if (adUnitId != null) {
                // 检查指定广告位是否有有效缓存
                adCachePool.any { it.adUnitId == adUnitId && !it.isExpired() }
            } else {
                // 检查缓存池中是否有任何有效广告
                adCachePool.any { !it.isExpired() }
            }
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
            val nativeAd = loadAd(context, adUnitId)
            if (nativeAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedFullScreenNativeAd(nativeAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("Admob全屏原生广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Admob full-screen native ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("Admob全屏原生loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "Admob full-screen native ad loadAdToCache exception: ${e.message}", e))
        }
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
     * 加载广告
     * @param context 上下文
     * @param adUnitId 广告位ID
     */
    private suspend fun loadAd(context: Context, adUnitId: String): NativeAd? {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("Admob全屏原生广告累积加载次数: $totalLoadCount")
        
        val requestId = AdEventReporter.reportStartLoad(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId, totalLoadCount)
        
        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            
            val videoOptions = VideoOptions.Builder().setStartMuted(true).build()
            val adRequest = NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE))
                .setAdChoicesPlacement(AdChoicesPlacement.TOP_RIGHT)
                .setMediaAspectRatio(NativeAd.NativeMediaAspectRatio.LANDSCAPE)
                .setVideoOptions(videoOptions)
                .build()
            
            NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {
                // 每次展示生成唯一 adUniqueId（用于点击保护）
                var currentAdUniqueId = ""
                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    nativeAds = nativeAd
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.d("Admob全屏原生广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    AdEventReporter.reportLoaded(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId, totalLoadSucCount, nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(), ceil(loadTime / 1000.0).toInt(), requestId)

                    // 设置事件回调
                    nativeAd.adEventCallback = object : NativeAdEventCallback {
                        var hasImpressionCounted = false

                        override fun onAdPaid(adValue: AdValue) {
                            AdLogger.d("Admob全屏原生广告收益回调: value=${adValue.valueMicros}, currency=${adValue.currencyCode}")

                            // adUniqueId = uuid + 广告位id + 创意id
                            val uuid = java.util.UUID.randomUUID().toString()
                            val creativeId = nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceId.orEmpty()
                            currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"

                            // 存储当前广告的收益信息
                            currentAdValue = adValue

                            if (!hasImpressionCounted) {
                                totalShowCount++
                                hasImpressionCounted = true
                                AdLogger.d("Admob全屏原生广告累积展示次数: $totalShowCount")
                                AdConfigManager.recordShow(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB)
                            }

                            AdEventReporter.reportImpression(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId, currentAdUniqueId, totalShowCount, nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(), currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0, currentAdValue?.currencyCode ?: "", sessionId = currentSessionId, isPreload = currentIsPreload)

                            // 上报真实的广告收益数据
                            AdRevenueReporter.reportRevenue(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId, adValue.valueMicros / 1_000_000.0, adValue.currencyCode, nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "Admob", nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceName.orEmpty())
                        }
                        
                        override fun onAdClicked() {
                            AdLogger.d("Admob全屏原生广告被点击")
                            
                            // 累积点击统计
                            totalClickCount++
                            AdLogger.d("Admob全屏原生广告累积点击次数: $totalClickCount")
                            
                            AdConfigManager.recordClick(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB)

                            // 记录点击用于重复点击保护
                            AdClickProtectionController.recordClick(currentAdUniqueId)
                            
                            AdEventReporter.reportClick(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId, currentAdUniqueId, totalClickCount, nativeAds?.getResponseInfo()?.loadedAdSourceResponseInfo?.name.orEmpty(), currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0, currentAdValue?.currencyCode ?: "", sessionId = currentSessionId)
                        }
                        
                        override fun onAdImpression() {
                            AdLogger.d("Admob全屏原生广告展示完成")
                            
                            // 设置广告正在显示标识
                            isShowing = true
                            
                            // 异步预加载下一个广告到缓存（如果缓存未满）
                            if (!isCacheFull(adUnitId)) {
                                GlobalScope.launch {
                                    try {
                                        AdLogger.d("Admob全屏原生广告曝光，开始预缓存，广告位ID: %s", adUnitId)
                                        AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(currentSessionId)
                                        ) {
                                            preloadAd(context, adUnitId)
                                        }
                                    } catch (e: Exception) {
                                        AdLogger.e("Admob全屏原生广告预缓存失败", e)
                                    }
                                }
                            }
                            AdLogger.d("Admob全屏原生广告显示成功")
                            notifyAdDisplayedIfNeeded()
                        }

                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            onAdDisplayedCallback = null
                            
                            totalCloseCount++

                            AdEventReporter.reportClose(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId, totalCloseCount, nativeAds?.getResponseInfo()?.loadedAdSourceResponseInfo?.name.orEmpty(), (currentAdValue?.valueMicros ?: 0) / 1_000_000.0, currentAdValue?.currencyCode ?: "", sessionId = currentSessionId)
                        }
                    }
                    
                    continuation.resume(nativeAd)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("Admob全屏原生ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, loadTime, loadAdError.message)
                    
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId, totalLoadFailCount, loadAdError.responseInfo?.loadedAdSourceResponseInfo?.name.orEmpty(), ceil(loadTime / 1000.0).toInt(), loadAdError.message, requestId)
                    
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
            AdLogger.e("Admob全屏原生 onAdDisplayed 回调异常", e)
        }
    }
    
    /**
     * 获取当前加载的广告数据
     */
    fun getCachedAdPeek(adUnitId: String? = null): NativeAd? {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.fullNativeId
        return peekCachedAd(finalAdUnitId)?.ad
    }

    fun getCurrentAd(adUnitId: String? = null): NativeAd? {
        return getCachedAdPeek(adUnitId)
    }
    
    /**
     * 检查是否有可用的广告
     */
    fun isAdLoaded(adUnitId: String? = null): Boolean {
        return getCachedAdPeek(adUnitId) != null
    }
    
    /**
     * 检查广告是否已过期
     */
    fun isAdExpired(adUnitId: String? = null): Boolean {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.fullNativeId
        val expired = synchronized(adCachePool) {
            adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }
        }
        if (expired) {
            AdLogger.d("Admob全屏原生广告已过期")
        }
        return expired
    }
    
    /**
     * 获取剩余有效时间（毫秒）
     */
    fun getRemainingTime(adUnitId: String? = null): Long {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.fullNativeId
        val cachedAd = peekCachedAd(finalAdUnitId) ?: return 0L
        val remaining = AD_TIMEOUT - (System.currentTimeMillis() - cachedAd.loadTime)
        return if (remaining > 0) remaining else 0L
    }
    
    /**
     * 销毁广告
     */
    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.forEach { cachedAd -> cachedAd.ad.destroy() }
            adCachePool.clear()
        }
        fullScreenNativeAd = null
        loadTime = 0L
        AdLogger.d("Admob全屏原生广告已销毁")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或容器被移除时调用，释放正在显示的全屏原生广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.destroy()
            AdLogger.d("Admob全屏原生广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }
    
    
    /**
     * 清理资源
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("全屏原生广告控制器已清理")
    }
    
    /**
     * 获取全屏原生广告是否正在显示的状态
     * @return true 如果全屏原生广告正在显示，false 否则
     */
    fun isAdShowing(): Boolean {
        return isShowing
    }
} 
