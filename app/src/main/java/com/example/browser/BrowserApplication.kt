package com.example.browser

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.android.common.bill.ads.log.AdLogger
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.Utils
import com.example.browser.service.MediaService
import com.example.browser.ui.splash.SplashActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mozilla.components.feature.media.MediaSessionFeature
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.webextensions.WebExtensionSupport
import net.corekit.core.controller.ChannelUserController
import net.corekit.core.log.CoreLogger
import net.corekit.core.report.ReportDataManager
import java.util.concurrent.TimeUnit

/**
 * 浏览器应用的 Application 类
 * 负责初始化全局组件和状态持久化
 */
class BrowserApplication : Application() {

    companion object {
        private const val TAG = "BrowserApplication"
        var isHotLaunch: Boolean = false
            get() = field && ActivityUtils.getActivityList().isNotEmpty()
    }

    /**
     * 浏览器组件实例
     * 延迟初始化,首次访问时创建
     */
    val browserComponents by lazy {
        BrowserComponents(applicationContext)
    }

    /**
     * 媒体会话功能
     * 管理媒体播放状态和通知
     */
    private val mediaSessionFeature by lazy {
        MediaSessionFeature(
            applicationContext,
            MediaService::class.java,
            browserComponents.store
        )
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        ChannelUserController.setDefaultChannel("natural") // "natural" 或 "paid"

        CoreLogger.setLogEnabled(BuildConfig.DEBUG)
        AdLogger.setLogEnabled(BuildConfig.DEBUG)
    }

    override fun onCreate() {
        super.onCreate()

        // 只在主进程中初始化，避免在其他进程（如 GeckoView 进程）中重复初始化
        if (!isMainProcess()) {
            return
        }

        // 预热引擎，确保 GeckoRuntime 正确初始化
        browserComponents.engine.warmUp()

        // 启动媒体会话功能，监听媒体播放状态
        mediaSessionFeature.start()

        // 初始化 Web 通知功能，注册到引擎
        // 这样所有网页都可以使用 Notification API
        browserComponents.webNotificationFeature

        // 初始化 WebExtension 支持（PDF.js 等内置扩展需要）
        WebExtensionSupport.initialize(
            runtime = browserComponents.engine,
            store = browserComponents.store,
            onNewTabOverride = { _, engineSession, url ->
                browserComponents.tabsUseCases.addTab(
                    url = url,
                    selectTab = true,
                    engineSession = engineSession
                )
            },
            onCloseTabOverride = { _, sessionId ->
                browserComponents.tabsUseCases.removeTab(sessionId)
            },
            onSelectTabOverride = { _, sessionId ->
                browserComponents.tabsUseCases.selectTab(sessionId)
            }
        )

        // 恢复之前保存的标签页状态并设置自动保存
        restoreBrowserState()

        // 恢复下载任务并清理失效记录
        browserComponents.downloadsUseCases.restoreDownloads()
        browserComponents.store.dispatch(
            mozilla.components.browser.state.action.DownloadAction.RemoveDeletedDownloads
        )
        registerActivityLifecycleCallbacks()
    }

    /**
     * 恢复浏览器状态并设置自动保存
     * 从 SessionStorage 中恢复之前保存的标签页，并设置自动保存策略
     */
    private fun restoreBrowserState() = GlobalScope.launch(Dispatchers.Main) {
        try {
            // 使用 TabsUseCases.restore 恢复标签页状态
            browserComponents.tabsUseCases.restore(browserComponents.sessionStorage)

            // 设置自动保存策略：
            // 1. 前台运行时每 30 秒保存一次
            // 2. 应用进入后台时保存
            // 3. 标签页状态发生变化时保存
            browserComponents.sessionStorage.autoSave(browserComponents.store)
                .periodicallyInForeground(interval = 30, unit = TimeUnit.SECONDS)
                .whenGoingToBackground()
                .whenSessionsChange()
        } catch (e: Exception) {
            // 如果恢复失败，记录错误但不崩溃
            // 这样应用至少可以启动，用户可以创建新标签页
            Log.e(TAG, "Failed to restore browser state", e)
        }
    }

    private fun registerActivityLifecycleCallbacks() {
        ActivityUtils.addActivityLifecycleCallbacks(object : Utils.ActivityLifecycleCallbacks() {
            override fun onActivityCreated(activity: Activity) {
                super.onActivityCreated(activity)
                ReportDataManager.reportData("activity_created", mapOf("activity_name" to activity.javaClass.simpleName))
            }

            override fun onActivityResumed(activity: Activity) {
                super.onActivityResumed(activity)
                ReportDataManager.reportData("activity_resumed", mapOf("activity_name" to activity.javaClass.simpleName))
            }

            override fun onActivityPaused(activity: Activity) {
                super.onActivityPaused(activity)
                ReportDataManager.reportData("activity_paused", mapOf("activity_name" to activity.javaClass.simpleName))
            }

            override fun onActivityStopped(activity: Activity) {
                super.onActivityStopped(activity)
                ReportDataManager.reportData("activity_stopped", mapOf("activity_name" to activity.javaClass.simpleName))
            }
            override fun onActivityDestroyed(activity: Activity) {
                super.onActivityDestroyed(activity)
                ReportDataManager.reportData("activity_destroyed", mapOf("activity_name" to activity.javaClass.simpleName))
            }
        })
        AppUtils.registerAppStatusChangedListener(object : Utils.OnAppStatusChangedListener {
            override fun onForeground(activity: Activity?) {
                activity?.let {
                    if (ActivityUtils.isActivityExistsInStack(SplashActivity::class.java)) {
                        return@let
                    }
                    LogUtils.d(it.localClassName)
                    // 过滤非 Browser 相关的 Activity
                    if (!it.javaClass.name.contains("com.example.browser")) {
                        return@let
                    }
                    // 过滤最近 5 秒内进入后台的 Activity
                    if (System.currentTimeMillis() - lastBackgroundTime < 5000) {
                        return@let
                    }
                    ThreadUtils.runOnUiThreadDelayed({
                        it.startActivity(Intent(it, SplashActivity::class.java).apply {
                            putExtra(SplashActivity.EXTRA_IS_FROM_BACKGROUND,true)
                        })
                    },200)
                }
            }

            override fun onBackground(activity: Activity?) {
                lastBackgroundTime = System.currentTimeMillis()
            }
        })
    }

}

private var lastBackgroundTime = 0L

/**
 * 扩展属性,方便在 Context 中获取 BrowserComponents
 * 使用方式: context.components
 */
val android.content.Context.components: BrowserComponents
    get() = (applicationContext as BrowserApplication).browserComponents
