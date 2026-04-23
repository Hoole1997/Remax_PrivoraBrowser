package com.example.browser.ui.junk

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.browser.base.BaseModel
import com.example.browser.data.junk.JunkScanResult
import com.example.browser.data.junk.JunkScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

class JunkModel : BaseModel() {
    
    // 扫描配置
    var scanDurationSeconds: Int = 6 // 默认6秒
    
    // 扫描进度 (0-100)
    private val _scanProgress = MutableLiveData<Int>(0)
    val scanProgress: LiveData<Int> = _scanProgress
    
    // 当前扫描路径
    private val _currentPath = MutableLiveData<String>()
    val currentPath: LiveData<String> = _currentPath
    
    // 累积的垃圾大小（字节）
    private val _accumulatedSize = MutableLiveData<Long>(0L)
    val accumulatedSize: LiveData<Long> = _accumulatedSize
    
    // 扫描完成状态
    private val _scanComplete = MutableLiveData<JunkScanResult>()
    val scanComplete: LiveData<JunkScanResult> = _scanComplete
    
    // 背景颜色（根据进度变化）
    private val _backgroundColor = MutableLiveData<String>("#00C194")
    val backgroundColor: LiveData<String> = _backgroundColor
    
    /**
     * 开始扫描
     */
    fun startScan() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val scanPaths = JunkScanner.getScanPaths()
            
            // 在后台线程执行真实扫描
            val allJunkFiles = withContext(Dispatchers.IO) {
                JunkScanner.scanJunkFiles()
            }
            
            val totalSize = allJunkFiles.sumOf { it.size }
            
            val totalDurationMs = scanDurationSeconds * 1000L
            val updateInterval = 50L // 每50ms更新一次
            val totalSteps = (totalDurationMs / updateInterval).toInt()
            
            var currentStep = 0
            var accumulatedBytes = 0L
            
            // 为每个文件分配一个出现时间点
            val fileTimings = allJunkFiles.mapIndexed { index, file ->
                val timing = (index.toFloat() / allJunkFiles.size * totalSteps).toInt()
                timing to file
            }
            
            while (currentStep <= totalSteps) {
                val elapsedMs = currentStep * updateInterval
                val elapsedSeconds = elapsedMs / 1000f
                
                // 使用缓动函数计算进度（开始快，后面慢）
                val rawProgress = currentStep.toFloat() / totalSteps
                val easedProgress = easeOutCubic(rawProgress)
                val progress = (easedProgress * 100).toInt().coerceIn(0, 100)
                
                _scanProgress.postValue(progress)
                
                // 更新背景颜色
                val color = when {
                    elapsedSeconds < 2f -> "#00C194"
                    elapsedSeconds < 4f -> "#EFA700"
                    else -> "#F45314"
                }
                _backgroundColor.postValue(color)
                
                // 更新当前扫描路径
                val pathIndex = (easedProgress * scanPaths.size).toInt().coerceIn(0, scanPaths.size - 1)
                _currentPath.postValue(scanPaths[pathIndex])
                
                // 累积添加文件大小
                fileTimings.filter { it.first == currentStep }.forEach { (_, file) ->
                    accumulatedBytes += file.size
                }
                _accumulatedSize.postValue(accumulatedBytes)
                
                currentStep++
                delay(updateInterval)
            }
            
            // 确保最终值正确
            _scanProgress.postValue(100)
            _accumulatedSize.postValue(totalSize)
            
            // 扫描完成
            val scanResult = JunkScanResult(
                junkFiles = allJunkFiles,
                totalSize = totalSize,
                scanDuration = System.currentTimeMillis() - startTime
            )
            _scanComplete.postValue(scanResult)
        }
    }
    
    /**
     * 缓动函数：开始快，结束慢
     */
    private fun easeOutCubic(x: Float): Float {
        return 1 - (1 - x).pow(3)
    }
}