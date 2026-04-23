package com.example.browser.ui.speed

/**
 * 测速阶段
 */
enum class TestPhase {
    PING, DOWNLOAD, UPLOAD
}

/**
 * 测速状态机
 */
sealed class SpeedTestState {
    object Idle : SpeedTestState()
    object Connecting : SpeedTestState()
    data class Testing(
        val currentSpeed: Float = 0f,
        val ping: Int = 0,
        val download: Float = 0f,
        val jitter: Int = 0,
        val upload: Float = 0f,
        val progress: Float = 0f,
        val phase: TestPhase = TestPhase.PING
    ) : SpeedTestState()

    data class Completed(
        val ping: Int = 0,
        val download: Float = 0f,
        val jitter: Int = 0,
        val upload: Float = 0f
    ) : SpeedTestState()
}

/**
 * ISP 信息
 */
data class IspInfo(
    val name: String = "",
    val ip: String = ""
)
