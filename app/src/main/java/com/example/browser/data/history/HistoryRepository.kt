package com.example.browser.data.history

import android.content.Context
import com.example.browser.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.VisitInfo

class HistoryRepository(private val appContext: Context) {

    suspend fun loadHistory(): List<VisitInfo> = withContext(Dispatchers.IO) {
        appContext.components.historyStorage.getDetailedVisits(0)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        appContext.components.historyStorage.deleteVisitsSince(0)
    }

    suspend fun deleteVisit(url: String) = withContext(Dispatchers.IO) {
        appContext.components.historyStorage.deleteVisitsFor(url)
    }
}
