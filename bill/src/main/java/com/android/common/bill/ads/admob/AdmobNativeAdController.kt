package com.android.common.bill.ads.admob

import android.content.Context
import android.view.ViewGroup
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
import com.android.common.bill.ui.NativeAdView
import com.android.common.bill.ui.NativeAdStyle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import androidx.fragment.app.FragmentActivity
import com.android.common.bill.ads.util.AdDestroyManager

/**
 * 原生广告控制器
 * 提供原生广告的加载和管理功能
 */
class AdmobNativeAdController private constructor() {
    // 累积点击统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("native_ad_total_clicks", 0)

    // 累积关闭统计（持久化）
    private var totalCloseCount by DataStoreIntDelegate("native_ad_total_close", 0)
    
    // 累积加载次数统计（持久化）
    private var totalLoadCount by DataStoreIntDelegate("native_ad_total_loads", 0)

    // 累积加载成功次数统计（持久化）
    private var totalLoadSucCount by DataStoreIntDelegate("native_ad_total_load_suc", 0)

    // 累积加载失败次数统计（持久化）
    private var totalLoadFailCount by DataStoreIntDelegate("native_ad_total_load_fails", 0)
    
    // 累积展示失败次数统计（持久化）
    private var totalShowFailCount by DataStoreIntDelegate("native_ad_total_show_fails", 0)
    
    // 累积触发统计（持久化）
    private var totalShowTriggerCount by DataStoreIntDelegate("native_ad_total_show_triggers", 0)
    
    // 累积展示统计（持久化）
    private var totalShowCount by DataStoreIntDelegate("native_ad_total_shows", 0)
    
    // 当前广告的收益信息（临时存储）
    private var currentAdValue: AdValue? = null
    
    companion object {
        private const val TAG = "AdmobNativeAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1
        
        @Volatile
        private var INSTANCE: AdmobNativeAdController? = null
        
        fun getInstance(): AdmobNativeAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobNativeAdController().also { INSTANCE = it }
            }
        }
    }
    
    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedNativeAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT
    
    private val nativeAdView = NativeAdView()
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: NativeAd? = null
    
    /**
     * 缓存的原生广告数据类
     */
    private data class CachedNativeAd(
        val ad: NativeAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }
    
    /**
     * 预加载原生广告（可选，用于提前准备）
     * @param context 上下文
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     */
    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.nativeId
        
        // 检查缓存是否有效
        val cached = synchronized(adCachePool) {
            adCachePool.firstOrNull { it.adUnitId == finalAdUnitId && !it.isExpired() }
        }
        if (cached != null) {
            AdLogger.d("Admob原生广告已有有效缓存，广告位ID: %s", finalAdUnitId)
            return AdResult.Success(Unit)
        }
        
        return loadAdToCache(context, finalAdUnitId)
    }
    
    /**
     * 获取原生广告（自动处理加载）
     * @param context 上下文
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     */
    suspend fun getAd(context: Context, adUnitId: String? = null): AdResult<NativeAd> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.nativeId
        
        // 1. 尝试从缓存获取广告
        var cachedAd = pollCachedAd(finalAdUnitId)
        
        // 2. 如果缓存为空，立即加载并缓存一个广告
        if (cachedAd == null) {
            AdLogger.d("Admob缓存为空，立即加载原生广告，广告位ID: %s", finalAdUnitId)
            loadAdToCache(context, finalAdUnitId)
            cachedAd = pollCachedAd(finalAdUnitId)
        }
        
        return if (cachedAd != null) {
            AdLogger.d("Admob使用缓存中的原生广告，广告位ID: %s", finalAdUnitId)
            AdResult.Success(cachedAd.ad)
        } else {
            AdResult.Failure(createAdException("Admob native ad no cached ad available"))
        }
    }
    
    /**
     * 显示原生广告到指定容器（简化版接口）
     * @param context 上下文
     * @param container 目标容器
     * @param style 广告样式，默认为标准样式
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     * @return 是否显示成功
     */
    suspend fun showAdInContainer(
        context: Context, 
        container: ViewGroup,
        style: NativeAdStyle = BillConfig.admob.nativeStyleStandard,
        adUnitId: String? = null,
        sessionId: String = ""
    ): Boolean {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.nativeId
        val showSessionId = sessionId
        val showIsPreload = synchronized(adCachePool) { adCachePool.any { it.adUnitId == finalAdUnitId && !it.isExpired() } }
        
        // 累积触发统计
        totalShowTriggerCount++
        AdLogger.d("Admob原生广告累积触发展示次数: $totalShowTriggerCount")

        // 拦截器检查
// 注册 Activity 销毁时的清理回调
        (context as? FragmentActivity)?.let { activity ->
            AdDestroyManager.instance.register(activity) {
                AdLogger.d("Admob原生广告: Activity销毁，清理展示资源")
                // 先销毁正在显示的广告对象
                destroyShowingAd()
                // 再移除已添加的广告 View
                container.removeAllViews()
            }
        }
        
        // 检查缓存过期
        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.NATIVE, AdPlatform.ADMOB, finalAdUnitId)
            }
        }

        return try {
            when (val result = getAd(context, adUnitId)) {
                is AdResult.Success -> {
                    val nativeAd = result.data
                    // 记录当前显示的广告（用于资源释放）
                    currentShowingAd = nativeAd
                    nativeAd.adEventCallback = createNativeAdEventCallback(
                        context = context,
                        adUnitId = finalAdUnitId,
                        nativeAd = nativeAd,
                        showSessionId = showSessionId,
                        showIsPreload = showIsPreload
                    )
                    // 绑定广告到容器
                    val bindSuccess = nativeAdView.bindNativeAdToContainer(context, container, nativeAd, style)
                    if (bindSuccess) {
                        true
                    } else {
                        totalShowFailCount++
                        AdLogger.d("Admob原生广告累积展示失败次数: $totalShowFailCount")
                        if (currentShowingAd == nativeAd) {
                            currentShowingAd = null
                        }
                        AdEventReporter.reportShowFail(
                            AdType.NATIVE,
                            AdPlatform.ADMOB,
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
                    // 累积展示失败次数统计
                    totalShowFailCount++
                    AdLogger.d("Admob原生广告累积展示失败次数: $totalShowFailCount")
                    
                    AdEventReporter.reportShowFail(AdType.NATIVE, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, result.error.message, sessionId = showSessionId, isPreload = showIsPreload)
                    false
                }
            }
        } catch (e: Exception) {
            // 累积展示失败次数统计
            totalShowFailCount++
            AdLogger.d("Admob原生广告累积展示失败次数: $totalShowFailCount")
            
            AdEventReporter.reportShowFail(AdType.NATIVE, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, "${e.message}", sessionId = showSessionId, isPreload = showIsPreload)
            
            AdLogger.e("Admob显示原生广告失败", e)
            false
        }
    }
    
    /**
     * 基础广告加载方法（可复用）
     */
    private suspend fun loadAd(context: Context, adUnitId: String): NativeAd? {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("Admob原生广告累积加载次数: $totalLoadCount")
        
        val requestId = AdEventReporter.reportStartLoad(AdType.NATIVE, AdPlatform.ADMOB, adUnitId, totalLoadCount)
        
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
                    AdLogger.d("Admob原生广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    AdEventReporter.reportLoaded(AdType.NATIVE, AdPlatform.ADMOB, adUnitId, totalLoadSucCount, nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(), ceil(loadTime / 1000.0).toInt(), requestId)
                    
                    continuation.resume(nativeAd)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("Admob原生ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, loadTime, loadAdError.message)
                    
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(AdType.NATIVE, AdPlatform.ADMOB, adUnitId, totalLoadFailCount, loadAdError.responseInfo?.loadedAdSourceResponseInfo?.name.orEmpty(), ceil(loadTime / 1000.0).toInt(), loadAdError.message, requestId)
                    
                    val result = AdResult.Failure(
                        AdException(
                            code = loadAdError.code.value,
                            message = loadAdError.message
                        )
                    )
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
            // 每次展示生成唯一 adUniqueId（用于点击保护）
            var currentAdUniqueId = ""

            override fun onAdPaid(adValue: AdValue) {
                AdLogger.d("Admob原生广告收益回调: value=${adValue.valueMicros}, currency=${adValue.currencyCode}")

                // adUniqueId = uuid + 广告位id + 创意id
                val uuid = java.util.UUID.randomUUID().toString()
                val creativeId = nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceId.orEmpty()
                currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"

                // 存储当前广告的收益信息
                currentAdValue = adValue

                AdEventReporter.reportImpression(
                    AdType.NATIVE,
                    AdPlatform.ADMOB,
                    adUnitId,
                    currentAdUniqueId,
                    totalShowCount,
                    nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name.orEmpty(),
                    currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                    currentAdValue?.currencyCode ?: "",
                    sessionId = showSessionId,
                    isPreload = showIsPreload
                )

                // 上报真实的广告收益数据
                AdRevenueReporter.reportRevenue(
                    AdType.NATIVE,
                    AdPlatform.ADMOB,
                    adUnitId,
                    adValue.valueMicros / 1_000_000.0,
                    adValue.currencyCode,
                    nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "Admob",
                    nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.instanceName.orEmpty()
                )
            }

            override fun onAdClicked() {
                AdLogger.d("Admob原生广告被点击")

                // 累积点击统计
                totalClickCount++
                AdLogger.d("Admob原生广告累积点击次数: $totalClickCount")

                AdConfigManager.recordClick(AdType.NATIVE, AdPlatform.ADMOB)

                // 记录点击用于重复点击保护
                AdClickProtectionController.recordClick(currentAdUniqueId)

                AdEventReporter.reportClick(
                    AdType.NATIVE,
                    AdPlatform.ADMOB,
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
                AdLogger.d("Admob原生广告展示完成")

                // 累积展示统计
                totalShowCount++
                AdLogger.d("Admob原生广告累积展示次数: $totalShowCount")

                // 记录展示
                AdConfigManager.recordShow(AdType.NATIVE, AdPlatform.ADMOB)

                // 异步预加载下一个广告到缓存（如果缓存未满）
                if (!isCacheFull(adUnitId)) {
                    GlobalScope.launch {
                        try {
                            AdLogger.d("Admob原生广告曝光，开始预缓存，广告位ID: %s", adUnitId)
                            AdEventReporter.withLoadPosition(AdEventReporter.findTrackedPositionBySessionId(showSessionId)
                            ) {
                                preloadAd(context, adUnitId)
                            }
                        } catch (e: Exception) {
                            AdLogger.e("Admob原生广告预缓存失败", e)
                        }
                    }
                }
            }

            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                totalCloseCount++
                AdEventReporter.reportClose(
                    AdType.NATIVE,
                    AdPlatform.ADMOB,
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
            
            // 加载广告
            val nativeAd = loadAd(context, adUnitId)
            if (nativeAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedNativeAd(nativeAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("Admob原生广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Admob native ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("Admob原生loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "Admob native ad loadAdToCache exception: ${e.message}", e))
        }
    }
    
    /**
     * 从缓存获取广告
     */
    private fun pollCachedAd(adUnitId: String): CachedNativeAd? {
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
     * 只读取缓存广告，不移除缓存池中的对象。
     */
    private fun peekCachedAd(adUnitId: String): CachedNativeAd? {
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
     * 检查指定广告位缓存是否已满
     */
    private fun isCacheFull(adUnitId: String): Boolean {
        return getCachedAdCount(adUnitId) >= maxCacheSizePerAdUnit
    }
    
    /**
     * 获取当前加载的广告数据
     */
    fun getCachedAdPeek(adUnitId: String? = null): NativeAd? {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.nativeId
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
     * 销毁广告
     */
    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.forEach { cachedAd ->
                cachedAd.ad.destroy()
            }
            adCachePool.clear()
        }
        AdLogger.d("Admob原生广告已销毁")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或容器被移除时调用，释放正在显示的原生广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.destroy()
            AdLogger.d("Admob原生广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }
    
    
    /**
     * 清理资源
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("Admob原生广告控制器已清理")
    }
    
    /**
     * 创建广告异常
     */
    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(
            code = 0,
            message = message,
            cause = cause
        )
    }
} 
