package com.example.browser.ui.bookmark

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.TextPaint
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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

    private val Ad_BookMark_Interval_Time = "Ad_BookMark_Interval_Time"
    private val createFolderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val pathText =
                    result.data?.getStringExtra(BookmarkFolderEditActivity.EXTRA_CREATED_FOLDER_PATH)
                bookmarkFragment.refreshAfterExternalChange(pathText)
            }
        }

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
            when (binding.viewPager.currentItem) {
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
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateActionIcon(position)
                binding.segmentedTabs.setSelectedIndex(position)
            }
        })

        val titles = listOf(
            getString(R.string.bookmark_tab_bookmarks),
            getString(R.string.bookmark_tab_history)
        )

        binding.segmentedTabs.setItems(titles, defaultTab)
        binding.segmentedTabs.setOnTabSelectedListener { index ->
            if (binding.viewPager.currentItem != index) {
                binding.viewPager.setCurrentItem(index, true)
            }
            if (index == 0) {
                ReportDataManager.reportData("Bookmarks_Click",mapOf())
            }
            updateActionIcon(index)
        }

        binding.viewPager.setCurrentItem(defaultTab, false)
        updateActionIcon(defaultTab)
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
        return when (binding.viewPager.currentItem) {
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

    class ActivityModel : BaseModel()

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
