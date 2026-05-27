package com.example.browser.ui.tabs

import android.content.Intent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import com.example.browser.R
import com.example.browser.base.BaseFragment
import com.example.browser.components
import com.example.browser.databinding.FragmentBrowserTabsBinding
import com.example.browser.ui.MainActivity
import com.example.browser.ui.web.WebActivity
import com.example.browser.view.ConfirmDialog
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.selector.privateTabs
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import net.corekit.core.report.ReportDataManager

/**
 * 浏览器标签页管理 Fragment - 使用 ViewPager 管理普通和无痕标签
 * 可复用的标签页管理组件
 *
 * 功能：
 * 1. 使用 ViewPager 分别管理普通标签和无痕标签，避免切换时的撕裂感
 * 2. 支持在普通模式和无痕模式之间切换
 * 3. 支持点击标签页切换到对应页面
 * 4. 支持关闭标签页
 * 5. 支持新建标签页
 * 6. Grid 布局显示（一行2个，类似 Chrome）
 */
class BrowserTabsFragment : BaseFragment<FragmentBrowserTabsBinding, BrowserTabsModel>() {

    companion object {
        private const val TAG = "BrowserTabsFragment"

        /**
         * 创建 Fragment 实例
         */
        fun newInstance(): BrowserTabsFragment {
            return BrowserTabsFragment()
        }
    }

    // ViewPager 适配器
    private lateinit var pagerAdapter: TabsPagerAdapter

    // 标签页选择回调（可选，用于嵌入式场景）
    private var onTabSelectedListener: ((TabSessionState) -> Unit)? = null

    // 新建标签页回调（可选，用于嵌入式场景）
    private var onNewTabListener: (() -> Unit)? = null

    override fun initBinding(): FragmentBrowserTabsBinding {
        return FragmentBrowserTabsBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): BrowserTabsModel {
        return activityViewModels<BrowserTabsModel>().value
    }

    override fun initView() {
        binding?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it.root) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
                insets
            }
        }
    }

    override fun lazyLoad() {
        super.lazyLoad()
        setupViewPager()
        setupSegmentedTabs()
        setupBottomToolbar()
    }

    /**
     * 设置 ViewPager
     */
    private fun setupViewPager() {
        pagerAdapter = TabsPagerAdapter(childFragmentManager) { fragment ->
            fragment.onTabClick = { tab ->
                selectTabAndReturn(tab)
            }
            fragment.onTabClose = { tab ->
                closeTab(tab)
            }
        }
        
        binding?.viewPager?.apply {
            adapter = pagerAdapter
            offscreenPageLimit = 1 // 预加载相邻页面
            
            // 监听 ViewPager 滑动，同步更新顶部 Tab
            addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    // 不需要处理
                }

                override fun onPageSelected(position: Int) {
                    // 同步更新顶部 SegmentedTabView 的选中状态
                    binding?.segmentedTabs?.setSelectedIndex(position)
                }

                override fun onPageScrollStateChanged(state: Int) {
                    // 不需要处理
                }
            })
        }
    }

    /**
     * 设置顶部分段控件
     */
    private fun setupSegmentedTabs() {
        val titles = listOf(
            getString(R.string.normal_tabs),
            getString(R.string.private_tabs)
        )
        binding?.segmentedTabs?.apply {
            setItems(titles, 0)
            setOnTabSelectedListener { index ->
                binding?.viewPager?.currentItem = index
                if (index == 1) {
                    ReportDataManager.reportData("Incognito_Click",mapOf())
                }
            }
        }
    }

    /**
     * 设置顶部工具栏
     */
    private fun setupBottomToolbar() {
        binding?.apply {
            // 右下角悬浮新建标签页按钮
            ivAdd.setOnClickListener {
                if (onNewTabListener != null) {
                    onNewTabListener?.invoke()
                } else {
                    createNewTab()
                }
            }
            ivDelete.setOnClickListener {
                handleDeleteTabs()
            }
            ivBack.setOnClickListener {
                handleBackNavigation()
            }
        }
    }

    private fun handleDeleteTabs() {
        val state = activity?.components?.store?.state ?: return
        val currentPage = binding?.viewPager?.currentItem ?: 0
        val isPrivate = currentPage == TabsPagerAdapter.TAB_PRIVATE
        val tabs = if (isPrivate) state.privateTabs else state.normalTabs
        if (tabs.isEmpty()) {
            return
        }
        ConfirmDialog.show(
            supportFragmentManager = childFragmentManager,
            title = getString(R.string.tabs_clear_title),
            content = getString(R.string.tabs_clear_message),
            button = getString(R.string.tabs_clear_confirm)
        ).apply {
            setOnConfirmListener {
                closeAllTabs()
            }
        }
    }

    private fun handleBackNavigation() {
        when (val hostActivity = activity) {
            is MainActivity -> {
                // MainActivity: switch ViewPager to Home tab (index 0)
                hostActivity.binding.viewPager.currentItem = 0
            }
            is BrowserTabsActivity -> {
                hostActivity.finish()
            }
            else -> activity?.onBackPressed()
        }
    }


    /**
     * 选中标签页并返回浏览器
     */
    private fun selectTabAndReturn(tab: TabSessionState) {
        if (onTabSelectedListener != null) {
            // 如果设置了回调，使用回调
            onTabSelectedListener?.invoke(tab)
        } else {
            ReportDataManager.reportData("Tabs_Click",mapOf())
            // 默认行为：选中该标签页并跳转到 WebActivity
            activity?.components?.tabsUseCases?.selectTab(tab.id)

            val intent = Intent(activity, WebActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)

            // 只在 BrowserTabsActivity 中才关闭 Activity，在 MainActivity 中不关闭
            if (activity is BrowserTabsActivity) {
                activity?.finish()
            }
        }
    }

    /**
     * 关闭标签页
     */
    private fun closeTab(tab: TabSessionState) {
        val state = activity?.components?.store?.state ?: return
        val isPrivate = tab.content.private

        // 获取当前模式的所有标签页
        val tabs = if (isPrivate) {
            state.privateTabs
        } else {
            state.normalTabs
        }

        // 如果关闭的是当前选中的标签页，需要选中另一个标签页
        if (tab.id == state.selectedTabId && tabs.size > 1) {
            val tabIndex = tabs.indexOfFirst { it.id == tab.id }
            val nextTab = if (tabIndex == 0) tabs[1] else tabs[tabIndex - 1]
            activity?.components?.tabsUseCases?.selectTab(nextTab.id)
        }

        // 删除标签页
        activity?.components?.tabsUseCases?.removeTab(tab.id)

        // 如果所有标签页都关闭了，自动切换到有标签的模式
        if (tabs.size == 1 && isPrivate) {
            // 如果关闭的是最后一个无痕标签页，切换到普通模式
            val normalTabs = state.normalTabs
            if (normalTabs.isNotEmpty()) {
                activity?.components?.tabsUseCases?.selectTab(normalTabs.last().id)
                binding?.viewPager?.currentItem = TabsPagerAdapter.TAB_NORMAL
                binding?.segmentedTabs?.setSelectedIndex(TabsPagerAdapter.TAB_NORMAL)
            }
        }
    }

    /**
     * 创建新标签页
     */
    private fun createNewTab() {
        // 根据当前 ViewPager 页面判断模式
        val currentPage = binding?.viewPager?.currentItem ?: 0
        val isPrivate = currentPage == TabsPagerAdapter.TAB_PRIVATE

        // 获取当前选中的搜索引擎的首页 URL
        val searchEngine = activity?.components?.store?.state?.search?.selectedOrDefaultSearchEngine
        val defaultUrl = searchEngine?.let { engine ->
            // 从搜索引擎的 resultUrls 提取主域名作为首页
            // 例如：https://www.google.com/search?q={searchTerms} -> https://www.google.com
            engine.resultUrls.firstOrNull()?.let { url ->
                val uri = android.net.Uri.parse(url)
                "${uri.scheme}://${uri.host}"
            }
        } ?: "https://www.google.com" // 后备方案

        // 创建新标签页
        activity?.components?.tabsUseCases?.addTab(
            url = defaultUrl,
            selectTab = true,
            private = isPrivate
        )

        // 跳转到 WebActivity
        val intent = Intent(activity, WebActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)

        // 只在 BrowserTabsActivity 中才关闭 Activity，在 MainActivity 中不关闭
        if (activity is BrowserTabsActivity) {
            activity?.finish()
        }
    }

    /**
     * 关闭当前模式下的所有标签页
     * 可从外部调用（例如从 Activity 的菜单）
     */
    fun closeAllTabs() {
        val state = activity?.components?.store?.state ?: return
        val currentPage = binding?.viewPager?.currentItem ?: 0
        val isPrivate = currentPage == TabsPagerAdapter.TAB_PRIVATE

        // 获取当前模式的所有标签页
        val tabs = if (isPrivate) {
            state.privateTabs
        } else {
            state.normalTabs
        }

        // 删除所有标签页
        val tabIds = tabs.map { it.id }
        activity?.components?.tabsUseCases?.removeTabs(tabIds)
    }

    /**
     * 设置标签页选择监听器（用于嵌入式场景）
     */
    fun setOnTabSelectedListener(listener: (TabSessionState) -> Unit) {
        this.onTabSelectedListener = listener
    }

    /**
     * 设置新建标签页监听器（用于嵌入式场景）
     */
    fun setOnNewTabListener(listener: () -> Unit) {
        this.onNewTabListener = listener
    }

    override fun onResume() {
        super.onResume()

        // 每次恢复时同步当前选中的标签页模式
        val state = activity?.components?.store?.state ?: return
        state.selectedTabId?.let { selectedId ->
            // 找到选中的标签页
            val selectedTab = state.tabs.find { it.id == selectedId }
            selectedTab?.let { tab ->
                // 根据选中标签页的隐私模式来切换当前显示模式
                val shouldBePrivate = tab.content.private
                val targetPage = if (shouldBePrivate) TabsPagerAdapter.TAB_PRIVATE else TabsPagerAdapter.TAB_NORMAL
                
                if (binding?.viewPager?.currentItem != targetPage) {
                    binding?.viewPager?.currentItem = targetPage
                    binding?.segmentedTabs?.setSelectedIndex(targetPage)
                }
            }
        }
    }
}
