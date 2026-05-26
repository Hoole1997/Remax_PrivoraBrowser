package com.example.browser.ui.home

import android.content.Intent
import android.net.Uri
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.LogUtils
import com.example.browser.R
import com.example.browser.base.BaseFragment
import com.example.browser.components
import com.example.browser.data.website.QuickWebsite
import com.example.browser.data.website.QuickWebsiteRepository
import com.example.browser.data.website.RecommendedWebsiteRepository
import com.example.browser.databinding.FragmentHomeRedesignBinding
import com.example.browser.ui.news.NewsDetailsActivity
import com.example.browser.ui.news.NewsFeedItem
import com.example.browser.ui.news.NewsItem
import com.example.browser.ui.news.NewsModel
import com.example.browser.ui.news.NewsMoreActivity
import com.example.browser.ui.scan.ScanResultActivity
import com.example.browser.ui.search.SearchActivity
import com.example.browser.ui.web.WebActivity
import com.example.browser.ui.website.RecommendedWebsitesActivity
import com.example.browser.utils.GoogleBarcodeScanner
import com.example.browser.view.ConfirmDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.lib.state.ext.flowScoped

class HomeRedesignFragment : BaseFragment<FragmentHomeRedesignBinding, HomeModel>() {

    private lateinit var headerAdapter: HomeRedesignHeaderAdapter
    private lateinit var newsAdapter: HomeRedesignNewsAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private lateinit var newsModel: NewsModel
    private var scrollIdleRunnable: Runnable? = null

    /** 推荐站点仓库；用于给 Add 按钮挑选 4 个"还没添加到 quick websites"的图标。 */
    private val recommendedWebsiteRepository by lazy {
        RecommendedWebsiteRepository.getInstance(requireContext())
    }

    override fun initBinding(): FragmentHomeRedesignBinding {
        return FragmentHomeRedesignBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): HomeModel {
        val repository = QuickWebsiteRepository.getInstance(requireContext())
        val weatherRepository = com.browser.weather.data.WeatherRepository(requireContext())
        return ViewModelProvider(requireActivity(), HomeModel.Factory(repository, weatherRepository))
            .get(HomeModel::class.java)
    }

    override fun initView() = Unit

    override fun lazyLoad() {
        super.lazyLoad()
        setupTopNavBar()
        setupHomeRecycler()
        setupSwipeRefresh()
        initNewsModel()
        observeQuickWebsites()
        observeNews()
        observeSearchEngine()
        observeWeather()
        observeTabCount()
        viewModel.loadWeather()
    }

    /**
     * 设置顶部导航栏：天气 + 历史 + Tab计数
     */
    private fun setupTopNavBar() {
        // 天气区域初始显示占位状态（"--"）
        binding?.weatherContainer?.isVisible = true
        binding?.tvTemperature?.text = "--"
        binding?.ivWeatherIcon?.setImageResource(R.drawable.ic_weather_home_partly_cloudy)

        // 天气区域点击 -> 跳转天气详情页
        binding?.weatherContainer?.setOnClickListener {
            val ctx = activity ?: return@setOnClickListener
            com.browser.weather.ui.WeatherActivity.start(ctx)
        }

        // 历史记录按钮 -> 跳转到 BookmarkActivity 的 History Tab
        ClickUtils.applyGlobalDebouncing(binding?.ivHistory) {
            val ctx = activity ?: return@applyGlobalDebouncing
            com.example.browser.ui.bookmark.BookmarkActivity.start(ctx, com.example.browser.ui.bookmark.BookmarkActivity.TAB_HISTORY)
        }

        // Tab计数按钮 -> 跳转到 BrowserTabsActivity
        binding?.tabCountContainer?.setOnClickListener {
            val ctx = activity ?: return@setOnClickListener
            val intent = Intent(ctx, com.example.browser.ui.tabs.BrowserTabsActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * 观察天气数据变化，更新顶部天气显示
     */
    private fun observeWeather() {
        viewModel.weatherData.observe(viewLifecycleOwner) { weatherData ->
            if (weatherData != null) {
                binding?.weatherContainer?.isVisible = true
                // 直接使用摄氏度显示（API返回的就是摄氏度）
                binding?.tvTemperature?.text = weatherData.temperature.toString()

                // 根据天气代码设置图标
                val iconRes = getWeatherIconRes(weatherData.weatherIcon, weatherData.isDayTime)
                binding?.ivWeatherIcon?.setImageResource(iconRes)
            } else {
                // 天气数据为空时隐藏天气区域
                binding?.weatherContainer?.isVisible = false
            }
        }
    }

    /**
     * 观察 Tab 数量变化
     */
    private fun observeTabCount() {
        requireContext().components.store.flowScoped(viewLifecycleOwner) { flow ->
            flow.collect { state ->
                val tabCount = state.tabs.size
                val countText = if (tabCount > 99) "99" else tabCount.toString()
                binding?.tvTabCount?.text = countText
            }
        }
    }

    private fun observeSearchEngine() {
        requireContext().components.store.flowScoped(viewLifecycleOwner) { flow ->
            flow.map { state -> state.search.selectedOrDefaultSearchEngine }
                .distinctUntilChanged()
                .collect { searchEngine ->
                    headerAdapter.submitSearchEngineIcon(searchEngine?.icon)
                }
        }
    }

    /**
     * 根据 WMO 天气代码返回对应图标资源（首页专用，小尺寸优化）
     */
    private fun getWeatherIconRes(weatherCode: Int, isDayTime: Boolean): Int {
        return when (weatherCode) {
            0 -> if (isDayTime) R.drawable.ic_weather_home_sunny else R.drawable.ic_weather_home_night_clear
            1 -> if (isDayTime) R.drawable.ic_weather_home_sunny else R.drawable.ic_weather_home_night_clear
            2 -> if (isDayTime) R.drawable.ic_weather_home_partly_cloudy else R.drawable.ic_weather_home_night_cloudy
            3 -> R.drawable.ic_weather_home_cloudy
            45, 48 -> R.drawable.ic_weather_home_fog
            51, 53, 55, 56, 57 -> R.drawable.ic_weather_home_rain
            61, 63, 65, 66, 67 -> R.drawable.ic_weather_home_rain
            71, 73, 75, 77 -> R.drawable.ic_weather_home_snow
            80, 81, 82 -> R.drawable.ic_weather_home_rain
            85, 86 -> R.drawable.ic_weather_home_snow
            95, 96, 99 -> R.drawable.ic_weather_home_thunderstorm
            else -> R.drawable.ic_weather_home_cloudy
        }
    }

    private fun setupHomeRecycler() {
        headerAdapter = HomeRedesignHeaderAdapter(
            onSearchClick = {
                val intent = Intent(activity ?: return@HomeRedesignHeaderAdapter, SearchActivity::class.java)
                startActivity(intent)
            },
            onVoiceClick = {
                val intent = Intent(activity ?: return@HomeRedesignHeaderAdapter, SearchActivity::class.java).apply {
                    putExtra(SearchActivity.EXTRA_START_VOICE_SEARCH, true)
                }
                startActivity(intent)
            },
            onScanClick = {
                GoogleBarcodeScanner().scanBarcode { rawValue ->
                    activity?.runOnUiThread {
                        ScanResultActivity.start(activity ?: return@runOnUiThread, rawValue)
                    }
                }
            },
            onPdfClick = {
                openWebSearch(getString(R.string.home_pdf_generation))
            },
            onVideoClick = {
                openWebSearch(getString(R.string.home_video_generation))
            },
            onMoreClick = { activity?.let { NewsMoreActivity.start(it) } },
            onWebsiteClick = { openWebsite(it) },
            onWebsiteLongClick = { showRemoveDialog(it) },
            onAddClick = { activity?.let { RecommendedWebsitesActivity.start(it) } },
            // 给 Add 按钮提供 4 个未添加推荐站的图标 asset 路径，
            // 用于在 QuickAddIconView 中拼出 2x2 缩略图，引导用户去添加。
            provideAddPreviewIcons = { collectAddPreviewIcons() },
        )

        newsAdapter = HomeRedesignNewsAdapter(
            onNewsClick = { newsItem ->
                newsItem.url?.let { url ->
                    NewsDetailsActivity.start(activity ?: return@let, url, true)
                }
            },
            onRetryClick = {
                newsModel.loadNews()
            },
        )

        concatAdapter = ConcatAdapter(headerAdapter, newsAdapter)

        binding?.rvHomeFeed?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = concatAdapter
            itemAnimator = null
            setHasFixedSize(false)
            // Feed 中存在新闻、广告、loading 等多种 viewType，适当放大缓存可减少滑动时的 bind 次数
            setItemViewCacheSize(8)
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy <= 0) return
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                    val totalCount = concatAdapter.itemCount
                    val hasData = !newsModel.newsItems.value.isNullOrEmpty()
                    if (hasData && lastVisiblePosition >= totalCount - 3 && newsModel.isLoadingMore.value != true && newsModel.hasMoreData()) {
                        val lastNewsKey = newsAdapter.currentList
                            .lastOrNull { it is NewsFeedItem.News }
                            ?.let { (it as NewsFeedItem.News).newsItem.key }
                        lastNewsKey?.let { key ->
                            newsModel.loadMoreNews(key)
                        }
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        scrollIdleRunnable?.let { recyclerView.removeCallbacks(it) }
                        scrollIdleRunnable = Runnable { loadVisibleAds() }
                        recyclerView.postDelayed(scrollIdleRunnable, 120)
                    }
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        binding?.swipeRefreshLayout?.apply {
            setColorSchemeResources(R.color.color_material_button)
            setOnRefreshListener {
                newsModel.loadNews()
                viewModel.loadWeather()
            }
        }
    }

    private fun initNewsModel() {
        newsModel = ViewModelProvider(requireActivity())[NewsModel::class.java]
        newsModel.loadNews()
    }

    private fun observeQuickWebsites() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.quickWebsites.collect { websites ->
                        headerAdapter.submitWebsites(websites)
                    }
                }
            }
        }
    }

    private fun observeNews() {
        newsModel.newsItems.observe(viewLifecycleOwner) { newsList ->
            newsAdapter.submitList(insertAdsIntoNewsList(newsList)) {
                binding?.rvHomeFeed?.post {
                    loadVisibleAds()
                }
            }
            updateNewsState(newsList, newsModel.error.value)
        }

        newsModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true) {
                newsAdapter.setEmptyViewVisible(false)
                newsAdapter.setNetworkErrorVisible(false)
            } else {
                binding?.swipeRefreshLayout?.isRefreshing = false
                updateNewsState(newsModel.newsItems.value, newsModel.error.value)
            }
        }

        newsModel.error.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                LogUtils.d(error)
                updateNewsState(newsModel.newsItems.value, error)
            }
        }

        newsModel.isLoadingMore.observe(viewLifecycleOwner) { isLoading ->
            newsAdapter.setLoadingFooterVisible(isLoading == true)
        }
    }

    private fun updateNewsState(newsList: List<NewsItem>?, error: String?) {
        val isLoading = newsModel.isLoading.value == true
        val hasData = !newsList.isNullOrEmpty()
        val hasError = !error.isNullOrEmpty()

        if (isLoading) {
            newsAdapter.setEmptyViewVisible(false)
            newsAdapter.setNetworkErrorVisible(false)
        } else if (hasData) {
            newsAdapter.setEmptyViewVisible(false)
            newsAdapter.setNetworkErrorVisible(false)
        } else if (hasError) {
            newsAdapter.setEmptyViewVisible(false)
            newsAdapter.setNetworkErrorVisible(true)
        } else {
            newsAdapter.setEmptyViewVisible(true)
            newsAdapter.setNetworkErrorVisible(false)
        }
    }

    private fun insertAdsIntoNewsList(newsList: List<NewsItem>?): List<NewsFeedItem> {
        if (newsList.isNullOrEmpty()) return emptyList()

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

    /**
     * 收集 4 个"未添加到 quick websites"的推荐站图标 asset 路径，
     * 用于在 Add 按钮上拼出 2x2 缩略图。当推荐网站不足 4 个时返回少于 4 个。
     */
    private fun collectAddPreviewIcons(): List<String> {
        val addedUrls = viewModel.quickWebsites.value
            .map { it.url.lowercase() }
            .toHashSet()
        return recommendedWebsiteRepository.getCategories()
            .asSequence()
            .flatMap { it.websites.asSequence() }
            .filter { !addedUrls.contains(it.url.lowercase()) }
            .mapNotNull { it.iconAsset?.takeIf { asset -> asset.isNotEmpty() } }
            .map { "weblogo/$it" }
            .distinct()
            .take(4)
            .toList()
    }

    private fun loadVisibleAds() {
        val recyclerView = binding?.rvHomeFeed ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) return

        val headerCount = headerAdapter.itemCount
        for (position in firstVisible..lastVisible) {
            val newsAdapterPosition = position - headerCount
            if (newsAdapterPosition < 0) continue
            val item = newsAdapter.currentList.getOrNull(newsAdapterPosition)
            if (item is NewsFeedItem.NativeAd) {
                newsAdapter.loadAdAtPosition(recyclerView, position)
            }
        }
    }

    private fun openWebsite(website: QuickWebsite) {
        val intent = Intent(activity ?: return, WebActivity::class.java).apply {
            putExtra(WebActivity.EXTRA_URL, website.url)
        }
        startActivity(intent)
    }

    /**
     * 以关键词形式跳转到 [WebActivity]，用于 PDF / Video Generation 等
     * "搜索式"入口。复用当前选中的搜索引擎模板，与 SearchActivity.performSearch 行为一致。
     */
    private fun openWebSearch(keyword: String) {
        val ctx = activity ?: return
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return

        val encodedQuery = Uri.encode(trimmed)
        val searchEngine = ctx.components.store.state.search.selectedOrDefaultSearchEngine
        val url = searchEngine?.resultUrls
            ?.firstOrNull()
            ?.replace("{searchTerms}", encodedQuery)
            ?: "https://www.google.com/search?q=$encodedQuery"

        val intent = Intent(ctx, WebActivity::class.java).apply {
            putExtra(WebActivity.EXTRA_URL, url)
        }
        startActivity(intent)
    }

    private fun showRemoveDialog(website: QuickWebsite) {
        ConfirmDialog.show(
            supportFragmentManager = childFragmentManager,
            title = getString(R.string.home_quick_remove_title),
            content = getString(R.string.home_quick_remove_confirm),
            button = getString(R.string.home_quick_remove),
        ).apply {
            setOnConfirmListener {
                viewModel.removeQuickWebsite(website.id)
            }
        }
    }
}
