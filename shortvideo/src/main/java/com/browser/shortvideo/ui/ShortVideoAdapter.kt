package com.browser.shortvideo.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.browser.shortvideo.R
import com.browser.shortvideo.data.YouTubeVideo
import com.browser.shortvideo.databinding.ItemShortVideoBinding
import com.browser.shortvideo.player.AbstractYouTubePlayerListener
import com.browser.shortvideo.player.IFramePlayerOptions
import com.browser.shortvideo.player.PlayerConstants
import com.browser.shortvideo.player.YouTubePlayer
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import jp.wasabeef.glide.transformations.BlurTransformation
import java.util.Collections
import java.util.WeakHashMap

/**
 * 短视频 Adapter
 * 使用自定义 WebView + YouTube IFrame API 实现
 */
class ShortVideoAdapter(
    private val onLikeClick: ((YouTubeVideo) -> Unit)? = null,
    private val onHeartClick: ((YouTubeVideo) -> Unit)? = null,  // 爱心按钮点击（关注/收藏）
    private val onMoreClick: ((YouTubeVideo) -> Unit)? = null,
    private val isFollowedChecker: ((String) -> Boolean)? = null  // 检查是否已关注
) : ListAdapter<YouTubeVideo, ShortVideoAdapter.VideoViewHolder>(VideoDiffCallback()) {
    
    companion object {
        private const val TAG = "ShortVideoAdapter"
        private const val PAYLOAD_FOLLOW_STATE = "follow_state"  // 关注状态变化的 payload
        private const val PAYLOAD_LIKE_STATE = "like_state"  // 点赞状态变化的 payload

    }
    
    /** 页面生命周期、广告状态和选中位置统一通过该状态对象决定播放许可。 */
    private val playbackState = ShortVideoPlaybackState()

    /**
     * RecyclerView 的缓存池也会持有 WebView，因此需要跟踪全部 Holder，而非只跟踪当前项。
     */
    private val createdHolders: MutableSet<VideoViewHolder> =
        Collections.newSetFromMap(WeakHashMap())

    val isPlaybackAllowed: Boolean
        get() = playbackState.isPlaybackAllowed
    
    // 记录已关注的视频 ID（用于 UI 状态缓存）
    private val followedVideoIds = mutableSetOf<String>()
    
    // 记录已点赞的视频 ID
    private val likedVideoIds = mutableSetOf<String>()

    // 原生广告间隔控制
    private var nativeDialogInterval = 60L
    private var lastNativeDialogShowTime = 0L  // 上次展示原生广告的时间戳（毫秒）
    
    /**
     * 检查是否应该展示原生广告弹框
     * @param context 上下文，用于展示广告
     * @return true=展示广告，false=不展示
     */
    private fun shouldShowNativeDialog(context: android.content.Context): Boolean {
        val currentTime = System.currentTimeMillis()
        val intervalMillis = nativeDialogInterval * 1000  // 转换为毫秒
        val timeSinceLastShow = currentTime - lastNativeDialogShowTime
        
        val shouldShow = timeSinceLastShow >= intervalMillis
        
        Log.d(TAG, "shouldShowNativeDialog - interval: ${nativeDialogInterval}s, " +
                "timeSinceLastShow: ${timeSinceLastShow / 1000}s, shouldShow: $shouldShow")
        
        if (shouldShow) {
            lastNativeDialogShowTime = currentTime
            Log.d(TAG, "展示原生广告弹框")
//            NativeDialog.show(context)
        } else {
            Log.d(TAG, "原生广告弹框冷却中，剩余: ${(intervalMillis - timeSinceLastShow) / 1000}s")
        }
        
        return shouldShow
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemShortVideoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding).also(createdHolders::add)
    }
    
    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = getItem(position)
        // 使用外部检查器或内部缓存判断关注状态
        val isFollowed = isFollowedChecker?.invoke(video.id) ?: followedVideoIds.contains(video.id)
        val isLiked = likedVideoIds.contains(video.id)
        holder.bind(video, isFollowed, isLiked)

        // 点赞按钮 - 切换点赞状态
        holder.binding.btnLike.setOnClickListener {
            toggleLike(video.id, holder)
            onLikeClick?.invoke(video)
        }
        
        // 爱心按钮（关注/收藏）
        holder.binding.btnHeart.setOnClickListener {
            onHeartClick?.invoke(video)
        }
    }
    
    /**
     * 切换点赞状态
     */
    private fun toggleLike(videoId: String, holder: VideoViewHolder) {
        if (likedVideoIds.contains(videoId)) {
            likedVideoIds.remove(videoId)
            holder.updateLikeState(false)
        } else {
            likedVideoIds.add(videoId)
            holder.updateLikeState(true)
        }
    }
    
    /**
     * 带 payload 的局部更新
     */
    override fun onBindViewHolder(holder: VideoViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            // 没有 payload，调用完整绑定
            onBindViewHolder(holder, position)
        } else {
            // 有 payload，只更新关注状态
            val video = getItem(position)
            for (payload in payloads) {
                when (payload) {
                    PAYLOAD_FOLLOW_STATE -> {
                        val isFollowed = isFollowedChecker?.invoke(video.id) ?: followedVideoIds.contains(video.id)
                        holder.updateHeartState(isFollowed)
                    }
                }
            }
        }
    }
    
    /**
     * 更新关注状态（由外部调用）- 使用 payload 局部刷新
     */
    fun updateFollowState(videoId: String, isFollowed: Boolean) {
        if (isFollowed) {
            followedVideoIds.add(videoId)
        } else {
            followedVideoIds.remove(videoId)
        }
        // 使用 payload 局部刷新，只更新爱心状态
        val position = currentList.indexOfFirst { it.id == videoId }
        if (position >= 0) {
            notifyItemChanged(position, PAYLOAD_FOLLOW_STATE)
        }
    }
    
    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        // 不在回收时释放播放器，只暂停视频并重置状态
        holder.onRecycled()
    }
    
    override fun onViewAttachedToWindow(holder: VideoViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.onAttach()
    }
    
    override fun onViewDetachedFromWindow(holder: VideoViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.onDetach()
    }
    
    /**
     * 更新当前页面选中的视频。该方法只改变选择，不会绕过生命周期门禁启动播放。
     */
    fun selectPosition(position: Int) {
        val normalizedPosition = if (position >= 0) {
            position
        } else {
            ShortVideoPlaybackState.NO_POSITION
        }

        if (playbackState.selectPosition(normalizedPosition)) {
            // 滑到其他视频后恢复自动播放语义，不继承上一项的手动暂停状态。
            createdHolders.toList().forEach(VideoViewHolder::onSelectionChanged)
        } else {
            reconcileAllHolders()
        }
    }

    /**
     * 由当前 Feed 的 RESUMED 状态驱动。关闭时先落状态，再向全部播放器发送暂停。
     */
    fun setFeedResumed(resumed: Boolean) {
        val changed = playbackState.setFeedResumed(resumed)
        if (changed && resumed) {
            createdHolders.toList().forEach(VideoViewHolder::onFeedResumed)
        }
        reconcileAllHolders()
    }

    /** 首页底栏切换时同步更新，早于 Fragment maxLifecycle 事务形成第一道暂停屏障。 */
    fun setHostSelected(selected: Boolean) {
        val changed = playbackState.setHostSelected(selected)
        if (changed && selected) {
            createdHolders.toList().forEach(VideoViewHolder::onFeedResumed)
        }
        reconcileAllHolders()
    }

    /**
     * 数据加载、错误或空态隐藏内容时，已缓存的 Holder 也必须保持静音。
     */
    fun setContentAvailable(available: Boolean) {
        playbackState.setContentAvailable(available)
        reconcileAllHolders()
    }

    /** 广告采用计数门禁，重叠请求必须全部结束后才允许恢复。 */
    fun onAdStarted() {
        playbackState.onAdStarted()
        reconcileAllHolders()
    }

    fun onAdFinished() {
        playbackState.onAdFinished()
        reconcileAllHolders()
    }

    /**
     * Fragment View 终止销毁时释放 RecyclerView 缓存池中的全部 WebView。
     */
    fun releaseAll() {
        if (!playbackState.release()) return

        createdHolders.toList().forEach(VideoViewHolder::release)
        createdHolders.clear()
    }

    private fun reconcileAllHolders() {
        createdHolders.toList().forEach(VideoViewHolder::reconcilePlayback)
    }
    
    /**
     * ViewHolder - 使用 android-youtube-player 源码实现
     */
    inner class VideoViewHolder(
        val binding: ItemShortVideoBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var currentVideoId: String? = null
        private var isPlayerReady = false
        private var youTubePlayer: YouTubePlayer? = null
        private var pendingVideoId: String? = null
        
        private var isPlaying = false
        private var isAttached = false  // 是否附加到窗口
        private var hasUserPaused = false  // 用户是否主动暂停过
        private var isReleased = false
        
        init {
            initializePlayer()
            setupTouchInterceptor()
        }
        
        private fun setupTouchInterceptor() {
            // 点击透明覆盖层控制播放/暂停
            binding.touchInterceptor.setOnClickListener {
                // 隐藏页面、非当前项和已回收 Holder 都不能通过点击绕过播放门禁。
                if (!isSelectedHolderActive()) return@setOnClickListener

                if (isPlaying) {
                    hasUserPaused = true  // 标记用户主动暂停
                    youTubePlayer?.pause()
                    // 手动暂停时检查是否应该展示原生广告
//                    shouldShowNativeDialog(binding.ivPlayPause.context)
                } else {
                    // 恢复播放前显示黑色遮罩，遮挡 YouTube UI 闪烁
                    hasUserPaused = false
                    showBlackOverlay()
                    reconcilePlayback()
                }
            }
        }
        
        /**
         * 显示黑色遮罩层
         */
        private fun showBlackOverlay() {
            binding.blackOverlay.visibility = View.VISIBLE
        }
        
        /**
         * 隐藏黑色遮罩层
         */
        private fun hideBlackOverlay() {
            binding.blackOverlay.visibility = View.GONE
        }
        
        /**
         * 隐藏封面图和 loading（淡出动画）
         */
        private fun hideCover() {
            // 隐藏 loading
            binding.progressLoading.visibility = View.GONE
            
            if (binding.ivCover.visibility != View.VISIBLE) return
            
            binding.ivCover.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    binding.ivCover.visibility = View.GONE
                    binding.ivCover.alpha = 1f  // 重置 alpha 以便下次使用
                }
                .start()
        }
        
        /**
         * 显示封面图和 loading
         */
        private fun showCover() {
            binding.ivCover.visibility = View.VISIBLE
            binding.progressLoading.visibility = View.VISIBLE
        }
        
        private fun initializePlayer() {
            // 配置播放器选项 - controls=0 隐藏 YouTube 原生控件，使用自定义覆盖层
            val options = IFramePlayerOptions.Builder(binding.root.context)
                .controls(0)  // 隐藏 YouTube 原生控件
                // IFrame 不自行决定播放，当前可见项统一由 playbackState 控制。
                .autoplay(0)
                .rel(0)
                .ivLoadPolicy(3)
                .ccLoadPolicy(0)
                .fullscreen(0)
                .build()
            
            binding.youtubePlayerView.initialize({ player ->
                if (isReleased) return@initialize

                youTubePlayer = player
                isPlayerReady = true
                Log.d(TAG, "onReady - Player initialized")
                
                player.addListener(object : AbstractYouTubePlayerListener() {
                    override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                        if (isReleased) return

                        val shouldPlay = shouldPlayNow()
                        Log.d(TAG, "onStateChange: $state, isAttached: $isAttached, shouldPlay: $shouldPlay")
                        // 更新播放状态
                        isPlaying = (state == PlayerConstants.PlayerState.PLAYING)
                        
                        // 如果不应该播放但正在播放，则暂停
                        if (isPlaying && !shouldPlay) {
                            Log.d(TAG, "Pausing because shouldPlay is false")
                            youTubePlayer.pause()
                            return
                        }
                        
                        // 播放时隐藏黑色遮罩层和封面图
                        if (isPlaying) {
                            hideBlackOverlay()
                            hideCover()
                        }
                        
                        // 视频播放结束时自动循环播放
                        if (state == PlayerConstants.PlayerState.ENDED && shouldPlay) {
                            Log.d(TAG, "Video ended, restarting for loop")
                            youTubePlayer.seekTo(0f)
                            youTubePlayer.play()
                        }
                        
                        // 更新播放/暂停按钮显示
                        updatePlayPauseButton()
                    }
                    
                    override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                        Log.e(TAG, "Player error: $error")
                    }
                })
                
                // onReady 可能晚于 Fragment onPause，必须读取实时门禁，不能使用 bind 时的旧值。
                pendingVideoId?.let { videoId ->
                    val shouldPlay = shouldPlayNow()
                    Log.d(TAG, "onReady - Loading pending video: $videoId, shouldPlay: $shouldPlay")
                    loadOrCueVideo(player, videoId, shouldPlay)
                    currentVideoId = videoId
                    pendingVideoId = null
                }
            }, options, null)
        }
        
        fun bind(video: YouTubeVideo, isFollowed: Boolean = false, isLiked: Boolean = false) {
            if (isReleased) return

            // 设置视频信息
            binding.tvVideoTitle.text = video.snippet.title
            binding.tvLikeCount.text = formatCount(video.statistics?.likeCount)
            binding.tvChannelName.text = "@${video.snippet.channelTitle}"
            // 更新爱心状态
            updateHeartState(isFollowed)
            
            // 更新点赞状态
            updateLikeState(isLiked)
            
            // 加载高斯模糊封面图
            val thumbnailUrl = video.snippet.thumbnails.high?.url 
                ?: video.snippet.thumbnails.medium?.url
                ?: video.snippet.thumbnails.default?.url
            
            if (!thumbnailUrl.isNullOrEmpty()) {
                binding.ivCover.visibility = View.VISIBLE
                Glide.with(binding.root.context)
                    .load(thumbnailUrl)
                    .apply(RequestOptions.bitmapTransform(BlurTransformation(25, 3)))
                    .into(binding.ivCover)
            }
            
            // 清理视频 ID（去除可能的空白字符）
            val videoId = video.id.trim()

            Log.d(
                TAG,
                "bind - Video ID: '$videoId', currentVideoId: '$currentVideoId', " +
                    "shouldPlay: ${shouldPlayNow()}, isPlayerReady: $isPlayerReady",
            )

            // 加载/切换视频
            if (currentVideoId != videoId) {
                hasUserPaused = false
                pendingVideoId = videoId
            }

            reconcilePlayback()

            // 更新播放按钮状态
            updatePlayPauseButton()
        }

        /**
         * 根据实时状态收敛播放器。所有异步入口最终都只能走到这里。
         */
        fun reconcilePlayback() {
            if (isReleased) return

            val player = youTubePlayer
            if (!isPlayerReady || player == null) return

            pendingVideoId?.let { videoId ->
                loadOrCueVideo(player, videoId, shouldPlayNow())
                currentVideoId = videoId
                pendingVideoId = null
                return
            }

            if (shouldPlayNow() && currentVideoId != null) {
                showBlackOverlay()
                player.play()
            } else {
                player.pause()
            }
        }

        private fun loadOrCueVideo(
            player: YouTubePlayer,
            videoId: String,
            shouldPlay: Boolean,
        ) {
            if (shouldPlay) {
                showBlackOverlay()
                player.loadVideo(videoId, 0f)
            } else {
                // 隐藏或预加载项只 cue，禁止“先播放再暂停”产生后台音频窗口。
                player.cueVideo(videoId, 0f)
            }
        }

        private fun shouldPlayNow(): Boolean {
            return playbackState.canPlay(
                position = bindingAdapterPosition,
                isAttached = isAttached,
                isUserPaused = hasUserPaused,
            )
        }

        private fun isSelectedHolderActive(): Boolean {
            val position = bindingAdapterPosition
            return playbackState.isPlaybackAllowed &&
                isAttached &&
                position != RecyclerView.NO_POSITION &&
                position == playbackState.selectedPosition
        }

        fun onSelectionChanged() {
            hasUserPaused = false
            reconcilePlayback()
        }

        fun onFeedResumed() {
            // 保持原有体验：离开再返回 Feed 时，当前视频自动恢复。
            hasUserPaused = false
        }

        fun onAttach() {
            isAttached = true
            reconcilePlayback()
        }
        
        fun onDetach() {
            isAttached = false
            youTubePlayer?.pause()
        }
        
        /**
         * ViewHolder 被回收时调用 - 轻量级处理
         * 不销毁播放器，只重置状态
         */
        fun onRecycled() {
            Log.d(TAG, "onRecycled - Pausing and resetting state")
            isAttached = false
            hasUserPaused = false
            youTubePlayer?.pause()
            // 不清除 currentVideoId 和播放器状态，保持 WebView 可复用
        }
        
        private fun updatePlayPauseButton() {
            if (isPlaying) {
                // 播放中，隐藏播放按钮
                binding.ivPlayPause.visibility = View.GONE
                hasUserPaused = false  // 重置暂停标记
            } else if (hasUserPaused) {
                // 只有用户主动暂停时才显示播放按钮
                binding.ivPlayPause.visibility = View.VISIBLE
                binding.ivPlayPause.setImageResource(R.drawable.ic_play)
            } else {
                // 初始状态或加载中，不显示播放按钮
                binding.ivPlayPause.visibility = View.GONE
            }
        }
        
        /**
         * 更新爱心状态 UI - payload 局部更新调用
         */
        fun updateHeartState(isFollowed: Boolean) {
            if (isFollowed) {
                binding.btnHeart.setImageResource(R.drawable.ic_heart_filled)  // 红色实心爱心
            } else {
                binding.btnHeart.setImageResource(R.drawable.ic_heart_outline)  // 白色描边爱心
            }
        }
        
        /**
         * 更新点赞状态 UI
         */
        fun updateLikeState(isLiked: Boolean) {
            if (isLiked) {
                binding.btnLike.setImageResource(R.drawable.ic_thumb_up_liked)  // 已点赞样式
            } else {
                binding.btnLike.setImageResource(R.drawable.ic_thumb_up)  // 未点赞样式
            }
        }
        
        /**
         * 完全释放播放器资源 - 仅在 Fragment 销毁时调用
         */
        fun release() {
            if (isReleased) return

            Log.d(TAG, "release - Fully releasing player")
            youTubePlayer?.pause()
            isReleased = true
            isAttached = false
            hasUserPaused = false
            binding.ivCover.animate().cancel()
            binding.youtubePlayerView.release()
            youTubePlayer = null
            currentVideoId = null
            pendingVideoId = null
            isPlayerReady = false
            isPlaying = false
            // 不重新初始化，因为 ViewHolder 即将被销毁
        }
        
        private fun formatCount(count: String?): String {
            if (count.isNullOrEmpty()) return "0"
            return try {
                val num = count.toLong()
                when {
                    num >= 1_000_000_000 -> String.format("%.1fB", num / 1_000_000_000.0)
                    num >= 1_000_000 -> String.format("%.1fM", num / 1_000_000.0)
                    num >= 1_000 -> String.format("%.1fK", num / 1_000.0)
                    else -> count
                }
            } catch (e: Exception) {
                count
            }
        }
    }
    
    private class VideoDiffCallback : DiffUtil.ItemCallback<YouTubeVideo>() {
        override fun areItemsTheSame(oldItem: YouTubeVideo, newItem: YouTubeVideo): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: YouTubeVideo, newItem: YouTubeVideo): Boolean {
            return oldItem == newItem
        }
    }
}
