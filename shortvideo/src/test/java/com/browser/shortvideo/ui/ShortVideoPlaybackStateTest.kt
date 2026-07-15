package com.browser.shortvideo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortVideoPlaybackStateTest {

    @Test
    fun initialState_doesNotAllowFirstItemToPlay() {
        val state = ShortVideoPlaybackState()

        state.selectPosition(0)
        state.setContentAvailable(true)

        assertFalse(state.canPlay(position = 0, isAttached = true, isUserPaused = false))
    }

    @Test
    fun resumedFeed_allowsOnlySelectedAttachedItem() {
        val state = activeState(selectedPosition = 2)

        assertTrue(state.canPlay(position = 2, isAttached = true, isUserPaused = false))
        assertFalse(state.canPlay(position = 1, isAttached = true, isUserPaused = false))
        assertFalse(state.canPlay(position = 2, isAttached = false, isUserPaused = false))
        assertFalse(state.canPlay(position = 2, isAttached = true, isUserPaused = true))
    }

    @Test
    fun delayedPlayerReady_afterFeedPausedCannotPlay() {
        val state = activeState(selectedPosition = 0)

        state.setFeedResumed(false)

        // 模拟低端机在 onPause 之后才收到 IFrame onReady。
        assertFalse(state.canPlay(position = 0, isAttached = true, isUserPaused = false))
    }

    @Test
    fun adFinished_afterFeedPausedDoesNotReactivatePlayback() {
        val state = activeState(selectedPosition = 0)
        state.onAdStarted()

        state.setFeedResumed(false)
        state.onAdFinished()

        assertFalse(state.isPlaybackAllowed)
        assertFalse(state.canPlay(position = 0, isAttached = true, isUserPaused = false))
    }

    @Test
    fun adFinished_whileFeedRemainsResumedRestoresSelectedItem() {
        val state = activeState(selectedPosition = 3)

        state.onAdStarted()
        assertFalse(state.canPlay(position = 3, isAttached = true, isUserPaused = false))

        state.onAdFinished()
        assertTrue(state.canPlay(position = 3, isAttached = true, isUserPaused = false))
    }

    @Test
    fun overlappingAds_requireEveryRequestToFinishBeforePlaybackResumes() {
        val state = activeState(selectedPosition = 0)
        state.onAdStarted()
        state.onAdStarted()

        state.onAdFinished()

        assertTrue(state.isAdShowing)
        assertFalse(state.canPlay(position = 0, isAttached = true, isUserPaused = false))

        state.onAdFinished()
        assertFalse(state.isAdShowing)
        assertTrue(state.canPlay(position = 0, isAttached = true, isUserPaused = false))
    }

    @Test
    fun hiddenContent_blocksPlaybackWhileFragmentRemainsResumed() {
        val state = activeState(selectedPosition = 0)

        state.setContentAvailable(false)

        assertFalse(state.canPlay(position = 0, isAttached = true, isUserPaused = false))
    }

    @Test
    fun hostDeselected_blocksPlaybackBeforeFragmentLifecycleCatchesUp() {
        val state = activeState(selectedPosition = 0)

        state.setHostSelected(false)

        assertFalse(state.canPlay(position = 0, isAttached = true, isUserPaused = false))
    }

    @Test
    fun releasePermanentlyClosesPlaybackGate() {
        val state = activeState(selectedPosition = 0)

        state.release()
        state.setHostSelected(true)
        state.setFeedResumed(true)
        state.setContentAvailable(true)
        state.onAdFinished()
        state.selectPosition(0)

        assertFalse(state.isPlaybackAllowed)
        assertFalse(state.canPlay(position = 0, isAttached = true, isUserPaused = false))
    }

    private fun activeState(selectedPosition: Int): ShortVideoPlaybackState {
        return ShortVideoPlaybackState().apply {
            selectPosition(selectedPosition)
            setHostSelected(true)
            setContentAvailable(true)
            setFeedResumed(true)
        }
    }
}
