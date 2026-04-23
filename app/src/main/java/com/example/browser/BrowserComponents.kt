package com.example.browser

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.example.browser.search.DefaultSearchEngines
import com.example.browser.service.MediaService
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.engine.gecko.fetch.GeckoViewFetchClient
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.session.storage.SessionStorage
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.state.searchEngines
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.browser.thumbnails.ThumbnailsMiddleware
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.permission.SitePermissionsStorage
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.app.links.AppLinksInterceptor
import mozilla.components.feature.app.links.AppLinksUseCases
import mozilla.components.feature.downloads.DefaultDateTimeProvider
import mozilla.components.feature.downloads.DefaultFileSizeFormatter
import mozilla.components.feature.downloads.DownloadMiddleware
import mozilla.components.feature.downloads.DownloadsUseCases
import mozilla.components.feature.media.MediaSessionFeature
import mozilla.components.feature.search.SearchUseCases
import mozilla.components.feature.search.middleware.SearchMiddleware
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.feature.sitepermissions.OnDiskSitePermissionsStorage
import mozilla.components.feature.webcompat.WebCompatFeature
import mozilla.components.feature.webnotifications.WebNotificationFeature
import mozilla.components.feature.prompts.file.FileUploadsDirCleaner
import mozilla.components.support.base.android.NotificationsDelegate
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

import com.example.browser.feature.VideoDetectorFeature
import com.example.browser.utils.SpUtils

/**
 * 浏览器组件管理类
 * 负责初始化和管理 GeckoView 引擎、Store 等核心组件
 *
 * 使用方式:
 * val components = context.applicationContext as BrowserApplication
 * components.browserComponents.engine
 */
class BrowserComponents(private val applicationContext: Context) {

    /**
     * GeckoRuntime - 浏览器引擎运行时
     * 整个应用生命周期内只创建一次
     */
    private val runtime: GeckoRuntime by lazy {
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(true)  // 启用 about:config
            .extensionsWebAPIEnabled(true)  // 启用扩展 WebAPI（PDF.js 需要）
            .debugLogging(BuildConfig.DEBUG)  // Debug 模式下启用日志
            .consoleOutput(BuildConfig.DEBUG)  // Debug 模式下输出 console.log
            .build()

        GeckoRuntime.create(applicationContext, runtimeSettings)
    }

    /**
     * Engine - 浏览器引擎
     * 基于 GeckoView 的封装
     */
    val engine: Engine by lazy {
        GeckoEngine(
            context = applicationContext,
            defaultSettings = DefaultSettings(
                javascriptEnabled = true,  // 启用 JavaScript
                domStorageEnabled = true,  // 启用 DOM Storage
                webFontsEnabled = true,    // 启用网页字体
                remoteDebuggingEnabled = BuildConfig.DEBUG,  // Debug 模式下启用远程调试
                allowFileAccess = true,
                allowFileAccessFromFileURLs = true,
                allowUniversalAccessFromFileURLs = true,
                // 注册请求拦截器，用于处理第三方应用 scheme 协议跳转
                requestInterceptor = AppRequestInterceptor(applicationContext)
            ),
            runtime = runtime
        ).also {
            // 安装 WebCompat 功能，提供网站兼容性修复（包括 PDF.js 支持）
            WebCompatFeature.install(it)
        }
    }

    /**
     * Client - 网络请求客户端
     * 用于下载、Favicon 等网络请求
     */
    val client: Client by lazy {
        GeckoViewFetchClient(applicationContext, runtime)
    }

    /**
     * SessionStorage - 会话存储
     * 负责持久化标签页状态，应用重启后可以恢复
     */
    val sessionStorage by lazy {
        SessionStorage(applicationContext, engine)
    }

    /**
     * ThumbnailStorage - 缩略图存储
     * 负责保存和加载标签页缩略图
     */
    val thumbnailStorage by lazy {
        ThumbnailStorage(applicationContext)
    }

    /**
     * SitePermissionsStorage - 网站权限存储
     * 负责保存和管理网站权限（通知、位置、摄像头等）
     */
    val sitePermissionsStorage: SitePermissionsStorage by lazy {
        OnDiskSitePermissionsStorage(applicationContext)
    }

    /**
     * BrowserIcons - 网站图标管理
     * 自动加载和缓存网站 favicon
     * 使用 install() 方法将其安装到 Engine 和 Store 中
     * BrowserIcons 内部会自动处理图标的持久化和恢复
     */
    val icons by lazy {
        BrowserIcons(applicationContext, client)
    }

    /**
     * FileSizeFormatter - 文件大小格式化器
     * 用于下载通知显示
     */
    val fileSizeFormatter by lazy {
        DefaultFileSizeFormatter(applicationContext)
    }

    /**
     * DateTimeProvider - 日期时间提供器
     * 用于下载通知显示
     */
    val dateTimeProvider by lazy {
        DefaultDateTimeProvider()
    }

    /**
     * NotificationsDelegate - 通知代理
     * 用于管理下载通知
     */
    val notificationsDelegate by lazy {
        NotificationsDelegate(NotificationManagerCompat.from(applicationContext))
    }

    /**
     * Store - 浏览器状态管理
     * 管理所有标签页、加载状态、搜索引擎等
     *
     * 包含的中间件：
     * - SearchMiddleware: 自动加载和保存搜索引擎配置
     * - DownloadMiddleware: 监听和处理下载请求
     * - ThumbnailsMiddleware: 自动捕获和管理标签页缩略图
     * - EngineMiddleware: 管理引擎会话和状态同步
     */
    val store: BrowserStore by lazy {
        BrowserStore(
            middleware = listOf(
                // 搜索中间件：自动加载默认搜索引擎和用户自定义搜索引擎
                SearchMiddleware(applicationContext),
                // 下载中间件：自动处理下载请求并启动 DownloadService
                DownloadMiddleware(
                    applicationContext,
                    com.example.browser.service.DownloadService::class.java
                ),
                // 缩略图中间件：自动捕获标签页截图并保存到 ThumbnailStorage
                ThumbnailsMiddleware(thumbnailStorage)
            ) + EngineMiddleware.create(
                engine = engine,
                // 不自动释放内存,由我们手动管理
                trimMemoryAutomatically = false
            )
        ).apply {
            // 安装 BrowserIcons，使其能够自动加载和管理网站图标
            icons.install(engine, this)
            
            // 初始化默认搜索引擎
            initializeDefaultSearchEngines()

            // 启用媒体会话功能，确保后台播放时显示通知
            MediaSessionFeature(
                applicationContext,
                MediaService::class.java,
                this
            ).start()
        }
    }


    /**
     * 初始化默认搜索引擎
     * 如果 BrowserStore 中没有搜索引擎，则加载内置的默认搜索引擎
     */
    private fun BrowserStore.initializeDefaultSearchEngines() {
        // 加载默认搜索引擎
        val defaultEngines = DefaultSearchEngines.getDefaultSearchEngines(applicationContext)
        
        // 从 SharedPreferences 读取用户上次选择的搜索引擎
        val savedEngineId = SpUtils.getSavedSearchEngineId(applicationContext)
        val savedEngineName = SpUtils.getSavedSearchEngineName(applicationContext)
        
        // 使用 SetSearchEnginesAction 设置搜索引擎列表
        dispatch(
            SearchAction.SetSearchEnginesAction(
                regionSearchEngines = defaultEngines,
                customSearchEngines = emptyList(),
                hiddenSearchEngines = emptyList(),
                disabledSearchEngineIds = emptyList(),
                additionalAvailableSearchEngines = emptyList(),
                additionalSearchEngines = emptyList(),
                regionSearchEnginesOrder = defaultEngines.map { it.id },
                regionDefaultSearchEngineId = DefaultSearchEngines.DEFAULT_SEARCH_ENGINE_ID,
                userSelectedSearchEngineId = savedEngineId,
                userSelectedSearchEngineName = savedEngineName
            )
        )
    }

    /**
     * SessionUseCases - 会话操作用例
     * 提供加载 URL、重新加载、停止等操作
     */
    val sessionUseCases: SessionUseCases by lazy {
        SessionUseCases(store)
    }

    /**
     * TabsUseCases - 标签页操作用例
     * 提供添加、删除、选择标签页等操作
     */
    val tabsUseCases: TabsUseCases by lazy {
        TabsUseCases(store)
    }

    /**
     * HistoryStorage - 历史记录存储
     * 使用 PlacesHistoryStorage 存储浏览历史
     * 支持搜索建议、自动补全等功能
     */
    val historyStorage by lazy {
        PlacesHistoryStorage(applicationContext)
    }

    /**
     * SearchUseCases - 搜索操作用例
     * 提供默认搜索、选择搜索引擎等操作
     */
    val searchUseCases: SearchUseCases by lazy {
        SearchUseCases(store, tabsUseCases, sessionUseCases)
    }

    /**
     * DownloadsUseCases - 下载操作用例
     * 提供下载管理相关操作
     */
    val downloadsUseCases: DownloadsUseCases by lazy {
        DownloadsUseCases(store)
    }

    /**
     * AppLinksUseCases - 应用链接操作用例
     * 提供打开第三方应用、获取应用链接等操作
     */
    val appLinksUseCases: AppLinksUseCases by lazy {
        AppLinksUseCases(applicationContext)
    }

    /**
     * AppLinksInterceptor - 应用链接拦截器
     * 自动识别和处理第三方应用的 scheme 协议
     * 
     * 支持的协议包括：
     * - 自定义 scheme（如 weixin://、alipay://、taobao:// 等）
     * - market:// 协议（Google Play 商店）
     * - intent:// 协议（Android Intent URI）
     * - 其他应用注册的 scheme
     * 
     * 注意：http/https 链接不会被拦截，始终在浏览器中打开
     * 
     * launchInApp 参数控制是否自动打开第三方应用：
     * - true: 自动打开第三方应用（推荐）
     * - false: 弹出选择器让用户选择
     */
    val appLinksInterceptor: AppLinksInterceptor by lazy {
        AppLinksInterceptor(
            context = applicationContext,
            alwaysDeniedSchemes = setOf("http", "https"),  // 不拦截 http/https
            launchInApp = {
                // 默认自动打开第三方应用
                // 如果需要让用户选择，可以从 SharedPreferences 读取用户设置
                // SpUtils.getBoolean(applicationContext, "auto_open_external_app", true)
                true
            }
        )
    }

    /**
     * WebNotificationFeature - Web 通知功能
     * 处理网页推送通知，支持订阅网页新闻等功能
     * 
     * 功能：
     * - 处理网页的 Notification API 请求
     * - 显示来自网页的推送通知
     * - 管理通知权限
     */
    val webNotificationFeature: WebNotificationFeature by lazy {
        WebNotificationFeature(
            context = applicationContext,
            engine = engine,
            browserIcons = icons,
            smallIcon = R.mipmap.ic_logo,
            sitePermissionsStorage = sitePermissionsStorage,
            activityClass = com.example.browser.ui.web.WebActivity::class.java,
            notificationsDelegate = notificationsDelegate
        )
    }
    
    /**
     * VideoDetectorFeature - 视频检测功能
     * 通过 WebExtension 监听网页中的视频链接
     */
    val videoDetectorFeature: VideoDetectorFeature by lazy {
        VideoDetectorFeature(engine, store)
    }

    /**
     * FileUploadsDirCleaner - 文件上传目录清理器
     * 用于清理文件上传时产生的临时文件
     * PromptFeature 需要此组件来处理网页的文件选择功能
     */
    val fileUploadsDirCleaner: FileUploadsDirCleaner by lazy {
        FileUploadsDirCleaner { applicationContext.cacheDir }
    }
}
