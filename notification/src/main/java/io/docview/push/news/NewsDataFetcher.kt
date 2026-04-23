package io.docview.push.news

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.docview.push.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 新闻数据获取器
 * 负责从服务器获取最新新闻
 */
object NewsDataFetcher {

    private const val TAG = "NewsDataFetcher"
    private const val NEWS_API_BASE_URL = "https://psv.gamespearl.com/news"

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val gson by lazy { Gson() }

    /**
     * 请求新闻列表
     * @param op 分页参数
     * @param key 翻页 key（上一页最后一条的 key）
     * @return 新闻列表
     */
    private fun requestNewsList(op: Int, key: String? = null): List<NewsData> {
        try {
            val url = if (key.isNullOrEmpty()) {
                "$NEWS_API_BASE_URL?op=$op"
            } else {
                "$NEWS_API_BASE_URL?op=$op&key=$key"
            }
            Logger.d("$TAG: 请求新闻列表 - op=$op, key=$key")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val newsResponse = gson.fromJson(responseBody, NewsApiResponse::class.java)
                    return newsResponse.data ?: emptyList()
                }
            } else {
                Logger.w("$TAG: 请求失败 - ${response.code} ${response.message}")
            }
        } catch (e: Exception) {
            Logger.e("$TAG: 请求新闻异常", e)
        }
        return emptyList()
    }

    /**
     * 获取最新的一条新闻（优先带图片的）
     * 请求3页数据，使用 key 进行翻页，随机返回一条带图片的新闻
     * @return 新闻数据，失败返回 null
     */
    suspend fun fetchLatestNews(): NewsData? = withContext(Dispatchers.IO) {
        try {
            Logger.d("$TAG: 开始获取最新新闻，请求3页")
            
            val allNews = mutableListOf<NewsData>()
            var lastKey: String? = null
            
            // 请求3页，每页使用上一页最后一条的 key 进行翻页
            for (page in 0..2) {
                val pageList = requestNewsList(op = page, key = lastKey)
                if (pageList.isEmpty()) break
                
                allNews.addAll(pageList)
                // 获取最后一条的 key 用于下一页请求
                lastKey = pageList.lastOrNull()?.key
                
                if (lastKey.isNullOrEmpty()) break
            }
            
            if (allNews.isNotEmpty()) {
                // 筛选所有有图片的新闻
                val newsWithImage = allNews.filter { !it.image.isNullOrEmpty() }
                
                // 随机选择一条有图片的新闻，如果没有则随机选择一条
                val latestNews = if (newsWithImage.isNotEmpty()) {
                    newsWithImage.random()
                } else {
                    allNews.random()
                }
                
                Logger.d("$TAG: 获取新闻成功 - ${latestNews.title}, hasImage=${!latestNews.image.isNullOrEmpty()}, 总共${allNews.size}条, 有图片${newsWithImage.size}条")
                return@withContext latestNews
            } else {
                Logger.w("$TAG: 新闻列表为空")
            }
        } catch (e: Exception) {
            Logger.e("$TAG: 获取新闻异常", e)
        }
        return@withContext null
    }

}

/**
 * 新闻 API 响应
 */
data class NewsApiResponse(
    @SerializedName("pagination")
    val pagination: NewsPagination?,
    @SerializedName("data")
    val data: List<NewsData>?
)

/**
 * 分页信息
 */
data class NewsPagination(
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
 * 新闻数据
 */
data class NewsData(
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
