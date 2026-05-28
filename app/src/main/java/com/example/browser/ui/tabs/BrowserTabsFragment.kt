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
 * 状态同步策略：
 * - 当前所处模式（普通 / 无痕）作为唯一真相源放在 [BrowserTabsModel.currentMode]。
 * - 顶部分段控件 SegmentedTabView、底部 ViewPager 都从该 LiveData 派生 UI 状态，
 *   两者互相不直接驱动彼此，避免 view 重建时 ViewPager state 恢复与
 *   SegmentedTabView 默认值竞争导致的「指示器 / 内容」错位。
 * - 用户手势（点击分段、滑动 ViewPager）只调用 [BrowserTabsModel.setMode]，
 *   再由观察者统一回写两边 UI。
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
        // 顺序很重要：
        // 1) 先建好分段控件的 tab，但不要硬把选中重置为 0；
        // 2) 再设置 ViewPager。监听器要在 setAdapter 之前注册，
        //    避免 ViewPager state 恢复阶段触发的 onPageSelected 事件被吞；
        // 3) 接着注册 LiveData 观察者，由它统一驱动两侧 UI；
        // 4) 最后兜底初始化 mode（仅首次进入）。
        setupSegmentedTabs()
        setupViewPager()
        setupModeObserver()
        setupBottomToolbar()
        initializeModeIfNeeded()
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
            // 先注册监听器，再 setAdapter。view 重建后 setAdapter 会消费
            // ViewPager 内保存的 mRestoredCurItem 触发一次 onPageSelected，
            // 必须保证那个事件能被我们捕获到，统一回写到 LiveData。
            addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    // 不需要处理
                }

                override fun onPageSelected(position: Int) {
                    viewModel.setMode(position)
                }

                override fun onPageScrollStateChanged(state: Int) {
                    // 不需要处理
                }
            })
            adapter = pagerAdapter
            offscreenPageLimit = 1 // 预加载相邻页面
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
            // 视图重建时不要把选中硬拉回 0，等 LiveData 观察者派发权威值。
            setItemsKeepingSelection(titles)
            setOnTabSelectedListener { index ->
                viewModel.setMode(index)
                if (index == TabsPagerAdapter.TAB_PRIVATE) {
                    ReportDataManager.reportData("Incognito_Click", mapOf())
                }
            }
        }
    }

    /**
     * 观察当前模式 LiveData，单向同步两侧 UI。
     */
    private fun setupModeObserver() {
        viewModel.currentMode.observe(viewLifecycleOwner) { mode ->
            applyMode(mode)
        }
    }

    private fun applyMode(mode: Int) {
        binding?.viewPager?.let { pager ->
            if (pager.currentItem != mode) {
                pager.setCurrentItem(mode, false)
            }
        }
        binding?.segmentedTabs?.setSelectedIndex(mode)
    }

    /**
     * 首次进入时根据 store 中已选标签的隐私属性决定默认模式。
     * 后续 view 重建只会复用 Activity 作用域 ViewModel 内已有值，不再走这条分支。
     */
    private fun initializeModeIfNeeded() {
        if (viewModel.currentMode.value != null) return
        viewModel.setMode(resolveModeFromSelectedTab())
    }

    private fun resolveModeFromSelectedTab(): Int {
        val state = activity?.components?.store?.state ?: return TabsPagerAdapter.TAB_NORMAL
        val selectedTab = state.selectedTabId?.let { id -> state.tabs.find { it.id == id } }
        return if (selectedTab?.content?.private == true) {
            TabsPagerAdapter.TAB_PRIVATE
        } else {
            TabsPagerAdapter.TAB_NORMAL
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
        val isPrivate = currentModeOrDefault() == TabsPagerAdapter.TAB_PRIVATE
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
                // 通过 LiveData 统一驱动 UI，避免直接写 ViewPager 跟分段控件不同步
                viewModel.setMode(TabsPagerAdapter.TAB_NORMAL)
            }
        }
    }

    /**
     * 创建新标签页
     */
    private fun createNewTab() {
        // 根据当前 LiveData 中的模式判断（避免读 ViewPager 中间态）
        val isPrivate = currentModeOrDefault() == TabsPagerAdapter.TAB_PRIVATE

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
        val isPrivate = currentModeOrDefault() == TabsPagerAdapter.TAB_PRIVATE

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

    private fun currentModeOrDefault(): Int {
        return viewModel.currentMode.value ?: TabsPagerAdapter.TAB_NORMAL
    }

    override fun onResume() {
        super.onResume()
        // 不再无条件用 store.selectedTab 覆盖当前段。
        // 视图重建时 LiveData 已通过 SavedStateHandle 恢复到正确值，
        // 观察者会拉齐两侧 UI；强行用 store.selectedTab 同步会把
        // 「用户在分段间切换但没点 tab 退出」的选择粗暴拉回，反而错乱。
    }
}
