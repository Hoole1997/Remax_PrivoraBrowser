package com.example.browser.ui.news

import android.content.Context
import android.content.Intent
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.browser.common.loadInterstitial
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivityNewsMoreBinding

class NewsMoreActivity : BaseActivity<ActivityNewsMoreBinding, NewsModel>() {

    companion object {
        private const val DEFAULT_CATEGORY = "general"

        fun start(activity: Context) {
            activity.startActivity(Intent(activity, NewsMoreActivity::class.java))
        }
    }

    private var newsItemsState by mutableStateOf<List<NewsItem>>(emptyList())
    private var feedItemsState by mutableStateOf<List<NewsFeedItem>>(emptyList())
    private var isLoadingState by mutableStateOf(false)
    private var isLoadingMoreState by mutableStateOf(false)
    private var errorState by mutableStateOf<String?>(null)

    override fun initBinding(): ActivityNewsMoreBinding {
        return ActivityNewsMoreBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): NewsModel {
        return viewModels<NewsModel>().value
    }

    override fun initView() {
        binding.composeContent.setContent {
            NewsMoreScreen(
                title = getString(R.string.news_page_title),
                feedItems = feedItemsState,
                isLoading = isLoadingState,
                isLoadingMore = isLoadingMoreState,
                error = errorState,
                onBackClick = ::finishWithInterstitial,
                onNewsClick = { newsItem ->
                    newsItem.url?.let { NewsDetailsActivity.start(this, it) }
                },
                onRetryClick = {
                    viewModel.loadNews(DEFAULT_CATEGORY)
                },
                onLoadMore = {
                    feedItemsState.lastOrNull { it is NewsFeedItem.News }
                        ?.let { item -> (item as NewsFeedItem.News).newsItem.key }
                        ?.let(viewModel::loadMoreNews)
                },
                canLoadMore = { viewModel.hasMoreData() },
            )
        }

        observeViewModel()
        onBackPressedDispatcher.addCallback(this) {
            finishWithInterstitial()
        }
        loadNews()
    }

    private fun observeViewModel() {
        viewModel.newsItems.observe(this) { newsList ->
            val safeList = newsList ?: emptyList()
            newsItemsState = safeList
            feedItemsState = insertAdsIntoNewsList(safeList)
        }
        viewModel.isLoading.observe(this) { isLoading ->
            isLoadingState = isLoading == true
        }
        viewModel.isLoadingMore.observe(this) { isLoadingMore ->
            isLoadingMoreState = isLoadingMore == true
        }
        viewModel.error.observe(this) { error ->
            errorState = error
        }
    }

    private fun loadNews() {
        newsItemsState = emptyList()
        feedItemsState = emptyList()
        isLoadingState = true
        errorState = null
        viewModel.loadNews(DEFAULT_CATEGORY)
    }

    private fun finishWithInterstitial() {
        loadInterstitial {
            finish()
        }
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
