package com.example.browser.ui.web

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.browser.base.BaseModel
import com.example.browser.data.VideoInfo
import com.example.browser.feature.VideoDetectorFeature

class WebModel : BaseModel() {
    
    // 检测到的视频列表
    private val _detectedVideos = MutableLiveData<List<VideoInfo>>(emptyList())
    val detectedVideos: LiveData<List<VideoInfo>> = _detectedVideos
    
    // 用于去重的 URL 集合
    private val videoUrls = mutableSetOf<String>()
    
    /**
     * 添加检测到的视频
     */
    fun addDetectedVideo(videoInfo: VideoInfo) {
        // 去重
        if (videoUrls.contains(videoInfo.url)) {
            return
        }
        videoUrls.add(videoInfo.url)
        
        val currentList = _detectedVideos.value?.toMutableList() ?: mutableListOf()
        currentList.add(videoInfo)
        _detectedVideos.value = currentList
    }
    
    /**
     * 清空检测到的视频列表
     */
    fun clearDetectedVideos() {
        videoUrls.clear()
        _detectedVideos.value = emptyList()
    }
    
    /**
     * 获取检测到的视频数量
     */
    fun getDetectedVideoCount(): Int {
        return _detectedVideos.value?.size ?: 0
    }
}