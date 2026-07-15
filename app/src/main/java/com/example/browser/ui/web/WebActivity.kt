package com.example.browser.ui.web

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.activity.viewModels
import com.blankj.utilcode.util.ToastUtils
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ActivityUtils
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.components
import com.example.browser.databinding.ActivityWebBinding
import com.example.browser.data.bookmark.BookmarkRepository
import com.example.browser.ui.MainActivity
import com.example.browser.ui.bookmark.BookmarkActivity
import com.example.browser.ui.bookmark.BookmarkEditActivity
import com.example.browser.ui.download.DownloadActivity
import com.example.browser.ui.download.DownloadStartBottomSheet
import com.example.browser.ui.search.SearchActivity
import com.example.browser.ui.tabs.BrowserTabsActivity
import com.example.browser.utils.NotificationPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.VisitType
import mozilla.components.feature.downloads.DownloadsFeature
import mozilla.components.feature.downloads.manager.FetchDownloadManager
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.feature.app.links.AppLinksFeature
import mozilla.components.feature.prompts.PromptFeature
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.sitepermissions.SitePermissionsFeature
import mozilla.components.feature.tabs.WindowFeature
import mozilla.components.feature.webnotifications.WebNotificationFeature
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import com.browser.common.loadInterstitial

/**
 * 网页浏览Activity
 * 基于 Mozilla Components 实现
 *
 * 功能：
 * 1. 使用 EngineView 加载网页
 * 2. 地址栏显示和编辑URL
 * 3. 页面导航（前进、后退、刷新）
 * 4. 显示页面加载进度
 * 5. 监听页面状态变化
 * 6. 自动捕获标签页缩略图
 */
class WebActivity : BaseActivity<ActivityWebBinding, WebModel>() {

    companion object {
        private const val TAG = "WebActivity"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FROM_NEWS = "FROM_NEWS"
        const val EXTRA_IS_LOCAL_FILE = "IS_LOCAL_FILE"
        const val EXTRA_MIME_TYPE = "MIME_TYPE"
        private const val REQUEST_CODE_SEARCH = 1001
        private const val DOWNLOAD_SHEET_TAG = "DownloadStartBottomSheet"
        private const val WEB_MENU_SHEET_TAG = "WebMenuBottomSheet"
    }

    // 当前标签页ID
    private var currentTabId: String? = null

    // Store流观察的协程作用域
    private var storeScope: CoroutineScope? = null
    private var downloadsScope: CoroutineScope? = null
    private var searchEngineScope: CoroutineScope? = null

    // 缓存上一次的进度和加载状态，减少重复刷新
    private var lastProgress: Int = -1
    private var lastLoading: Boolean? = null
    private var lastUrl: String? = null

    private var downloadSheet: DownloadStartBottomSheet? = null
    private var sheetDownloadId: String? = null
    private val shownDownloadIds = mutableSetOf<String>()
    private var webMenuSheet: WebMenuBottomSheet? = null
    
    // SessionFeature：自动将 EngineView 与当前选中的标签页关联
    // 这个 Feature 会自动处理标签页切换，不需要手动管理
    private val sessionFeature = ViewBoundFeatureWrapper<SessionFeature>()

    // DownloadsFeature：处理下载请求
    // 监听下载事件并通过 DownloadManager 处理下载
    private val downloadsFeature = ViewBoundFeatureWrapper<DownloadsFeature>()

    // WindowFeature：处理新窗口/新标签页请求
    // PDF.js 等功能需要此特性来打开新标签页
    private val windowFeature = ViewBoundFeatureWrapper<WindowFeature>()

    // FindInPageIntegration：页面内查找功能
    private val findInPageIntegration = ViewBoundFeatureWrapper<FindInPageIntegration>()

    // AppLinksFeature：处理第三方应用链接跳转
    // 当网页中有第三方应用的 scheme 链接时，自动启动对应应用
    private val appLinksFeature = ViewBoundFeatureWrapper<AppLinksFeature>()

    // SitePermissionsFeature：处理网站权限请求（摄像头、麦克风、位置等）
    private val sitePermissionsFeature = ViewBoundFeatureWrapper<SitePermissionsFeature>()

    // PromptFeature：处理网页弹窗、文件上传等
    private val promptsFeature = ViewBoundFeatureWrapper<PromptFeature>()

    // 权限请求 Launcher
    private lateinit var requestSitePermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var requestPromptsPermissionsLauncher: ActivityResultLauncher<Array<String>>

    // BrowserThumbnails：自动捕获标签页缩略图
    // 在页面加载完成时自动截图并保存到 ThumbnailStorage
    private val thumbnails: BrowserThumbnails by lazy {
        BrowserThumbnails(applicationContext, binding.engineView, components.store)
    }

    private val isFromNews: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_FROM_NEWS, false)
    }

    override fun initViewModel(): WebModel {
        return viewModels<WebModel>().value
    }

    override fun initBinding(): ActivityWebBinding {
        return ActivityWebBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        // 在 super.onCreate 之前注册权限请求 Launcher
        setupPermissionLaunchers()
        super.onCreate(savedInstanceState)
    }

    /**
     * 设置权限请求 Launcher
     * 必须在 onCreate 中 super.onCreate 之前调用
     */
    private fun setupPermissionLaunchers() {
        requestSitePermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val permissions = results.keys.toTypedArray()
            val grantResults = results.values.map {
                if (it) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
            }.toIntArray()
            sitePermissionsFeature.withFeature {
                it.onPermissionsResult(permissions, grantResults)
            }
        }

        requestPromptsPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val permissions = results.keys.toTypedArray()
            val grantResults = results.values.map {
                if (it) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
            }.toIntArray()
            promptsFeature.withFeature {
                it.onPermissionsResult(permissions, grantResults)
            }
        }
    }

    override fun initView() {
        setupListeners()
        setupSessionFeature()
        setupDownloadsFeature()
        setupWindowFeature()
        setupFindInPageIntegration()
        setupAppLinksFeature()
        setupSitePermissionsFeature()
        setupPromptsFeature()
        setupThumbnails()

        // 优先尝试恢复已有 tab
        if (!restoreSelectedTab()) {
            // 没有已有 tab,加载初始 URL
            loadInitialUrl()
        } else {
            // 有已有 tab,检查是否有新的 URL 需要加载
            intent.getStringExtra(EXTRA_URL)?.let { url ->
                currentTabId?.let { tabId ->
                    components.sessionUseCases.loadUrl(url, tabId)
                }
            }
        }

        observeBrowserState()
        observeSearchEngine()
        observeDownloadState()
        updateTabCount()

        // 请求通知权限（Android 13+）
        requestNotificationPermissionIfNeeded()
        if (isFromNews) {
            showNativeDialog()
        }
    }

    private fun showNativeDialog() {
//        NativeDialog.show(this)
    }
    
    private var notificationPermissionRunnable: Runnable? = null

    /**
     * 请求通知权限（如果需要）
     * Android 13+ 需要运行时请求 POST_NOTIFICATIONS 权限
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            // 延迟请求，避免在页面加载时打断用户
            val runnable = Runnable {
                // 延迟期间 Activity 可能已退出，避免 XXPermissions 的状态校验抛异常
                if (isFinishing || isDestroyed) {
                    return@Runnable
                }
                NotificationPermissionHelper.requestNotificationPermission(
                    activity = this,
                    onGranted = {
                        // 权限已授予，Web 通知功能可以正常使用
                    },
                    onDenied = {
                        // 权限被拒绝，Web 通知功能将无法使用
                        // 可以在这里显示提示信息
                    }
                )
            }
            notificationPermissionRunnable = runnable
            binding.root.postDelayed(runnable, 2000) // 延迟 2 秒请求
        }
    }

    /**
     * 设置 WindowFeature
     * 自动处理 GeckoView 的 onNewSession 回调，创建新标签页
     * 这对于 PDF.js 等需要打开新窗口的功能至关重要
     */
    private fun setupWindowFeature() {
        windowFeature.set(
            feature = WindowFeature(
                store = components.store,
                tabsUseCases = components.tabsUseCases
            ),
            owner = this,
            view = binding.root
        )
    }

    /**
     * 设置页面内查找功能
     */
    private fun setupFindInPageIntegration() {
        findInPageIntegration.set(
            feature = FindInPageIntegration(
                store = components.store,
                stub = binding.stubFindInPage,
                engineView = binding.engineView,
                onClose = {
                    // 查找工具栏关闭时的回调
                }
            ),
            owner = this,
            view = binding.root
        )
    }

    /**
     * 设置 AppLinksFeature
     * 处理第三方应用链接跳转
     * 当用户点击网页中的第三方应用 scheme 链接时，自动启动对应应用
     */
    private fun setupAppLinksFeature() {
        appLinksFeature.set(
            feature = AppLinksFeature(
                context = applicationContext,
                store = components.store,
                sessionId = null, // null 表示使用当前选中的标签页
                fragmentManager = supportFragmentManager,
                launchInApp = {
                    // 使用 BrowserComponents 中配置的 appLinksInterceptor 的设置
                    // 默认为 true，自动打开第三方应用
                    true
                },
                useCases = components.appLinksUseCases
            ),
            owner = this,
            view = binding.root
        )
    }

    /**
     * 设置 SitePermissionsFeature
     * 处理网站权限请求（摄像头、麦克风、位置等）
     * 当网页请求使用设备功能时，会显示权限请求对话框
     */
    private fun setupSitePermissionsFeature() {
        sitePermissionsFeature.set(
            feature = SitePermissionsFeature(
                context = this,
                fragmentManager = supportFragmentManager,
                sessionId = null, // null 表示使用当前选中的标签页
                storage = components.sitePermissionsStorage,
                onNeedToRequestPermissions = { permissions ->
                    requestSitePermissionsLauncher.launch(permissions)
                },
                onShouldShowRequestPermissionRationale = { permission ->
                    shouldShowRequestPermissionRationale(permission)
                },
                store = components.store
            ),
            owner = this,
            view = binding.root
        )
    }

    /**
     * 设置 PromptFeature
     * 处理网页弹窗、文件上传、颜色选择等
     * 当网页需要选择文件上传时，会打开系统文件选择器
     */
    private fun setupPromptsFeature() {
        promptsFeature.set(
            feature = PromptFeature(
                activity = this,
                store = components.store,
                fragmentManager = supportFragmentManager,
                tabsUseCases = components.tabsUseCases,
                fileUploadsDirCleaner = components.fileUploadsDirCleaner,
                onNeedToRequestPermissions = { permissions ->
                    requestPromptsPermissionsLauncher.launch(permissions)
                },
                isSuggestEmailMaskEnabled = { false },
                isEmailMaskFeatureEnabled = { false },
                androidPhotoPicker = null,
            ),
            owner = this,
            view = binding.root
        )
    }

    /**
     * 设置缩略图自动捕获
     * BrowserThumbnails 会在页面加载完成时自动截图
     */
    private fun setupThumbnails() {
        // 启动缩略图自动捕获
        // 这会监听页面加载状态，在合适的时机自动截图
        lifecycle.addObserver(thumbnails)
    }
    
    /**
     * 设置 SessionFeature
     * 这个 Feature 会自动将 EngineView 与当前选中的标签页关联
     * 当标签页切换时，它会自动切换 EngineView 显示的内容
     */
    private fun setupSessionFeature() {
        sessionFeature.set(
            feature = SessionFeature(
                store = components.store,
                goBackUseCase = components.sessionUseCases.goBack,
                goForwardUseCase = components.sessionUseCases.goForward,
                engineView = binding.engineView,
                tabId = currentTabId  // null 表示使用当前选中的标签页
            ),
            owner = this,
            view = binding.root
        )
    }

    /**
     * 设置 DownloadsFeature
     * 监听并处理网页中的下载请求
     * 当网页触发下载时，会显示自定义确认对话框，然后启动 DownloadService 进行后台下载
     */
    private fun setupDownloadsFeature() {
        downloadsFeature.set(
            feature = DownloadsFeature(
                applicationContext = applicationContext,
                store = components.store,
                useCases = components.downloadsUseCases,
                downloadFileUtils = components.downloadFileUtils,
                fragmentManager = null,  // 设置为 null 以禁用内置对话框
                downloadManager = FetchDownloadManager(
                    applicationContext = applicationContext,
                    store = components.store,
                    service = com.example.browser.service.DownloadService::class,
                    notificationsDelegate = components.notificationsDelegate
                ),
                onNeedToRequestPermissions = { permissions ->
                    // 请求存储权限
                    // 这里可以使用 XXPermissions 或 ActivityCompat 请求权限
                    // 为简化，假设已有权限（在 FileFragment 中已请求）
                },
                // 使用自定义下载对话框
                customFirstPartyDownloadDialog = {
                        currentDownload,
                        duplicateFileName,
                        positiveAction,
                        negativeAction,
                        _ ->
                    val download = currentDownload.value
                    showCustomDownloadDialog(
                        filename = duplicateFileName.value ?: download.fileName ?: "download",
                        fileSize = download.contentLength ?: 0L,
                        onDownloadConfirmed = { positiveAction.value.invoke(download) },
                        onDownloadCancelled = { negativeAction.value.invoke() }
                    )
                }
            ),
            owner = this,
            view = binding.root
        )
    }

    /**
     * 显示自定义下载对话框
     */
    private fun showCustomDownloadDialog(
        filename: String,
        fileSize: Long,
        onDownloadConfirmed: () -> Unit,
        onDownloadCancelled: () -> Unit
    ) {
        if (isFinishing || isDestroyed) {
            return
        }
        // 创建 DownloadState 对象用于对话框显示
        val downloadState = DownloadState(
            url = "",
            fileName = filename,
            contentType = null,
            contentLength = fileSize,
            status = DownloadState.Status.INITIATED
        )

        val dialog = com.example.browser.ui.download.CustomDownloadDialogFragment.newInstance(
            downloadState = downloadState,
            onConfirm = { download ->
                onDownloadConfirmed()
            }
        )

        // 避免重复显示对话框
        if (supportFragmentManager.findFragmentByTag("CustomDownloadDialog") == null) {
            dialog.show(supportFragmentManager, "CustomDownloadDialog")
        }
    }

    /**
     * 尝试恢复已选中的标签页
     * 从标签页管理界面返回时使用
     *
     * @return true 如果有标签页存在，false 需要创建新标签页
     */
    private fun restoreSelectedTab(): Boolean {
        val selectedTab = components.store.state.selectedTab
        if (selectedTab != null) {
            currentTabId = selectedTab.id
            // SessionFeature 会自动处理 EngineView 的渲染
            return true
        }
        return false
    }

    /**
     * 加载初始URL
     * 创建一个新的标签页并加载URL
     */
    private fun loadInitialUrl() {
        val url = intent.getStringExtra(EXTRA_URL) ?: getDefaultSearchEngineHomePage()
        val isLocalFile = intent.getBooleanExtra(EXTRA_IS_LOCAL_FILE, false)

        if (isLocalFile) {
            // 本地文件，使用 loadData 加载
            loadLocalFile(url)
        } else {
            // 网络 URL，正常加载
            components.tabsUseCases.addTab(
                url = url,
                selectTab = true,  // 自动选中该标签页
                private = false    // 非隐私模式
            )

            // 记录当前标签页ID
            currentTabId = components.store.state.selectedTabId
        }
    }

    /**
     * 加载本地文件
     */
    private fun loadLocalFile(filePath: String) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                android.widget.Toast.makeText(this, "文件不存在", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            // 读取文件内容
            val content = file.readText()
            val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "text/plain"

            // 创建一个新标签页
            components.tabsUseCases.addTab(
                url = "about:blank",  // 先创建空白页
                selectTab = true,
                private = false
            )

            // 记录当前标签页ID
            currentTabId = components.store.state.selectedTabId

            lifecycleScope.launch {
                // 使用 loadData 加载文件内容
                currentTabId?.let { tabId ->
                    components.sessionUseCases.loadData(
                        data = content,
                        mimeType = mimeType,
                        encoding = "UTF-8",
                        tabId = tabId
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取当前搜索引擎的首页 URL
     */
    private fun getDefaultSearchEngineHomePage(): String {
        val searchEngine = components.store.state.search.selectedOrDefaultSearchEngine
        return searchEngine?.let { engine ->
            // 从搜索引擎的 resultUrls 提取主域名作为首页
            engine.resultUrls.firstOrNull()?.let { url ->
                val uri = Uri.parse(url)
                "${uri.scheme}://${uri.host}"
            }
        } ?: "https://www.google.com" // 后备方案
    }

    /**
     * 监听浏览器状态变化
     * 使用 Flow 监听 Store 中的状态更新
     */
    private fun observeBrowserState() {
        // 先取消之前的订阅，确保不会重复收集
        storeScope?.cancel()
        lastProgress = -1
        lastLoading = null
        storeScope = components.store.flowScoped(
            dispatcher = kotlinx.coroutines.Dispatchers.Main.immediate,
        ) { flow ->
            flow.mapNotNull { state -> state.selectedTab }
                .collect { tab ->
                    // 更新当前标签页 ID
                    if (currentTabId != tab.id) {
                        currentTabId = tab.id
                    }

                    // SessionFeature 会自动处理 EngineView 的切换
                    // 我们只需要更新 UI
                    updateUI(tab)

                    val progress = tab.content.progress
                    if (progress != lastProgress) {
                        lastProgress = progress
                        updateProgress(tab)
                    }

                    val loading = tab.content.loading
                    if (loading != lastLoading) {
                        lastLoading = loading
                        updateLoadingState(tab)
                    }
                }
        }
    }

    private fun observeDownloadState() {
        downloadsScope?.cancel()
        downloadsScope = components.store.flowScoped(
            dispatcher = kotlinx.coroutines.Dispatchers.Main.immediate,
        ) { flow ->
            flow.map { state -> state.downloads }
                .collect { downloadsMap ->
                    handleDownloadUpdates(downloadsMap.values)
                }
        }
    }

    private fun observeSearchEngine() {
        searchEngineScope?.cancel()
        searchEngineScope = components.store.flowScoped(
            dispatcher = kotlinx.coroutines.Dispatchers.Main.immediate,
        ) { flow ->
            flow.map { state -> state.search.selectedOrDefaultSearchEngine?.id }
                .distinctUntilChanged()
                .collect {
                    updateSearchEngineIcon()
                }
        }
    }

    private fun handleDownloadUpdates(downloads: Collection<DownloadState>) {
        val nonPrivate = downloads.filter { !it.private }
        if (nonPrivate.isEmpty()) {
            dismissDownloadSheet()
            return
        }

        val active = nonPrivate.filter {
            it.status == DownloadState.Status.DOWNLOADING ||
                it.status == DownloadState.Status.INITIATED ||
                it.status == DownloadState.Status.PAUSED
        }

        val candidate = when {
            active.isNotEmpty() -> active.maxByOrNull { it.createdTime }
            sheetDownloadId != null -> nonPrivate.firstOrNull { it.id == sheetDownloadId }
            else -> null
        }

        if (candidate == null) {
            dismissDownloadSheet()
            return
        }

        when (candidate.status) {
            DownloadState.Status.CANCELLED, DownloadState.Status.FAILED -> {
                if (candidate.id == sheetDownloadId) {
                    dismissDownloadSheet()
                }
            }
            DownloadState.Status.COMPLETED -> {
                if (candidate.id == sheetDownloadId) {
                    showOrUpdateDownloadSheet(candidate)
                }
            }
            else -> showOrUpdateDownloadSheet(candidate)
        }
    }

    private fun showOrUpdateDownloadSheet(download: DownloadState) {
        // 检查 Activity 状态，避免在 onSaveInstanceState 之后显示 Dialog
        if (isFinishing || isDestroyed) {
            return
        }

        val fileName = download.fileName ?: getString(R.string.download_unknown_file)
        val isSheetShowing = downloadSheet?.isAdded == true

        when {
            isSheetShowing && sheetDownloadId == download.id -> {
                downloadSheet?.updateFileName(fileName)
                updateDownloadSheet(download)
            }
            !shownDownloadIds.contains(download.id) -> {
                dismissDownloadSheet()
                val sheet = DownloadStartBottomSheet.newInstance(fileName)
                sheet.setOnViewClickListener {
                    DownloadActivity.start(this@WebActivity)
                }
                sheet.setOnDismissListener {
                    downloadSheet = null
                    sheetDownloadId = null
                }
                // 安全地显示 DialogFragment，避免状态丢失异常
                try {
                    // 使用 commitAllowingStateLoss 替代 commit
                    val transaction = supportFragmentManager.beginTransaction()
                    transaction.add(sheet, DOWNLOAD_SHEET_TAG)
                    transaction.commitAllowingStateLoss()
                    
                    downloadSheet = sheet
                    sheetDownloadId = download.id
                    shownDownloadIds.add(download.id)
                    updateDownloadSheet(download)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            else -> {
                // 已经展示过且当前没有弹框，忽略
            }
        }
    }

    private fun updateDownloadSheet(download: DownloadState) {
        val sheet = downloadSheet ?: return
        val progress = when (download.status) {
            DownloadState.Status.COMPLETED -> 100
            else -> calculateProgress(download)
        }
        val indeterminate = download.status != DownloadState.Status.COMPLETED && progress == null
        val statusText = when (download.status) {
            DownloadState.Status.DOWNLOADING, DownloadState.Status.INITIATED -> {
                if (progress != null) getString(R.string.download_progress_percent, progress) else getString(R.string.download_started)
            }
            DownloadState.Status.PAUSED -> getString(R.string.download_paused)
            DownloadState.Status.COMPLETED -> getString(R.string.download_complete)
            else -> getString(R.string.download_started)
        }
        sheet.updateProgress(statusText, progress, indeterminate)
    }

    private fun dismissDownloadSheet() {
        downloadSheet?.let { sheet ->
            if (sheet.isAdded) {
                sheet.dismissAllowingStateLoss()
            }
        }
        downloadSheet = null
        sheetDownloadId = null
    }

    private fun showWebMenu() {
        if (isFinishing || isDestroyed) {
            return
        }
        val tab = components.store.state.selectedTab ?: return
        val url = tab.content.url
        if (url.isBlank()) {
            ToastUtils.showShort(R.string.bookmark_add_failed)
            return
        }
        if (supportFragmentManager.isStateSaved) {
            return
        }
        val repository = BookmarkRepository.getInstance(applicationContext)
        val isBookmarked = repository.isBookmarked(url)
        val sheet = WebMenuBottomSheet.newInstance(
            isBookmarked = isBookmarked,
            canGoBack = tab.content.canGoBack,
            canGoForward = tab.content.canGoForward,
            isDesktopMode = tab.content.desktopMode,
            currentUrl = url
        ).apply {
            setListener(createWebMenuListener())
        }
        sheet.setOnDismissCallback {
            webMenuSheet = null
        }
        webMenuSheet = sheet
        sheet.show(supportFragmentManager, WEB_MENU_SHEET_TAG)
    }

    private fun createWebMenuListener(): WebMenuBottomSheet.Listener {
        return object : WebMenuBottomSheet.Listener {
            override fun onBack() {
                components.store.state.selectedTab?.let { tab ->
                    components.sessionUseCases.goBack(tab.id)
                }
            }

            override fun onForward() {
                components.store.state.selectedTab?.let { tab ->
                    components.sessionUseCases.goForward(tab.id)
                }
            }

            override fun onRefresh() {
                components.store.state.selectedTab?.let { tab ->
                    components.sessionUseCases.reload(tab.id)
                }
            }

            override fun onShare() {
                val tab = components.store.state.selectedTab ?: return
                WebUtils.shareUrl(this@WebActivity, tab.content.url, tab.content.title)
            }

            override fun onNewTab() {
                // 在当前 WebActivity 中创建并切换到新的空白标签页
                components.tabsUseCases.addTab(url = getDefaultSearchEngineHomePage(), selectTab = true)
                webMenuSheet?.dismissAllowingStateLoss()
            }

            override fun onOpenBookmarks() {
                BookmarkActivity.start(this@WebActivity)
            }

            override fun onOpenHistory() {
                BookmarkActivity.start(this@WebActivity, BookmarkActivity.TAB_HISTORY)
            }

            override fun onOpenDownloads() {
                DownloadActivity.start(this@WebActivity)
            }

            override fun onFindInPage() {
                findInPageIntegration.withFeature { it.launch() }
            }

            override fun onToggleDesktopSite(enabled: Boolean) {
                components.store.state.selectedTab?.let { tab ->
                    components.sessionUseCases.requestDesktopSite(enabled, tab.id)
                }
            }

            override fun onAddToHomeScreen() {
                val tab = components.store.state.selectedTab ?: return
                WebUtils.addToHomeScreen(this@WebActivity, tab)
            }

            override fun onAddBookmark() {
                val tab = components.store.state.selectedTab ?: return
                val url = tab.content.url
                if (url.isBlank()) {
                    ToastUtils.showShort(R.string.bookmark_add_failed)
                    return
                }
                
                val repository = BookmarkRepository.getInstance(applicationContext)
                val isBookmarked = repository.isBookmarked(url)
                
                if (isBookmarked) {
                    // Remove bookmark
                    val bookmarkNode = repository.findBookmarkByUrl(url)
                    if (bookmarkNode != null) {
                        val success = repository.deleteBookmark(bookmarkNode.id)
                        if (success) {
                            ToastUtils.showShort(R.string.bookmark_deleted)
                        } else {
                            ToastUtils.showShort(R.string.bookmark_delete_failed)
                        }
                    }
                } else {
                    // Add bookmark
                    webMenuSheet?.dismissAllowingStateLoss()
                    showAddBookmarkDialog(tab.content.title, url)
                }
            }
        }
    }

    private fun showAddBookmarkDialog(defaultTitle: String?, defaultUrl: String) {
        val intent = BookmarkEditActivity.createIntentForCreate(
            context = this,
            defaultTitle = defaultTitle,
            defaultUrl = defaultUrl,
            parentId = BookmarkRepository.ROOT_FOLDER_ID
        )
        startActivity(intent)
    }

    private fun calculateProgress(download: DownloadState): Int? {
        val total = download.contentLength
        if (total == null || total <= 0) {
            return null
        }
        val copied = download.currentBytesCopied
        val percent = ((copied.toDouble() / total.toDouble()) * 100).toInt()
        return percent.coerceIn(0, 100)
    }

    /**
     * 更新UI显示
     * 包括URL、标题、导航按钮状态等
     */
    private fun updateUI(tab: SessionState) {
        // 更新地址栏URL
        binding.tvUrl.text = tab.content.url

        // 更新导航按钮状态
        binding.ivBack.isEnabled = tab.content.canGoBack
        binding.ivBack.alpha = if (tab.content.canGoBack) 1.0f else 0.3f

        binding.ivForward.isEnabled = tab.content.canGoForward
        binding.ivForward.alpha = if (tab.content.canGoForward) 1.0f else 0.3f

        // 更新搜索引擎图标
        updateSearchEngineIcon()

        // 更新标签页计数
        updateTabCount()
    }

    /**
     * 更新顶部搜索区域的搜索引擎图标
     */
    private fun updateSearchEngineIcon() {
        val searchEngine = components.store.state.search.selectedOrDefaultSearchEngine
        searchEngine?.icon?.let { searchIcon ->
            binding.ivSiteIcon.setImageBitmap(searchIcon)
        } ?: run {
            binding.ivSiteIcon.setImageResource(R.mipmap.ic_logo)
        }
    }

    /**
     * 更新标签页计数显示
     */
    private fun updateTabCount() {
        val tabCount = components.store.state.tabs.size
        binding.tvTabCount.text = if (tabCount > 99) "99+" else tabCount.toString()
    }

    /**
     * 更新加载进度
     */
    private fun updateProgress(tab: SessionState) {
        val progress = tab.content.progress
        binding.progressBar.progress = progress

        if (progress >= 100) {
            binding.progressBar.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    /**
     * 更新加载状态
     * 页面开始/停止加载时调用
     */
    private fun updateLoadingState(tab: SessionState) {
        val isLoading = tab.content.loading

        // 更新刷新/停止按钮图标
        if (isLoading) {
            binding.ivRefresh.setImageResource(R.drawable.ic_browser_close)
            binding.progressBar.visibility = View.VISIBLE
        } else {
            binding.ivRefresh.setImageResource(R.drawable.ic_browser_refresh)
            binding.progressBar.visibility = View.GONE

            // 页面加载完成时，记录历史
            recordHistoryVisit(tab)
        }
    }

    /**
     * 记录页面访问历史
     * 在页面加载完成时调用
     */
    private fun recordHistoryVisit(tab: SessionState) {
        val url = tab.content.url
        val title = tab.content.title

        // 跳过空白页、about 页面等
        if (url.isBlank() || url.startsWith("about:") || url == lastUrl) {
            return
        }

        // 如果是无痕模式，不记录历史
        if (tab.content.private) {
            return
        }

        lastUrl = url

        // 异步保存历史记录
        lifecycleScope.launch {
            try {
                components.historyStorage.recordVisit(
                    uri = url,
                    visit = PageVisit(
                        visitType = VisitType.LINK
                    )
                )

                // 如果有标题，更新页面信息
                if (!title.isNullOrBlank()) {
                    components.historyStorage.recordObservation(
                        uri = url,
                        observation = mozilla.components.concept.storage.PageObservation(title = title)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 后退按钮
        binding.ivBack.setOnClickListener {
            currentTabId?.let { tabId ->
                components.sessionUseCases.goBack(tabId)
            }
        }

        // 前进按钮
        binding.ivForward.setOnClickListener {
            currentTabId?.let { tabId ->
                components.sessionUseCases.goForward(tabId)
            }
        }

        // 主页按钮（关闭当前界面，返回首页并切换到 Tabs 页面）
        binding.ivHome.setOnClickListener {
            loadInterstitial {
                navigateToMainWithTabs()
            }
        }

        // 刷新/停止按钮
        binding.ivRefresh.setOnClickListener {
            currentTabId?.let { tabId ->
                val tab = components.store.state.tabs.find { it.id == tabId }
                if (tab?.content?.loading == true) {
                    // 正在加载，执行停止
                    components.sessionUseCases.stopLoading(tabId)
                } else {
                    // 未加载，执行刷新
                    components.sessionUseCases.reload(tabId)
                }
            }
        }

        // 下载按钮
        binding.ivDownload.setOnClickListener {
            DownloadActivity.start(this@WebActivity)
        }

        // 菜单按钮
        binding.ivMenu.setOnClickListener {
            showWebMenu()
        }

        // 标签页计数按钮
        binding.tabCounterContainer.setOnClickListener {
            val intent = Intent(this, BrowserTabsActivity::class.java)
            startActivity(intent)
        }

        // 地址栏点击 - 跳转到搜索页面
        binding.tvUrl.setOnClickListener {
            openSearchActivity()
        }
    }

    /**
     * 打开搜索页面
     */
    private fun openSearchActivity() {
        val currentUrl = binding.tvUrl.text?.toString() ?: ""
        val intent = Intent(this, SearchActivity::class.java).apply {
            putExtra(SearchActivity.EXTRA_SEARCH_TEXT, currentUrl)
        }
        startActivityForResult(intent, REQUEST_CODE_SEARCH)
    }

    /**
     * 处理 Activity 结果
     * 包括搜索结果和文件选择结果
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // 先让 PromptFeature 处理文件选择结果
        promptsFeature.withFeature {
            it.onActivityResult(requestCode, data, resultCode)
        }
        
        if (requestCode == REQUEST_CODE_SEARCH && resultCode == RESULT_OK) {
            // SearchActivity 已经打开了 WebActivity 并加载了 URL
            // 这里不需要额外处理
        }
    }

    /**
     * 处理新的 Intent
     * 支持 FLAG_ACTIVITY_CLEAR_TOP 或 FLAG_ACTIVITY_SINGLE_TOP
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // 如果有新的 URL，加载它
        intent.getStringExtra(EXTRA_URL)?.let { url ->
            currentTabId?.let { tabId ->
                components.sessionUseCases.loadUrl(url, tabId)
            }
        }
    }

    /**
     * 处理返回键
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 先检查 FindInPage 是否需要处理返回键
        if (findInPageIntegration.onBackPressed()) {
            return
        }
        
        currentTabId?.let { tabId ->
            val tab = components.store.state.tabs.find { it.id == tabId }
            if (tab?.content?.canGoBack == true) {
                components.sessionUseCases.goBack(tabId)
                return
            }
        }
        // 无法后退时，返回首页并切换到 Tabs 页面
        navigateToMainWithTabs()
    }
    
    /**
     * 返回首页并切换到 Tabs 页面
     */
    private fun navigateToMainWithTabs() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SWITCH_TO_TABS, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        // 禁用切换动画，实现无缝切换
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }

    /**
     * 清理资源
     * 注意：不在这里删除标签页，由用户主动关闭或在标签页管理界面删除
     */
    override fun onDestroy() {
        dismissDownloadSheet()
        webMenuSheet = null

        // 取消挂起的通知权限请求 Runnable，防止 Activity 销毁后仍触发权限弹框
        notificationPermissionRunnable?.let { binding.root.removeCallbacks(it) }
        notificationPermissionRunnable = null

        super.onDestroy()

        // 取消Store流的订阅，避免协程泄漏
        storeScope?.cancel()
        storeScope = null
        downloadsScope?.cancel()
        downloadsScope = null
        searchEngineScope?.cancel()
        searchEngineScope = null

        // 注意：不删除标签页，因为用户可能只是切换到其他界面
        // 标签页应该由用户在标签页管理界面主动关闭
        // SessionFeature 会自动处理 EngineView 和 EngineSession 的生命周期
    }
}
