package com.example.browser.utils

import java.text.DecimalFormat

/**
 * 文件大小格式化工具类
 */
object FileSizeUtils {
    
    private val df = DecimalFormat("#.#")
    
    /**
     * 格式化文件大小
     * @param sizeInBytes 字节数
     * @return Pair<显示数值, 单位>
     */
    fun formatSize(sizeInBytes: Long): Pair<String, String> {
        return when {
            sizeInBytes < 1024 -> {
                sizeInBytes.toString() to "B"
            }
            sizeInBytes < 1024 * 1024 -> {
                val sizeInKB = sizeInBytes / 1024.0
                df.format(sizeInKB) to "KB"
            }
            sizeInBytes < 1024 * 1024 * 1024 -> {
                val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
                df.format(sizeInMB) to "MB"
            }
            else -> {
                val sizeInGB = sizeInBytes / (1024.0 * 1024.0 * 1024.0)
                df.format(sizeInGB) to "GB"
            }
        }
    }
    
    /**
     * 格式化文件大小为完整字符串
     * @param sizeInBytes 字节数
     * @return 格式化后的字符串，如 "32.5 MB"
     */
    fun formatSizeString(sizeInBytes: Long): String {
        val (size, unit) = formatSize(sizeInBytes)
        return "$size $unit"
    }
}
