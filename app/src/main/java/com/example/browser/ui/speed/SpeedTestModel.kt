package com.example.browser.ui.speed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.browser.base.BaseModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.corekit.core.report.ReportDataManager

/**
 * 网速测试 ViewModel
 * 管理测速状态流转和数据
 */
class SpeedTestModel : BaseModel() {

    private val engine = SpeedTestEngine()

    private val _state = MutableLiveData<SpeedTestState>(SpeedTestState.Idle)
    val state: LiveData<SpeedTestState> = _state

    private val _ispInfo = MutableLiveData<IspInfo>()
    val ispInfo: LiveData<IspInfo> = _ispInfo

    private var testJob: Job? = null

    // 累积测试结果
    private var pingResult: Int = 0
    private var jitterResult: Int = 0
    private var downloadResult: Float = 0f
    private var uploadResult: Float = 0f

    fun loadIspInfo() {
        viewModelScope.launch {
            val info = engine.getIspInfo()
            _ispInfo.postValue(info)
        }
    }

    fun startTest() {
        if (_state.value is SpeedTestState.Testing || _state.value is SpeedTestState.Connecting) {
            return
        }

        testJob?.cancel()
        testJob = viewModelScope.launch {
            try {
                // Phase 1: Connecting（显示连接中，同时预加载ISP信息）
                _state.postValue(SpeedTestState.Connecting)
                delay(800)

                // Phase 2: Ping + Jitter（快速完成，~2秒）
                _state.postValue(
                    SpeedTestState.Testing(
                        phase = TestPhase.PING,
                        progress = 0f
                    )
                )

                val (ping, jitter) = engine.testPing()
                pingResult = ping
                jitterResult = jitter

                _state.postValue(
                    SpeedTestState.Testing(
                        ping = pingResult,
                        jitter = jitterResult,
                        phase = TestPhase.DOWNLOAD,
                        progress = 0.10f
                    )
                )

                // Phase 3: Download（滑动窗口实时速度，progress 0.10 → 0.55）
                var dlCallbackCount = 0
                downloadResult = engine.testDownload { speedMbps ->
                    dlCallbackCount++
                    // 基于回调次数平滑推进进度
                    val p = (dlCallbackCount.toFloat() / 200f).coerceAtMost(1f)
                    _state.postValue(
                        SpeedTestState.Testing(
                            currentSpeed = speedMbps,
                            ping = pingResult,
                            download = speedMbps,
                            jitter = jitterResult,
                            phase = TestPhase.DOWNLOAD,
                            progress = 0.10f + 0.45f * p
                        )
                    )
                }

                _state.postValue(
                    SpeedTestState.Testing(
                        currentSpeed = downloadResult,
                        ping = pingResult,
                        download = downloadResult,
                        jitter = jitterResult,
                        phase = TestPhase.UPLOAD,
                        progress = 0.55f
                    )
                )

                // Phase 4: Upload（多轮渐进，progress 0.55 → 0.95）
                var ulCallbackCount = 0
                uploadResult = engine.testUpload { speedMbps ->
                    ulCallbackCount++
                    val p = (ulCallbackCount.toFloat() / 5f).coerceAtMost(1f)
                    _state.postValue(
                        SpeedTestState.Testing(
                            currentSpeed = speedMbps,
                            ping = pingResult,
                            download = downloadResult,
                            jitter = jitterResult,
                            upload = speedMbps,
                            phase = TestPhase.UPLOAD,
                            progress = 0.55f + 0.4f * p
                        )
                    )
                }

                // Phase 5: Completed
                _state.postValue(
                    SpeedTestState.Completed(
                        ping = pingResult,
                        download = downloadResult,
                        jitter = jitterResult,
                        upload = uploadResult
                    )
                )
            } catch (e: Exception) {
                ReportDataManager.reportData("speed_test_fail",mapOf())
                // 测试中断或出错，回到完成状态（展示已有数据）
                _state.postValue(
                    SpeedTestState.Completed(
                        ping = pingResult,
                        download = downloadResult,
                        jitter = jitterResult,
                        upload = uploadResult
                    )
                )
            }
        }
    }

    fun resetToIdle() {
        testJob?.cancel()
        pingResult = 0
        jitterResult = 0
        downloadResult = 0f
        uploadResult = 0f
        _state.postValue(SpeedTestState.Idle)
    }

    override fun onCleared() {
        super.onCleared()
        engine.cancel()
        testJob?.cancel()
    }
}
