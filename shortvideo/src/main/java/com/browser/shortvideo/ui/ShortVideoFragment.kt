package com.browser.shortvideo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.blankj.utilcode.util.ActivityUtils
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
        
        // 不在这里加载数据，等待 onResume 时懒加载
    }
    
    override fun onResume() {
        super.onResume()
        
        // 懒加载：首次进入时加载数据
        if (!isDataLoaded && isViewCreated) {
            viewModel.loadVideos()
            isDataLoaded = true
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
            
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    // 使用 post 延迟执行，避免在 scroll callback 中修改数据
                    binding.viewPager.post {
                        // 暂停其他视频，播放当前视频
                        this@ShortVideoFragment.adapter.pauseAllExcept(position)
                        this@ShortVideoFragment.adapter.playAt(position)
                    }
                    
                    // 接近底部时加载更多
                    val itemCount = this@ShortVideoFragment.adapter.itemCount
                    if (position >= itemCount - 3) {
                        viewModel.loadMore()
                    }
                }
            })
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
    }
    
    private fun showLoading() {
        binding.viewPager.isVisible = false
    }
    
    private fun showContent(hasData: Boolean) {
        binding.viewPager.isVisible = hasData
    }
    
    private fun showError() {
        binding.viewPager.isVisible = false
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
