package com.example.browser.data.website

import android.content.Context
import com.example.browser.R
import org.json.JSONObject

private data class CategoryInfo(
    val titleResId: Int,
    val iconResId: Int,
    val colorResId: Int
)

private val CATEGORY_INFO = mapOf(
    "social" to CategoryInfo(R.string.recommended_section_social, R.mipmap.ic_web_category_social, R.color.recommended_category_bg),
    "video" to CategoryInfo(R.string.recommended_section_video, R.mipmap.ic_web_category_video, R.color.recommended_category_bg),
    "news" to CategoryInfo(R.string.recommended_section_news, R.mipmap.ic_web_category_news, R.color.recommended_category_bg),
    "tools" to CategoryInfo(R.string.recommended_section_tools, R.mipmap.ic_web_category_tools, R.color.recommended_category_bg),
    "entertainment" to CategoryInfo(R.string.recommended_section_entertainment, R.mipmap.ic_web_category_entertainment, R.color.recommended_category_bg),
    "music" to CategoryInfo(R.string.recommended_section_music, R.mipmap.ic_web_category_music, R.color.recommended_category_bg),
    "shopping" to CategoryInfo(R.string.recommended_section_shopping, R.mipmap.ic_web_category_shopping, R.color.recommended_category_bg),
    "sport" to CategoryInfo(R.string.recommended_section_sport, R.mipmap.ic_web_category_sport, R.color.recommended_category_bg),
    "travel" to CategoryInfo(R.string.recommended_section_travel, R.mipmap.ic_web_category_travel, R.color.recommended_category_bg),
    "healthy" to CategoryInfo(R.string.recommended_section_healthy, R.mipmap.ic_web_category_healthy, R.color.recommended_category_bg)
)

private val CATEGORY_ORDER = listOf(
    "social",
    "video",
    "music",
    "news",
    "tools",
    "entertainment",
    "shopping",
    "sport",
    "travel"
)

class RecommendedWebsiteRepository private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: RecommendedWebsiteRepository? = null

        fun getInstance(context: Context): RecommendedWebsiteRepository {
            return instance ?: synchronized(this) {
                instance ?: RecommendedWebsiteRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val appContext = context.applicationContext
    private val cachedCategories: List<RecommendedCategory> by lazy(LazyThreadSafetyMode.NONE) { loadCategories() }

    fun getCategories(): List<RecommendedCategory> = cachedCategories

    private fun loadCategories(): List<RecommendedCategory> {
        val json = runCatching {
            appContext.assets.open("web_site.json").use { input ->
                input.bufferedReader().readText()
            }
        }.getOrNull() ?: return emptyList()

        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()

        val results = mutableListOf<RecommendedCategory>()
        CATEGORY_ORDER.forEach { key ->
            val sitesArray = root.optJSONArray(key) ?: return@forEach
            val info = CATEGORY_INFO[key] ?: return@forEach
            if (sitesArray.length() == 0) {
                return@forEach
            }
            val websites = buildList {
                for (index in 0 until sitesArray.length()) {
                    val item = sitesArray.optJSONObject(index) ?: continue
                    val iconAsset = item.optString("icon")
                    if (iconAsset != null) {
                        add(
                            RecommendedWebsite(
                                name = item.optString("name"),
                                url = item.optString("url"),
                                iconAsset = iconAsset
                            )
                        )
                    }
                }
            }
            if (websites.isNotEmpty()) {
                results.add(
                    RecommendedCategory(
                        key = key,
                        titleResId = info.titleResId,
                        iconResId = info.iconResId,
                        colorResId = info.colorResId,
                        websites = websites
                    )
                )
            }
        }
        return results
    }
}
