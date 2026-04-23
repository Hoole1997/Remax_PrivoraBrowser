package com.browser.shortvideo.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.browser.shortvideo.ad.VideoAdConfig
import com.browser.shortvideo.ad.VideoAdManager
import com.browser.shortvideo.databinding.FragmentShortVideoContainerBinding
import kotlinx.coroutines.launch
import net.corekit.core.utils.ConfigRemoteManager

/**
 * 短视频容器 Fragment
 * 包含 Popular 和 Following 两个 Tab
 */
class ShortVideoContainerFragment : Fragment() {
    
    companion object {
        private const val TAG = "ShortVideoContainer"
        private const val ARG_API_KEY = "api_key"
        private const val ARG_REGION_CODE = "region_code"

        private const val KEY_AD_SWITCH = "video_AD_Switch"
        private const val KEY_AD_NUMBER = "Video_AD_number"
        private const val KEY_AD_TIME = "Video_AD_time"

        /**
         * 创建 Fragment 实例
         * @param apiKey YouTube Data API Key
         * @param regionCode 地区代码，默认 "US"
         */
        fun newInstance(): ShortVideoContainerFragment {
            return ShortVideoContainerFragment().apply {
                arguments = Bundle().apply {

                }
            }
        }
    }
    
    private var _binding: FragmentShortVideoContainerBinding? = null
    private val binding get() = _binding!!
    
    // 广告管理器 - 供子 Fragment 使用
    val videoAdManager: VideoAdManager by lazy { VideoAdManager() }
    private val viewModel by activityViewModels<ShortVideoViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShortVideoContainerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it.root) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
                insets
            }
        }
        // 初始化广告配置
        initAdConfig()
        
        setupViewPager()
        setupTabs()
    }
    
    /**
     * 初始化广告配置 - 从远程配置获取参数
     */
    private fun initAdConfig() {
        Log.d(TAG, "initAdConfig() called, starting to load ad config...")
        lifecycleScope.launch {
            val config = loadAdConfig()
            videoAdManager.updateConfig(config)
            
            Log.d(TAG, "Ad config initialized: enabled=${config.isEnabled}, " +
                    "minVideos=${config.minVideosBetweenAds}, minSeconds=${config.minSecondsBetweenAds}")
        }
    }
    
    /**
     * 从远程配置加载广告参数
     */
    private suspend fun loadAdConfig(): VideoAdConfig {
        val videoAdSwitch = ConfigRemoteManager.getInt(
            KEY_AD_SWITCH,
            VideoAdConfig.DEFAULT_AD_SWITCH
        ) ?: VideoAdConfig.DEFAULT_AD_SWITCH
        
        val videoAdNumber = ConfigRemoteManager.getInt(
            KEY_AD_NUMBER,
            VideoAdConfig.DEFAULT_AD_NUMBER
        ) ?: VideoAdConfig.DEFAULT_AD_NUMBER
        
        val videoAdTime = ConfigRemoteManager.getInt(
            KEY_AD_TIME,
            VideoAdConfig.DEFAULT_AD_TIME
        ) ?: VideoAdConfig.DEFAULT_AD_TIME
        
        Log.d(TAG, "Remote config loaded: switch=$videoAdSwitch, number=$videoAdNumber, time=$videoAdTime")
        
        return VideoAdConfig(
            isEnabled = videoAdSwitch == 1,
            minVideosBetweenAds = videoAdNumber,
            minSecondsBetweenAds = videoAdTime
        )
    }
    
    private fun setupViewPager() {
        binding.viewPager.apply {
            offscreenPageLimit = 2
            adapter = TabPagerAdapter(this@ShortVideoContainerFragment)
            
            // 监听页面切换，更新 Tab 样式
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateTabStyle(position)
                }
            })
        }
    }
    
    private fun setupTabs() {
        binding.tabPopular.setOnClickListener {
            binding.viewPager.currentItem = 0
        }
        
        binding.tabFollowing.setOnClickListener {
            binding.viewPager.currentItem = 1
        }
        
        // 初始化 Tab 样式
        updateTabStyle(0)
    }
    
    private fun updateTabStyle(selectedPosition: Int) {
        val selectedColor = 0xFFFFFFFF.toInt()  // 白色
        val unselectedColor = 0x80FFFFFF.toInt()  // 半透明白色
        
        // Popular Tab
        binding.tabPopular.apply {
            setTextColor(if (selectedPosition == 0) selectedColor else unselectedColor)
            setTypeface(null, if (selectedPosition == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        
        // Following Tab
        binding.tabFollowing.apply {
            setTextColor(if (selectedPosition == 1) selectedColor else unselectedColor)
            setTypeface(null, if (selectedPosition == 1) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    /**
     * ViewPager2 Adapter
     */
    private inner class TabPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> PopularFragment.newInstance()
                1 -> FollowingFragment.newInstance()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }
}
