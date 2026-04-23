package com.browser.shortvideo.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.ext.AdShowExt
import com.browser.shortvideo.ad.VideoAdManager
import com.browser.shortvideo.data.FollowedVideoStorage
import com.browser.shortvideo.R
import com.browser.shortvideo.databinding.FragmentShortVideoBinding
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import net.corekit.core.report.ReportDataManager

/**
 * 推荐视频 Fragment (Popular)
 * 使用 ViewPager2 实现类抖音/YouTube Shorts 的垂直滑动体验
 */
class PopularFragment : Fragment() {
    
    companion object {
        private const val TAG = "PopularFragment"
        
        /**
         * 创建 Fragment 实例
         * @param apiKey YouTube Data API Key
         * @param regionCode 地区代码，默认 "US"
         */
        fun newInstance(): PopularFragment {
            return PopularFragment().apply {
                arguments = Bundle().apply {

                }
            }
        }
    }
    
    private var _binding: FragmentShortVideoBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: ShortVideoAdapter
    private lateinit var followedStorage: FollowedVideoStorage
    
    // 是否已加载数据（懒加载标志）
    private var isDataLoaded = false
    // 是否已初始化视图
    private var isViewCreated = false

    
    private val viewModel by activityViewModels<ShortVideoViewModel>()
    
    // 广告管理器 - 从父 Fragment 获取
    private val videoAdManager: VideoAdManager?
        get() {
            val parent = parentFragment
            Log.d(TAG, "Getting videoAdManager, parentFragment: $parent, type: ${parent?.javaClass?.simpleName}")
            return (parent as? ShortVideoContainerFragment)?.videoAdManager
        }
    
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
        setupRetryButton()
        observeViewModel()
        
        // 不在这里加载数据，等待 onResume 时懒加载
    }
    
    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            isDataLoaded = false
            viewModel.loadVideos()
            isDataLoaded = true
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // 懒加载：首次进入时加载数据
        if (!isDataLoaded && isViewCreated) {
//            if (apiKey.isNotEmpty()) {
                viewModel.loadVideos()
                isDataLoaded = true
//            } else {
//                showError()
//            }
        }
        
        // 恢复播放当前视频
        if (isViewCreated && adapter.itemCount > 0) {
            val currentPosition = binding.viewPager.currentItem
            adapter.playAt(currentPosition)
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
                ReportDataManager.reportData("ShortLike",mapOf())
            },
            onHeartClick = { video ->
                // 切换关注状态并持久化
                val isFollowed = followedStorage.toggleFollow(video)
                adapter.updateFollowState(video.id, isFollowed)
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
            adapter = this@PopularFragment.adapter
            orientation = ViewPager2.ORIENTATION_VERTICAL
            offscreenPageLimit = 2  // 预加载前后各2个
            
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    
                    Log.d(TAG, "onPageSelected: position=$position")
                    
                    // 保存当前播放的视频 ID
                    val currentVideo = this@PopularFragment.adapter.currentList.getOrNull(position)
                    currentVideo?.let {
                        viewModel.saveCurrentVideoId(it.id)
                    }
                    
                    // 检查是否应该展示广告，如果展示广告则不播放视频
                    val showingAd = checkAndShowAd(position)
                    
                    if (!showingAd) {
                        // 使用 post 延迟执行，避免在 scroll callback 中修改数据
                        binding.viewPager.post {
                            // 暂停其他视频，播放当前视频
                            this@PopularFragment.adapter.pauseAllExcept(position)
                            this@PopularFragment.adapter.playAt(position)
                        }
                    }
                    
                    // 接近底部时加载更多
                    val itemCount = this@PopularFragment.adapter.itemCount
                    if (position >= itemCount - 3) {
                        viewModel.loadMore()
                    }
                    ReportDataManager.reportData("ShortVideoPage",mapOf())
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
        Log.d(TAG, "📺 Showing interstitial ad in video feed...")
        
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

    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ShortVideoViewModel.UiState.Loading -> {
                            showLoading()
                        }
                        is ShortVideoViewModel.UiState.Success -> {
                            showContent(state.videos.isNotEmpty())
                            adapter.submitList(state.videos)
                        }
                        is ShortVideoViewModel.UiState.Error -> {
                            showError()
                        }
                    }
                }
            }
        }
        viewModel.videoPause.observe(viewLifecycleOwner) {
            if (it) {
                adapter.pauseAll()
            }
        }
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
