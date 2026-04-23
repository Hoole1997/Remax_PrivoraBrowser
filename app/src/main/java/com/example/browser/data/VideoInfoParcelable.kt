package com.example.browser.data

import com.example.browser.feature.VideoDetectorFeature

@kotlinx.parcelize.Parcelize
data class VideoInfoParcelable(
    val url: String,
    val type: String,
    val mime: String?,
    val suffix: String?,
    val size: Long?,
    val source: String,
    val tabId: Int?,
    val pageUrl: String?
) : android.os.Parcelable {

    fun toVideoInfo(): VideoInfo {
        return VideoInfo(
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
}