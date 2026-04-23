package com.browser.shortvideo.ad

/**
 * 短视频广告配置
 * @property isEnabled 广告总开关 (video_AD_Switch: 0=关闭, 1=打开)
 * @property minVideosBetweenAds 距离上一个广告的最小视频数 (Video_AD_number, 默认=3)
 * @property minSecondsBetweenAds 距离上一个广告的最小秒数 (Video_AD_time, 默认=60)
 */
data class VideoAdConfig(
    val isEnabled: Boolean = true,
    val minVideosBetweenAds: Int = 3,
    val minSecondsBetweenAds: Int = 60
) {
    companion object {
        
        // 默认值
        const val DEFAULT_AD_SWITCH = 1
        const val DEFAULT_AD_NUMBER = 3
        const val DEFAULT_AD_TIME = 60
    }
}
