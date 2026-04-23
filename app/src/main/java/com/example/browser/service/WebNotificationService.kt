package com.example.browser.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.browser.R
import com.example.browser.ui.web.WebActivity

/**
 * Web 通知服务
 * 用于处理来自网页的推送通知
 * 
 * 功能：
 * 1. 显示网页推送通知
 * 2. 处理通知点击事件
 * 3. 管理通知渠道
 */
class WebNotificationService : Service() {

    companion object {
        private const val TAG = "WebNotificationService"
        const val CHANNEL_ID = "web_notifications"
        const val CHANNEL_NAME = "网页通知"
        
        // Intent extras
        const val EXTRA_NOTIFICATION_TAG = "notification_tag"
        const val EXTRA_NOTIFICATION_TITLE = "notification_title"
        const val EXTRA_NOTIFICATION_TEXT = "notification_text"
        const val EXTRA_NOTIFICATION_URL = "notification_url"
        const val EXTRA_NOTIFICATION_ICON_URL = "notification_icon_url"
        
        /**
         * 创建通知渠道
         */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "来自网页的推送通知"
                    enableVibration(true)
                    enableLights(true)
                }
                
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
        
        /**
         * 显示 Web 通知
         */
        fun showNotification(
            context: Context,
            tag: String,
            title: String,
            text: String,
            url: String?,
            iconUrl: String?
        ) {
            createNotificationChannel(context)
            
            val intent = Intent(context, WebNotificationService::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_TAG, tag)
                putExtra(EXTRA_NOTIFICATION_TITLE, title)
                putExtra(EXTRA_NOTIFICATION_TEXT, text)
                putExtra(EXTRA_NOTIFICATION_URL, url)
                putExtra(EXTRA_NOTIFICATION_ICON_URL, iconUrl)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val tag = it.getStringExtra(EXTRA_NOTIFICATION_TAG) ?: ""
            val title = it.getStringExtra(EXTRA_NOTIFICATION_TITLE) ?: "新通知"
            val text = it.getStringExtra(EXTRA_NOTIFICATION_TEXT) ?: ""
            val url = it.getStringExtra(EXTRA_NOTIFICATION_URL)
            
            displayNotification(tag, title, text, url)
        }
        
        // 显示通知后停止服务
        stopSelf(startId)
        return START_NOT_STICKY
    }
    
    private fun displayNotification(tag: String, title: String, text: String, url: String?) {
        val notificationId = tag.hashCode()
        
        // 创建点击通知时打开的 Intent
        val clickIntent = Intent(this, WebActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            url?.let { putExtra(WebActivity.EXTRA_URL, it) }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 构建通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        // 显示通知
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(tag, notificationId, notification)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
