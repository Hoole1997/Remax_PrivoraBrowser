package com.example.browser.ui.home

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.LogUtils
import com.example.browser.BrowserApplication
import com.example.browser.R
import com.example.browser.base.BaseFragment
import com.example.browser.data.website.QuickWebsite
import com.example.browser.data.website.QuickWebsiteRepository
import com.example.browser.databinding.FragmentHomeRedesignBinding
import com.example.browser.ui.MainActivity
import com.example.browser.ui.bookmark.BookmarkActivity
import com.example.browser.ui.dialog.StoragePermissionDialog
import com.example.browser.ui.junk.JunkScanActivity
import com.example.browser.ui.junk.ProcessCleanActivity
import com.example.browser.ui.news.NewsDetailsActivity
import com.example.browser.ui.news.NewsFeedItem
import com.example.browser.ui.news.NewsItem
import com.example.browser.ui.news.NewsModel
import com.example.browser.ui.news.NewsMoreActivity
import com.example.browser.ui.photoclean.PhotoCleanActivity
import com.example.browser.ui.photoclean.PhotoScanDialogFragment
import com.example.browser.ui.photoclean.model.PhotoCleanMode
import com.example.browser.ui.scan.ScanResultActivity
import com.example.browser.ui.search.SearchActivity
import com.example.browser.ui.speed.SpeedTestActivity
import com.example.browser.ui.web.WebActivity
import com.example.browser.ui.website.RecommendedWebsitesActivity
import com.example.browser.utils.GoogleBarcodeScanner
import com.example.browser.view.ConfirmDialog
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
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
        setupSearchListeners()
        setupHomeRecycler()
        setupSwipeRefresh()
        initNewsModel()
        observeQuickWebsites()
        observeNews()
        observeSearchEngine()
    }

    private fun setupSearchListeners() {
        ClickUtils.applyGlobalDebouncing(binding?.searchBarContainer) {
            val intent = Intent(activity ?: return@applyGlobalDebouncing, SearchActivity::class.java)
            startActivity(intent)
        }

        ClickUtils.applyGlobalDebouncing(binding?.voiceSearchIcon) {
            val intent = Intent(activity ?: return@applyGlobalDebouncing, SearchActivity::class.java).apply {
                putExtra(SearchActivity.EXTRA_START_VOICE_SEARCH, true)
            }
            startActivity(intent)
        }

        ClickUtils.applyGlobalDebouncing(binding?.qrCodeScanIcon) {
            GoogleBarcodeScanner().scanBarcode { rawValue ->
                it.post {
                    ScanResultActivity.start(activity ?: return@post, rawValue)
                }
            }
        }
    }

    private fun setupHomeRecycler() {
        headerAdapter = HomeRedesignHeaderAdapter(
            onCleanClick = { openClean() },
            onNewsClick = { activity?.let { NewsMoreActivity.start(it) } },
            onSpeedClick = { activity?.let { SpeedTestActivity.start(it) } },
            onProcessClick = { activity?.let { ProcessCleanActivity.start(it) } },
            onBookmarkClick = { activity?.let { BookmarkActivity.start(it) } },
            onDuplicateClick = { openDuplicateCleaner() },
            onEditClick = { activity?.let { RecommendedWebsitesActivity.start(it) } },
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

    private fun observeSearchEngine() {
        val application = requireActivity().application as BrowserApplication
        val store = application.browserComponents.store

        lifecycleScope.launch {
            store.flowScoped(viewLifecycleOwner) { flow ->
                flow.map { state -> state.search }
                    .distinctUntilChanged()
                    .collect { searchState ->
                        val selectedEngine = searchState.selectedOrDefaultSearchEngine
                        selectedEngine?.icon?.let { icon ->
                            binding?.searchIcon?.setImageBitmap(icon)
                        }
                    }
            }
        }
    }

    private fun openClean() {
        if (hasStoragePermission()) {
            JunkScanActivity.start(activity ?: return)
        } else {
            showStoragePermissionDialog(
                call = {
                    JunkScanActivity.start(activity ?: return@showStoragePermissionDialog)
                },
                autoJump = {
                    startActivity(Intent().apply {
                        setClass(activity ?: return@apply, MainActivity::class.java)
                        putExtra(MainActivity.EXTRA_AUTO_JUNK, true)
                    })
                },
            )
        }
    }

    private fun openDuplicateCleaner() {
        if (hasStoragePermission()) {
            launchPhotoClean(PhotoCleanMode.DUPLICATE)
        } else {
            showStoragePermissionDialog(
                call = { launchPhotoClean(PhotoCleanMode.DUPLICATE) },
                autoJump = {},
            )
        }
    }

    private fun openWebsite(website: QuickWebsite) {
        val intent = Intent(activity ?: return@openWebsite, WebActivity::class.java).apply {
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

    private fun launchPhotoClean(mode: PhotoCleanMode) {
        val dialog = PhotoScanDialogFragment.newInstance(mode)
        dialog.setOnResultReadyListener { groups ->
            val ctx = activity ?: return@setOnResultReadyListener
            PhotoCleanActivity.start(ctx, mode, groups)
        }
        dialog.show(childFragmentManager, "photo_scan_dialog")
    }

    private fun hasStoragePermission(): Boolean {
        return XXPermissions.isGrantedPermissions(
            activity ?: return false,
            arrayOf(PermissionLists.getManageExternalStoragePermission()),
        )
    }

    private fun showStoragePermissionDialog(call: () -> Unit, autoJump: () -> Unit) {
        StoragePermissionDialog(
            context = activity ?: return,
            onGoNowClick = {
                XXPermissions.with(this)
                    .permission(PermissionLists.getManageExternalStoragePermission())
                    .request { _, deniedList ->
                        if (deniedList.isEmpty()) {
                            call.invoke()
                        }
                    }
                lifecycleScope.launch(Dispatchers.IO) {
                    for (i in 0 until 25) {
                        delay(200)
                        if (XXPermissions.isGrantedPermissions(
                                activity ?: ActivityUtils.getTopActivity(),
                                arrayOf(PermissionLists.getManageExternalStoragePermission()),
                            )
                        ) {
                            autoJump.invoke()
                            return@launch
                        }
                    }
                }
            },
        ).show()
    }
}
