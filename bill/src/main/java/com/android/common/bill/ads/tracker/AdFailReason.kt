package com.android.common.bill.ads.tracker

/**
 * 广告失败原因常量
 * 统一维护 ad_show_fail / ad_bid_fail 的 reason 字段，避免字符串散落
 */
object AdFailReason {
    const val GLOBAL_AD_SWITCH_DISABLED = "global_ad_switch_disabled"
    const val TOTAL_SHOW_LIMIT_EXCEEDED = "total_show_limit_exceeded"
    const val TOTAL_CLICK_LIMIT_EXCEEDED = "total_click_limit_exceeded"
    const val NO_PLATFORM_AVAILABLE = "no_platform_available_after_exclusion"
    const val REDIRECT_TO_INTERSTITIAL_HIGHER_ECPM = "redirect_to_interstitial_higher_ecpm"
    const val REDIRECT_TO_FULL_SCREEN_NATIVE = "redirect_to_full_screen_native"
    const val ACTIVITY_DESTROYED = "activity_destroyed"
    const val AD_SHOW_INTERRUPTED = "ad_show_interrupted"
    const val REPLACED_BY_NEW_SHOW_REQUEST = "replaced_by_new_show_request"
    const val APP_IN_BACKGROUND_NOT_SUPPORTED = "app_in_background_not_supported"
}
