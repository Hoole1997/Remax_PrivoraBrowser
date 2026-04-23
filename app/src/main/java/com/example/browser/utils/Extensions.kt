package com.example.browser.utils

import android.content.Context
import com.example.browser.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时间相关扩展方法
 * 提供友好的时间显示格式，支持国际化
 */

/**
 * 获取友好的时间跨度显示
 * @param context 上下文
 * @param millis 时间戳（毫秒）
 * @return 友好的时间字符串
 */
fun getFriendlyTimeSpan(context: Context, millis: Long): String {
    val now = System.currentTimeMillis()
    val span = now - millis
    
    // 如果时间在未来
    if (span < 0) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(millis))
    }
    
    // 刚刚（1秒内）
    if (span < 1000) {
        return context.getString(R.string.time_just_now)
    }
    
    // X秒前（1分钟内）
    if (span < 60 * 1000) {
        val seconds = span / 1000
        return context.getString(R.string.time_seconds_ago, seconds)
    }
    
    // X分钟前（1小时内）
    if (span < 60 * 60 * 1000) {
        val minutes = span / (60 * 1000)
        return context.getString(R.string.time_minutes_ago, minutes)
    }
    
    // X小时前（1天内）
    if (span < 24 * 60 * 60 * 1000) {
        val hours = span / (60 * 60 * 1000)
        return context.getString(R.string.time_hours_ago, hours)
    }
    
    // 获取当天 00:00
    val weeOfToday = getWeeOfToday()
    
    // 今天 HH:mm
    if (millis >= weeOfToday) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val time = timeFormat.format(Date(millis))
        return context.getString(R.string.time_today, time)
    }
    
    // 昨天 HH:mm
    if (millis >= weeOfToday - 24 * 60 * 60 * 1000) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val time = timeFormat.format(Date(millis))
        return context.getString(R.string.time_yesterday, time)
    }
    
    // 前天 HH:mm
    if (millis >= weeOfToday - 2 * 24 * 60 * 60 * 1000) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val time = timeFormat.format(Date(millis))
        return context.getString(R.string.time_day_before_yesterday, time)
    }
    
    // 更早的时间，显示日期
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return dateFormat.format(Date(millis))
}

/**
 * 获取当天 00:00 的时间戳
 */
private fun getWeeOfToday(): Long {
    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/**
 * Long 类型的时间戳扩展方法
 * 使用方式：timestamp.toFriendlyTimeSpan(context)
 */
fun Long.toFriendlyTimeSpan(context: Context): String {
    return getFriendlyTimeSpan(context, this)
}
