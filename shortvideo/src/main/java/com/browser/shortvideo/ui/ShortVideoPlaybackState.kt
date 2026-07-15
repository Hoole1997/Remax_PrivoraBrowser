package com.browser.shortvideo.ui

/**
 * 短视频播放许可的唯一状态源。
 *
 * Fragment 生命周期和广告状态分别写入这里，播放器回调只读取最终结果，避免某个延迟
 * 回调在页面退出后单独恢复播放。该对象只在主线程访问。
 */
internal class ShortVideoPlaybackState {

    companion object {
        const val NO_POSITION = -1
    }

    var selectedPosition: Int = NO_POSITION
        private set

    var isFeedResumed: Boolean = false
        private set

    var isHostSelected: Boolean = false
        private set

    var isContentAvailable: Boolean = false
        private set

    private var activeAdRequests: Int = 0

    val isAdShowing: Boolean
        get() = activeAdRequests > 0

    var isReleased: Boolean = false
        private set

    val isPlaybackAllowed: Boolean
        get() = isHostSelected &&
            isFeedResumed &&
            isContentAvailable &&
            !isAdShowing &&
            !isReleased

    fun selectPosition(position: Int): Boolean {
        require(position >= NO_POSITION) { "position must be >= $NO_POSITION" }
        if (isReleased || selectedPosition == position) return false

        selectedPosition = position
        return true
    }

    fun setFeedResumed(resumed: Boolean): Boolean {
        if (isReleased || isFeedResumed == resumed) return false

        isFeedResumed = resumed
        return true
    }

    fun setHostSelected(selected: Boolean): Boolean {
        if (isReleased || isHostSelected == selected) return false

        isHostSelected = selected
        return true
    }

    fun setContentAvailable(available: Boolean): Boolean {
        if (isReleased || isContentAvailable == available) return false

        isContentAvailable = available
        return true
    }

    fun onAdStarted(): Boolean {
        if (isReleased) return false

        activeAdRequests++
        return activeAdRequests == 1
    }

    fun onAdFinished(): Boolean {
        if (isReleased || activeAdRequests == 0) return false

        activeAdRequests--
        return activeAdRequests == 0
    }

    /**
     * Holder 每次真正执行播放前都必须重新调用，不能缓存这个结果。
     */
    fun canPlay(
        position: Int,
        isAttached: Boolean,
        isUserPaused: Boolean,
    ): Boolean {
        return isPlaybackAllowed &&
            isAttached &&
            !isUserPaused &&
            position != NO_POSITION &&
            position == selectedPosition
    }

    fun release(): Boolean {
        if (isReleased) return false

        isReleased = true
        isHostSelected = false
        isFeedResumed = false
        isContentAvailable = false
        activeAdRequests = 0
        selectedPosition = NO_POSITION
        return true
    }
}
