package com.browser.shortvideo.data

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 视频服务 API 客户端
 * 使用 OkHttp 直接调用自定义视频 API
 * 
 * API: https://psv.gamespearl.com/videos
 * 参数: key (可选，翻页时传入最后一个视频的 id)
 */
class YouTubeApi(
    // 保留 apiKey 参数以保持 API 兼容性，但不再使用
    @Suppress("UNUSED_PARAMETER") apiKey: String = "",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()
    
    companion object {
        private const val TAG = "VideoApi"
        private const val BASE_URL = "https://psv.gamespearl.com/videos"
    }
    
    /**
     * 获取视频列表
     * @param key 翻页 key，为空表示从头拉取，非空表示获取该 key 之后的数据
     * @param op 操作类型，0=从头拉取，1=翻页（从 key 位置继续）
     * @return 视频列表结果
     */
    suspend fun getVideos(
        key: String? = null,
        op: Int = 0
    ): Result<List<YouTubeVideo>> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = BASE_URL.toHttpUrl().newBuilder()
            
            // 如果有 key，添加到查询参数用于翻页
            if (!key.isNullOrEmpty()) {
                urlBuilder.addQueryParameter("key", key)
                urlBuilder.addQueryParameter("op", op.toString())
            }
            
            val url = urlBuilder.build()
            Log.d(TAG, "请求视频列表: $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API 请求失败: ${response.code} ${response.message}")
                return@withContext Result.failure(
                    IOException("API 请求失败: ${response.code} ${response.message}")
                )
            }
            
            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                Log.e(TAG, "响应体为空")
                return@withContext Result.failure(IOException("响应体为空"))
            }
            
            Log.d(TAG, "收到响应: ${body.take(200)}...")
            
            val result = gson.fromJson(body, VideoListResponse::class.java)
            val videos = result.videos.map { it.toYouTubeVideo() }
            
            Log.d(TAG, "解析成功: 获取到 ${videos.size} 个视频")
            Result.success(videos)
            
        } catch (e: Exception) {
            Log.e(TAG, "请求异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取热门视频列表 (兼容旧接口，实际调用 getVideos)
     * @param regionCode 地区代码 (不再使用，保留参数以兼容)
     * @param maxResults 返回结果数量 (不再使用，保留参数以兼容)
     * @param pageToken 分页 token，对应新 API 的 key 参数
     * @param videoCategoryId 视频分类 ID (不再使用，保留参数以兼容)
     */
    @Deprecated("使用 getVideos() 替代", ReplaceWith("getVideos(pageToken)"))
    suspend fun getPopularVideos(
        regionCode: String = "US",
        maxResults: Int = 20,
        pageToken: String? = null,
        videoCategoryId: String? = null
    ): Result<YouTubeVideoListResponse> = withContext(Dispatchers.IO) {
        // 调用新接口获取视频
        val result = getVideos(pageToken)
        
        result.map { videos ->
            // 转换为旧的响应格式以保持兼容
            YouTubeVideoListResponse(
                kind = "youtube#videoListResponse",
                etag = "",
                nextPageToken = videos.lastOrNull()?.id,  // 使用最后一个视频的 id 作为翻页 token
                prevPageToken = null,
                pageInfo = PageInfo(
                    totalResults = videos.size,
                    resultsPerPage = videos.size
                ),
                items = videos
            )
        }
    }
}
