package com.example.browser.ui.download.model

import android.content.Intent
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.ImageUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.example.browser.ui.file.ImageActivity
import com.example.browser.ui.file.model.FileType
import com.example.browser.ui.web.WebActivity
import com.example.browser.utils.ApkUtils
import com.example.browser.utils.FileManagerUtils
import mozilla.components.browser.state.state.content.DownloadState
import java.io.File

/**
 * 下载项数据模型
 * 封装 Mozilla 的 DownloadState 并添加额外字段
 */
data class DownloadItem(
    /**
     * 下载ID
     */
    val id: String,

    /**
     * 文件名
     */
    val fileName: String,

    /**
     * 下载URL
     */
    val url: String,

    /**
     * 文件大小（字节）
     */
    val contentLength: Long?,

    /**
     * 已下载大小（字节）
     */
    val downloadedBytes: Long,

    /**
     * 下载状态
     */
    val status: DownloadStatus,

    /**
     * 创建时间（毫秒时间戳）
     */
    val createdTime: Long,

    /**
     * 文件保存路径
     */
    val filePath: String?,

    /**
     * MIME 类型
     */
    val contentType: String?,

    /**
     * 下载速度（字节/秒）
     */
    val downloadSpeed: Long = 0,

    /**
     * 上次更新时间（毫秒时间戳）
     */
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    /**
     * 获取下载进度（0-100）
     */
    fun getProgress(): Int {
        if (contentLength == null || contentLength <= 0) return 0
        return ((downloadedBytes * 100) / contentLength).toInt().coerceIn(0, 100)
    }

    /**
     * 是否下载完成
     */
    fun isCompleted(): Boolean = status == DownloadStatus.COMPLETED

    /**
     * 是否正在下载
     */
    fun isDownloading(): Boolean = status == DownloadStatus.DOWNLOADING

    /**
     * 是否已暂停
     */
    fun isPaused(): Boolean = status == DownloadStatus.PAUSED
    
    /**
     * 是否已取消
     */
    fun isCancelled(): Boolean = status == DownloadStatus.CANCELLED

    /**
     * 获取格式化的下载速度字符串
     */
    fun getFormattedSpeed(): String {
        if (downloadSpeed <= 0 || !isDownloading()) return ""
        return "${formatSize(downloadSpeed)}/S"
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun open() {
        FileManagerUtils.openFile(filePath, contentType)
    }

    companion object {
        /**
         * 从 Mozilla DownloadState 转换
         */
        fun fromDownloadState(download: DownloadState): DownloadItem {
            val status = when (download.status) {
                DownloadState.Status.INITIATED -> DownloadStatus.DOWNLOADING
                DownloadState.Status.DOWNLOADING -> DownloadStatus.DOWNLOADING
                DownloadState.Status.PAUSED -> DownloadStatus.PAUSED
                DownloadState.Status.COMPLETED -> DownloadStatus.COMPLETED
                DownloadState.Status.FAILED -> DownloadStatus.FAILED
                DownloadState.Status.CANCELLED -> DownloadStatus.CANCELLED
                else -> DownloadStatus.DOWNLOADING
            }

            val currentBytes = when (download.status) {
                DownloadState.Status.DOWNLOADING,
                DownloadState.Status.PAUSED,
                DownloadState.Status.FAILED,
                DownloadState.Status.CANCELLED,
                DownloadState.Status.INITIATED -> download.currentBytesCopied
                DownloadState.Status.COMPLETED -> download.contentLength ?: download.currentBytesCopied
                else -> download.currentBytesCopied
            }

            return DownloadItem(
                id = download.id,
                fileName = download.fileName ?: "未知文件",
                url = download.url,
                contentLength = download.contentLength,
                downloadedBytes = currentBytes,
                status = status,
                createdTime = download.createdTime,
                filePath = download.filePath,
                contentType = download.contentType
            )
        }
    }
}
