package com.example.browser.ui.file.model

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.example.browser.ui.file.ImageActivity
import com.example.browser.ui.web.WebActivity
import com.example.browser.utils.ApkUtils
import com.example.browser.utils.FileManagerUtils
import java.io.File

/**
 * 最近文件数据类
 */
data class RecentFile(
    val file: File,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String?,
    val thumbnailPath: String? = null,  // 缩略图路径（用于图片和视频）
    val fileType: FileType = FileType.OTHER,  // 文件类型
    val videoDuration: Long? = null  // 视频时长（毫秒）
) {
    /**
     * 获取格式化的文件大小
     */
    fun getFormattedSize(): String {
        return formatFileSize(size)
    }

    fun open() {
        FileManagerUtils.openFile(file.absolutePath, mimeType)
    }

    /**
     * 打开文件（带 Context 参数）
     */
    fun open(context: android.content.Context) {
        FileManagerUtils.openFile(file.absolutePath, mimeType)
    }

    /**
     * 获取文件的 MIME 类型（非空）
     */
    fun getMimeTypeOrDefault(): String {
        return mimeType ?: "*/*"
    }

    /**
     * 获取格式化的视频时长
     */
    fun getFormattedDuration(): String? {
        if (videoDuration == null) return null
        val seconds = videoDuration / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    companion object {
        fun formatFileSize(size: Long): String {
            if (size <= 0) return "0B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format(
                "%.1f%s",
                size / Math.pow(1024.0, digitGroups.toDouble()),
                units[digitGroups]
            )
        }
    }
}
