package com.example.browser.ui.junk

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.MenuItem
import android.view.animation.LinearInterpolator
import androidx.activity.viewModels
import com.example.browser.base.BaseActivity
import com.example.browser.data.junk.JunkScanResult
import com.example.browser.databinding.ActivityJunkScanBinding
import com.example.browser.utils.FileSizeUtils

class JunkScanActivity : BaseActivity<ActivityJunkScanBinding, JunkModel>() {
    
    private var rotationAnimator: ObjectAnimator? = null
    
    companion object {
        const val TAG = "JunkScanActivity"
        fun start(context: Context) {
            val intent = Intent(context, JunkScanActivity::class.java)
            context.startActivity(intent)
        }
    }
    override fun initBinding(): ActivityJunkScanBinding {
        return ActivityJunkScanBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): JunkModel {
        return viewModels<JunkModel>().value
    }

    override fun initView() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // 设置返回图标为白色
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)
        
        // 初始化进度条
        binding.pbJunkScan.max = 100
        binding.pbJunkScan.progress = 0
        
        // 初始化显示
        binding.tvJunkSize.text = "0"
        binding.tvJunkSizeUnit.text = "KB"
        
        // 观察扫描进度
        viewModel.scanProgress.observe(this) { progress ->
            binding.pbJunkScan.progress = progress
        }
        
        // 观察当前扫描路径
        viewModel.currentPath.observe(this) { path ->
            binding.tvScanTitle.text = path
        }
        
        // 观察累积大小
        viewModel.accumulatedSize.observe(this) { sizeInBytes ->
            updateSizeDisplay(sizeInBytes)
        }
        
        // 观察背景颜色
        viewModel.backgroundColor.observe(this) { colorHex ->
            binding.clRoot.setBackgroundColor(Color.parseColor(colorHex))
        }
        
        // 观察扫描完成
        viewModel.scanComplete.observe(this) { result ->
            onScanComplete(result)
        }
        
        // 启动旋转动画
        startRotationAnimation()
        
        // 开始扫描
        viewModel.startScan()
    }
    
    /**
     * 更新大小显示
     */
    private fun updateSizeDisplay(sizeInBytes: Long) {
        val (size, unit) = FileSizeUtils.formatSize(sizeInBytes)
        binding.tvJunkSize.text = size
        binding.tvJunkSizeUnit.text = unit
    }
    
    /**
     * 启动旋转动画
     */
    private fun startRotationAnimation() {
        // 计算旋转时长：与扫描时长保持一致的频率
        // 扫描6秒，让图标旋转3圈，即每2秒一圈
        val rotationDuration = (viewModel.scanDurationSeconds * 1000L) / 3
        
        rotationAnimator = ObjectAnimator.ofFloat(
            binding.ivJunkScan,
            "rotation",
            0f,
            360f
        ).apply {
            duration = rotationDuration
            interpolator = LinearInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            start()
        }
    }
    
    /**
     * 停止旋转动画
     */
    private fun stopRotationAnimation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }
    
    /**
     * 扫描完成回调
     */
    private fun onScanComplete(result: JunkScanResult) {
        // 停止旋转动画
        stopRotationAnimation()
        
        // 跳转到清理页面，传递扫描结果
        JunkCleanActivity.start(this, result)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        if (item.itemId == android.R.id.home) {
//            finish()
//            return true
//        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理动画资源
        stopRotationAnimation()
    }

    override fun onBackPressed() {

    }
}