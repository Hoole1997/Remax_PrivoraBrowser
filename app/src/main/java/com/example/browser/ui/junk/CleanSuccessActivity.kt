package com.example.browser.ui.junk

import android.content.Context
import android.content.Intent
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.activity.viewModels
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.LogUtils
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivityCleanSuccessBinding
import com.example.browser.ui.dialog.RatingDialog
import com.example.browser.utils.SpUtils
import com.google.android.play.core.review.ReviewManagerFactory

class CleanSuccessActivity  : BaseActivity<ActivityCleanSuccessBinding, JunkModel>() {

    companion object {
        private const val TAG = "CleanSuccessActivity"
        private const val EXTRA_CLEAN_RESULT = "clean_result"
        private const val EXTRA_FROM_JUNK = "from_junk"
        fun start(context: Context,cleanResult:String,fromJunk: Boolean) {
            val intent = Intent(context, CleanSuccessActivity::class.java)
            intent.putExtra(EXTRA_CLEAN_RESULT, cleanResult)
            intent.putExtra(EXTRA_FROM_JUNK, fromJunk)
            context.startActivity(intent)
        }
    }

    private val cleanResult by lazy {
        intent.getStringExtra(EXTRA_CLEAN_RESULT) ?: ""
    }

    private val fromJunk by lazy {
        intent.getBooleanExtra(EXTRA_FROM_JUNK, false)
    }

    override fun initBinding(): ActivityCleanSuccessBinding {
        return ActivityCleanSuccessBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): JunkModel {
        return viewModels<JunkModel>().value
    }

    override fun initView() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        binding.tvSuccessMessage.text = if (fromJunk) {
            getString(R.string.clean_success_junk, cleanResult)
        } else {
            getString(R.string.clean_success_process, "")
        }

        if (fromJunk) {
            binding.ivIcon.setImageResource(R.mipmap.ic_clean_success_process)
            binding.tvOtherTitle.text = getString(R.string.clean_success_other_process_title)
            binding.tvOtherMessage.text = getString(R.string.clean_success_other_process_message)
            binding.btnOther.text = getString(R.string.clean_success_other_process_button)
        } else {
            binding.ivIcon.setImageResource(R.mipmap.ic_clean_success_junk)
            binding.tvOtherTitle.text = getString(R.string.clean_success_other_clean_title)
            binding.tvOtherMessage.text = getString(R.string.clean_success_other_clean_message)
            binding.btnOther.text = getString(R.string.clean_success_other_clean_button)
        }
        ClickUtils.applyGlobalDebouncing(binding.btnOther) {
            if (fromJunk) {
                ProcessCleanActivity.start(this)
            } else {
                JunkScanActivity.start(this)
            }
            finish()
        }
        loadInterstitial {

        }
        loadNative(binding.adContainer)
        onBackPressedDispatcher.addCallback {
            finish()
        }
        if (!SpUtils.hasAddedShortcut(this) || !SpUtils.hasShownRatingDialog(this)) {
            SpUtils.setPendingShortcutDialog(this, true)
        }
        // 检查是否需要显示好评弹框（只显示一次）
        checkAndShowRatingDialog()
    }

    /**
     * 检查并显示好评弹框
     */
    private fun checkAndShowRatingDialog() {
        if (!SpUtils.hasShownRatingDialog(this)) {
            // 标记已显示
            SpUtils.setRatingDialogShown(this)
            // 显示好评弹框
            showRatingDialog()
        }
    }

    /**
     * 显示好评弹框
     */
    private fun showRatingDialog() {
        RatingDialog(
            context = this,
            onSubmitClick = { rating ->
                // 点击提交后，调用谷歌好评弹框
                launchInAppReview()
            }
        ).show()
    }

    /**
     * 启动 Google Play In-App Review
     */
    private fun launchInAppReview() {
        val reviewManager = ReviewManagerFactory.create(this)
        val requestReviewFlow = reviewManager.requestReviewFlow()
        requestReviewFlow.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
                    // 评价流程完成（无论用户是否实际评价）
                }
            }
        }.addOnFailureListener {
            LogUtils.e(it.message)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

}