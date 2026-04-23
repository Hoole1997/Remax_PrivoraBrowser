package com.example.browser.ui.news

import com.google.gson.annotations.SerializedName

/**
 * 新闻响应数据
 */
data class NewsResponse(
    @SerializedName("pagination")
    val pagination: Pagination?,
    @SerializedName("data")
    val data: List<NewsItem>?
)

/**
 * 分页信息
 */
data class Pagination(
    @SerializedName("limit")
    val limit: Int,
    @SerializedName("offset")
    val offset: Int,
    @SerializedName("count")
    val count: Int,
    @SerializedName("total")
    val total: Int
)

/**
 * 新闻条目
 */
data class NewsItem(
    @SerializedName("author")
    val author: String?,
    @SerializedName("title")
    val title: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("url")
    val url: String?,
    @SerializedName("source")
    val source: String?,
    @SerializedName("image")
    val image: String?,
    @SerializedName("category")
    val category: String?,
    @SerializedName("language")
    val language: String?,
    @SerializedName("country")
    val country: String?,
    @SerializedName("published_at")
    val publishedAt: String?,
    @SerializedName("key")
    val key: String?
)

/**
 * 新闻列表项（包含新闻和广告）
 */
sealed class NewsFeedItem {
    data class News(val newsItem: NewsItem) : NewsFeedItem()
    data class NativeAd(val id: Int) : NewsFeedItem()
}
