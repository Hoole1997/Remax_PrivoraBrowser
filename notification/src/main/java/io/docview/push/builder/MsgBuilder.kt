package io.docview.push.builder

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.blankj.utilcode.util.StringUtils
import com.google.gson.Gson
import io.docview.push.utils.ViewBuilder
import io.docview.push.R
import io.docview.push.check.CheckCtrl
import io.docview.push.config.Content
import io.docview.push.config.Content.Companion.ICON_TYPE_EARTHQUAKE
import io.docview.push.config.ContentController
import io.docview.push.earthquake.EarthquakeController
import io.docview.push.earthquake.EarthquakeInfo
import io.docview.push.service.KeepAliveServiceManager
import net.corekit.core.utils.BusinessLanguageController
import java.time.LocalDate
import kotlin.random.Random


enum class NotificationType {
    GENERAL,
    JUNK, NEWS, PROCESS, BOOKMARK,MAIN,SCAN,DUPLICATE,SIMILAR,SPEED,
    EARTHQUAKE
}

val type2notificationId = mapOf(
    NotificationType.GENERAL to 10000,
    NotificationType.JUNK to 10001,
    NotificationType.NEWS to 10002,
    NotificationType.PROCESS to 10003,
    NotificationType.BOOKMARK to 10004,
    NotificationType.EARTHQUAKE to 10005,
    NotificationType.DUPLICATE to 10006,
    NotificationType.SIMILAR to 10007,
    NotificationType.SPEED to 10008
)

val LANDING_NOTIFICATION_ID = "landing_notification_id"
val LANDING_NOTIFICATION_ACTION = "landing_notification_action"
val LANDING_NOTIFICATION_FROM = "landing_notification_from"
val LANDING_NOTIFICATION_TITLE = "landing_notification_title"
val LANDING_NOTIFICATION_CONTENT = "landing_notification_content"
val LANDING_NOTIFICATION_EARTHQUAKE_DATA = "landing_notification_earthquake_data"

/**
 * 通知数据对象
 */
class GeneralNotificationData(
    val notificationId: Int,
    val contentTitle: String,
    val contentContent: String,
    val contentIntent: PendingIntent? = null,
    val contentView: RemoteViews? = null,
    val bigContentView: RemoteViews? = null,
)

fun entryPointPendingIntent(
    context: Context,
    notificationId: Int,
    applyIntent: ((Intent) -> Unit)? = null,
): PendingIntent {
    val intent = entryPointIntent(context)
    intent.putExtra(LANDING_NOTIFICATION_ID, notificationId)
    applyIntent?.invoke(intent)


    val options = if (Build.VERSION.SDK_INT >= 35) {
        ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }.toBundle()
    } else {
        null
    }

    return PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        options
    )
}

fun entryPointIntent(context: Context): Intent =
    Intent().apply {
        // 使用 Action 方式跳转，避免直接依赖外部 Activity
        action = "io.docview.push.ACTION_OPEN_APP"
        addCategory(Intent.CATEGORY_DEFAULT)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // 设置包名，确保跳转到正确的应用
        setPackage(context.packageName)
        // 添加通知来源标识
        putExtra("from_notification", true)
        putExtra("notification_timestamp", System.currentTimeMillis())
    }

/**
 * 重置红点显示状态（用于测试或特殊情况）
 * @param context 上下文
 */
fun resetRedPointStatus(context: Context) {
    try {
        val sharedPreferences =
            context.getSharedPreferences("notification_red_point", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .remove("has_clicked_today")
            .remove("last_click_date")
            .apply()
    } catch (e: Exception) {
        // 忽略异常
    }
}

/**
 * 检查是否应该显示红点（点击后才隐藏）
 * @param context 上下文
 * @return true 如果应该显示红点，false 如果不应该显示
 */
private fun shouldShowRedPoint(context: Context): Boolean {
    return try {
        val sharedPreferences =
            context.getSharedPreferences("notification_red_point", Context.MODE_PRIVATE)
        val hasClickedToday = sharedPreferences.getBoolean("has_clicked_today", false)
        val lastClickDate = sharedPreferences.getString("last_click_date", "")
        val today = LocalDate.now().toString()

        // 如果是新的一天，重置点击状态
        if (lastClickDate != today) {
            sharedPreferences.edit()
                .putBoolean("has_clicked_today", false)
                .putString("last_click_date", today)
                .apply()
            return true // 新的一天显示红点
        }

        // 如果今天还没点击过，显示红点
        !hasClickedToday
    } catch (e: Exception) {
        // 异常情况下默认显示红点
        true
    }
}

/**
 * 标记红点已点击（隐藏红点）
 * @param context 上下文
 */
fun markRedPointClicked(context: Context) {
    try {
        val sharedPreferences =
            context.getSharedPreferences("notification_red_point", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        sharedPreferences.edit()
            .putBoolean("has_clicked_today", true)
            .putString("last_click_date", today)
            .apply()

        KeepAliveServiceManager.startKeepAliveService(context)
    } catch (e: Exception) {
        // 忽略异常
    }
}

class EarthquakeModelManager() {
    private val gson = Gson()
    
    fun getModel(context: Context, earthquake:EarthquakeInfo,type:CheckCtrl.NotificationType): GeneralNotificationData {
        val notificationId = type2notificationId[NotificationType.GENERAL] ?: 0
        val content = earthquake.place.orEmpty()
        val curLang = BusinessLanguageController.getInstance().getAliens()
        val pushText = EarthquakeController.earthquakePushMessageMap.getOrDefault(curLang,EarthquakeController.earthquakePushMessageMap.values.first())
        val title = pushText.format(earthquake.magnitude.toString())
        
        // 将地震信息序列化为JSON字符串
        val earthquakeJson = gson.toJson(earthquake)
        
        val pendingIntent = entryPointPendingIntent(context, notificationId) {
            it.putExtra(LANDING_NOTIFICATION_ACTION,ICON_TYPE_EARTHQUAKE)
            it.putExtra(LANDING_NOTIFICATION_FROM, type.string)
            it.putExtra(LANDING_NOTIFICATION_TITLE, title)
            it.putExtra(LANDING_NOTIFICATION_CONTENT, content)
            it.putExtra(LANDING_NOTIFICATION_EARTHQUAKE_DATA, earthquakeJson)
        }


        val contentView = ViewBuilder(
            context.packageName,
            R.layout.layout_notification_earthquake_12,
            R.layout.layout_notification_earthquake
        )
            .setTextViewText(R.id.title, title)
            .setTextViewText(R.id.time, earthquake.shortTime)
            .setTextViewText(R.id.info, StringUtils.getString(R.string.noti_earthquake_click_text))
            .build()


        return GeneralNotificationData(
            notificationId = notificationId,
            contentTitle = title,
            contentContent = content,
            contentIntent = pendingIntent,
            contentView = contentView,
            bigContentView = contentView
        )
    }

}

class GeneralModelManager() {

    fun getModel(context: Context,type:CheckCtrl.NotificationType): GeneralNotificationData {
        val notificationId = type2notificationId[NotificationType.GENERAL] ?: 0
        val data = ContentController.getNextContent()!!
        val title = data.title
        val content = data.desc
        val pendingIntent = entryPointPendingIntent(context, notificationId) {
            it.putExtra(LANDING_NOTIFICATION_ACTION, data.actionType)
            it.putExtra(LANDING_NOTIFICATION_FROM, type.string)
            it.putExtra(LANDING_NOTIFICATION_TITLE, title)
            it.putExtra(LANDING_NOTIFICATION_CONTENT, content)
        }

        val badgeCount = Random.nextInt(1, 100).toString()
        val contentView = ViewBuilder(
            context.packageName,
            R.layout.layout_notification_general_12,
            R.layout.layout_notification_general
        )
            .setImageViewResource(R.id.iv, getIcon(data))
            .setTextViewText(R.id.tvCount, badgeCount)
            .setTextViewText(R.id.tvTitle, title)
            .setTextViewText(R.id.tvDesc, content)
            .setTextViewText(R.id.tvAction, data.buttonText)
            .build()

        val bigContentView = ViewBuilder(
            context.packageName,
            R.layout.layout_notification_general_big_12,
            R.layout.layout_notification_general_big
        )
            .setImageViewResource(R.id.iv, getIcon(data))
            .setTextViewText(R.id.tvCount, badgeCount)
            .setTextViewText(R.id.tvTitle, title)
            .setTextViewText(R.id.tvDesc, content)
            .setTextViewText(R.id.tvAction, data.buttonText)
            .build()

        return GeneralNotificationData(
            notificationId = notificationId,
            contentTitle = title,
            contentContent = content,
            contentIntent = pendingIntent,
            contentView = contentView,
            bigContentView = bigContentView
        )
    }

    private fun getIcon(data: Content): Int = when (data.iconType) {
        Content.ICON_TYPE_JUNK -> R.mipmap.ic_noti_junk
        Content.ICON_TYPE_PROCESS -> R.mipmap.ic_noti_process
        Content.ICON_TYPE_NEWS -> R.mipmap.ic_noti_news
        Content.ICON_TYPE_SHORT_VIDEO -> R.mipmap.ic_noti_short_video
        Content.ICON_TYPE_WEATHER -> R.mipmap.ic_noti_weather
        Content.ICON_TYPE_MAIN -> R.mipmap.ic_noti_main
        Content.ICON_TYPE_SCAN -> R.mipmap.ic_noti_scan
        Content.ICON_TYPE_PHOTO_DUPLICATE -> R.mipmap.ic_home_duplicate
        Content.ICON_TYPE_PHOTO_SIMILAR -> R.mipmap.ic_home_similar
        Content.ICON_TYPE_SPEED -> R.mipmap.ic_home_speed
        else -> R.mipmap.ic_noti_main
    }

}

class ResidentModelManger {

    fun getModel(context: Context): GeneralNotificationData {

        val contentView = ViewBuilder(
            context.packageName,
            R.layout.layout_notification_resident_12,
            R.layout.layout_notification_resident
        )
            .setOnClickPendingIntent(
                R.id.btn_container_1, entryPointPendingIntent(
                    context,
                    type2notificationId[NotificationType.JUNK] ?: 0
                ) {
                    it.putExtra(
                        LANDING_NOTIFICATION_ACTION,
                        Content.ICON_TYPE_JUNK
                    )
                    it.putExtra(LANDING_NOTIFICATION_FROM, CheckCtrl.NotificationType.RESIDENT.string)
                })
            .setOnClickPendingIntent(
                R.id.btn_container_2, entryPointPendingIntent(
                    context,
                    type2notificationId[NotificationType.NEWS] ?: 0
                ) {
                    it.putExtra(
                        LANDING_NOTIFICATION_ACTION,
                        Content.ICON_TYPE_NEWS
                    )
                    it.putExtra(LANDING_NOTIFICATION_FROM, CheckCtrl.NotificationType.RESIDENT.string)
                })
            .setOnClickPendingIntent(
                R.id.btn_container_3, entryPointPendingIntent(
                    context,
                    type2notificationId[NotificationType.PROCESS] ?: 0
                ) {
                    it.putExtra(
                        LANDING_NOTIFICATION_ACTION,
                        Content.ICON_TYPE_PROCESS
                    )
                    it.putExtra(LANDING_NOTIFICATION_FROM, CheckCtrl.NotificationType.RESIDENT.string)
                })
            .setOnClickPendingIntent(
                R.id.btn_container_4, entryPointPendingIntent(
                    context,
                    type2notificationId[NotificationType.SCAN] ?: 0
                ) {
                    it.putExtra(
                        LANDING_NOTIFICATION_ACTION,
                        Content.ICON_TYPE_SCAN
                    )
                    it.putExtra(LANDING_NOTIFICATION_FROM, CheckCtrl.NotificationType.RESIDENT.string)
                })
            .build()


        return GeneralNotificationData(
            notificationId = type2notificationId[NotificationType.MAIN] ?: 0,
            contentTitle = StringUtils.getString(R.string.noti_resident_title),
            contentContent = StringUtils.getString(R.string.noti_resident_service_running),
            contentIntent = null,
            contentView = contentView,
            bigContentView = null
        )
    }
}
