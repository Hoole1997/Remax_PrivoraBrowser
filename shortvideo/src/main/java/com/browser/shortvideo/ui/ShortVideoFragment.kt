package com.browser.shortvideo.ui

import android.os.Bundle
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
import com.browser.shortvideo.databinding.FragmentShortVideoBinding
import kotlinx.coroutines.launch

/**
 * 短视频 Fragment
 * 使用 ViewPager2 实现类抖音/YouTube Shorts 的垂直滑动体验
 */
class ShortVideoFragment : Fragment() {
    
    private var _binding: FragmentShortVideoBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: ShortVideoAdapter
    
    // 是否已加载数据（懒加载标志）
    private var isDataLoaded = false
    // 是否已初始化视图
    private var isViewCreated = false

    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
    
    private val viewModel: ShortVideoViewModel by activityViewModels<ShortVideoViewModel>()
    
    companion object {

        
        /**
         * 创建 Fragment 实例
         * @param apiKey YouTube Data API Key
         * @param regionCode 地区代码，默认 "US"
         */
        fun newInstance(): ShortVideoFragment {
            return ShortVideoFragment().apply {
                arguments = Bundle().apply {

                }
            }
        }
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
        
        setupAdapter()
        setupViewPager()
        observeViewModel()
        viewModel.isHostSelected.observe(viewLifecycleOwner) { isSelected ->
            adapter.setHostSelected(isSelected)
        }
        
        // 不在这里加载数据，等待 onResume 时懒加载
    }
    
    override fun onResume() {
        super.onResume()
        
        // 懒加载：首次进入时加载数据
        if (!isDataLoaded && isViewCreated) {
            viewModel.loadVideos()
            isDataLoaded = true
        }
        
        if (isViewCreated) {
            val currentPosition = binding.viewPager.currentItem
            adapter.selectPosition(currentPosition)
            adapter.setContentAvailable(adapter.currentList.getOrNull(currentPosition) != null)
            adapter.setFeedResumed(true)
        }
    }
    
    override fun onPause() {
        if (isViewCreated) {
            adapter.setFeedResumed(false)
        }
        super.onPause()
    }
    
    private fun setupAdapter() {
        adapter = ShortVideoAdapter(
            onLikeClick = { video ->

            },
            onMoreClick = { video ->

            }
        )
    }
    
    private fun setupViewPager() {
        binding.viewPager.apply {
            adapter = this@ShortVideoFragment.adapter
            orientation = ViewPager2.ORIENTATION_VERTICAL
            offscreenPageLimit = 2  // 预加载前后各2个
            
            val callback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    this@ShortVideoFragment.adapter.selectPosition(position)
                    
                    // 接近底部时加载更多
                    val itemCount = this@ShortVideoFragment.adapter.itemCount
                    if (position >= itemCount - 3) {
                        viewModel.loadMore()
                    }
                }
            }
            pageChangeCallback = callback
            registerOnPageChangeCallback(callback)
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
                            adapter.setContentAvailable(false)
                            adapter.submitList(state.videos) {
                                if (!isViewCreated || _binding == null) return@submitList
                                val selectedPosition = if (state.videos.isEmpty()) {
                                    ShortVideoPlaybackState.NO_POSITION
                                } else {
                                    binding.viewPager.currentItem.coerceAtMost(state.videos.lastIndex)
                                }
                                adapter.selectPosition(selectedPosition)
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
    }
    
    private fun showLoading() {
        adapter.setContentAvailable(false)
        binding.viewPager.isVisible = false
    }
    
    private fun showContent(hasData: Boolean) {
        if (!hasData) {
            adapter.selectPosition(ShortVideoPlaybackState.NO_POSITION)
            adapter.setContentAvailable(false)
        }
        binding.viewPager.isVisible = hasData
    }
    
    private fun showError() {
        adapter.setContentAvailable(false)
        binding.viewPager.isVisible = false
    }
    
    override fun onDestroyView() {
        isViewCreated = false
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
