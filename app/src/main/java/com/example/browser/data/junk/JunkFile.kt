package com.example.browser.data.junk

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 垃圾文件类型
 */
enum class JunkType {
    RESIDUAL_FILES,      // 残留文件
    JUNK_FILES,          // 垃圾文件
    ADVERTISEMENT_FILES, // 广告文件
    OBSOLETE_APK_FILES   // 过时APK文件
}

/**
 * 垃圾文件数据结构
 */
@Parcelize
data class JunkFile(
    val path: String,           // 文件路径
    val name: String,           // 文件名
    val size: Long,             // 文件大小（字节）
    val type: JunkType,         // 垃圾类型
    val lastModified: Long = System.currentTimeMillis() // 最后修改时间
) : Parcelable

/**
 * 垃圾扫描结果
 */
@Parcelize
data class JunkScanResult(
    val junkFiles: List<JunkFile>,  // 所有垃圾文件
    val totalSize: Long,             // 总大小（字节）
    val scanDuration: Long           // 扫描时长（毫秒）
) : Parcelable {
    
    /**
     * 按类型分组的垃圾文件
     */
    fun getFilesByType(type: JunkType): List<JunkFile> {
        return junkFiles.filter { it.type == type }
    }
    
    /**
     * 获取某类型的总大小
     */
    fun getSizeByType(type: JunkType): Long {
        return getFilesByType(type).sumOf { it.size }
    }
}
