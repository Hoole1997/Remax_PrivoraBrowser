package com.example.browser.data

/**
 * 视频项数据类
 */
data class VideoItem(
    val videoInfo: VideoInfo,
    val isSelected: Boolean = true,
    val isLoadingSize: Boolean = false
)