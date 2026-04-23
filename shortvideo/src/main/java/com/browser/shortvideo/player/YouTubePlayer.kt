package com.browser.shortvideo.player

interface YouTubePlayer {
    fun loadVideo(videoId: String, startSeconds: Float)
    fun cueVideo(videoId: String, startSeconds: Float)
    fun play()
    fun pause()
    fun mute()
    fun unMute()
    fun setVolume(volumePercent: Int)
    fun seekTo(time: Float)
    fun setPlaybackRate(playbackRate: PlayerConstants.PlaybackRate)
    fun addListener(listener: YouTubePlayerListener): Boolean
    fun removeListener(listener: YouTubePlayerListener): Boolean
}
