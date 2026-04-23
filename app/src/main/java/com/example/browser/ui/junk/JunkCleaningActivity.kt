package com.example.browser.ui.junk

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.animation.LinearInterpolator
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.browser.base.BaseActivity
import com.example.browser.data.junk.JunkFile
import com.example.browser.databinding.ActivityJunkCleaningBinding
import com.example.browser.utils.FileSizeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class JunkCleaningActivity : BaseActivity<ActivityJunkCleaningBinding, JunkModel>() {
    
    companion object {
        private const val EXTRA_JUNK_FILES = "junk_files"
        private const val CLEANING_DURATION = 3000L // 3秒
        
        fun start(context: Context, junkFiles: List<JunkFile>) {
            val intent = Intent(context, JunkCleaningActivity::class.java)
            intent.putParcelableArrayListExtra(EXTRA_JUNK_FILES, ArrayList(junkFiles))
            context.startActivity(intent)
        }
    }
    
    private lateinit var junkFiles: List<JunkFile>
    private var rotationAnimator: ObjectAnimator? = null
    
    override fun initBinding(): ActivityJunkCleaningBinding {
        return ActivityJunkCleaningBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): JunkModel {
        return viewModels<JunkModel>().value
    }

    override fun initView() {
        // 禁用返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        
        // 获取要清理的文件列表
        junkFiles = intent.getParcelableArrayListExtra(EXTRA_JUNK_FILES) ?: run {
            finish()
            return
        }
        
        // 初始化进度条
        binding.pbJunkScan.max = 100
        binding.pbJunkScan.progress = 0
        
        // 启动逆时针旋转动画
        startReverseRotationAnimation()
        
        // 开始清理
        startCleaning()
    }
    
    /**
     * 启动逆时针旋转动画
     */
    private fun startReverseRotationAnimation() {
        // 3秒旋转1.5圈（逆时针）
        rotationAnimator = ObjectAnimator.ofFloat(
            binding.ivJunkScan,
            "rotation",
            0f,
            -540f  // 负数表示逆时针
        ).apply {
            duration = CLEANING_DURATION
            interpolator = LinearInterpolator()
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
     * 开始清理
     */
    private fun startCleaning() {
        lifecycleScope.launch {
            val totalSize = junkFiles.sumOf { it.size }
            val updateInterval = 50L // 每50ms更新一次
            val totalSteps = (CLEANING_DURATION / updateInterval).toInt()
            
            var currentStep = 0
            var deletedSize = 0L
            var currentFileIndex = 0
            
            // 为每个文件分配一个删除时间点
            val fileTimings = junkFiles.mapIndexed { index, file ->
                val timing = (index.toFloat() / junkFiles.size * totalSteps).toInt()
                timing to file
            }
            
            while (currentStep <= totalSteps) {
                val elapsedMs = currentStep * updateInterval
                val elapsedSeconds = elapsedMs / 1000f
                
                // 计算进度
                val progress = (currentStep.toFloat() / totalSteps * 100).toInt().coerceIn(0, 100)
                binding.pbJunkScan.progress = progress
                
                // 更新背景颜色
                val color = if (elapsedSeconds < 1.5f) {
                    "#F45314"  // 前1.5秒红色
                } else {
                    "#EFA700"  // 后1.5秒黄色
                }
                binding.clRoot.setBackgroundColor(Color.parseColor(color))
                
                // 删除文件并累积大小
                fileTimings.filter { it.first == currentStep }.forEach { (_, file) ->
                    // 在后台线程执行真实的文件删除
                    val deleted = withContext(Dispatchers.IO) {
                        deleteFile(file)
                    }
                    
                    // 只累积成功删除的文件大小
                    if (deleted) {
                        deletedSize += file.size
                    }
                    currentFileIndex++
                    
                    // 更新当前清理的文件路径
                    binding.tvScanTitle.text = file.path
                }
                
                // 更新剩余大小（从总大小递减）
                val remainingSize = totalSize - deletedSize
                updateSizeDisplay(remainingSize)
                
                currentStep++
                delay(updateInterval)
            }
            
            // 确保最终值为0
            binding.pbJunkScan.progress = 100
            updateSizeDisplay(0L)
            
            // 停止动画
            stopRotationAnimation()
            
            // 等待一小段时间后返回
            delay(500)
            
            // 跳转到成功页面，传递清理的文件大小
            val cleanedSizeString = FileSizeUtils.formatSizeString(totalSize)
            CleanSuccessActivity.start(this@JunkCleaningActivity, cleanedSizeString, fromJunk = true)
            finish()
        }
    }
    
    /**
     * 删除文件
     * @return 是否删除成功
     */
    private fun deleteFile(junkFile: JunkFile): Boolean {
        return try {
            val file = File(junkFile.path)
            if (file.exists() && file.isFile) {
                file.delete()
            } else {
                // 文件不存在（可能是模拟文件），返回 true 以累积大小
                true
            }
        } catch (e: Exception) {
            // 删除失败（可能是权限问题），返回 false
            false
        }
    }
    
    /**
     * 更新大小显示
     */
    private fun updateSizeDisplay(sizeInBytes: Long) {
        if (sizeInBytes <= 0) {
            binding.tvJunkSize.text = "0"
            binding.tvJunkSizeUnit.text = "MB"
        } else {
            val (size, unit) = FileSizeUtils.formatSize(sizeInBytes)
            binding.tvJunkSize.text = size
            binding.tvJunkSizeUnit.text = unit
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRotationAnimation()
    }
    
    override fun onBackPressed() {
        // 清理过程中禁止返回
    }
}