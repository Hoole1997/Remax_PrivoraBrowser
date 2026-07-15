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

    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
    private var pendingAdPosition: Int? = null

    
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
        
        if (isViewCreated) {
            val currentPosition = binding.viewPager.currentItem
            val hasCurrentVideo = adapter.currentList.getOrNull(currentPosition) != null

            // 初次创建时 onPageSelected 可能发生在 STARTED，广告计数推迟到真正可见时处理。
            if (hasCurrentVideo && pendingAdPosition == currentPosition) {
                checkAndShowAd(currentPosition)
                pendingAdPosition = null
            } else if (!hasCurrentVideo) {
                pendingAdPosition = currentPosition
            }

            adapter.selectPosition(currentPosition)
            adapter.setContentAvailable(hasCurrentVideo)
            adapter.setFeedResumed(true)
        }
    }
    
    override fun onPause() {
        // 先关闭持续门禁，再进入父类生命周期，避免排队回调在暂停过程中恢复视频。
        if (isViewCreated) {
            adapter.setFeedResumed(false)
        }
        super.onPause()
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

            pendingAdPosition = currentItem
            val callback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    
                    Log.d(TAG, "onPageSelected: position=$position")
                    
                    // 保存当前播放的视频 ID
                    val currentVideo = this@PopularFragment.adapter.currentList.getOrNull(position)
                    currentVideo?.let {
                        viewModel.saveCurrentVideoId(it.id)
                    }
                    
                    if (
                        currentVideo != null &&
                        this@PopularFragment.adapter.isPlaybackAllowed &&
                        viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    ) {
                        // showInterstitialAd 会先关闭广告门禁，再切换选中项，不会短暂串音。
                        checkAndShowAd(position)
                        pendingAdPosition = null
                    } else {
                        // 缓存页只记录选择，等真正 RESUMED 后再统计曝光和决定播放。
                        pendingAdPosition = position
                    }

                    // 不再使用 View.post；selectPosition 不修改 RecyclerView 数据，可安全同步收敛。
                    this@PopularFragment.adapter.selectPosition(position)

                    // 接近底部时加载更多
                    val itemCount = this@PopularFragment.adapter.itemCount
                    if (position >= itemCount - 3) {
                        viewModel.loadMore()
                    }
                    ReportDataManager.reportData("ShortVideoPage",mapOf())
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
        Log.d(TAG, "📺 Showing interstitial ad in video feed...")
        
        val activity = activity ?: run {
            Log.w(TAG, "Activity not available")
            return
        }
        
        // 广告门禁与 Feed 生命周期独立；广告结束不会把已经隐藏的页面重新激活。
        adapter.onAdStarted()
        adapter.selectPosition(pendingPosition)
        
        // 使用协程展示插屏广告
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val result = AdShowExt.showInterstitialAd(activity)) {
                    is AdResult.Success -> {
                        Log.d(TAG, "✅ Interstitial ad displayed successfully")
                    }
                    is AdResult.Failure -> {
                        Log.w(TAG, "❌ Failed to display interstitial ad: ${result.error.message}")
                    }
                }
                // 保持现有广告成功/失败后都更新间隔的业务语义。
                videoAdManager?.onAdShown(activity)
            } finally {
                // 这里只清广告条件，最终能否播放仍由 Feed 的 RESUMED 状态决定。
                adapter.onAdFinished()
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
                            val currentPosition = binding.viewPager.currentItem
                            val currentVideoUnchanged =
                                adapter.currentList.getOrNull(currentPosition)?.id ==
                                    state.videos.getOrNull(currentPosition)?.id
                            if (!currentVideoUnchanged) {
                                // 替换列表时先关闭门禁；单纯分页追加不打断当前视频。
                                adapter.setContentAvailable(false)
                            }
                            adapter.submitList(state.videos) {
                                if (!isViewCreated || _binding == null) return@submitList

                                val selectedPosition = if (state.videos.isEmpty()) {
                                    ShortVideoPlaybackState.NO_POSITION
                                } else {
                                    binding.viewPager.currentItem.coerceAtMost(state.videos.lastIndex)
                                }
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
                                // 广告门禁先落状态，随后开放内容也不会越过广告直接播放。
                                adapter.setContentAvailable(state.videos.isNotEmpty())
                            }
                        }
                        is ShortVideoViewModel.UiState.Error -> {
                            showError()
                        }
                    }
                }
            }
        }
        viewModel.isHostSelected.observe(viewLifecycleOwner) { isSelected ->
            adapter.setHostSelected(isSelected)
        }
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
            // WebView 必须先离开 ViewPager/RecyclerView，再做终止销毁。
            binding.viewPager.adapter = null
            adapter.releaseAll()
        }

        _binding = null
        super.onDestroyView()
    }
}
