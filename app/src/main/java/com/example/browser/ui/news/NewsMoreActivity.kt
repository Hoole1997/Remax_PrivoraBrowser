package com.example.browser.ui.news

import android.content.Context
import android.content.Intent
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.browser.common.loadInterstitial
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivityNewsMoreBinding
import com.example.browser.ui.home.HomeRedesignNewsAdapter
import net.corekit.core.report.ReportDataManager

/**
 * News 更多列表页。
 *
 * 之前使用 Compose 实现，但 LazyColumn 的 item 复用机制与广告 SDK 容器复用难以兼容，
 * 导致滑出再滑回时广告 SDK 会被反复触发展示计数。这里改用与首页一致的 RecyclerView 方案，
 * 直接复用 [HomeRedesignNewsAdapter]，统一广告复用与去重逻辑。
 */
class NewsMoreActivity : BaseActivity<ActivityNewsMoreBinding, NewsModel>() {

    companion object {
        private const val DEFAULT_CATEGORY = "general"

        /** 距离列表底部还剩这么多 item 时触发加载更多。 */
        private const val PREFETCH_THRESHOLD = 4

        /** 滑动停止后延迟这么久再尝试加载可见广告，避免快速滑动期间发起请求。 */
        private const val LOAD_AD_AFTER_SCROLL_IDLE_MS = 120L

        /** 显示"回到顶部"按钮的最小滚动偏移阈值。 */
        private const val BACK_TO_TOP_OFFSET_PX = 800

        fun start(activity: Context) {
            activity.startActivity(Intent(activity, NewsMoreActivity::class.java))
        }
    }

    private lateinit var newsAdapter: HomeRedesignNewsAdapter
    private var scrollIdleRunnable: Runnable? = null

    override fun initBinding(): ActivityNewsMoreBinding {
        return ActivityNewsMoreBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): NewsModel {
        return viewModels<NewsModel>().value
    }

    override fun initView() {
        binding.tvTitle.text = getString(R.string.news_page_title)
        binding.ivBack.setOnClickListener { finishWithInterstitial() }
        binding.btnBackToTop.setOnClickListener {
            binding.rvNews.smoothScrollToPosition(0)
        }

        setupRecycler()
        observeViewModel()
        onBackPressedDispatcher.addCallback(this) { finishWithInterstitial() }

        viewModel.loadNews(DEFAULT_CATEGORY)
    }

    private fun setupRecycler() {
        newsAdapter = HomeRedesignNewsAdapter(
            onNewsClick = { newsItem ->
                ReportDataManager.reportData(
                    "News_Detail_Page_Click",
                    mapOf("Entry_Position" to "news")
                )
                newsItem.url?.let { NewsDetailsActivity.start(this, it) }
            },
            onRetryClick = {
                viewModel.loadNews(DEFAULT_CATEGORY)
            },
        )

        binding.rvNews.apply {
            layoutManager = LinearLayoutManager(this@NewsMoreActivity)
            adapter = newsAdapter
            itemAnimator = null
            // 多种 viewType（新闻/广告/loading）共存，适当放大缓存减少 bind 次数
            setItemViewCacheSize(8)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    updateBackToTopVisibility(recyclerView)
                    if (dy <= 0) return
                    handlePrefetch(recyclerView)
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        scrollIdleRunnable?.let { recyclerView.removeCallbacks(it) }
                        scrollIdleRunnable = Runnable { loadVisibleAds() }
                        recyclerView.postDelayed(scrollIdleRunnable, LOAD_AD_AFTER_SCROLL_IDLE_MS)
                    }
                }
            })
        }
    }

    private fun handlePrefetch(recyclerView: RecyclerView) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        val total = newsAdapter.itemCount
        val items = viewModel.newsItems.value
        val hasData = !items.isNullOrEmpty()
        val isLoadingMore = viewModel.isLoadingMore.value == true
        if (hasData && lastVisible >= total - PREFETCH_THRESHOLD &&
            !isLoadingMore && viewModel.hasMoreData()
        ) {
            val lastNewsKey = newsAdapter.currentList
                .lastOrNull { it is NewsFeedItem.News }
                ?.let { (it as NewsFeedItem.News).newsItem.key }
            lastNewsKey?.let(viewModel::loadMoreNews)
        }
    }

    private fun updateBackToTopVisibility(recyclerView: RecyclerView) {
        val offset = recyclerView.computeVerticalScrollOffset()
        binding.btnBackToTop.isVisible = offset > BACK_TO_TOP_OFFSET_PX
    }

    private fun loadVisibleAds() {
        val recyclerView = binding.rvNews
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return

        for (position in first..last) {
            val item = newsAdapter.currentList.getOrNull(position) ?: continue
            if (item is NewsFeedItem.NativeAd) {
                newsAdapter.loadAdAtPosition(recyclerView, position)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.newsItems.observe(this) { newsList ->
            val feedItems = insertAdsIntoNewsList(newsList ?: emptyList())
            newsAdapter.submitList(feedItems) {
                binding.rvNews.post { loadVisibleAds() }
            }
            updateNewsState(newsList, viewModel.error.value)
        }
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading == true) {
                newsAdapter.setEmptyViewVisible(false)
                newsAdapter.setNetworkErrorVisible(false)
            } else {
                updateNewsState(viewModel.newsItems.value, viewModel.error.value)
            }
        }
        viewModel.isLoadingMore.observe(this) { isLoadingMore ->
            newsAdapter.setLoadingFooterVisible(isLoadingMore == true)
        }
        viewModel.error.observe(this) { error ->
            updateNewsState(viewModel.newsItems.value, error)
        }
    }

    private fun updateNewsState(newsList: List<NewsItem>?, error: String?) {
        val isLoading = viewModel.isLoading.value == true
        val hasData = !newsList.isNullOrEmpty()
        val hasError = !error.isNullOrEmpty()
        when {
            isLoading || hasData -> {
                newsAdapter.setEmptyViewVisible(false)
                newsAdapter.setNetworkErrorVisible(false)
            }
            hasError -> {
                newsAdapter.setEmptyViewVisible(false)
                newsAdapter.setNetworkErrorVisible(true)
            }
            else -> {
                newsAdapter.setEmptyViewVisible(true)
                newsAdapter.setNetworkErrorVisible(false)
            }
        }
    }

    private fun finishWithInterstitial() {
        loadInterstitial(position = "IV_Newslist_Back") { finish() }
    }

    private fun insertAdsIntoNewsList(newsList: List<NewsItem>): List<NewsFeedItem> {
        if (newsList.isEmpty()) return emptyList()

        val result = mutableListOf<NewsFeedItem>()
        var adId = 0
        var newsCountSinceLastAd = 0

        newsList.forEachIndexed { index, newsItem ->
            result.add(NewsFeedItem.News(newsItem))
            newsCountSinceLastAd++

            if (index == 0) {
                result.add(NewsFeedItem.NativeAd(adId++))
                newsCountSinceLastAd = 0
            } else if (newsCountSinceLastAd == 3) {
                result.add(NewsFeedItem.NativeAd(adId++))
                newsCountSinceLastAd = 0
            }
        }

        return result
    }
}
