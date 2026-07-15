package com.example.browser.ui

import android.content.Intent
import io.docview.push.builder.LANDING_NOTIFICATION_ACTION
import io.docview.push.controller.LandingCtrl
import io.docview.push.news.NewsNotificationBuilder.EXTRA_NEWS_URL

/**
 * MainActivity 一次启动需要执行的导航请求。
 *
 * Intent 是可变对象，且 MainActivity 使用 singleTask。先将参数转换为不可变请求并消费
 * 一次性 extra，再执行 startActivity，可避免 Activity 重入时重复执行上一次通知动作。
 */
internal data class MainLaunchRequest(
    val externalUrl: String? = null,
    val notification: NotificationLaunchRequest? = null,
    val openJunkScan: Boolean = false,
    val openDuplicatePhotos: Boolean = false,
    val openSimilarPhotos: Boolean = false,
    val switchToTabs: Boolean = false,
)

/** 通知动作及其附属数据的快照，导航阶段不再读取 Activity 当前的 Intent。 */
internal data class NotificationLaunchRequest(
    val actionType: Int,
    val newsUrl: String?,
)

/**
 * 负责解析并消费 MainActivity 的一次性 Intent 参数，页面跳转仍由 MainActivity 执行。
 *
 * 外部 ACTION_VIEW 不在这里清除：它属于系统分发的浏览器协议。通知参数只有在明确存在
 * [LANDING_NOTIFICATION_ACTION] 时才清除，避免 LandingCtrl 将普通外链的 action 置空。
 */
internal object MainLaunchIntentConsumer {

    fun consume(
        sourceIntent: Intent,
        includeOneShotActions: Boolean,
        includeAutomaticActions: Boolean,
    ): MainLaunchRequest {
        // 必须在清理通知参数前保存外链和新闻 URL，兼容极端情况下组合了多个参数的 Intent。
        val externalUrl = if (includeOneShotActions) {
            sourceIntent
                .takeIf { it.action == Intent.ACTION_VIEW }
                ?.data
                ?.toString()
        } else {
            null
        }
        // 恢复 Activity 时也清理旧通知，只是不再次执行；真正的新投递会进入 onNewIntent。
        val consumedNotification = consumeNotification(sourceIntent)
        val notification = if (includeOneShotActions) consumedNotification else null

        // 三个 AUTO 参数历史上只在 onNewIntent 中生效，冷启动时保持原有触发范围。
        val openJunkScan = includeAutomaticActions &&
            sourceIntent.consumeBooleanExtra(MainActivity.EXTRA_AUTO_JUNK)
        val openDuplicatePhotos = includeAutomaticActions &&
            sourceIntent.consumeBooleanExtra(MainActivity.EXTRA_AUTO_DUPLICATE)
        val openSimilarPhotos = includeAutomaticActions &&
            sourceIntent.consumeBooleanExtra(MainActivity.EXTRA_AUTO_SIMILAR)

        // 全新创建与 onNewIntent 都支持切换 Tabs；恢复态只消费旧 base Intent 中的指令，
        // 真正由 WebActivity 新投递的指令会随后在 onNewIntent 中执行。
        val consumedSwitchToTabs = sourceIntent.consumeBooleanExtra(MainActivity.EXTRA_SWITCH_TO_TABS)
        val switchToTabs = includeOneShotActions && consumedSwitchToTabs

        return MainLaunchRequest(
            externalUrl = externalUrl,
            notification = notification,
            openJunkScan = openJunkScan,
            openDuplicatePhotos = openDuplicatePhotos,
            openSimilarPhotos = openSimilarPhotos,
            switchToTabs = switchToTabs,
        )
    }

    private fun consumeNotification(sourceIntent: Intent): NotificationLaunchRequest? {
        if (!sourceIntent.hasExtra(LANDING_NOTIFICATION_ACTION)) return null

        val request = NotificationLaunchRequest(
            actionType = sourceIntent.getIntExtra(LANDING_NOTIFICATION_ACTION, 0),
            newsUrl = sourceIntent.getStringExtra(EXTRA_NEWS_URL),
        )

        // 先快照、后清理，确保真实新闻仍使用本次通知携带的 URL。
        LandingCtrl.clearNotificationParameters(sourceIntent)
        sourceIntent.removeExtra(EXTRA_NEWS_URL)
        return request
    }

    private fun Intent.consumeBooleanExtra(key: String): Boolean {
        if (!hasExtra(key)) return false

        val enabled = getBooleanExtra(key, false)
        removeExtra(key)
        return enabled
    }
}
