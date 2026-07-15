package com.browser.shortvideo.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.ext.AdShowExt
import com.browser.shortvideo.ad.VideoAdManager
import com.browser.shortvideo.data.FollowedVideoStorage
import com.browser.shortvideo.R
import com.browser.shortvideo.databinding.FragmentShortVideoBinding
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

/**
 * 已关注视频 Fragment (Following)
 * 显示用户已关注的视频列表
 */
class FollowingFragment : Fragment() {
    
    companion object {
        private const val TAG = "FollowingFragment"
        
        /**
         * 创建 Fragment 实例
         */
        fun newInstance(): FollowingFragment {
            return FollowingFragment().apply {
                arguments = Bundle().apply {

                }
            }
        }
    }
    
    private var _binding: FragmentShortVideoBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: ShortVideoAdapter
    private lateinit var followedStorage: FollowedVideoStorage
    
    // 是否已初始化视图
    private var isViewCreated = false

    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
    private var pendingAdPosition: Int? = null
    
    // 广告管理器 - 从父 Fragment 获取
    private val videoAdManager: VideoAdManager?
        get() = (parentFragment as? ShortVideoContainerFragment)?.videoAdManager
    private val viewModel by activityViewModels<ShortVideoViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShortVideoBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        isViewCreated = true
        followedStorage = FollowedVideoStorage.getInstance(requireContext())
        
        setupAdapter()
        setupViewPager()
        viewModel.isHostSelected.observe(viewLifecycleOwner) { isSelected ->
            adapter.setHostSelected(isSelected)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // 每次回到此页面时重新加载已关注列表
        if (isViewCreated) {
            loadFollowedVideos()
            adapter.setFeedResumed(true)
        }
    }
    
    override fun onPause() {
        // 先关闭持续门禁，确保后续广告和播放器回调只能保持暂停。
        if (isViewCreated) {
            adapter.setFeedResumed(false)
        }
        super.onPause()
    }
    
    private fun setupAdapter() {
        adapter = ShortVideoAdapter(
            onLikeClick = { video ->
                // 点赞操作（可选实现）
            },
            onHeartClick = { video ->
                // 取消关注
                followedStorage.removeVideo(video.id)
                // 重新加载列表
                loadFollowedVideos()
            },
            onMoreClick = { video ->
                // 更多操作
            },
            isFollowedChecker = { videoId ->
                followedStorage.isFollowed(videoId)
            }
        )
    }
    
    private fun setupViewPager() {
        binding.viewPager.apply {
            adapter = this@FollowingFragment.adapter
            orientation = ViewPager2.ORIENTATION_VERTICAL
            offscreenPageLimit = 2

            pendingAdPosition = currentItem
            val callback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)

                    val hasVideo = this@FollowingFragment.adapter.currentList.getOrNull(position) != null

                    if (
                        hasVideo &&
                        this@FollowingFragment.adapter.isPlaybackAllowed &&
                        viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    ) {
                        checkAndShowAd(position)
                        pendingAdPosition = null
                    } else {
                        pendingAdPosition = position
                    }

                    // 选择和播放许可分离，缓存页可以更新位置但绝不会启动播放器。
                    this@FollowingFragment.adapter.selectPosition(position)
                }
            }
            pageChangeCallback = callback
            registerOnPageChangeCallback(callback)
        }
    }
    
    /**
     * 检查并展示广告
     * @param position 当前视频位置
     * @return 是否展示了广告
     */
    private fun checkAndShowAd(position: Int): Boolean {
        val adManager = videoAdManager ?: run {
            Log.w(TAG, "VideoAdManager not available")
            return false
        }
        
        // 检查是否应该展示广告
        val shouldShowAd = adManager.onVideoViewed(activity ?: return false, position)
        
        if (shouldShowAd) {
            Log.d(TAG, "🎯 Should show ad at position $position")
            showInterstitialAd(position)
            return true
        }
        return false
    }
    
    /**
     * 展示插屏广告
     * @param pendingPosition 广告关闭后要播放的视频位置
     */
    private fun showInterstitialAd(pendingPosition: Int) {
        Log.d(TAG, "📺 Showing interstitial ad in following feed...")
        
        val activity = activity ?: run {
            Log.w(TAG, "Activity not available")
            return
        }
        
        // 广告只关闭自身条件，不能在回调时覆盖 Fragment 已暂停的生命周期条件。
        adapter.onAdStarted()
        adapter.selectPosition(pendingPosition)
        
        // 使用协程展示插屏广告
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val result = AdShowExt.showInterstitialAd(activity, position = "IV_Shorts")) {
                    is AdResult.Success -> {
                        Log.d(TAG, "✅ Interstitial ad displayed successfully")
                    }
                    is AdResult.Failure -> {
                        Log.w(TAG, "❌ Failed to display interstitial ad: ${result.error.message}")
                    }
                }
                // 保持原有广告成功/失败后都更新间隔的业务语义。
                videoAdManager?.onAdShown(activity)
            } finally {
                adapter.onAdFinished()
            }
        }
    }
    
    private fun loadFollowedVideos() {
        val videos = followedStorage.getFollowedVideos()
        val selectedPosition = if (videos.isEmpty()) {
            ShortVideoPlaybackState.NO_POSITION
        } else {
            binding.viewPager.currentItem.coerceAtMost(videos.lastIndex)
        }

        // 先关闭内容门禁并收敛位置，Diff 较慢时也不会继续播放已删除的视频。
        adapter.setContentAvailable(false)
        adapter.selectPosition(selectedPosition)

        if (
            selectedPosition >= 0 &&
            pendingAdPosition == selectedPosition &&
            viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            checkAndShowAd(selectedPosition)
            pendingAdPosition = null
        }

        adapter.submitList(videos) {
            if (!isViewCreated || _binding == null) return@submitList

            if (
                selectedPosition >= 0 &&
                binding.viewPager.currentItem != selectedPosition
            ) {
                binding.viewPager.setCurrentItem(selectedPosition, false)
            }
            adapter.selectPosition(selectedPosition)

            if (
                selectedPosition >= 0 &&
                pendingAdPosition == selectedPosition &&
                viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                checkAndShowAd(selectedPosition)
                pendingAdPosition = null
            }
            adapter.setContentAvailable(videos.isNotEmpty())
        }
        
        // 显示/隐藏状态
        showContent(videos.isNotEmpty())
    }
    
    private fun showLoading() {
        adapter.setContentAvailable(false)
        binding.viewPager.isVisible = false
        binding.layoutLoading.isVisible = true
        binding.layoutEmpty.isVisible = false
        binding.layoutError.isVisible = false
        
        // 使用 Glide 加载 GIF
        Glide.with(this)
            .asGif()
            .load(R.raw.short_api_loading)
            .into(binding.ivLoading)
    }
    
    private fun showContent(hasData: Boolean) {
        if (!hasData) {
            adapter.selectPosition(ShortVideoPlaybackState.NO_POSITION)
            adapter.setContentAvailable(false)
        }
        binding.viewPager.isVisible = hasData
        binding.layoutLoading.isVisible = false
        binding.layoutEmpty.isVisible = !hasData
        binding.layoutError.isVisible = false
    }
    
    private fun showError() {
        adapter.setContentAvailable(false)
        binding.viewPager.isVisible = false
        binding.layoutLoading.isVisible = false
        binding.layoutEmpty.isVisible = false
        binding.layoutError.isVisible = true
    }
    
    override fun onDestroyView() {
        isViewCreated = false
        pendingAdPosition = null

        pageChangeCallback?.let(binding.viewPager::unregisterOnPageChangeCallback)
        pageChangeCallback = null

        if (::adapter.isInitialized) {
            adapter.setFeedResumed(false)
            binding.viewPager.adapter = null
            adapter.releaseAll()
        }

        _binding = null
        super.onDestroyView()
    }
}
