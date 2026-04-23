package com.example.browser.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.example.browser.ui.file.model.StorageInfo
import java.io.File

/**
 * 存储空间分析工具类
 */
object StorageUtils {

    /**
     * 获取存储信息
     */
    fun getStorageInfo(context: Context): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalSpace = stat.blockSizeLong * stat.blockCountLong
        val availableSpace = stat.blockSizeLong * stat.availableBlocksLong
        val usedSpace = totalSpace - availableSpace

        // 分析各类文件占用（这里简化处理，实际应该扫描文件系统）
        val appSize = getAppDataSize(context)
        val videoSize = getMediaSize(context, "video")
        val imageSize = getMediaSize(context, "image")
        val audioSize = getMediaSize(context, "audio")

        return StorageInfo(
            usedSpace = usedSpace,
            totalSpace = totalSpace,
            appSize = appSize,
            videoSize = videoSize,
            imageSize = imageSize,
            audioSize = audioSize
        )
    }

    /**
     * 格式化存储大小显示
     * @param size 字节数
     * @return 格式化后的字符串，如 "54.6GB"
     */
    fun formatSize(size: Long): String {
        if (size <= 0) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
            "%.1f%s",
            size / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    /**
     * 获取应用数据大小（简化版本）
     */
    private fun getAppDataSize(context: Context): Long {
        var totalSize = 0L
        try {
            val cacheDir = context.cacheDir
            val filesDir = context.filesDir
            totalSize += getDirSize(cacheDir)
            totalSize += getDirSize(filesDir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return totalSize
    }

    /**
     * 获取媒体文件大小（通过 MediaStore 查询）
     */
    private fun getMediaSize(context: Context, type: String): Long {
        var totalSize = 0L
        try {
            val uri = when (type) {
                "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> return 0L
            }

            val projection = arrayOf(MediaStore.MediaColumns.SIZE)
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (it.moveToNext()) {
                    val size = it.getLong(sizeColumn)
                    totalSize += size
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return totalSize
    }

    /**
     * 计算目录大小
     */
    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        try {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    size += if (file.isDirectory) {
                        getDirSize(file)
                    } else {
                        file.length()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    /**
     * 获取下载目录
     */
    fun getDownloadDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    /**
     * 获取DCIM目录（相机照片）
     */
    fun getDCIMDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    }
}
