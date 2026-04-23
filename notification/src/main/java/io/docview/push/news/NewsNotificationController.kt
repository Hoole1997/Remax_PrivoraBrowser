package io.docview.push.news

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.docview.push.R
import io.docview.push.check.CheckCtrl
import io.docview.push.controller.TriggerCtrl
import io.docview.push.receiver.DeleteReceiver
import io.docview.push.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.corekit.core.ext.canSendNotification
import net.corekit.core.report.ReportDataManager
import java.util.Calendar
import android.graphics.Bitmap
import com.bumptech.glide.Glide

/**
 * 新闻推送控制器
 * 统一管理新闻推送的入口
 */
@SuppressLint("StaticFieldLeak")
object NewsNotificationController {

    private const val TAG = "NewsNotificationController"

    private var context: Context? = null
    private var notificationManager: NotificationManagerCompat? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isInitialized = false

    /**
     * 初始化新闻推送模块
     * @param context 上下文
     */
    fun initialize(context: Context) {
        if (isInitialized && this.context != null) {
            Logger.d("$TAG: 新闻推送模块已初始化，跳过")
            return
        }
        
        this.context = context.applicationContext
        this.notificationManager = NotificationManagerCompat.from(context)
        
        // 初始化各组件
        NewsNotificationConfig.initialize()
        NewsNotificationChecker.initialize(context)
        
        isInitialized = true
        Logger.d("$TAG: 新闻推送模块初始化完成")
    }

    /**
     * 尝试在用户解锁时推送新闻
     * 由 TimingCtrl 调用
     */
    fun tryPushOnUnlock() {
        Logger.d("$TAG: tryPushOnUnlock() 被调用")
        
        if (context == null) {
            Logger.w("$TAG: context 未初始化，跳过解锁推送")
            return
        }
        
        if (!NewsNotificationChecker.canPushOnUnlock()) {
            Logger.d("$TAG: 解锁推送检查未通过")
            return
        }

        scope.launch {
            try {
                val news = NewsDataFetcher.fetchLatestNews()
                if (news != null) {
                    pushNews(news, "新闻推送", CheckCtrl.NotificationType.NEWS)
                } else {
                    Logger.w("$TAG: 解锁推送 - 获取新闻失败")
                }
            } catch (e: Exception) {
                Logger.e("$TAG: 解锁推送异常", e)
            }
        }
    }

    /**
     * 尝试定时推送新闻
     * 由 KeepAliveWorker 调用
     */
    fun tryPushOnSchedule() {
        Logger.d("$TAG: tryPushOnSchedule() 被调用")
        
        if (context == null) {
            Logger.w("$TAG: context 未初始化，跳过定时推送")
            return
        }
        
        if (!NewsNotificationChecker.canPushOnSchedule()) {
            Logger.d("$TAG: 定时推送检查未通过")
            return
        }

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val pushType = NewsNotificationChecker.getScheduledPushType()

        scope.launch {
            try {
                // 定时推送获取单条新闻
                val news = NewsDataFetcher.fetchLatestNews()
                if (news != null) {
                    pushNews(news, pushType, CheckCtrl.NotificationType.NEWS)
                    NewsNotificationChecker.recordScheduledPush(currentHour)
                } else {
                    Logger.w("$TAG: 定时推送 - 获取新闻失败")
                }
            } catch (e: Exception) {
                Logger.e("$TAG: 定时推送异常", e)
            }
        }
    }

    /**
     * 使用 Glide 下载图片
     */
    private fun downloadImageWithGlide(ctx: Context, imageUrl: String): Bitmap? {
        return try {
            Glide.with(ctx)
                .asBitmap()
                .load(imageUrl)
                .submit()
                .get()
        } catch (e: Exception) {
            Logger.e("$TAG: Glide 下载图片失败 - $imageUrl", e)
            null
        }
    }

    /**
     * 推送单条新闻
     */
    private suspend fun pushNews(
        news: NewsData,
        pushType: String,
        triggerType: CheckCtrl.NotificationType
    ) {
        val ctx = context ?: return
        if (ctx.canSendNotification() != true) {
            Logger.w("$TAG: 无通知权限")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val notificationData = NewsNotificationBuilder.buildNotificationData(
                    ctx, news, pushType, triggerType
                )
                
                // 如果有图片URL，使用 Glide 下载图片并设置到RemoteViews
                // 下载失败则只显示文字，不显示图片
                if (!notificationData.imageUrl.isNullOrEmpty()) {
                    val imageBitmap = downloadImageWithGlide(ctx, notificationData.imageUrl)
                    if (imageBitmap != null) {
                        // 设置小图到紧凑布局
                        notificationData.contentView?.setImageViewBitmap(R.id.ivImage, imageBitmap)
                        notificationData.contentView?.setViewVisibility(R.id.ivImage, android.view.View.VISIBLE)
                        // 设置大图到展开布局
                        notificationData.bigContentView?.setImageViewBitmap(R.id.ivImage, imageBitmap)
                        notificationData.bigContentView?.setViewVisibility(R.id.ivImage, android.view.View.VISIBLE)
                        Logger.d("$TAG: 图片加载成功")
                    } else {
                        Logger.d("$TAG: 图片加载失败，只显示文字")
                    }
                }
                
                val notification = buildNotification(notificationData)
                
                withContext(Dispatchers.Main) {
                    notificationManager?.notify(notificationData.notificationId, notification)
                    NewsNotificationChecker.recordPush()
                    trackNewsPush(triggerType, notificationData)
                    Logger.d("$TAG: 新闻推送成功 - ${notificationData.contentTitle}")
                }
            } catch (e: Exception) {
                Logger.e("$TAG: 推送新闻失败", e)
            }
        }
    }

    /**
     * 构建通知对象
     */
    @SuppressLint("MissingPermission")
    private fun buildNotification(data: NewsNotificationData): Notification {
        val ctx = context ?: throw IllegalStateException("Context not initialized")

        // 创建删除监听 Intent
        val deleteIntent = Intent(ctx, DeleteReceiver::class.java).apply {
            action = "io.docview.push.ACTION_NOTIFICATION_DELETE"
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            ctx,
            System.currentTimeMillis().toInt(),
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val useBigLayout = Build.VERSION.SDK_INT >= 33

        val builder = if (useBigLayout) {
            NotificationCompat.Builder(ctx, TriggerCtrl.CHANNEL_ID_GENERAL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(ContextCompat.getColor(ctx, R.color.noti_color))
                .setSmallIcon(R.mipmap.ic_noti_small_icon)
                .setAutoCancel(true)
                .setGroup("news_push_" + System.currentTimeMillis())
                .setContentTitle(data.contentTitle)
                .setContentText(data.contentContent)
                .setContentIntent(data.contentIntent)
                .setDeleteIntent(deletePendingIntent)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(data.contentView)
                .setCustomBigContentView(data.bigContentView)
        } else {
            NotificationCompat.Builder(ctx, TriggerCtrl.CHANNEL_ID_GENERAL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setColor(ContextCompat.getColor(ctx, R.color.noti_color))
                .setSmallIcon(R.mipmap.ic_noti_small_icon)
                .setAutoCancel(true)
                .setGroup("news_push_" + System.currentTimeMillis())
                .setContentTitle(data.contentTitle)
                .setContentText(data.contentContent)
                .setContentIntent(data.contentIntent)
                .setDeleteIntent(deletePendingIntent)
                .setCustomContentView(data.contentView)
                .setCustomBigContentView(data.bigContentView)
                .apply {
                    if (Build.VERSION.SDK_INT >= 31) {
                        setCustomHeadsUpContentView(data.contentView)
                    }
                }
        }

        return builder.build()
    }

    /**
     * 上报新闻推送事件
     */
    private fun trackNewsPush(
        triggerType: CheckCtrl.NotificationType,
        data: NewsNotificationData
    ) {
        ReportDataManager.reportData(
            "News_Notific_Show", mapOf(
                "trigger_type" to triggerType.string,
                "title" to data.contentTitle,
                "remaining_count" to NewsNotificationChecker.getRemainingPushCount()
            )
        )
    }

    /**
     * 取消新闻通知
     */
    fun cancelNewsNotification() {
        notificationManager?.cancel(NewsNotificationBuilder.NEWS_NOTIFICATION_ID)
        Logger.d("$TAG: 新闻通知已取消")
    }
}
