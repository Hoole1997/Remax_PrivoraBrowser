package com.example.browser.ui.download

import java.util.concurrent.ConcurrentHashMap

/**
 * 下载速度跟踪器
 * 用于计算每个下载的实时速度
 */
class DownloadSpeedTracker {

    private data class DownloadProgress(
        val bytes: Long,
        val timestamp: Long
    )

    private val previousProgress = ConcurrentHashMap<String, DownloadProgress>()

    /**
     * 计算下载速度（字节/秒）
     * @param downloadId 下载ID
     * @param currentBytes 当前已下载字节数
     * @return 下载速度（字节/秒），如果无法计算则返回0
     */
    fun calculateSpeed(downloadId: String, currentBytes: Long): Long {
        val currentTime = System.currentTimeMillis()
        val previous = previousProgress[downloadId]

        // 更新进度记录
        previousProgress[downloadId] = DownloadProgress(currentBytes, currentTime)

        // 如果没有历史记录，返回0
        if (previous == null) {
            return 0
        }

        val timeDiff = currentTime - previous.timestamp
        val bytesDiff = currentBytes - previous.bytes

        // 至少需要100ms的时间差才计算速度，避免数值跳动
        if (timeDiff < 100 || bytesDiff <= 0) {
            return 0
        }

        // 计算速度：字节/秒
        return (bytesDiff * 1000) / timeDiff
    }

    /**
     * 清除指定下载的速度记录
     */
    fun clearSpeed(downloadId: String) {
        previousProgress.remove(downloadId)
    }

    /**
     * 清除所有速度记录
     */
    fun clearAll() {
        previousProgress.clear()
    }
}
