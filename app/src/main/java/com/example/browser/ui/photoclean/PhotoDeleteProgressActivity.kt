package com.example.browser.ui.photoclean

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ActivityUtils
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.base.BaseModel
import com.example.browser.databinding.ActivityPhotoDeleteProgressBinding
import com.example.browser.ui.photoclean.model.PhotoCleanMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PhotoDeleteProgressActivity :
    BaseActivity<ActivityPhotoDeleteProgressBinding, BaseModel>() {

    private val cleanMode: PhotoCleanMode
        get() {
            val modeStr = intent.getStringExtra(EXTRA_MODE) ?: PhotoCleanMode.DUPLICATE.name
            return PhotoCleanMode.valueOf(modeStr)
        }

    companion object {
        private const val EXTRA_MODE = "clean_mode"
        private var pendingFiles: List<File>? = null

        fun start(context: Context, mode: PhotoCleanMode, files: List<File>) {
            pendingFiles = files
            val intent = Intent(context, PhotoDeleteProgressActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode.name)
            }
            context.startActivity(intent)
        }
    }

    override fun initBinding(): ActivityPhotoDeleteProgressBinding {
        return ActivityPhotoDeleteProgressBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): BaseModel {
        return viewModels<BaseModel>().value
    }

    private var rotationAnimator: ObjectAnimator? = null

    override fun initView() {
        val files = pendingFiles ?: emptyList()
        pendingFiles = null

        if (files.isEmpty()) {
            finish()
            return
        }

        showProgressState()
        startDeletion(files)

        loadNative(binding.adContainer)
    }

    override fun initEdgeToEdge() {
        // 不启用 edge-to-edge，蓝色全屏
    }

    private fun showProgressState() {
        binding.llProgressArea.visibility = View.VISIBLE
        binding.llCompleteArea.visibility = View.GONE
        binding.ivBack.visibility = View.GONE
        binding.tvPercentNumber.text = "0"
        startRotationAnimation()
    }

    private fun startRotationAnimation() {
        rotationAnimator?.cancel()
        rotationAnimator = ObjectAnimator.ofFloat(binding.ivCircular, "rotation", 0f, 360f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopRotationAnimation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    private fun showCompleteState(deletedCount: Int) {
        stopRotationAnimation()
        binding.llProgressArea.visibility = View.GONE
        binding.llCompleteArea.visibility = View.VISIBLE
        binding.ivBack.visibility = View.VISIBLE

        binding.tvDeletedCount.text = deletedCount.toString()
        binding.tvDeletedLabel.text = getString(R.string.photo_clean_photos_label)
        binding.tvCompleteDesc.text = getString(R.string.photo_clean_delete_success)
        binding.btnContinue.text = getString(R.string.photo_clean_continue)

        binding.btnContinue.setOnClickListener {
            finish()
        }
        binding.ivBack.setOnClickListener {
            finishPlayAd()
        }
    }

    private fun startDeletion(files: List<File>) {
        val minDuration = 4000L

        // 平滑进度动画：0 → 100，持续 minDuration
        val progressAnimator = android.animation.ValueAnimator.ofInt(0, 100).apply {
            duration = minDuration
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { animation ->
                val percent = animation.animatedValue as Int
                binding.tvPercentNumber.text = percent.toString()
            }
            start()
        }

        // 后台删除文件
        lifecycleScope.launch {
            var deletedCount = 0
            val startTime = System.currentTimeMillis()

            withContext(Dispatchers.IO) {
                files.forEach { file ->
                    try {
                        if (file.exists() && file.delete()) {
                            deletedCount++
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            // 等待动画完成
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < minDuration) {
                delay(minDuration - elapsed)
            }
            progressAnimator.cancel()

            binding.tvPercentNumber.text = "100"
            delay(300)
            showCompleteState(deletedCount)
        }
    }

    private fun finishPlayAd() {
        loadInterstitial {
            ActivityUtils.finishActivity(PhotoCleanActivity::class.java)
            finish()
        }
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        // 删除过程中禁止返回
        finishPlayAd()
    }

    override fun onDestroy() {
        stopRotationAnimation()
        super.onDestroy()
    }
}
