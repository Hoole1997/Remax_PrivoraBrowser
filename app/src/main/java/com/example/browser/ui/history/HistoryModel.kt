package com.example.browser.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.browser.base.BaseModel
import com.example.browser.data.history.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryModel(
    private val repository: HistoryRepository
) : BaseModel() {

    private val allEntries = mutableListOf<HistoryEntry>()

    private val _listItems = MutableStateFlow<List<HistoryListItem>>(emptyList())
    val listItems: StateFlow<List<HistoryListItem>> = _listItems

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        refreshHistory()
    }

    fun refreshHistory() {
        val job = viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                repository.loadHistory()
            }.onSuccess { visits ->
                allEntries.clear()
                visits.sortedByDescending { it.visitTime }
                    .mapNotNull { visit ->
                        val url = visit.url ?: return@mapNotNull null
                        HistoryEntry(
                            id = "${url}_${visit.visitTime}",
                            title = visit.title?.takeIf { it.isNotBlank() } ?: url,
                            url = url,
                            visitTime = visit.visitTime,
                            date = visit.visitTime.toLocalDate(),
                            timeFormatted = visit.visitTime.toTimeLabel()
                        )
                    }.also { entries ->
                        allEntries.addAll(entries)
                    }
                updateVisibleList()
            }.onFailure {
                allEntries.clear()
                _listItems.value = emptyList()
            }
            _isLoading.value = false
        }
        addJob(job)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        updateVisibleList()
    }

    fun clearSearch() {
        _searchQuery.value = ""
        updateVisibleList()
    }

    fun clearHistory() {
        val job = viewModelScope.launch {
            repository.clearHistory()
            allEntries.clear()
            updateVisibleList()
            refreshHistory()
        }
        addJob(job)
    }

    fun deleteHistoryEntry(url: String) {
        val job = viewModelScope.launch {
            repository.deleteVisit(url)
            allEntries.removeAll { it.url == url }
            updateVisibleList()
        }
        addJob(job)
    }

    fun hasHistory(): Boolean = allEntries.isNotEmpty()

    private fun updateVisibleList() {
        val query = _searchQuery.value.trim()
        val filtered = if (query.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { entry ->
                entry.title.contains(query, ignoreCase = true) ||
                        entry.url.contains(query, ignoreCase = true)
            }
        }

        val grouped = filtered.groupBy { it.date }.toSortedMap(compareByDescending { it })
        val items = mutableListOf<HistoryListItem>()
        grouped.forEach { (date, entries) ->
            val type = when (date) {
                LocalDate.now() -> HeaderType.TODAY
                LocalDate.now().minusDays(1) -> HeaderType.YESTERDAY
                else -> HeaderType.OTHER
            }
            items.add(HistoryListItem.Header(date = date, type = type))
            entries.forEach { entry ->
                items.add(HistoryListItem.Entry(entry))
            }
        }
        _listItems.value = items
    }

    private fun Long.toLocalDate(): LocalDate {
        return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun Long.toTimeLabel(): String {
        val dateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
        return DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(dateTime)
    }

    data class HistoryEntry(
        val id: String,
        val title: String,
        val url: String,
        val visitTime: Long,
        val date: LocalDate,
        val timeFormatted: String
    )

    sealed class HistoryListItem {
        data class Header(val date: LocalDate, val type: HeaderType) : HistoryListItem()
        data class Entry(val entry: HistoryEntry) : HistoryListItem()
    }

    enum class HeaderType {
        TODAY,
        YESTERDAY,
        OTHER
    }

    class Factory(
        private val repository: HistoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HistoryModel::class.java)) {
                return HistoryModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object
}
