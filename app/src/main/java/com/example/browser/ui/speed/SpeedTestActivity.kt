package com.example.browser.ui.speed

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.MenuItem
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.activity.viewModels
import com.android.common.bill.ads.PreloadController
import com.blankj.utilcode.util.ClickUtils
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivitySpeedTestBinding
import net.corekit.core.report.ReportDataManager
import java.util.Locale

class SpeedTestActivity : BaseActivity<ActivitySpeedTestBinding, SpeedTestModel>() {

    companion object {
        private const val TAG = "SpeedTestActivity"

        fun start(context: Context) {
            val intent = Intent(context, SpeedTestActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun initBinding(): ActivitySpeedTestBinding {
        return ActivitySpeedTestBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): SpeedTestModel {
        return viewModels<SpeedTestModel>().value
    }

    override fun initView() {
        setupToolbar()
        setupClickListeners()
        observeState()
        observeIspInfo()

        viewModel.loadIspInfo()
    }

    private var outerRingAnimator: ObjectAnimator? = null

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.navigationIcon?.setTint(Color.WHITE)
    }

    private fun startOuterRingAnimation() {
        val idleView = binding.viewFlipper.getChildAt(0)
        val ivOuter = idleView.findViewById<ImageView>(R.id.ivStartOuter)
        outerRingAnimator?.cancel()
        outerRingAnimator = ObjectAnimator.ofFloat(ivOuter, "rotation", 0f, 360f).apply {
            duration = 8000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopOuterRingAnimation() {
        outerRingAnimator?.cancel()
        outerRingAnimator = null
    }

    private fun setupClickListeners() {
        // Start button (Idle state)
        ClickUtils.applyGlobalDebouncing(binding.viewFlipper.getChildAt(0)) {
            ReportDataManager.reportData("speed_test_start",mapOf())
            viewModel.startTest()
        }

        // Test Again button (Completed state)
        val completedView = binding.viewFlipper.getChildAt(3)
        val btnTestAgain = completedView.findViewById<View>(
            com.example.browser.R.id.btnTestAgain
        )
        ClickUtils.applyGlobalDebouncing(btnTestAgain) {
            ReportDataManager.reportData("test_again_click",mapOf())
            viewModel.resetToIdle()
            viewModel.startTest()
        }
    }

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is SpeedTestState.Idle -> showIdleState()
                is SpeedTestState.Connecting -> showConnectingState()
                is SpeedTestState.Testing -> showTestingState(state)
                is SpeedTestState.Completed -> showCompletedState(state)
            }
        }
    }

    private fun observeIspInfo() {
        viewModel.ispInfo.observe(this) { info ->
            binding.tvIspName.text = info.name.ifEmpty { getString(R.string.speed_isp_unknown) }
            binding.tvIspIp.text = info.ip.ifEmpty { "—" }
        }
    }

    private fun showIdleState() {
        binding.viewFlipper.displayedChild = 0
        binding.rootLayout.setBackgroundColor(Color.parseColor("#3845FF"))
        binding.ispContainer.visibility = View.VISIBLE
        binding.tvToolbarTitle.visibility = View.VISIBLE
        startOuterRingAnimation()
    }

    private fun showConnectingState() {
        binding.viewFlipper.displayedChild = 1
        binding.rootLayout.setBackgroundColor(Color.parseColor("#0881FE"))
        binding.ispContainer.visibility = View.VISIBLE
        binding.tvToolbarTitle.visibility = View.VISIBLE
        stopOuterRingAnimation()
        // Animate connecting outer ring
        val connectView = binding.viewFlipper.getChildAt(1)
        val ivOuter = connectView.findViewById<ImageView>(R.id.ivConnectOuter)
        ObjectAnimator.ofFloat(ivOuter, "rotation", 0f, 360f).apply {
            duration = 4000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun showTestingState(state: SpeedTestState.Testing) {
        binding.viewFlipper.displayedChild = 2
        binding.rootLayout.setBackgroundColor(Color.parseColor("#0881FE"))
        binding.ispContainer.visibility = View.VISIBLE
        binding.tvToolbarTitle.visibility = View.VISIBLE

        // Update gauge
        val testingView = binding.viewFlipper.getChildAt(2)
        val gauge = testingView.findViewById<SpeedGaugeView>(
            com.example.browser.R.id.speedGauge
        )
        gauge.setSpeed(state.currentSpeed)

        // Update speed text
        val tvCurrentSpeed = testingView.findViewById<android.widget.TextView>(
            com.example.browser.R.id.tvCurrentSpeed
        )
        tvCurrentSpeed.text = formatSpeed(state.currentSpeed)

        // Update metrics
        updateMetrics(
            testingView,
            state.ping, state.download, state.jitter, state.upload
        )

        // Update progress bar width based on track width
        val progressTrack = testingView.findViewById<View>(R.id.progressTrack)
        val progressBar = testingView.findViewById<View>(R.id.progressBar)
        progressTrack.post {
            val trackWidth = progressTrack.width
            if (trackWidth > 0) {
                val params = progressBar.layoutParams
                params.width = (trackWidth * state.progress).toInt().coerceAtLeast(1)
                progressBar.layoutParams = params
            }
        }
    }

    private fun showCompletedState(state: SpeedTestState.Completed) {
        binding.viewFlipper.displayedChild = 3
        binding.rootLayout.setBackgroundColor(Color.parseColor("#0881FE"))
        binding.ispContainer.visibility = View.GONE
        binding.tvToolbarTitle.visibility = View.GONE

        val completedView = binding.viewFlipper.getChildAt(3)
        updateMetrics(
            completedView,
            state.ping, state.download, state.jitter, state.upload
        )
        loadNative(binding.adContainer)
        ReportDataManager.reportData("speed_test_success",mapOf())
        ReportDataManager.reportData("ad_show_result",mapOf())
    }

    private fun updateMetrics(view: View, ping: Int, download: Float, jitter: Int, upload: Float) {
        view.findViewById<android.widget.TextView>(
            com.example.browser.R.id.tvPingValue
        )?.text = if (ping > 0) ping.toString() else "——"

        view.findViewById<android.widget.TextView>(
            com.example.browser.R.id.tvDownloadValue
        )?.text = if (download > 0) formatSpeed(download) else "——"

        view.findViewById<android.widget.TextView>(
            com.example.browser.R.id.tvJitterValue
        )?.text = if (jitter > 0) jitter.toString() else "——"

        view.findViewById<android.widget.TextView>(
            com.example.browser.R.id.tvUploadValue
        )?.text = if (upload > 0) formatSpeed(upload) else "——"
    }

    private fun formatSpeed(speed: Float): String {
        return if (speed >= 100) {
            String.format(Locale.US, "%.0f", speed)
        } else if (speed >= 10) {
            String.format(Locale.US, "%.1f", speed)
        } else {
            String.format(Locale.US, "%.2f", speed)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            backFinish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        backFinish()
    }

    private fun backFinish() {
        loadInterstitial {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopOuterRingAnimation()
        viewModel.resetToIdle()
    }
}
