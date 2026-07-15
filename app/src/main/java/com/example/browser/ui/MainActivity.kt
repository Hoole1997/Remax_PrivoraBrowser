package com.example.browser.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.LogUtils
import com.browser.shortvideo.ui.ShortVideoViewModel
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.components
import com.example.browser.databinding.ActivityMainBinding
import androidx.activity.OnBackPressedCallback
import com.example.browser.ui.dialog.DefaultBrowserDialog
import com.example.browser.ui.dialog.ExitConfirmDialog
import com.example.browser.ui.dialog.ProcessScanDialog
import com.example.browser.ui.dialog.RatingDialog
import com.example.browser.ui.dialog.StoragePermissionDialog
import com.example.browser.utils.DefaultBrowserHelper
import com.example.browser.ui.junk.JunkScanActivity
import com.example.browser.ui.junk.ProcessCleanActivity
import com.example.browser.ui.news.NewsDetailsActivity
import com.example.browser.utils.SpUtils
import com.example.browser.ui.news.NewsMoreActivity
import com.example.browser.ui.photoclean.PhotoCleanActivity
import com.example.browser.ui.photoclean.PhotoScanDialogFragment
import com.example.browser.ui.photoclean.model.PhotoCleanMode
import com.example.browser.ui.scan.ScanResultActivity
import com.example.browser.ui.speed.SpeedTestActivity
import com.example.browser.ui.tabs.TabCountUi
import com.example.browser.ui.tabs.tabCountChanges
import com.example.browser.ui.web.WebActivity
import com.example.browser.utils.GoogleBarcodeScanner
import com.example.browser.view.NavigationItemView
import com.example.browser.view.NavigationTabsItemView
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import io.docview.push.config.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.majiajie.pagerbottomtabstrip.item.BaseTabItem
import mozilla.components.lib.state.ext.flowScoped
import net.corekit.core.report.ReportDataManager
import kotlin.coroutines.resume

class MainActivity : BaseActivity<ActivityMainBinding, MainModel>() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_SWITCH_TO_TABS = "extra_switch_to_tabs"
        const val EXTRA_AUTO_JUNK = "extra_auto_junk"
        const val EXTRA_AUTO_DUPLICATE = "extra_auto_duplicate"
        const val EXTRA_AUTO_SIMILAR = "extra_auto_similar"
    }

    private var navigationTabsItemView: NavigationTabsItemView? = null
    private val navigationItems = mutableListOf<NavigationItemView>()
    private val shortViewModel by viewModels<ShortVideoViewModel>()
    private var isRestoringActivityState = false

    /**
     * 默认浏览器系统弹框结果回调。
     * 注册在 Activity 上确保 Dialog/Fragment 都能拿到回调。
     */
    private val defaultBrowserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val granted = DefaultBrowserHelper.isDefaultBrowser(this)
        ReportDataManager.reportData(
            if (granted) "Set_Default_Browser_Success" else "Set_Default_Browser_Fail",
            mapOf()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // BaseActivity 会在 super.onCreate() 内调用 initView，因此必须提前记录恢复状态。
        // 低内存重建时系统可能先提供旧 base Intent，新的 singleTask Intent 随后才进入 onNewIntent。
        isRestoringActivityState = savedInstanceState != null
        super.onCreate(savedInstanceState)
    }

    override fun initBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): MainModel {
        return viewModels<MainModel>().value
    }

    override fun initView() {
        initNavigation()
        // 冷启动不改变三个 AUTO 参数的历史语义；恢复态只清理、不重放旧的一次性动作。
        handleLaunchIntent(
            sourceIntent = intent,
            includeOneShotActions = !isRestoringActivityState,
            includeAutomaticActions = false,
        )
        components.store.flowScoped(
            owner = this,
            dispatcher = kotlinx.coroutines.Dispatchers.Main.immediate,
        ) { flow ->
            flow.tabCountChanges()
                .collect { tabCount ->
                    updateNavigationTabCount(tabCount)
                }
        }
        requestNotificationPermission()
        // 首次启动显示引导弹框
        checkFirstLaunchDialogs()
        setupBackPressInterception()
    }

    private fun setupBackPressInterception() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmDialog()
            }
        })
    }

    private fun showExitConfirmDialog() {
        ExitConfirmDialog.show(
            fragmentManager = supportFragmentManager,
            onConfirmExit = {
                ReportDataManager.reportData("Exit_Retention_Confirm_Click",mapOf())
                moveTaskToBack(true)
            },
            onCancel = {
                ReportDataManager.reportData("Exit_Retention_Watch_News_Click",mapOf())
                NewsMoreActivity.start(this)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // 首次启动时不检查（正在执行引导弹框流程，避免弹框覆盖）
        if (SpUtils.isFirstLaunch(this)) return
        
        // 检查是否有待显示的弹框（从清理成功页面返回时设置的标记）
        if (SpUtils.hasPendingShortcutDialog(this)) {
            // 清除标记
            SpUtils.setPendingShortcutDialog(this, false)
            // 显示对应弹框
            showPendingDialogs()
        }
    }
    
    /**
     * 显示待处理的弹框（快捷方式或好评弹框）
     */
    private fun showPendingDialogs() {
        if (SpUtils.hasShownRatingDialog(this)) {
            // 已经显示过好评弹框，检查快捷方式
            checkAndAddShortcut()
        } else {
            // 还没显示过好评弹框，显示好评弹框
            checkAndShowRatingDialog()
        }
    }

    /**
     * 检查并添加桌面快捷方式
     */
    private fun checkAndAddShortcut() {
        if (!SpUtils.hasAddedShortcut(this)) {
            // 标记已添加（避免重复弹框，实际上是否添加成功取决于用户操作）
            SpUtils.setShortcutAdded(this)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val shortcutManager = getSystemService(android.content.pm.ShortcutManager::class.java)
                if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.action = Intent.ACTION_MAIN
                    intent.addCategory(Intent.CATEGORY_LAUNCHER)
                    
                    val shortcutInfo = android.content.pm.ShortcutInfo.Builder(this, "browser_shortcut")
                        .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_logo_round))
                        .setShortLabel(getString(R.string.app_name))
                        .setIntent(intent)
                        .build()
                        
                    shortcutManager.requestPinShortcut(shortcutInfo, null)
                }
            }
        }
    }

    /**
     * 检查并显示好评弹框
     */
    private fun checkAndShowRatingDialog() {
        // 未弹出过好评弹框 且 不是首次启动（避免冲突）
        if (!SpUtils.hasShownRatingDialog(this)) {
            // 标记已显示
            SpUtils.setRatingDialogShown(this)
            
            RatingDialog(
                context = this,
                onSubmitClick = { _ ->
                    // 点击提交后，调用谷歌好评弹框
                    launchInAppReview()
                }
            ).show()
        }
    }

    /**
     * 启动 Google Play In-App Review
     */
    private fun launchInAppReview() {
        val reviewManager = com.google.android.play.core.review.ReviewManagerFactory.create(this)
        val requestReviewFlow = reviewManager.requestReviewFlow()
        requestReviewFlow.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
                    // 评价流程完成
                    ReportDataManager.reportData("Review_Complete",mapOf())
                }
            }
        }
    }

    /**
     * 当 Activity 已经在运行时，接收到新的 Intent（例如从外部打开链接）
     * 这个方法会在 Activity 的 launchMode 为 singleTask 或 singleTop 时被调用
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 更新当前 Intent，确保 getIntent() 返回最新的 Intent
        setIntent(intent)
        handleLaunchIntent(
            sourceIntent = intent,
            includeOneShotActions = true,
            includeAutomaticActions = true,
        )
    }

    private fun launchPhotoClean(mode: PhotoCleanMode) {
        val showDialog = {
            val dialog = PhotoScanDialogFragment.newInstance(mode)
            dialog.setOnResultReadyListener { groups ->
                val ctx = this
                PhotoCleanActivity.start(ctx, mode, groups)
            }
            dialog.show(supportFragmentManager, "photo_scan_dialog")
        }
        if (hasStoragePermission()) {
            showDialog.invoke()
        } else {
            showStoragePermissionDialog(call = {
                showDialog.invoke()
            }, autoJump = {

            })
        }

    }

    /**
     * 统一处理创建和 singleTask 新投递的 Intent。
     *
     * 解析阶段会先消费所有应用内的一次性参数，再按原有顺序执行跳转，避免 startActivity
     * 引发的生命周期重入再次读取到旧动作。
     */
    private fun handleLaunchIntent(
        sourceIntent: Intent,
        includeOneShotActions: Boolean,
        includeAutomaticActions: Boolean,
    ) {
        val request = MainLaunchIntentConsumer.consume(
            sourceIntent = sourceIntent,
            includeOneShotActions = includeOneShotActions,
            includeAutomaticActions = includeAutomaticActions,
        )

        request.externalUrl?.let(::openExternalUrl)
        request.notification?.let(::navigateToNotificationDestination)

        if (request.openJunkScan) {
            JunkScanActivity.start(this)
        }
        if (request.openDuplicatePhotos) {
            launchPhotoClean(PhotoCleanMode.DUPLICATE)
        }
        if (request.openSimilarPhotos) {
            launchPhotoClean(PhotoCleanMode.SIMILAR)
        }
        if (request.switchToTabs) {
            binding.viewPager.setCurrentItem(MainFragmentPagerAdapter.POSITION_TABS, false)
        }
    }

    /** 处理从系统分发的 HTTP/HTTPS 链接（作为默认浏览器）。 */
    private fun openExternalUrl(url: String) {
        LogUtils.d(TAG, "handleExternalIntent: $url")
        val webIntent = Intent(this, WebActivity::class.java).apply {
            putExtra(WebActivity.EXTRA_URL, url)
            // 如果 WebActivity 已存在，复用现有实例并通过 onNewIntent 加载新地址。
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(webIntent)
    }

    private fun initNavigation() {
        navigationItems.clear()
        val homeItem = newNavigationItem(
            getString(R.string.tab_home),
            R.mipmap.main_navigation_home_select,
            R.mipmap.main_navigation_home_default
        )
        val shortItem = newNavigationItem(
            getString(R.string.tab_short),
            R.drawable.ic_navigation_short_select,
            R.drawable.ic_navigation_short_default
        )
        val filesItem = newNavigationItem(
            getString(R.string.tab_files),
            R.mipmap.main_navigation_folder_select,
            R.mipmap.main_navigation_folder_default
        )
        val settingsItem = newNavigationItem(
            getString(R.string.tab_settings),
            R.mipmap.main_navigation_setting_select,
            R.mipmap.main_navigation_setting_default
        )
        navigationItems.add(homeItem)
        navigationItems.add(shortItem)
        navigationItems.add(filesItem)
        navigationItems.add(settingsItem)
        
        val navigationController = binding.tab.custom()
            .addItem(homeItem)
            .addItem(shortItem)
            .addItem(filesItem)
            .addItem(newTabsItem().apply {
                navigationTabsItemView = this as NavigationTabsItemView
            })
            .addItem(settingsItem)
            .build()
        binding.viewPager.apply {
            adapter = MainFragmentPagerAdapter(this@MainActivity, supportFragmentManager)
            offscreenPageLimit = 5
            navigationController.setupWithViewPager(this)
            
            // 监听页面切换，根据当前页面设置状态栏样式
            addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
                override fun onPageScrollStateChanged(state: Int) {}
                override fun onPageSelected(position: Int) {
                    updateStatusBarForPage(position)
                }
            })
        }

        // 初始化时同步一次宿主可见状态，覆盖 Activity 恢复到短视频 Tab 的场景。
        shortViewModel.setHostSelected(
            binding.viewPager.currentItem == MainFragmentPagerAdapter.POSITION_SHORT,
        )

        // Flow 会在 STARTED 后开始推送；先同步当前值，避免首帧显示布局中的默认数字。
        updateNavigationTabCount(components.store.state.tabs.size)
    }

    private fun newNavigationItem(
        title: String,
        checkedDrawable: Int,
        defaultDrawable: Int
    ): NavigationItemView {
        return NavigationItemView(this).apply {
            initialize(
                title = title,
                checkedDrawable = ContextCompat.getDrawable(this@MainActivity, checkedDrawable),
                defaultDrawable = ContextCompat.getDrawable(this@MainActivity, defaultDrawable),
                checkedTextColor = ContextCompat.getColor(this@MainActivity, R.color.main_nav_check_color),
                defaultTextColor = ContextCompat.getColor(this@MainActivity, R.color.main_nav_default_color)
            )
        }
    }

    private fun newTabsItem(): BaseTabItem {
        return NavigationTabsItemView(this).apply {
            initialize(
                title = getString(R.string.tab_tabs),
                checkedDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_navigation_tabs_select),
                defaultDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_navigation_tabs_default),
                checkedTextColor = ContextCompat.getColor(this@MainActivity, R.color.main_nav_check_color),
                defaultTextColor = ContextCompat.getColor(this@MainActivity, R.color.main_nav_default_color)
            )
        }
    }

    private fun updateNavigationTabCount(tabCount: Int) {
        navigationTabsItemView?.setTabCounts(TabCountUi.format(tabCount))
    }

    override fun initEdgeToEdge() {
        enableEdgeToEdge()
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            
            // 设置导航栏背景 View 的高度为系统导航栏高度
            val navBarHeight = systemBars.bottom
            binding.navBarBackground.layoutParams.height = navBarHeight
            binding.navBarBackground.requestLayout()
            
            insets
        }
    }

    /** 使用已快照的通知请求跳转，不再依赖 Activity 可能已被替换的当前 Intent。 */
    private fun navigateToNotificationDestination(request: NotificationLaunchRequest) {
        LogUtils.d("通知类型: ${request.actionType}")
        when (request.actionType) {
            Content.ICON_TYPE_JUNK -> {
                JunkScanActivity.start(this)
            }
            Content.ICON_TYPE_PROCESS -> {
                ProcessCleanActivity.start(this)
            }
            Content.ICON_TYPE_NEWS -> {
                NewsMoreActivity.start(this)
            }
            Content.ICON_TYPE_SCAN -> {
                GoogleBarcodeScanner().scanBarcode { rawValue ->
                    // 跳转到扫描结果页面
                    ScanResultActivity.start(this, rawValue)
                }
            }
            Content.ICON_TYPE_REAL_NEWS -> {
                request.newsUrl?.let {
                    NewsDetailsActivity.start(this, it, isMoreNews = true)
                }
            }
            Content.ICON_TYPE_SPEED -> {
                SpeedTestActivity.start(this)
            }

            Content.ICON_TYPE_PHOTO_SIMILAR -> {
                launchPhotoClean(PhotoCleanMode.SIMILAR)
            }
            Content.ICON_TYPE_PHOTO_DUPLICATE -> {
                launchPhotoClean(PhotoCleanMode.DUPLICATE)
            }
            else -> {

            }
        }
    }

    private fun requestNotificationPermission() {
        if (XXPermissions.isGrantedPermissions(this,arrayOf(PermissionLists.getPostNotificationsPermission()))){
            return
        }
        ReportDataManager.reportData(
            "Notific_Allow_Start",
            mapOf("Notific_Allow_Position" to "AppMain")
        )
        XXPermissions.with(this)
            .permission(PermissionLists.getPostNotificationsPermission())
            .request { granted, _ ->
                val isGranted = granted.isNotEmpty()
                ReportDataManager.reportData(
                    "Notific_Allow_Result", mapOf(
                        "Notific_Allow_Position" to "AppMain",
                        "Result" to if (isGranted) "allow" else if (XXPermissions.isDoNotAskAgainPermissions(
                                this,
                                arrayOf(PermissionLists.getPostNotificationsPermission())
                            )
                        ) "deined_forever" else "denied"
                    )
                )
                if (isGranted) {
                    Log.d(TAG, "通知权限申请成功")
                } else {
                    Log.d(TAG, "通知权限申请失败")
                }
            }
    }

    /**
     * 检查首次启动弹框
     */
    private fun checkFirstLaunchDialogs() {
        if (SpUtils.isFirstLaunch(this)) {
            // 显示默认浏览器引导弹框
            showDefaultBrowserDialog()
        } else {
            showPendingDialogs()
        }
    }

    /**
     * 显示默认浏览器引导弹框
     */
    private fun showDefaultBrowserDialog() {
        DefaultBrowserDialog(
            context = this,
            defaultBrowserLauncher = defaultBrowserLauncher,
            onLaterClick = {
                ReportDataManager.reportData("Set_Default_Browser_Later_Click",mapOf())
                // 点击 Later，显示扫描引导弹框
                showProcessScanDialog()
            },
            onSetDefaultClick = {
                ReportDataManager.reportData("Set_Default_Browser_Agree_Click",mapOf())
                // 点击 Set as default，系统设置完成后显示扫描引导弹框
                showProcessScanDialog()
            },
            onDialogShow = {

            },
            onDialogDismiss = {
                SpUtils.setFirstLaunchCompleted(this)
            }
        ).show()
    }

    /**
     * 显示进程扫描引导弹框
     */
    private fun showProcessScanDialog() {
        ProcessScanDialog(
            context = this,
            lifecycleScope = lifecycleScope,
            onScanNowClick = {
                ReportDataManager.reportData("ProcessManage_ScanNow_Click",mapOf())
                // 跳转到进程清理页面
                ProcessCleanActivity.start(this)
            }
        ).show()
    }

    /**
     * 根据当前页面更新状态栏和导航栏样式
     * Short Video 页面使用深色背景，需要浅色状态栏图标和黑色底部导航栏
     */
    private fun updateStatusBarForPage(position: Int) {
        if (position == 1) {
            ReportDataManager.reportData("Short_Click",mapOf())
        }
        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        val isShortVideoTab = (position == MainFragmentPagerAdapter.POSITION_SHORT)

        // 同步状态先于 Fragment 生命周期事务生效，低端机切页时也能立即关闭播放器门禁。
        shortViewModel.setHostSelected(isShortVideoTab)
        
        // Short Video Tab (position = 1) 使用深色背景，需要浅色图标
        insetsController.isAppearanceLightStatusBars = !isShortVideoTab
        insetsController.isAppearanceLightNavigationBars = !isShortVideoTab
        
        // 更新底部导航栏背景色和文字颜色
        if (isShortVideoTab) {
            binding.tab.setBackgroundColor(android.graphics.Color.BLACK)
            // 设置系统导航栏背景 View 颜色为黑色
            binding.navBarBackground.setBackgroundColor(android.graphics.Color.BLACK)
            // 更新导航项默认文字颜色为白色
            val whiteColor = android.graphics.Color.parseColor("#999999")
            navigationItems.forEach { it.updateDefaultTextColor(whiteColor) }
            navigationTabsItemView?.updateDefaultTextColor(whiteColor)
        } else {
            binding.tab.setBackgroundColor(android.graphics.Color.WHITE)
            // 设置系统导航栏背景 View 颜色为白色
            binding.navBarBackground.setBackgroundColor(android.graphics.Color.WHITE)
            // 恢复导航项默认文字颜色
            val defaultColor = ContextCompat.getColor(this, R.color.main_nav_default_color)
            navigationItems.forEach { it.updateDefaultTextColor(defaultColor) }
            navigationTabsItemView?.updateDefaultTextColor(defaultColor)

        }
    }

    private fun hasStoragePermission(): Boolean {
        return XXPermissions.isGrantedPermissions(this, arrayOf(PermissionLists.getManageExternalStoragePermission()))
    }

    /**
     * 显示存储权限引导弹框
     */
    private fun showStoragePermissionDialog(call:() -> Unit,autoJump:() -> Unit) {
        StoragePermissionDialog(
            context = this,
            onGoNowClick = {
                XXPermissions.with(this)
                    .permission(PermissionLists.getManageExternalStoragePermission())
                    .request { _, deniedList ->
                        if (deniedList.isEmpty()) {
                            call.invoke()
                        }
                    }
                lifecycleScope.launch(Dispatchers.IO) {
                    // 启动一个 5秒的循环，检查权限是否申请成功
                    for (i in 0 until 25) {
                        delay(200)
                        if (XXPermissions.isGrantedPermissions(this@MainActivity, arrayOf(PermissionLists.getManageExternalStoragePermission()))) {
                            autoJump.invoke()
                            return@launch
                        }
                    }
                }
            }
        ).show()
    }

}
