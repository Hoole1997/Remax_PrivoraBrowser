package io.docview.push.news

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import io.docview.push.check.BlockReason
import io.docview.push.timing.TimingCtrl
import io.docview.push.utils.Logger
import net.corekit.core.ext.canSendNotification
import java.util.Calendar

/**
 * 新闻推送检查控制器
 * 负责检查是否满足推送条件（频率、时机）
 */
@SuppressLint("StaticFieldLeak")
object NewsNotificationChecker {

    private const val TAG = "NewsNotificationChecker"
    private const val SP_NAME = "news_notification_prefs"
    
    // SharedPreferences Keys
    private const val KEY_DAILY_PUSH_COUNT = "daily_push_count"
    private const val KEY_LAST_PUSH_DATE = "last_push_date"
    private const val KEY_LAST_PUSH_TIME = "last_push_time"
    private const val KEY_LAST_SCHEDULED_HOUR = "last_scheduled_hour"
    private const val KEY_LAST_SCHEDULED_DATE = "last_scheduled_date"

    private var sharedPreferences: SharedPreferences? = null
    private var isInitialized = false
    private var mContext: Context ?= null

    /**
     * 初始化
     * @param context 上下文
     */
    fun initialize(context: Context) {
        if (isInitialized && sharedPreferences != null) {
            Logger.d("$TAG: 已初始化，跳过")
            return
        }
        mContext = context
        sharedPreferences = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        isInitialized = true
        // 检查是否需要重置每日计数
        checkAndResetDailyCount()
        Logger.d("$TAG: 初始化完成")
    }
    
    /**
     * 检查是否已初始化
     */
    private fun ensureInitialized(): Boolean {
        if (!isInitialized || sharedPreferences == null) {
            Logger.w("$TAG: 未初始化，跳过检查")
            return false
        }
        return true
    }

    /**
     * 检查是否可以推送新闻（解锁时触发）
     * @return true 如果可以推送
     */
    fun canPushOnUnlock(): Boolean {
        Logger.d("$TAG: canPushOnUnlock() 检查开始")
        
        if (!ensureInitialized()) {
            return false
        }
        
        val isEnabled = NewsNotificationConfig.isEnabled()
        val pushLimit = NewsNotificationConfig.getPushLimit()
        Logger.d("$TAG: 配置状态 - isEnabled=$isEnabled, pushLimit=$pushLimit")
        if (mContext?.canSendNotification() != true) {
            Logger.d("$TAG: 无通知权限")
            return false
        }
        if (!isEnabled) {
            Logger.d("$TAG: 新闻推送未启用")
            return false
        }
        // 检查应用前台状态
        if (TimingCtrl.getInstance().isAppInForeground()) {
            Logger.d("$TAG:新闻通知检查未通过：应用在前台，不发送通知")
            return false
        }
        // 检查每日推送次数
        if (!checkDailyLimit()) {
            Logger.d("$TAG: 已达每日推送上限")
            return false
        }

        // 检查推送间隔
        if (!checkPushInterval()) {
            Logger.d("$TAG: 未达推送间隔时间")
            return false
        }

        Logger.d("$TAG: 解锁推送检查通过")
        return true
    }

    /**
     * 检查是否可以进行定时推送
     * @return true 如果可以推送
     */
    fun canPushOnSchedule(): Boolean {
        Logger.d("$TAG: canPushOnSchedule() 检查开始")
        
        if (!ensureInitialized()) {
            return false
        }
        
        val isEnabled = NewsNotificationConfig.isEnabled()
        Logger.d("$TAG: 配置状态 - isEnabled=$isEnabled")
        
        if (!isEnabled) {
            Logger.d("$TAG: 新闻推送未启用")
            return false
        }

        // 检查每日推送次数
        if (!checkDailyLimit()) {
            Logger.d("$TAG: 已达每日推送上限")
            return false
        }

        // 检查是否在定时推送时间点
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val scheduledHours = NewsNotificationConfig.SCHEDULED_PUSH_HOURS
        Logger.d("$TAG: 当前时间=$currentHour, 定时时间点=$scheduledHours")
        
        if (!scheduledHours.contains(currentHour)) {
            Logger.d("$TAG: 当前时间 $currentHour 不在定时推送时间点")
            return false
        }

        // 检查该时间点今天是否已推送
        if (hasScheduledPushForHourToday(currentHour)) {
            Logger.d("$TAG: 今日 $currentHour 点已推送过")
            return false
        }

        Logger.d("$TAG: 定时推送检查通过 - 当前时间点: $currentHour")
        return true
    }

    /**
     * 获取当前定时推送的类型描述
     * @return 推送类型描述（早间汇总/午间精选/晚间回顾）
     */
    fun getScheduledPushType(): String {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (currentHour) {
            8 -> "早间汇总"
            12 -> "午间精选"
            20 -> "晚间回顾"
            else -> "新闻推送"
        }
    }

    /**
     * 记录一次推送
     */
    fun recordPush() {
        val sp = sharedPreferences ?: return
        val today = getTodayDateString()
        val currentTime = System.currentTimeMillis()
        
        // 更新每日推送次数
        val currentCount = getDailyPushCount()
        sp.edit()
            .putInt(KEY_DAILY_PUSH_COUNT, currentCount + 1)
            .putString(KEY_LAST_PUSH_DATE, today)
            .putLong(KEY_LAST_PUSH_TIME, currentTime)
            .apply()

        Logger.d("$TAG: 记录推送 - 今日第 ${currentCount + 1} 次")
    }

    /**
     * 记录定时推送
     * @param hour 推送的小时
     */
    fun recordScheduledPush(hour: Int) {
        val sp = sharedPreferences ?: return
        val today = getTodayDateString()
        sp.edit()
            .putInt(KEY_LAST_SCHEDULED_HOUR, hour)
            .putString(KEY_LAST_SCHEDULED_DATE, today)
            .apply()
        
        // 同时记录普通推送
        recordPush()
        
        Logger.d("$TAG: 记录定时推送 - $hour 点")
    }

    /**
     * 检查每日推送次数是否未超限
     * @return true 如果未超限
     */
    private fun checkDailyLimit(): Boolean {
        checkAndResetDailyCount()
        val limit = NewsNotificationConfig.getPushLimit()
        val currentCount = getDailyPushCount()
        Logger.d("$TAG: 每日限制检查 - 当前=$currentCount, 上限=$limit")
        return currentCount < limit
    }

    /**
     * 检查推送间隔是否满足
     * @return true 如果满足间隔要求
     */
    private fun checkPushInterval(): Boolean {
        val intervalMinutes = NewsNotificationConfig.getPushInterval()
        val intervalMillis = NewsNotificationConfig.getPushIntervalMillis()
        Logger.d("$TAG: 推送间隔配置 - 间隔=${intervalMinutes}分钟")
        
        if (intervalMillis <= 0) {
            Logger.d("$TAG: 无间隔限制")
            return true
        }

        val lastPushTime = sharedPreferences?.getLong(KEY_LAST_PUSH_TIME, 0) ?: 0
        if (lastPushTime == 0L) {
            Logger.d("$TAG: 从未推送过，允许推送")
            return true
        }

        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastPushTime
        val elapsedMinutes = elapsed / 1000 / 60
        val remaining = intervalMillis - elapsed
        val remainingMinutes = remaining / 1000 / 60
        val remainingSeconds = (remaining / 1000) % 60
        
        Logger.d("$TAG: 间隔检查 - 已过去${elapsedMinutes}分钟, 剩余${remainingMinutes}分${remainingSeconds}秒")
        
        val canPush = elapsed >= intervalMillis
        if (canPush) {
            Logger.d("$TAG: 间隔检查通过，允许推送")
        } else {
            Logger.d("$TAG: 间隔检查未通过，还需等待${remainingMinutes}分${remainingSeconds}秒")
        }
        return canPush
    }

    /**
     * 检查该小时今天是否已进行过定时推送
     * @param hour 小时
     * @return true 如果已推送
     */
    private fun hasScheduledPushForHourToday(hour: Int): Boolean {
        val today = getTodayDateString()
        val lastScheduledDate = sharedPreferences?.getString(KEY_LAST_SCHEDULED_DATE, "") ?: ""
        val lastScheduledHour = sharedPreferences?.getInt(KEY_LAST_SCHEDULED_HOUR, -1) ?: -1
        
        return lastScheduledDate == today && lastScheduledHour == hour
    }

    /**
     * 获取今日推送次数
     * @return 推送次数
     */
    private fun getDailyPushCount(): Int {
        return sharedPreferences?.getInt(KEY_DAILY_PUSH_COUNT, 0) ?: 0
    }

    /**
     * 检查并重置每日计数（如果是新的一天）
     */
    private fun checkAndResetDailyCount() {
        val sp = sharedPreferences ?: return
        val today = getTodayDateString()
        val lastDate = sp.getString(KEY_LAST_PUSH_DATE, "") ?: ""
        
        if (lastDate != today) {
            sp.edit()
                .putInt(KEY_DAILY_PUSH_COUNT, 0)
                .putString(KEY_LAST_PUSH_DATE, today)
                .putInt(KEY_LAST_SCHEDULED_HOUR, -1)
                .putString(KEY_LAST_SCHEDULED_DATE, "")
                .apply()
            Logger.d("$TAG: 新的一天，重置每日推送计数")
        }
    }

    /**
     * 获取今天的日期字符串
     * @return 日期字符串 (yyyy-MM-dd)
     */
    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}"
    }

    /**
     * 获取剩余可推送次数
     * @return 剩余次数
     */
    fun getRemainingPushCount(): Long {
        checkAndResetDailyCount()
        val limit = NewsNotificationConfig.getPushLimit()
        val currentCount = getDailyPushCount()
        return maxOf(0, limit - currentCount)
    }

    /**
     * 获取距离下次可推送的剩余时间（毫秒）
     * @return 剩余时间，0表示可以立即推送
     */
    fun getTimeUntilNextPush(): Long {
        val intervalMillis = NewsNotificationConfig.getPushIntervalMillis()
        if (intervalMillis <= 0) {
            return 0
        }

        val lastPushTime = sharedPreferences?.getLong(KEY_LAST_PUSH_TIME, 0) ?: 0
        if (lastPushTime == 0L) {
            return 0
        }

        val elapsed = System.currentTimeMillis() - lastPushTime
        return maxOf(0, intervalMillis - elapsed)
    }
}
