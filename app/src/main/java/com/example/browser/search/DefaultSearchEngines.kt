package com.example.browser.search

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.browser.R
import mozilla.components.browser.state.search.SearchEngine
import androidx.core.graphics.createBitmap

/**
 * 默认搜索引擎配置
 * 提供应用内置的搜索引擎列表
 */
object DefaultSearchEngines {

    /**
     * 默认搜索引擎 ID
     */
    const val DEFAULT_SEARCH_ENGINE_ID = "buzzonfeed"

    const val DEFAULT_SEARCH_ENGINE_NAME = "Privora Browser"
    private const val DEFAULT_SEARCH_ENGINE_URL = "https://cse.buzzonfeed.com/search/?q={searchTerms}"

    /**
     * 获取所有内置的默认搜索引擎
     */
    fun getDefaultSearchEngines(context: Context): List<SearchEngine> {
        return listOf(
            createDefaultSearchEngine(context),
            SearchEngine(
                id = "google",
                name = "Google",
                icon = ContextCompat.getDrawable(
                    context,
                    R.mipmap.ic_search_icon_google
                )?.toBitmap() ?: createBitmap(1, 1),
                type = SearchEngine.Type.BUNDLED,
                resultUrls = listOf("https://www.google.com/search?q={searchTerms}"),
                suggestUrl = "https://www.google.com/complete/search?client=firefox&q={searchTerms}"
            ),
            SearchEngine(
                id = "baidu",
                name = "Baidu",
                icon = ContextCompat.getDrawable(
                    context,
                    R.mipmap.ic_search_icon_baidu
                )?.toBitmap() ?: createBitmap(1, 1),
                type = SearchEngine.Type.BUNDLED,
                resultUrls = listOf("https://www.baidu.com/s?wd={searchTerms}"),
                suggestUrl = null
            ),
            SearchEngine(
                id = "bing",
                name = "Bing",
                icon = ContextCompat.getDrawable(
                    context,
                    R.mipmap.ic_search_icon_bing
                )?.toBitmap() ?: createBitmap(1, 1),
                type = SearchEngine.Type.BUNDLED,
                resultUrls = listOf("https://www.bing.com/search?q={searchTerms}"),
                suggestUrl = "https://www.bing.com/osjson.aspx?query={searchTerms}"
            ),
            SearchEngine(
                id = "yandex",
                name = "Yandex",
                icon = ContextCompat.getDrawable(
                    context,
                    R.mipmap.ic_search_icon_yandex
                )?.toBitmap() ?: createBitmap(1, 1),
                type = SearchEngine.Type.BUNDLED,
                resultUrls = listOf("https://yandex.com/search/?text={searchTerms}"),
                suggestUrl = null
            ),
            SearchEngine(
                id = "duckduckgo",
                name = "DuckDuckGo",
                icon = ContextCompat.getDrawable(
                    context,
                    R.mipmap.ic_search_icon_duckduckgo
                )?.toBitmap() ?: createBitmap(1, 1),
                type = SearchEngine.Type.BUNDLED,
                resultUrls = listOf("https://duckduckgo.com/?q={searchTerms}"),
                suggestUrl = "https://ac.duckduckgo.com/ac/?q={searchTerms}"
            )
        )
    }

    fun createDefaultSearchEngine(context: Context): SearchEngine {
        return SearchEngine(
            id = DEFAULT_SEARCH_ENGINE_ID,
            name = DEFAULT_SEARCH_ENGINE_NAME,
            icon = ContextCompat.getDrawable(
                context,
                R.mipmap.ic_logo
            )?.toBitmap() ?: createBitmap(1, 1),
            type = SearchEngine.Type.CUSTOM,
            resultUrls = listOf(DEFAULT_SEARCH_ENGINE_URL),
            suggestUrl = null
        )
    }
}
