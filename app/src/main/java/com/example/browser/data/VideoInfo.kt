package com.example.browser.data

/**
 * 检测到的视频信息
 */
data class VideoInfo(
    val url: String,
    val type: String,      // video, audio, m3u8
    val mime: String?,
    val suffix: String?,
    val size: Long?,
    val source: String,    // webRequest, content_script
    val tabId: Int?,
    val pageUrl: String?
)


/**
 * VideoInfo 转换为 Parcelable
 */
fun VideoInfo.toParcelable(): VideoInfoParcelable {
    return VideoInfoParcelable(
        url = url,
        type = type,
        mime = mime,
        suffix = suffix,
        size = size,
        source = source,
        tabId = tabId,
        pageUrl = pageUrl
    )
}