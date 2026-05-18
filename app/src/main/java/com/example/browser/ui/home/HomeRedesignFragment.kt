package com.example.browser.ui.home

import android.content.Intent
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
import mozilla.components.lib.state.ext.flowScoped

class HomeRedesignFragment : BaseFragment<FragmentHomeRedesignBinding, HomeModel>() {

    private lateinit var headerAdapter: HomeRedesignHeaderAdapter
    private lateinit var newsAdapter: HomeRedesignNewsAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private lateinit var newsModel: NewsModel
    private var scrollIdleRunnable: Runnable? = null

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
        binding?.ivWeatherIcon?.setImageResource(com.browser.weather.R.drawable.ic_weather_partly_cloudy)

        // 天气区域点击 -> 可跳转天气详情
        binding?.weatherContainer?.setOnClickListener {
            // 可以跳转到天气详情页
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
                // 温度转华氏度显示
                val tempF = (weatherData.temperature * 9 / 5) + 32
                binding?.tvTemperature?.text = tempF.toString()

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

    /**
     * 根据 WMO 天气代码返回对应图标资源
     */
    private fun getWeatherIconRes(weatherCode: Int, isDayTime: Boolean): Int {
        return when (weatherCode) {
            0 -> if (isDayTime) com.browser.weather.R.drawable.ic_weather_sunny else com.browser.weather.R.drawable.ic_weather_night_clear
            1 -> if (isDayTime) com.browser.weather.R.drawable.ic_weather_sunny else com.browser.weather.R.drawable.ic_weather_night_clear
            2 -> if (isDayTime) com.browser.weather.R.drawable.ic_weather_partly_cloudy else com.browser.weather.R.drawable.ic_weather_night_cloudy
            3 -> com.browser.weather.R.drawable.ic_weather_cloudy
            45, 48 -> com.browser.weather.R.drawable.ic_weather_fog
            51, 53, 55, 56, 57 -> com.browser.weather.R.drawable.ic_weather_rain
            61, 63, 65, 66, 67 -> com.browser.weather.R.drawable.ic_weather_rain
            71, 73, 75, 77 -> com.browser.weather.R.drawable.ic_weather_snow
            80, 81, 82 -> com.browser.weather.R.drawable.ic_weather_rain
            85, 86 -> com.browser.weather.R.drawable.ic_weather_snow
            95, 96, 99 -> com.browser.weather.R.drawable.ic_weather_thunderstorm
            else -> com.browser.weather.R.drawable.ic_weather_cloudy
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
                // PDF Generation 功能入口
            },
            onVideoClick = {
                // Video Generation 功能入口
            },
            onMoreClick = { activity?.let { NewsMoreActivity.start(it) } },
            onWebsiteClick = { openWebsite(it) },
            onWebsiteLongClick = { showRemoveDialog(it) },
            onAddClick = { activity?.let { RecommendedWebsitesActivity.start(it) } },
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
