package com.example.browser.ui.bookmark

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.TextPaint
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.blankj.utilcode.util.ActivityUtils
import com.browser.common.loadInterstitial
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.base.BaseModel
import com.example.browser.databinding.ActivityBookmarkBinding
import com.example.browser.ui.history.HistoryFragment
import kotlinx.coroutines.launch
import net.corekit.core.report.ReportDataManager
import net.corekit.core.utils.ConfigRemoteManager
import kotlin.math.ceil

class BookmarkActivity : BaseActivity<ActivityBookmarkBinding, BookmarkActivity.ActivityModel>() {

    private val bookmarkFragment = BookmarkFragment()
    private val historyFragment = HistoryFragment()
    private lateinit var pagerAdapter: BookmarkPagerAdapter
    private val defaultTab: Int by lazy {
        intent?.getIntExtra(EXTRA_DEFAULT_TAB, TAB_BOOKMARK) ?: TAB_BOOKMARK
    }
    private val createFolderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val pathText =
                    result.data?.getStringExtra(BookmarkFolderEditActivity.EXTRA_CREATED_FOLDER_PATH)
                supportFragmentManager.setFragmentResult(
                    BookmarkFragment.REQUEST_EXTERNAL_REFRESH,
                    bundleOf(BookmarkFragment.EXTRA_EXTERNAL_PATH_TEXT to pathText)
                )
            }
        }

    private val Ad_BookMark_Interval_Time = "Ad_BookMark_Interval_Time"
    private var bookmarkAdIntervalTime = 0L

    override fun initBinding(): ActivityBookmarkBinding {
        return ActivityBookmarkBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): ActivityModel {
        return viewModels<ActivityModel>().value
    }

    override fun initView() {
        setupToolbar()
        setupViewPager()
        setupTabObserver()
        initializeTabIfNeeded()
        lifecycleScope.launch {
            bookmarkAdIntervalTime = bookmarkAdIntervalTime().toLong()
        }
        onBackPressedDispatcher.addCallback(this) {
            if (!handleBackPressed()) {
                showInterstitial {
                    finish()
                }
            }
        }
    }

    private suspend fun bookmarkAdIntervalTime(): Int {
        return ConfigRemoteManager.getInt(Ad_BookMark_Interval_Time, 0) ?: 0
    }

    private fun showInterstitial(onNext: () -> Unit) {
        loadInterstitial(call = {
            if (it) {
                lastAdShowTime = System.currentTimeMillis()
            }
            onNext.invoke()
        }, condition = {
            (System.currentTimeMillis() - lastAdShowTime >= bookmarkAdIntervalTime).apply {
                if (!this) {
                    ReportDataManager.reportData("ad_show_fail",mapOf(
                        "ad_unit_name" to "",
                        "position" to this@BookmarkActivity.javaClass.simpleName,
                        "number" to "1",
                        "reason" to "bookmark_interval_time"
                    ))
                }
            }
        }, position = "IV_Bookmark_Back")
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            if (!handleBackPressed()) {
                showInterstitial {
                    finish()
                }
            }
        }

        binding.ivAction.setOnClickListener {
            when (viewModel.currentTab.value ?: TAB_BOOKMARK) {
                TAB_BOOKMARK -> {
                    val parentId = bookmarkFragment.getCurrentFolderId()
                    val intent = BookmarkFolderEditActivity.createIntentForCreate(this, parentId)
                    createFolderLauncher.launch(intent)
                }

                TAB_HISTORY -> historyFragment.confirmClearHistory()
            }
        }
    }

    private fun setupViewPager() {
        pagerAdapter = BookmarkPagerAdapter(this, listOf(bookmarkFragment, historyFragment))
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 1

        // 先注册回调再设 adapter 已经来不及（ViewPager2 不像 ViewPager1 那样在
        // setAdapter 时恢复），但 ViewPager2 的恢复发生在 layout pass 期间，
        // 所以这里注册的回调能捕获到恢复触发的 onPageSelected。
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.setCurrentTab(position)
            }
        })

        val titles = listOf(
            getString(R.string.bookmark_tab_bookmarks),
            getString(R.string.bookmark_tab_history)
        )

        // 使用 setItemsKeepingSelection 避免视图重建时把选中粗暴拉回 0。
        // 正确的选中由 LiveData 观察者派发。
        binding.segmentedTabs.setItemsKeepingSelection(titles)
        binding.segmentedTabs.setOnTabSelectedListener { index ->
            viewModel.setCurrentTab(index)
            if (index == TAB_BOOKMARK) {
                ReportDataManager.reportData("Bookmarks_Click", mapOf())
            }
        }
    }

    /**
     * 观察 ViewModel 中的 currentTab，统一驱动 ViewPager2 和 SegmentedTabView。
     */
    private fun setupTabObserver() {
        viewModel.currentTab.observe(this) { tab ->
            applyTab(tab)
        }
    }

    private fun applyTab(tab: Int) {
        if (binding.viewPager.currentItem != tab) {
            binding.viewPager.setCurrentItem(tab, false)
        }
        binding.segmentedTabs.setSelectedIndex(tab)
        updateActionIcon(tab)
    }

    /**
     * 首次进入时根据 intent 中的 defaultTab 初始化。
     * Activity 重建时 ViewModel 已持有上次用户停留的 tab，不再覆盖。
     */
    private fun initializeTabIfNeeded() {
        if (viewModel.currentTab.value == null) {
            viewModel.setCurrentTab(defaultTab)
        }
    }

    private fun updateActionIcon(position: Int) {
        if (position == TAB_BOOKMARK) {
            binding.ivAction.setImageResource(R.drawable.ic_bookmark_add_outline)
            binding.ivAction.contentDescription = getString(R.string.bookmark_add_folder)
        } else {
            binding.ivAction.setImageResource(R.drawable.ic_history_clear)
            binding.ivAction.contentDescription = getString(R.string.history_clear_title)
        }
    }

    private fun handleBackPressed(): Boolean {
        return when (viewModel.currentTab.value ?: TAB_BOOKMARK) {
            TAB_BOOKMARK -> bookmarkFragment.handleBackPressed()
            else -> false
        }
    }

    private fun calculateTabTitleWidths(titles: List<String>): List<Int> {
        if (titles.isEmpty()) {
            return emptyList()
        }
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = resources.getDimension(R.dimen.bookmark_tab_text_size)
            typeface = Typeface.DEFAULT
        }
        val extra = resources.getDimensionPixelSize(R.dimen.bookmark_tab_text_extra_width)
        return titles.map { title ->
            ceil(paint.measureText(title).toDouble()).toInt() + extra
        }
    }

    override fun onBackPressed() {
        if (!handleBackPressed()) {
            super.onBackPressed()
        }
    }

    class ActivityModel(savedState: SavedStateHandle) : BaseModel() {
        // 使用 SavedStateHandle.getLiveData 让当前 tab 自动持久化到
        // ActivityRecord 的 saved state，Activity 进程死亡 / 配置变更后
        // 重建时可直接从 Bundle 取出，与 ViewPager2 自身保存的
        // mCurrentItem 同源，避免两边恢复不一致。
        private val _currentTab: MutableLiveData<Int> =
            savedState.getLiveData(KEY_CURRENT_TAB)
        val currentTab: LiveData<Int> get() = _currentTab

        fun setCurrentTab(tab: Int) {
            if (_currentTab.value != tab) {
                _currentTab.value = tab
            }
        }

        companion object {
            private const val KEY_CURRENT_TAB = "current_tab"
        }
    }

    companion object {
        const val TAB_BOOKMARK = 0
        const val TAB_HISTORY = 1
        private const val EXTRA_DEFAULT_TAB = "extra_default_tab"
        private var lastAdShowTime = 0L
        fun start(context: Context, defaultTab: Int = TAB_BOOKMARK) {
            val intent = Intent(context, BookmarkActivity::class.java).apply {
                putExtra(EXTRA_DEFAULT_TAB, defaultTab)
            }
            context.startActivity(intent)
        }
    }
}
