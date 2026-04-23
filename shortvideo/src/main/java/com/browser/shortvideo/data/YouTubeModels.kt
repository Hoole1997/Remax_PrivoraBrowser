package com.browser.shortvideo.data

import com.google.gson.annotations.SerializedName

/**
 * 自定义视频 API 响应
 * API: https://psv.gamespearl.com/videos
 */
data class VideoListResponse(
    @SerializedName("videos") val videos: List<VideoItem>
)

/**
 * 视频项 - 新 API 格式
 */
data class VideoItem(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("thumbnails") val thumbnails: Thumbnails,
    @SerializedName("statistics") val statistics: VideoStatistics?,
    @SerializedName("channelTitle") val channelTitle: String?
) {
    /**
     * 转换为统一的 YouTubeVideo 模型
     */
    fun toYouTubeVideo(): YouTubeVideo {
        return YouTubeVideo(
            kind = "youtube#video",
            etag = "",
            id = id,
            snippet = VideoSnippet(
                publishedAt = "",
                channelId = "",
                title = title,
                description = "",
                thumbnails = thumbnails,
                channelTitle = channelTitle ?: "Video",
                tags = null,
                categoryId = ""
            ),
            statistics = statistics,
            contentDetails = null
        )
    }
}

/**
 * YouTube Data API v3 视频列表响应 (保留用于兼容)
 */
data class YouTubeVideoListResponse(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("prevPageToken") val prevPageToken: String?,
    @SerializedName("pageInfo") val pageInfo: PageInfo,
    @SerializedName("items") val items: List<YouTubeVideo>
)

data class PageInfo(
    @SerializedName("totalResults") val totalResults: Int,
    @SerializedName("resultsPerPage") val resultsPerPage: Int
)

/**
 * YouTube 视频资源 - 统一的内部模型
 */
data class YouTubeVideo(
    @SerializedName("kind") val kind: String,
    @SerializedName("etag") val etag: String,
    @SerializedName("id") val id: String,
    @SerializedName("snippet") val snippet: VideoSnippet,
    @SerializedName("statistics") val statistics: VideoStatistics?,
    @SerializedName("contentDetails") val contentDetails: VideoContentDetails?
)

/**
 * 视频片段信息（标题、描述、缩略图等）
 */
data class VideoSnippet(
    @SerializedName("publishedAt") val publishedAt: String,
    @SerializedName("channelId") val channelId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("thumbnails") val thumbnails: Thumbnails,
    @SerializedName("channelTitle") val channelTitle: String,
    @SerializedName("tags") val tags: List<String>?,
    @SerializedName("categoryId") val categoryId: String
)

data class Thumbnails(
    @SerializedName("default") val default: Thumbnail?,
    @SerializedName("medium") val medium: Thumbnail?,
    @SerializedName("high") val high: Thumbnail?,
    @SerializedName("standard") val standard: Thumbnail?,
    @SerializedName("maxres") val maxres: Thumbnail?
)

data class Thumbnail(
    @SerializedName("url") val url: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int
)

/**
 * 视频统计数据
 */
data class VideoStatistics(
    @SerializedName("viewCount") val viewCount: String?,
    @SerializedName("likeCount") val likeCount: String?,
    @SerializedName("favoriteCount") val favoriteCount: String?,
    @SerializedName("commentCount") val commentCount: String?
)

/**
 * 视频内容详情
 */
data class VideoContentDetails(
    @SerializedName("duration") val duration: String,
    @SerializedName("dimension") val dimension: String,
    @SerializedName("definition") val definition: String,
    @SerializedName("caption") val caption: String,
    @SerializedName("licensedContent") val licensedContent: Boolean
)
