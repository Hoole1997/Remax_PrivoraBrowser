package com.browser.shortvideo.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 视频数据仓库
 * 封装 API 调用和分页逻辑
 */
class YouTubeRepository(
    private val api: YouTubeApi,
    context: Context
) {
    companion object {
        private const val TAG = "VideoRepository"
        private const val SP_NAME = "short_video_prefs"
        private const val KEY_LAST_VIDEO_ID = "last_video_id"
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    
    // 用于翻页的 key (最后一个视频的 id)，初始化时从 SP 读取
    private var lastVideoId: String? = sharedPreferences.getString(KEY_LAST_VIDEO_ID, null)
    private var isLoading = false
    
    // 已加载的视频 ID 集合，用于去重
    private val loadedVideoIds = mutableSetOf<String>()
    
    init {
        Log.d(TAG, "初始化 - 从 SP 读取 lastVideoId: $lastVideoId")
    }
    
    /**
     * 保存当前播放的视频 ID 到 SP
     * @param videoId 视频 ID
     */
    fun saveCurrentVideoId(videoId: String) {
        lastVideoId = videoId
        sharedPreferences.edit()
            .putString(KEY_LAST_VIDEO_ID, videoId)
            .apply()
        Log.d(TAG, "保存当前视频 ID: $videoId")
    }
    
    /**
     * 获取保存的视频 ID
     */
    fun getSavedVideoId(): String? {
        return sharedPreferences.getString(KEY_LAST_VIDEO_ID, null)
    }
    
    /**
     * 获取视频列表 Flow
     * @param regionCode 地区代码 (保留参数，不再使用)
     * @param pageSize 每页数量 (保留参数，不再使用)
     * @param categoryId 分类 ID (保留参数，不再使用)
     */
    fun getPopularVideosFlow(): Flow<Result<List<YouTubeVideo>>> = flow {
        if (isLoading) return@flow
        isLoading = true
        
        // 清空已加载的视频 ID
        loadedVideoIds.clear()
        
        // 检查是否有保存的 key，如果有则使用 op=1 翻页模式
        val savedKey = getSavedVideoId()
        val usePageMode = !savedKey.isNullOrEmpty()
        
        Log.d(TAG, "获取视频列表 - savedKey=$savedKey, usePageMode=$usePageMode")
        
        val result = if (usePageMode) {
            // 有保存的 key，使用 op=1 翻页模式从上次位置继续
            api.getVideos(key = savedKey, op = 1)
        } else {
            // 没有保存的 key，从头加载
            api.getVideos(key = null, op = 0)
        }
        
        result.onSuccess { videos ->
            // 记录加载的视频 ID
            videos.forEach { loadedVideoIds.add(it.id) }
            
            // 记录最后一个视频的 id 用于翻页
            lastVideoId = videos.lastOrNull()?.id
            Log.d(TAG, "获取成功: ${videos.size} 个视频, 翻页 key: $lastVideoId")
            emit(Result.success(videos))
        }.onFailure { error ->
            Log.e(TAG, "获取失败: ${error.message}")
            emit(Result.failure(error))
        }
        
        isLoading = false
    }
    
    /**
     * 加载更多视频
     * @param regionCode 地区代码 (保留参数，不再使用)
     * @param pageSize 每页数量 (保留参数，不再使用)
     */
    suspend fun loadMore(): Result<List<YouTubeVideo>> {
        if (isLoading) {
            Log.d(TAG, "正在加载中，跳过")
            return Result.success(emptyList())
        }
        
        if (lastVideoId == null) {
            Log.d(TAG, "没有更多数据")
            return Result.success(emptyList())
        }
        
        isLoading = true
        Log.d(TAG, "加载更多视频, key: $lastVideoId")
        
        // 翻页加载使用 op=1
        val result = api.getVideos(key = lastVideoId, op = 1)
        
        isLoading = false
        
        return result.map { videos ->
            // 过滤已加载过的视频，避免重复
            val newVideos = videos.filter { !loadedVideoIds.contains(it.id) }
            
            // 记录新加载的视频 ID
            newVideos.forEach { loadedVideoIds.add(it.id) }
            
            // 更新翻页 key
            if (newVideos.isNotEmpty()) {
                lastVideoId = newVideos.lastOrNull()?.id
            } else {
                // 没有新视频了，清空翻页 key
                lastVideoId = null
            }
            
            Log.d(TAG, "加载更多成功: ${newVideos.size} 个新视频 (过滤前: ${videos.size}), 下一页 key: $lastVideoId")
            newVideos
        }
    }
    
    /**
     * 重置分页状态
     */
    fun reset() {
        lastVideoId = null
        isLoading = false
        loadedVideoIds.clear()
        Log.d(TAG, "已重置分页状态")
    }
    
    /**
     * 是否有更多数据
     */
    fun hasMore(): Boolean = lastVideoId != null
}
