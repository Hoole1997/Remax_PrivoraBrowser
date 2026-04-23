package com.browser.shortvideo.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
        viewModel.videoPause.observe(viewLifecycleOwner) {
            if (it) {
                adapter.pauseAll()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // 每次回到此页面时重新加载已关注列表
        if (isViewCreated) {
            loadFollowedVideos()
            
            // 恢复播放当前视频
            if (adapter.itemCount > 0) {
                val currentPosition = binding.viewPager.currentItem
                adapter.playAt(currentPosition)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // 暂停所有视频播放
        if (isViewCreated) {
            adapter.pauseAll()
        }
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
            
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    
                    // 检查是否应该展示广告，如果展示广告则不播放视频
                    val showingAd = checkAndShowAd(position)
                    
                    if (!showingAd) {
                        binding.viewPager.post {
                            this@FollowingFragment.adapter.pauseAllExcept(position)
                            this@FollowingFragment.adapter.playAt(position)
                        }
                    }
                }
            })
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
        
        // 暂停当前视频播放
        adapter.pauseAll()
        
        val resumeVideo = {
            // 广告关闭后播放指定位置的视频
            adapter.playAt(pendingPosition)
            
            // 标记广告已展示
            videoAdManager?.onAdShown(activity)
        }
        
        // 使用协程展示插屏广告
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = AdShowExt.showInterstitialAd(activity as androidx.fragment.app.FragmentActivity)) {
                is AdResult.Success -> {
                    Log.d(TAG, "✅ Interstitial ad displayed successfully")
                    resumeVideo.invoke()
                }
                is AdResult.Failure -> {
                    Log.w(TAG, "❌ Failed to display interstitial ad: ${result.error.message}")
                    resumeVideo.invoke()
                }
            }
        }
    }
    
    private fun loadFollowedVideos() {
        val videos = followedStorage.getFollowedVideos()
        adapter.submitList(videos)
        
        // 显示/隐藏状态
        showContent(videos.isNotEmpty())
    }
    
    private fun showLoading() {
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
        binding.viewPager.isVisible = hasData
        binding.layoutLoading.isVisible = false
        binding.layoutEmpty.isVisible = !hasData
        binding.layoutError.isVisible = false
    }
    
    private fun showError() {
        binding.viewPager.isVisible = false
        binding.layoutLoading.isVisible = false
        binding.layoutEmpty.isVisible = false
        binding.layoutError.isVisible = true
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

