package com.android.common.bill.ui.gam

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.BillConfig
import com.android.common.bill.R
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.gam.GamFullScreenNativeAdController
import com.android.common.bill.ads.gam.GamManager
import com.android.common.bill.ads.log.AdLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * GAM 全屏原生广告展示页
 */
class GamFullScreenNativeAdActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GamFullScreenNativeAdActivity"
        private var pendingSessionId: String = ""

        suspend fun start(
            activity: Activity,
            sessionId: String = "",
            onAdDisplayedCallback: ((FragmentActivity) -> Unit)? = null
        ): AdResult<Unit> {
            return suspendCancellableCoroutine { continuation ->
                pendingSessionId = sessionId
                onAdDisplayed = onAdDisplayedCallback
                val intent = Intent(activity, GamFullScreenNativeAdActivity::class.java)
                activity.startActivity(intent)
                activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                this.continuation = continuation
            }
        }

        @Volatile
        private var continuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null

        @Volatile
        private var onAdDisplayed: ((FragmentActivity) -> Unit)? = null

        private fun consumeOnAdDisplayedCallback(): ((FragmentActivity) -> Unit)? {
            val callback = onAdDisplayed
            onAdDisplayed = null
            return callback
        }

        fun setResult(result: AdResult<Unit>) {
            continuation?.let { cont ->
                if (cont.isActive) {
                    cont.resume(result)
                }
            }
            continuation = null
            onAdDisplayed = null
        }
    }

    private val fullScreenNativeController = GamManager.Controllers.fullScreenNative

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.apply {
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            navigationBarColor = Color.TRANSPARENT
        }
        setContentView(R.layout.activity_gam_full_screen_native_ad)
        loadAndShowFullScreenNativeAd()
    }

    private fun loadAndShowFullScreenNativeAd() {
        lifecycleScope.launch {
            try {
                when (val result = fullScreenNativeController.showAdInContainer(
                    context = this@GamFullScreenNativeAdActivity,
                    container = findViewById<ViewGroup>(R.id.adContainer),
                    lifecycleOwner = this@GamFullScreenNativeAdActivity,
                    adUnitId = BillConfig.gam.fullNativeId,
                    sessionId = pendingSessionId,
                    onAdDisplayed = { notifyAdDisplayedIfNeeded() }
                )) {
                    is AdResult.Success -> {
                        findViewById<View>(R.id.rl_top_buttons).apply {
                            isVisible = true
                            findViewById<View>(R.id.btn_close).setOnClickListener {
                                GamFullScreenNativeAdController.getInstance().closeEvent(adUnitId = BillConfig.gam.fullNativeId)
                                closeAdAndFinish()
                            }
                        }
                        AdLogger.d("GAM 全屏原生广告页面加载成功")
                    }

                    is AdResult.Failure -> {
                        setResult(result)
                        closeAdAndFinish()
                    }
                }
            } catch (e: Exception) {
                AdLogger.e("GAM 全屏原生广告页面加载失败:${e.message}")
                setResult(
                    AdResult.Failure(
                        AdException(
                            code = -2,
                            message = "GAM 全屏原生广告加载异常: ${e.message}",
                            cause = e
                        )
                    )
                )
                closeAdAndFinish()
            }
        }
    }

    private fun notifyAdDisplayedIfNeeded() {
        val callback = consumeOnAdDisplayedCallback() ?: return
        try {
            callback.invoke(this)
        } catch (e: Exception) {
            AdLogger.e("$TAG onAdDisplayed 回调异常", e)
        }
    }

    private fun closeAdAndFinish() {
        if (continuation != null) {
            setResult(AdResult.Success(Unit))
        }
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (continuation != null) {
            setResult(
                AdResult.Failure(
                    AdException(
                        code = -3,
                        message = "Activity被销毁"
                    )
                )
            )
        }
    }

    override fun onBackPressed() {
        // 禁用返回键，只能通过关闭按钮退出
    }
}
