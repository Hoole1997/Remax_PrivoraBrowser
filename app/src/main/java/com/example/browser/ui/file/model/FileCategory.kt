package com.example.browser.ui.file.model

import androidx.annotation.DrawableRes

/**
 * 文件分类数据类
 */
data class FileCategory(
    val type: FileType,
    val name: String,
    @DrawableRes val iconRes: Int,
    val count: Int,
    val iconBgColor: Int  // 图标背景颜色
)

/**
 * 文件类型枚举
 */
enum class FileType {
    IMAGE,      // 图片
    VIDEO,      // 视频
    DOCUMENT,   // 文档
    APK,        // APK文件
    AUDIO,      // 音乐
    ZIP,        // ZIP压缩文件
    DOWNLOAD,   // 下载
    OTHER       // 其他类型
}
