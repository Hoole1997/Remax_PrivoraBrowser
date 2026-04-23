package com.example.browser.ui.file.model

/**
 * 存储信息数据类
 */
data class StorageInfo(
    val usedSpace: Long,      // 已使用空间（字节）
    val totalSpace: Long,     // 总空间（字节）
    val appSize: Long = 0,    // 应用占用大小
    val videoSize: Long = 0,  // 视频占用大小
    val imageSize: Long = 0,  // 图片占用大小
    val audioSize: Long = 0   // 音乐占用大小
) {
    /**
     * 获取使用百分比
     */
    fun getUsedPercentage(): Int {
        return if (totalSpace > 0) {
            ((usedSpace.toDouble() / totalSpace.toDouble()) * 100).toInt()
        } else {
            0
        }
    }

    /**
     * 获取剩余空间
     */
    fun getFreeSpace(): Long {
        return totalSpace - usedSpace
    }
}
