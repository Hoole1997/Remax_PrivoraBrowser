package com.browser.shortvideo.player

interface YouTubePlayerListener {
    fun onReady(youTubePlayer: YouTubePlayer)
    fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState)
    fun onPlaybackQualityChange(youTubePlayer: YouTubePlayer, playbackQuality: PlayerConstants.PlaybackQuality)
    fun onPlaybackRateChange(youTubePlayer: YouTubePlayer, playbackRate: PlayerConstants.PlaybackRate)
    fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError)
    fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float)
    fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float)
    fun onVideoLoadedFraction(youTubePlayer: YouTubePlayer, loadedFraction: Float)
    fun onVideoId(youTubePlayer: YouTubePlayer, videoId: String)
    fun onApiChange(youTubePlayer: YouTubePlayer)
}

abstract class AbstractYouTubePlayerListener : YouTubePlayerListener {
    override fun onReady(youTubePlayer: YouTubePlayer) {}
    override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {}
    override fun onPlaybackQualityChange(youTubePlayer: YouTubePlayer, playbackQuality: PlayerConstants.PlaybackQuality) {}
    override fun onPlaybackRateChange(youTubePlayer: YouTubePlayer, playbackRate: PlayerConstants.PlaybackRate) {}
    override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {}
    override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {}
    override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {}
    override fun onVideoLoadedFraction(youTubePlayer: YouTubePlayer, loadedFraction: Float) {}
    override fun onVideoId(youTubePlayer: YouTubePlayer, videoId: String) {}
    override fun onApiChange(youTubePlayer: YouTubePlayer) {}
}
