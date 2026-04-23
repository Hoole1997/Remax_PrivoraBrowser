package com.example.browser.ui.website

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.browser.base.BaseModel
import com.example.browser.data.website.QuickWebsiteRepository
import com.example.browser.data.website.QuickWebsiteAddResult
import com.example.browser.data.website.RecommendedCategory
import com.example.browser.data.website.RecommendedWebsite
import com.example.browser.data.website.RecommendedWebsiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RecommendedWebsitesViewModel(
    private val quickRepository: QuickWebsiteRepository,
    private val recommendedRepository: RecommendedWebsiteRepository
) : BaseModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    val categories: List<RecommendedCategory> = recommendedRepository.getCategories()

    val listItems: StateFlow<List<RecommendedListItem>> =
        combine(
            quickRepository.observeWebsites(),
            _selectedCategory
        ) { quickWebsites, _ ->
            val quickUrls = quickWebsites.map { it.url.lowercase() }.toSet()
            buildList {
                categories.forEach { category ->
                    add(RecommendedListItem.SectionHeader(category))
                    category.websites.forEach { website ->
                        add(
                            RecommendedListItem.WebsiteItem(
                                categoryKey = category.key,
                                website = website,
                                isAdded = quickUrls.contains(website.url.lowercase())
                            )
                        )
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectCategory(key: String?) {
        _selectedCategory.value = key
    }

    fun addQuickWebsite(website: RecommendedWebsite): QuickWebsiteAddResult? {
        return quickRepository.addWebsite(
            title = website.name,
            url = website.url,
            iconUrl = website.iconAsset
        )
    }

    fun isAlreadyAdded(url: String): Boolean {
        return quickRepository.getCurrentSnapshot().any { it.url.equals(url, ignoreCase = true) }
    }

    class Factory(
        private val quickRepository: QuickWebsiteRepository,
        private val recommendedRepository: RecommendedWebsiteRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RecommendedWebsitesViewModel::class.java)) {
                return RecommendedWebsitesViewModel(quickRepository, recommendedRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

sealed interface RecommendedListItem {
    val stableId: Long

    data class SectionHeader(val category: RecommendedCategory) : RecommendedListItem {
        override val stableId: Long = category.key.hashCode().toLong()
    }

    data class WebsiteItem(
        val categoryKey: String,
        val website: RecommendedWebsite,
        val isAdded: Boolean
    ) : RecommendedListItem {
        override val stableId: Long = (categoryKey + website.url).hashCode().toLong()
    }
}
