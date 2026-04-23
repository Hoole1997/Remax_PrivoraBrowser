package com.example.browser.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.browser.components
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.feature.media.service.AbstractMediaSessionService
import mozilla.components.support.base.android.NotificationsDelegate

/**
 * 媒体服务
 *
 * - 用于在后台播放网页音视频
 * - 显示媒体通知，提供播放/暂停控制
 * - 支持锁屏/耳机/车载控制
 *
 * 注意：该服务由 MediaMiddleware 自动启动和停止
 */
class MediaService : AbstractMediaSessionService() {

    override val store: BrowserStore by lazy { applicationContext.components.store }

    override val crashReporter: CrashReporting? = null

    override val notificationsDelegate: NotificationsDelegate by lazy {
        applicationContext.components.notificationsDelegate
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_NOT_STICKY: 当服务被杀掉时不自动重启，交由 MediaMiddleware 重新启动
        return Service.START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}