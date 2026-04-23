package com.example.browser.ui.web

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import com.blankj.utilcode.util.ToastUtils
import com.example.browser.R
import mozilla.components.browser.state.state.SessionState

/**
 * Web 相关工具类
 * 提供网页操作的辅助方法
 */
object WebUtils {

    /**
     * 添加网页快捷方式到主屏幕
     * @param context Context
     * @param tab 当前标签页状态
     */
    fun addToHomeScreen(context: Context, tab: SessionState) {
        val url = tab.content.url
        val title = tab.content.title.ifEmpty { url }
        
        if (url.isBlank()) {
            ToastUtils.showShort(R.string.web_shortcut_url_empty)
            return
        }

        // Get favicon from tab
        val favicon = tab.content.icon
        val iconBitmap = favicon

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addToHomeScreenModern(context, url, title, iconBitmap)
        } else {
            addToHomeScreenLegacy(context, url, title, iconBitmap)
        }
    }

    /**
     * Android 8.0+ 使用 ShortcutManager 添加快捷方式
     */
    private fun addToHomeScreenModern(
        context: Context,
        url: String,
        title: String,
        iconBitmap: Bitmap?
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported) {
            ToastUtils.showShort(R.string.web_shortcut_not_supported)
            return
        }

        // Create intent that directly opens WebActivity with the URL
        val shortcutIntent = Intent(context, WebActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
            putExtra(WebActivity.EXTRA_URL, url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Create icon - use favicon if available, otherwise use app icon
        val icon = if (iconBitmap != null) {
            Icon.createWithBitmap(iconBitmap)
        } else {
            Icon.createWithResource(context, R.mipmap.ic_launcher)
        }

        val shortcut = ShortcutInfo.Builder(context, "shortcut_${url.hashCode()}")
            .setShortLabel(title.take(25))  // Short label max 25 chars
            .setLongLabel(title.take(100))  // Long label max 100 chars
            .setIcon(icon)
            .setIntent(shortcutIntent)
            .build()

        val pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(shortcut)
        val successCallback = PendingIntent.getBroadcast(
            context, 0,
            pinnedShortcutCallbackIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        shortcutManager.requestPinShortcut(shortcut, successCallback.intentSender)
        ToastUtils.showShort(R.string.web_shortcut_adding)
    }

    /**
     * Android 7.1 及以下使用广播方式添加快捷方式
     */
    private fun addToHomeScreenLegacy(
        context: Context,
        url: String,
        title: String,
        iconBitmap: Bitmap?
    ) {
        val shortcutIntent = Intent(context, WebActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
            putExtra(WebActivity.EXTRA_URL, url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, title)

            // Use favicon if available, otherwise use app icon
            if (iconBitmap != null) {
                putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap)
            } else {
                putExtra(
                    Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(context, R.mipmap.ic_launcher)
                )
            }
            putExtra("duplicate", false)
        }
        context.sendBroadcast(addIntent)
        ToastUtils.showShort(R.string.web_shortcut_added)
    }

    /**
     * 分享网页
     * @param context Context
     * @param url 网页URL
     * @param title 网页标题
     */
    fun shareUrl(context: Context, url: String, title: String?) {
        if (url.isBlank()) {
            ToastUtils.showShort(R.string.web_share_url_empty)
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            if (!title.isNullOrBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.web_menu_share)))
    }
}
