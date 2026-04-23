package io.docview.push.news

import android.app.PendingIntent
import android.content.Context
import android.widget.RemoteViews
import io.docview.push.R
import io.docview.push.builder.LANDING_NOTIFICATION_ACTION
import io.docview.push.builder.LANDING_NOTIFICATION_CONTENT
import io.docview.push.builder.LANDING_NOTIFICATION_FROM
import io.docview.push.builder.LANDING_NOTIFICATION_TITLE
import io.docview.push.builder.entryPointPendingIntent
import io.docview.push.check.CheckCtrl
import io.docview.push.config.Content
import io.docview.push.utils.Logger
import io.docview.push.utils.ViewBuilder

/**
 * 新闻通知构建器
 * 负责构建新闻推送通知的数据
 */
object NewsNotificationBuilder {

    private const val TAG = "NewsNotificationBuilder"
    const val EXTRA_NEWS_URL = "extra_news_url"
    // 新闻通知 ID
    const val NEWS_NOTIFICATION_ID = 10010

    /**
     * 构建新闻通知数据
     * @param context 上下文
     * @param news 新闻数据
     * @param pushType 推送类型描述（如"早间汇总"）
     * @param triggerType 触发类型
     * @return 通知数据
     */
    fun buildNotificationData(
        context: Context,
        news: NewsData,
        pushType: String,
        triggerType: CheckCtrl.NotificationType
    ): NewsNotificationData {
        val title = news.title ?: pushType
        val content = news.description ?: news.source ?: ""
        val newsUrl = news.url ?: ""
        val hasImage = !news.image.isNullOrEmpty()

        Logger.d("$TAG: 构建新闻通知 - $title, hasImage=$hasImage")

        val pendingIntent = entryPointPendingIntent(context, NEWS_NOTIFICATION_ID) {
            it.putExtra(LANDING_NOTIFICATION_ACTION, Content.ICON_TYPE_REAL_NEWS)
            it.putExtra(LANDING_NOTIFICATION_FROM, triggerType.string)
            it.putExtra(LANDING_NOTIFICATION_TITLE, title)
            it.putExtra(LANDING_NOTIFICATION_CONTENT, content)
            it.putExtra(EXTRA_NEWS_URL, newsUrl)
        }

        val contentView = ViewBuilder(
            context.packageName,
            R.layout.layout_notification_news_12,
            R.layout.layout_notification_news
        )
            .setTextViewText(R.id.tvTitle, title)
            .setTextViewText(R.id.tvDesc, content)
            .build()

        val bigContentView = ViewBuilder(
            context.packageName,
            R.layout.layout_notification_news_big_12,
            R.layout.layout_notification_news_big
        )
            .setTextViewText(R.id.tvTitle, title)
            .setTextViewText(R.id.tvDesc, content)
            .build()

        return NewsNotificationData(
            notificationId = NEWS_NOTIFICATION_ID,
            contentTitle = title,
            contentContent = content,
            contentIntent = pendingIntent,
            contentView = contentView,
            bigContentView = bigContentView,
            newsUrl = newsUrl,
            imageUrl = news.image
        )
    }

}

/**
 * 新闻通知数据
 */
data class NewsNotificationData(
    val notificationId: Int,
    val contentTitle: String,
    val contentContent: String,
    val contentIntent: PendingIntent?,
    val contentView: RemoteViews?,
    val bigContentView: RemoteViews?,
    val newsUrl: String,
    val imageUrl: String? = null
)
