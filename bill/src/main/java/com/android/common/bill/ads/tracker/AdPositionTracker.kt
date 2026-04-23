package com.android.common.bill.ads.tracker

import com.android.common.bill.ads.config.AdType
import net.corekit.core.ext.DataStoreIntDelegate

/**
 * 广告位置埋点控制器
 * 统一处理各类广告的ad_position埋点
 * 内部使用 AdEventReporter 统一上报
 */
object AdPositionTracker {
    
    // 插页广告累积触发统计（持久化）
    private var interstitialShowTriggerCount by DataStoreIntDelegate("ad_position_interstitial_show_triggers", 0)
    
    // 原生广告累积触发统计（持久化）
    private var nativeShowTriggerCount by DataStoreIntDelegate("ad_position_native_show_triggers", 0)
    
    // 开屏广告累积触发统计（持久化）
    private var splashShowTriggerCount by DataStoreIntDelegate("ad_position_splash_show_triggers", 0)
    
    // 全屏原生广告累积触发统计（持久化）
    private var fullNativeShowTriggerCount by DataStoreIntDelegate("ad_position_full_native_show_triggers", 0)
    
    // Banner广告累积触发统计（持久化）
    private var bannerShowTriggerCount by DataStoreIntDelegate("ad_position_banner_show_triggers", 0)
    
    // 激励广告累积触发统计（持久化）
    private var rewardedShowTriggerCount by DataStoreIntDelegate("ad_position_rewarded_show_triggers", 0)
    
    /**
     * 上报插页广告位置埋点
     * @return 生成的 session_id
     */
    fun trackInterstitialAdPosition(position: String? = null): String {
        interstitialShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.INTERSTITIAL, interstitialShowTriggerCount, position)
    }
    
    /**
     * 上报原生广告位置埋点
     * @return 生成的 session_id
     */
    fun trackNativeAdPosition(position: String? = null): String {
        nativeShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.NATIVE, nativeShowTriggerCount, position)
    }
    
    /**
     * 上报开屏广告位置埋点
     * @return 生成的 session_id
     */
    fun trackSplashAdPosition(position: String? = null): String {
        splashShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.APP_OPEN, splashShowTriggerCount, position)
    }
    
    /**
     * 上报全屏原生广告位置埋点
     * @return 生成的 session_id
     */
    fun trackFullNativeAdPosition(position: String? = null): String {
        fullNativeShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.FULL_SCREEN_NATIVE, fullNativeShowTriggerCount, position)
    }
    
    /**
     * 上报Banner广告位置埋点
     * @return 生成的 session_id
     */
    fun trackBannerAdPosition(position: String? = null): String {
        bannerShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.BANNER, bannerShowTriggerCount, position)
    }
    
    /**
     * 上报激励广告位置埋点
     * @return 生成的 session_id
     */
    fun trackRewardedAdPosition(position: String? = null): String {
        rewardedShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.REWARDED, rewardedShowTriggerCount, position)
    }
}
