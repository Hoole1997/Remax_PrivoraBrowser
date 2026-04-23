package com.browser.shortvideo.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 已关注视频本地存储
 * 使用 SharedPreferences + Gson 实现持久化
 */
class FollowedVideoStorage(context: Context) {
    
    companion object {
        private const val PREF_NAME = "followed_videos"
        private const val KEY_VIDEOS = "videos"
        
        @Volatile
        private var INSTANCE: FollowedVideoStorage? = null
        
        fun getInstance(context: Context): FollowedVideoStorage {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FollowedVideoStorage(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // 内存缓存
    private val cachedVideos: MutableList<YouTubeVideo> by lazy {
        loadFromPrefs().toMutableList()
    }
    
    /**
     * 添加视频到关注列表
     */
    fun addVideo(video: YouTubeVideo) {
        if (!isFollowed(video.id)) {
            cachedVideos.add(video)  // 添加到列表末尾，向下滑可见
            saveToPrefs()
        }
    }
    
    /**
     * 从关注列表移除视频
     */
    fun removeVideo(videoId: String) {
        cachedVideos.removeAll { it.id == videoId }
        saveToPrefs()
    }
    
    /**
     * 切换关注状态
     * @return 新的关注状态
     */
    fun toggleFollow(video: YouTubeVideo): Boolean {
        return if (isFollowed(video.id)) {
            removeVideo(video.id)
            false
        } else {
            addVideo(video)
            true
        }
    }
    
    /**
     * 获取所有已关注视频
     */
    fun getFollowedVideos(): List<YouTubeVideo> {
        return cachedVideos.toList()
    }
    
    /**
     * 检查视频是否已关注
     */
    fun isFollowed(videoId: String): Boolean {
        return cachedVideos.any { it.id == videoId }
    }
    
    /**
     * 获取已关注视频数量
     */
    fun getCount(): Int = cachedVideos.size
    
    private fun loadFromPrefs(): List<YouTubeVideo> {
        val json = prefs.getString(KEY_VIDEOS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<YouTubeVideo>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveToPrefs() {
        val json = gson.toJson(cachedVideos)
        prefs.edit().putString(KEY_VIDEOS, json).apply()
    }
}
