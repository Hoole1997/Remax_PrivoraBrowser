package com.browser.shortvideo.ad

import android.content.Context
import android.util.Log

/**
 * 短视频广告管理器
 * 负责判断何时应该展示广告，使用 InterstitialAds 进行广告加载和管理
 */
class VideoAdManager(
    private var config: VideoAdConfig = VideoAdConfig()
) {
    companion object {
        private const val TAG = "VideoAdManager"
    }
    
    // 上一次展示广告的时间戳 (毫秒)
    private var lastAdShownTimeMs: Long = 0L
    
    // 自上次广告后观看的视频数量
    private var videosSinceLastAd: Int = 0
    
    /**
     * 更新广告配置
     */
    fun updateConfig(config: VideoAdConfig) {
        this.config = config
        Log.d(TAG, "配置已更新: 开关=${config.isEnabled}, " +
                "最小视频间隔=${config.minVideosBetweenAds}, 最小时间间隔=${config.minSecondsBetweenAds}秒")
    }
    
    /**
     * 当用户滑动到新视频时调用
     * @param context 上下文
     * @param videoPosition 当前视频在列表中的位置
     * @return 是否应该在当前位置展示广告
     */
    fun onVideoViewed(context: Context, videoPosition: Int): Boolean {
        videosSinceLastAd++
        
        Log.d(TAG, "观看视频: 位置=$videoPosition, 距上次广告已看=$videosSinceLastAd 个视频")
        
        // 检查是否满足展示广告的条件
        val shouldShowAd = shouldShowAd()
        
        if (shouldShowAd) {
            Log.d(TAG, "✅ 满足广告展示条件，将在位置 $videoPosition 展示广告")
            return true
        }
        
        return false
    }
    
    /**
     * 检查是否满足展示广告的条件
     * 条件：
     * 1. 广告总开关打开
     * 2. 距离上一个广告 ≥ M 个视频
     * 3. 距离上一个广告 ≥ T 秒
     */
    private fun shouldShowAd(): Boolean {
        // 条件1：广告总开关
        if (!config.isEnabled) {
            Log.d(TAG, "❌ 广告开关已关闭")
            return false
        }
        
        // 条件2：距离上一个广告 ≥ M 个视频
        if (videosSinceLastAd < config.minVideosBetweenAds) {
            Log.d(TAG, "❌ 视频数量不足: $videosSinceLastAd < ${config.minVideosBetweenAds}")
            return false
        }
        
        // 条件3：距离上一个广告 ≥ T 秒
        val currentTimeMs = System.currentTimeMillis()
        val secondsSinceLastAd = (currentTimeMs - lastAdShownTimeMs) / 1000
        if (secondsSinceLastAd < config.minSecondsBetweenAds) {
            Log.d(TAG, "❌ 时间间隔不足: ${secondsSinceLastAd}秒 < ${config.minSecondsBetweenAds}秒")
            return false
        }
        
        Log.d(TAG, "✅ 满足所有广告条件: " +
                "视频数=$videosSinceLastAd >= ${config.minVideosBetweenAds}, " +
                "时间=${secondsSinceLastAd}秒 >= ${config.minSecondsBetweenAds}秒")
        
        return true
    }
    
    /**
     * 广告展示成功后调用，重置计数器
     * @param context 上下文
     */
    fun onAdShown(context: Context) {
        Log.d(TAG, "✅ 广告已展示，重置计数器")
        lastAdShownTimeMs = System.currentTimeMillis()
        videosSinceLastAd = 0
    }
    
    /**
     * 重置所有状态（用于测试或重新初始化）
     */
    fun reset() {
        lastAdShownTimeMs = 0L
        videosSinceLastAd = 0
        Log.d(TAG, "管理器已重置")
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        reset()
        Log.d(TAG, "管理器已清理")
    }
}
