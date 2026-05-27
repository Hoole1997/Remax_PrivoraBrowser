package com.example.browser.ui.speed

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.core.view.WindowCompat
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import net.corekit.core.report.ReportDataManager

class SpeedTestActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SpeedTestActivity::class.java)
            context.startActivity(intent)
        }
    }

    private val viewModel: SpeedTestModel by viewModels()
    private var adContainer: FrameLayout? = null
    private var adLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Create ad container for native ad
        adContainer = FrameLayout(this)

        viewModel.loadIspInfo()

        setContent {
            val state by viewModel.state.observeAsState(SpeedTestState.Idle)
            val ispInfo by viewModel.ispInfo.observeAsState()

            // Load ad when completed
            if (state is SpeedTestState.Completed && !adLoaded) {
                adLoaded = true
//                loadNative(adContainer!!)
                ReportDataManager.reportData("speed_test_success", mapOf())
                ReportDataManager.reportData("ad_show_result", mapOf())
            }

            SpeedTestScreen(
                state = state,
                ispInfo = ispInfo,
                onStartClick = {
                    if (state is SpeedTestState.Idle || state is SpeedTestState.Completed) {
                        adLoaded = false
                        ReportDataManager.reportData("speed_test_start", mapOf())
                        viewModel.resetToIdle()
                        viewModel.startTest()
                    }
                },
                onBackClick = { backFinish() },
                adContainer = if (state is SpeedTestState.Completed) adContainer else null,
            )
        }
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        backFinish()
    }

    private fun backFinish() {
        loadInterstitial(position = "IV_Speed_Back") {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.resetToIdle()
    }
}
