package com.example.browser.ui.splash

import android.animation.ValueAnimator
import android.content.Intent
import android.util.Log
import android.view.animation.LinearInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.PreloadController
import com.android.common.bill.ads.bidding.AppOpenBiddingInitializer
import com.android.common.bill.ads.ext.AdShowExt
import com.android.common.bill.ads.ext.CountdownConfig
import com.android.common.bill.ads.util.GoogleMobileAdsConsentManager
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.LogUtils
import com.example.browser.BrowserApplication
import com.example.browser.R
import com.example.browser.ad.AdConfig
import com.example.browser.ad.BrowserAdLoadingDialogRenderer
import com.example.browser.ad.BrowserAdmobFullScreenNativeAdRenderer
import com.example.browser.ad.BrowserAdmobNativeAdRenderer
import com.example.browser.ad.BrowserPangleFullScreenNativeAdRenderer
import com.example.browser.ad.BrowserPangleNativeAdRenderer
import com.example.browser.ad.BrowserToponFullScreenNativeAdRenderer
import com.example.browser.ad.BrowserToponNativeAdRenderer
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivitySplashBinding
import com.example.browser.ui.MainActivity
import com.example.browser.ui.MainModel
import com.example.browser.ui.junk.JunkScanActivity
import com.example.browser.ui.junk.ProcessCleanActivity
import com.example.browser.ui.news.NewsDetailsActivity
import com.example.browser.ui.news.NewsMoreActivity
import com.example.browser.ui.scan.ScanResultActivity
import com.example.browser.ui.speed.SpeedTestActivity
import com.example.browser.ui.uninstall.UninstallActivity
import com.example.browser.utils.GoogleBarcodeScanner

import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import io.docview.push.builder.LANDING_NOTIFICATION_ACTION
import io.docview.push.builder.LANDING_NOTIFICATION_CONTENT
import io.docview.push.builder.LANDING_NOTIFICATION_FROM
import io.docview.push.builder.LANDING_NOTIFICATION_TITLE
import io.docview.push.check.CheckCtrl
import io.docview.push.config.Content
import io.docview.push.news.NewsNotificationBuilder.EXTRA_NEWS_URL
import kotlin.coroutines.resume
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import net.corekit.core.report.ReportDataManager
import net.corekit.core.utils.ConfigRemoteManager
import kotlin.math.ceil

class SplashActivity : BaseActivity<ActivitySplashBinding, MainModel>() {

    companion object {

        private const val TAG = "SplashActivity"
        const val EXTRA_IS_FROM_BACKGROUND = "is_from_background"
    }

    private var loadingAnimator: ValueAnimator? = null
    private var shouldNavigateOnCountdownEnd: Boolean = true
    private var hasNavigated: Boolean = false

    // 广告是否已加载
    var isAdLoaded = false
        private set

    private var launchTime = 0L
    private val notificationType by lazy {
        intent.getIntExtra(LANDING_NOTIFICATION_ACTION, 0)
    }

    private val isFromBackground: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_IS_FROM_BACKGROUND, false)
    }

    private val shortcutAction: String? by lazy {
        intent.getStringExtra("shortcut_action")
    }

    /**
     * 检查是否有全屏原生广告正在显示
     */
    private val hasFullNativeShowing: Boolean by lazy {
        AdShowExt.isAnyInterstitialOrFullScreenNativeShowing()
    }

    override fun initBinding(): ActivitySplashBinding {
        return ActivitySplashBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): MainModel {
        return viewModels<MainModel>().value
    }

    override fun initView() {
        launchTime = System.currentTimeMillis()
        ReportDataManager.reportData("loading_pagge_show", mapOf())
        dataReport()
        Log.d(TAG, "启动页：开始启动倒计时与广告流程")
        startSplashFlow()
        lifecycleScope.launch() {
            ConfigRemoteManager.getString("Grouping", "")
                ?.takeIf { it.isNotEmpty() }?.let {
                Log.d(TAG, "启动页：获取分组参数成功，准备上报 Grouping_${it}")
                ReportDataManager.reportData(eventName = "Grouping_${it}", data = mapOf())
            }
        }
    }

    private fun startSplashFlow() {
        lifecycleScope.launch {
            runCatching {
                // ump
                GoogleMobileAdsConsentManager.getInstance(this@SplashActivity)
                    .gatherConsent(this@SplashActivity)
                    .let {
                        if (!it) {
                            finish()
                            return@launch
                        }
                    }

                //请求通知权限，挂起等待用户操作完成（不阻塞倒计时动画）
                requestNotificationPermission()

                val countdownMillis = getSplashAdTimeSeconds()

                Log.d(TAG, "启动页：使用默认倒计时启动动画: ${countdownMillis}ms")
                startLoading(countdownMillis)

                awaitAdWithTimeout()

                navigateNext()
            }.onFailure {
                Log.e(TAG, "启动页加载失败", it)
                navigateNext()
            }
        }
    }

    private suspend fun awaitAdWithTimeout() {
        val adJob = lifecycleScope.async {
            initializeAndShowAd(onTick = {

            })
        }

        val timeoutJob = lifecycleScope.async {
            delay(getSplashAdTimeSeconds())
        }

        val isTimeout = select<Boolean> {
            adJob.onAwait { false }
            timeoutJob.onAwait { true }
        }

        if (isTimeout) {
            // 超时但有广告在显示，继续等待
            if (isAdLoaded || hasFullNativeShowing) {
                Log.d(TAG, "超时但有广告，继续等待")
                runCatching { adJob.await() }
            } else {
                Log.d(TAG, "超时且无广告，继续流程")
            }
        }
    }

    private suspend fun initializeAndShowAd(onTick: ((Int) -> Unit)): Boolean{
        return try {
            val initResult = AppOpenBiddingInitializer.initialize(this,R.mipmap.ic_logo) {
                // ===== 广告ID和布局 =====
                admob = AdConfig.admobConfig()
                pangle = AdConfig.pangleConfig()
                topon = AdConfig.toponConfig()

                // ===== 渲染器 =====
                admobNativeRenderer = BrowserAdmobNativeAdRenderer()
                admobFullScreenNativeRenderer = BrowserAdmobFullScreenNativeAdRenderer()
                pangleNativeRenderer = BrowserPangleNativeAdRenderer()
                pangleFullScreenNativeRenderer = BrowserPangleFullScreenNativeAdRenderer()
                toponNativeRenderer = BrowserToponNativeAdRenderer()
                toponFullScreenNativeRenderer = BrowserToponFullScreenNativeAdRenderer()
                // ===== Loading 弹框渲染器 =====
                adLoadingDialogRenderer = BrowserAdLoadingDialogRenderer()
            }

            if (initResult is AdResult.Success) {
                Log.d(TAG, "AdMob SDK 初始化成功")
                showAdWithBidding(onTick)
            } else {
                Log.e(TAG, "AdMob SDK 初始化失败: ${(initResult as? AdResult.Failure)?.error?.message}")
                false
            }

        } catch ( e: Exception) {
            Log.e(TAG, "广告初始化或显示异常", e)
            false
        }

    }

    private suspend fun showAdWithBidding(onTick: ((Int) -> Unit)): Boolean {
        Log.d(TAG, "准备显示开屏广告")
        val adResult = AdShowExt.showAppOpenAd(
            activity = this,
            onLoaded = { isSuccess ->
                PreloadController.preloadAll(this)
                isAdLoaded = isSuccess
//                onAdLoaded?.invoke(isSuccess)
            },
            countdown = CountdownConfig(
                seconds = 1,
                onTick = { remaining -> onTick(remaining) }
            ))

        return if (adResult is AdResult.Success) {
            Log.d(TAG, "广告显示成功")
            true
        } else {
            Log.d(TAG, "广告显示失败: ${(adResult as? AdResult.Failure)?.error?.message}")
            false
        }
    }

    private fun getSplashAdTimeSeconds(): Long {
        return 15000L
    }

    private fun startLoading(durationMillis: Long) {
        loadingAnimator?.cancel()
        shouldNavigateOnCountdownEnd = true

        Log.d(TAG, "启动页：开始倒计时进度动画，duration=${durationMillis}ms")

        val animator = ValueAnimator.ofInt(0, 100)
        loadingAnimator = animator

        animator.duration = durationMillis
        animator.interpolator = LinearInterpolator()

        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            binding.progressBar.progress = progress
            binding.tvProgress.text = "$progress%"
        }

        animator.start()
    }

    private fun navigateNext() {
        if (hasNavigated) return
        hasNavigated = true
        Log.d(TAG, "启动页：执行跳转逻辑")
        checkNavigation()
    }

    override fun initEdgeToEdge() {
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        super.onDestroy()
    }

    private fun checkNavigation() {
        Log.d(TAG, "启动页：非首次启动，进入主页")
        // 检查是否来自桌面快捷方式
        // Go to MainActivity
        if (intent.hasExtra(LANDING_NOTIFICATION_ACTION)) {
            //来自通知
            handleNotificationIntent()
        } else {
            if (!isFromBackground) {
                // 不是从通知启动，直接进入主页
                if (shortcutAction == "uninstall") {
                    Log.d(TAG, "启动页：来自卸载快捷方式，跳转卸载页面")
                    UninstallActivity.start(this, fromShortcut = true)
                } else {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
            }
        }
        BrowserApplication.isHotLaunch = true
        reportExitEvent()
        Log.d(TAG, "启动页：finish() 结束当前页面")
        finish()
    }

    private fun handleNotificationIntent() {
        if (!ActivityUtils.isActivityExistsInStack(MainActivity::class.java)) {
            // 主页不存在，启动主页，并传递通知类型
            LogUtils.d("主页不存在，启动主页，并传递通知类型")
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.putExtras(this@SplashActivity.intent)
            startActivity(mainIntent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            return
        }
        LogUtils.d("通知类型: ${notificationType}")
        when (notificationType) {
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
                intent.getStringExtra(EXTRA_NEWS_URL)?.let {
                    NewsDetailsActivity.start(this, it, isMoreNews = true)
                }
            }

            Content.ICON_TYPE_SPEED -> {
                SpeedTestActivity.start(this)
            }
            else -> {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtras(this@SplashActivity.intent)
                startActivity(intent)
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }

    private suspend fun requestNotificationPermission(): Boolean =
        suspendCancellableCoroutine { continuation ->
            if (XXPermissions.isGrantedPermissions(
                    this,
                    arrayOf(PermissionLists.getPostNotificationsPermission())
                )
            ) {
                continuation.resume(true)
                return@suspendCancellableCoroutine
            }
            ReportDataManager.reportData(
                "Notific_Allow_Start",
                mapOf("Notific_Allow_Position" to "Appstart")
            )
            XXPermissions.with(this)
                .permission(PermissionLists.getPostNotificationsPermission())
                .request { granted, _ ->
                    val isGranted = granted.isNotEmpty()
                    ReportDataManager.reportData(
                        "Notific_Allow_Result", mapOf(
                            "Notific_Allow_Position" to "Appstart",
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
                    if (continuation.isActive) {
                        continuation.resume(isGranted)
                    }
                }
        }

    private fun dataReport() {
        ReportDataManager.reportData(
            "app_open", mapOf(
                "type" to if (ActivityUtils.isActivityExistsInStack(MainActivity::class.java)) "hot_open" else "cold_open",
                "position" to if (intent.hasExtra(LANDING_NOTIFICATION_FROM)) intent.getStringExtra(
                    LANDING_NOTIFICATION_FROM
                ).orEmpty().ifBlank { "other" } else "other"
            ))
        if (intent.hasExtra(LANDING_NOTIFICATION_FROM)) {
            ReportDataManager.reportData(
                "Notific_Click", mapOf(
                    "Notific_Type" to when (intent.getStringExtra(LANDING_NOTIFICATION_FROM)
                        .orEmpty()) {
                        CheckCtrl.NotificationType.UNLOCK.string -> 1
                        CheckCtrl.NotificationType.BACKGROUND.string -> 1
                        CheckCtrl.NotificationType.KEEPALIVE.string -> 1
                        CheckCtrl.NotificationType.FCM.string -> 3
                        CheckCtrl.NotificationType.RESIDENT.string -> 4
                        CheckCtrl.NotificationType.EARTHQUAKE.string -> 5
                        CheckCtrl.NotificationType.NEWS.string -> 6
                        else -> 4
                    },
                    "Notific_Position" to when (intent.getStringExtra(LANDING_NOTIFICATION_FROM)
                        .orEmpty()) {
                        CheckCtrl.NotificationType.RESIDENT.string -> 2
                        else -> 1
                    },
                    "Notific_Priority" to when (intent.getStringExtra(LANDING_NOTIFICATION_FROM)
                        .orEmpty()) {
                        CheckCtrl.NotificationType.RESIDENT.string -> "PRIORITY_DEFAULT"
                        else -> "PRIORITY_MAX"
                    },
                    "event_id" to when (intent.getStringExtra(LANDING_NOTIFICATION_FROM)
                        .orEmpty()) {
                        CheckCtrl.NotificationType.RESIDENT.string -> "permanent"
                        else -> "customer_general_style"
                    },
                    "title" to intent.getStringExtra(LANDING_NOTIFICATION_TITLE).orEmpty(),
                    "text" to intent.getStringExtra(LANDING_NOTIFICATION_CONTENT).orEmpty(),
                    "from_background" to !ActivityUtils.isActivityExistsInStack(
                        MainActivity::class.java
                    )
                )
            )
            ReportDataManager.reportData(
                "Notific_Enter", mapOf(
                    "Notific_Type" to when (intent.getStringExtra(LANDING_NOTIFICATION_FROM)
                        .orEmpty()) {
                        CheckCtrl.NotificationType.UNLOCK.string -> 1
                        CheckCtrl.NotificationType.BACKGROUND.string -> 1
                        CheckCtrl.NotificationType.KEEPALIVE.string -> 1
                        CheckCtrl.NotificationType.FCM.string -> 3
                        CheckCtrl.NotificationType.RESIDENT.string -> 4
                        CheckCtrl.NotificationType.EARTHQUAKE.string -> 5
                        CheckCtrl.NotificationType.NEWS.string -> 6
                        else -> 4
                    },
                    "Notific_Position" to when (intent.getStringExtra(LANDING_NOTIFICATION_FROM)
                        .orEmpty()) {
                        CheckCtrl.NotificationType.RESIDENT.string -> 2
                        else -> 1
                    },
                    "Notific_Priority" to when (intent.getStringExtra(LANDING_NOTIFICATION_FROM)
                        .orEmpty()) {
                        CheckCtrl.NotificationType.RESIDENT.string -> "PRIORITY_DEFAULT"
                        else -> "PRIORITY_MAX"
                    },
                    "event_id" to when (intent.getStringExtra(LANDING_NOTIFICATION_FROM)
                        .orEmpty()) {
                        CheckCtrl.NotificationType.RESIDENT.string -> "permanent"
                        else -> "customer_general_style"
                    },
                    "title" to intent.getStringExtra(LANDING_NOTIFICATION_TITLE).orEmpty(),
                    "text" to intent.getStringExtra(LANDING_NOTIFICATION_CONTENT).orEmpty(),
                )
            )
        }
    }

    private fun reportExitEvent() {
        ReportDataManager.reportData(
            "loading_page_end", mapOf(
                "pass_time" to ceil((System.currentTimeMillis() - launchTime) / 1000.0).toInt()
            )
        )
    }

    override fun onBackPressed() {

    }

}
