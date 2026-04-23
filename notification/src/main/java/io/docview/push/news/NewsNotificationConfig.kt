package io.docview.push.news

import io.docview.push.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.corekit.core.utils.ConfigRemoteManager

/**
 * 新闻推送配置管理器
 * 负责管理新闻推送的远程配置参数
 */
object NewsNotificationConfig {

    private const val TAG = "NewsNotificationConfig"

    // 远程配置 Key

    // 默认值
    private const val DEFAULT_PUSH_LIMIT = 10L      // 每日推送次数上限，默认10次
    private const val DEFAULT_PUSH_INTERVAL = 60L   // 推送间隔，默认60分钟

    // 定时推送时间点（小时）
    val SCHEDULED_PUSH_HOURS = listOf(8, 12, 20)  // 8:00, 12:00, 20:00

    // 缓存的配置值
    private var cachedPushLimit: Long = DEFAULT_PUSH_LIMIT
    private var cachedPushInterval: Long = DEFAULT_PUSH_INTERVAL
    private const val KEY_PUSH_LIMIT = "news_push_limit"
    private const val KEY_PUSH_INTERVAL = "news_push_interval"
    /**
     * 初始化配置，从远程获取最新配置
     */
    fun initialize() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refreshConfig()
                Logger.d("$TAG: 配置初始化完成 - limit=$cachedPushLimit, interval=$cachedPushInterval")
            } catch (e: Exception) {
                Logger.e("$TAG: 配置初始化失败", e)
            }
        }
    }

    /**
     * 刷新远程配置
     */
    suspend fun refreshConfig() {
        try {
            cachedPushLimit = ConfigRemoteManager.getLong(KEY_PUSH_LIMIT, DEFAULT_PUSH_LIMIT)
                ?: DEFAULT_PUSH_LIMIT
            cachedPushInterval = ConfigRemoteManager.getLong(KEY_PUSH_INTERVAL, DEFAULT_PUSH_INTERVAL)
                ?: DEFAULT_PUSH_INTERVAL
            
            Logger.d("$TAG: 配置刷新成功 - limit=$cachedPushLimit, interval=$cachedPushInterval")
        } catch (e: Exception) {
            Logger.e("$TAG: 配置刷新失败，使用缓存值", e)
        }
    }

    /**
     * 获取每日推送次数上限
     * @return 推送次数上限，0表示不推送
     */
    fun getPushLimit(): Long = cachedPushLimit

    /**
     * 获取推送间隔时间（分钟）
     * @return 推送间隔，0表示无间隔限制
     */
    fun getPushInterval(): Long = cachedPushInterval

    /**
     * 是否启用新闻推送
     * @return true 如果 pushLimit > 0
     */
    fun isEnabled(): Boolean = cachedPushLimit > 0L

    /**
     * 获取推送间隔时间（毫秒）
     * @return 推送间隔毫秒数
     */
    fun getPushIntervalMillis(): Long = cachedPushInterval * 60 * 1000L
}
