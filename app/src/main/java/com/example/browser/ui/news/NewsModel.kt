package com.example.browser.ui.news

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.example.browser.base.BaseModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.corekit.core.report.ReportDataManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class NewsModel : BaseModel() {

    private val _newsItems = MutableLiveData<List<NewsItem>>()
    val newsItems: LiveData<List<NewsItem>> = _newsItems

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isLoadingMore = MutableLiveData<Boolean>()
    val isLoadingMore: LiveData<Boolean> = _isLoadingMore

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var currentOffset = 0
    private var hasMore = true
    private val pageSize = 25
    private var currentCategory: String? = null

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val gson by lazy { Gson() }

    companion object {
        private const val NEWS_KEY = ""
        private const val BASE_URL = "https://psv.gamespearl.com/news"
        private const val INITIAL_PAGE_SIZE = 25
    }

    /**
     * 加载新闻列表（初始加载）
     */
    fun loadNews(categories: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            currentOffset = 0
            hasMore = true
            currentCategory = categories

            try {
                val url = buildUrl(limit = INITIAL_PAGE_SIZE, offset = 0, category = currentCategory,key = null)
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val newsResponse = gson.fromJson(responseBody, NewsResponse::class.java)
                        val newsList = newsResponse.data ?: emptyList()
                        _newsItems.value = newsList
                        
                        // 更新分页状态
                        currentOffset = INITIAL_PAGE_SIZE
                        newsResponse.pagination?.let { pagination ->
                            hasMore = currentOffset < pagination.total
                        }
                        LogUtils.d("loadNews: ${newsList.size}")
                        ReportDataManager.reportData("LoadNews",mapOf("reason" to "success"))
                    } else {
                        ReportDataManager.reportData("LoadNews",mapOf("reason" to "empty response body"))
                        _error.value = "Empty response body"
                    }
                } else {
                    ReportDataManager.reportData("LoadNews",mapOf("reason" to "request failed: ${response.code} ${response.message}"))
                    _error.value = "Request failed: ${response.code} ${response.message}"
                }
            } catch (e: Exception) {
                ReportDataManager.reportData("LoadNews",mapOf("reason" to "error: ${e.message}"))
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 加载更多新闻（分页加载）
     */
    fun loadMoreNews(key: String) {
        // 如果正在加载或没有更多数据，则不执行
        if (_isLoadingMore.value == true || !hasMore) return
        LogUtils.d("loadMoreNews: $currentOffset")
        viewModelScope.launch {
            _isLoadingMore.value = true

            try {
                val url = buildUrl(limit = pageSize, offset = currentOffset, category = currentCategory,key = key)
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val newsResponse = gson.fromJson(responseBody, NewsResponse::class.java)
                        val newsList = newsResponse.data ?: emptyList()
                        
                        // 增量添加数据
                        val currentList = _newsItems.value ?: emptyList()
                        _newsItems.value = currentList + newsList
                        
                        // 更新分页状态
                        currentOffset += newsList.size
                        newsResponse.pagination?.let { pagination ->
                            hasMore = currentOffset < pagination.total
                        }
                        ReportDataManager.reportData("LoadNews",mapOf("reason" to "success"))
                    } else {
                        ReportDataManager.reportData("LoadNews",mapOf("reason" to "empty response body"))
                        _error.value = "Empty response body"
                    }
                } else {
                    ReportDataManager.reportData("LoadNews",mapOf("reason" to "request failed: ${response.code} ${response.message}"))
                    _error.value = "Load more failed: ${response.code} ${response.message}"
                }
            } catch (e: Exception) {
                ReportDataManager.reportData("LoadNews",mapOf("reason" to "error: ${e.message}"))
                _error.value = "Load more error: ${e.message}"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * 是否还有更多数据
     */
    fun hasMoreData(): Boolean = hasMore

    private fun buildUrl(limit: Int, offset: Int, category: String?,key: String?): String {
        val builder = StringBuilder()
            .append(BASE_URL)
            .append("?op=").append(if (offset>0) 1 else 0)
            .apply {
                if (offset>0 && key!=null) {
                    append("&key=").append(key)
                }
            }
        category?.takeIf { it.isNotBlank() }?.let {
            builder.append("&categories=").append(it)
        }
        return builder.toString()
    }
}