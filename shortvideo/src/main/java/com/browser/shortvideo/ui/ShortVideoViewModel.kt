package com.browser.shortvideo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.browser.shortvideo.data.YouTubeApi
import com.browser.shortvideo.data.YouTubeRepository
import com.browser.shortvideo.data.YouTubeVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 短视频 ViewModel
 */
class ShortVideoViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    private val repository: YouTubeRepository by lazy {
        val api = YouTubeApi("")
        YouTubeRepository(api, application)
    }
    
    /**
     * UI 状态
     */
    sealed class UiState {
        data object Loading : UiState()
        data class Success(val videos: List<YouTubeVideo>) : UiState()
        data class Error(val message: String) : UiState()
    }
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val _videos = mutableListOf<YouTubeVideo>()

    val videoPause = MutableLiveData<Boolean>(false)

    /**
     * 初始加载
     */
    fun loadVideos() {
        repository.reset()
        _videos.clear()
        
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            repository.getPopularVideosFlow().collect { result ->
                result.onSuccess { videos ->
                    _videos.addAll(videos)
                    _uiState.value = UiState.Success(_videos.toList())
                }.onFailure { error ->
                    _uiState.value = UiState.Error(error.message ?: "Unknown error")
                }
            }
        }
    }
    
    /**
     * 加载更多
     */
    fun loadMore() {
        if (_uiState.value is UiState.Loading) return
        if (!repository.hasMore()) {
            android.util.Log.d("ShortVideoViewModel", "loadMore - No more data available")
            return
        }
        
        android.util.Log.d("ShortVideoViewModel", "loadMore - Loading more videos...")
        
        viewModelScope.launch {
            val result = repository.loadMore()
            result.onSuccess { newVideos ->
                android.util.Log.d("ShortVideoViewModel", "loadMore - Loaded ${newVideos.size} new videos")
                if (newVideos.isNotEmpty()) {
                    _videos.addAll(newVideos)
                    _uiState.value = UiState.Success(_videos.toList())
                }
            }.onFailure { error ->
                android.util.Log.e("ShortVideoViewModel", "loadMore - Error: ${error.message}")
            }
        }
    }
    
    /**
     * 刷新
     */
    fun refresh() {
        loadVideos()
    }
    
    /**
     * 保存当前播放的视频 ID
     */
    fun saveCurrentVideoId(videoId: String) {
        repository.saveCurrentVideoId(videoId)
    }
    
    /**
     * 获取保存的视频 ID
     */
    fun getSavedVideoId(): String? {
        return repository.getSavedVideoId()
    }
    
    fun pauseVideo() {
        videoPause.postValue(true)
    }
}
