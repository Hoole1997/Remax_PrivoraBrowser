package com.example.browser.ui.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.browser.base.BaseModel
import com.example.browser.data.bookmark.BookmarkFolder
import com.example.browser.data.bookmark.BookmarkNode
import com.example.browser.data.bookmark.BookmarkRepository
import com.example.browser.data.bookmark.BookmarkRepository.Companion.ROOT_FOLDER_ID
import com.example.browser.data.bookmark.BookmarkSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookmarkModel(
    private val repository: BookmarkRepository
) : BaseModel() {

    private val folderStack = ArrayDeque<Long>()
    private var latestRoot: BookmarkFolder = repository.getCurrentRootSnapshot()

    private val _currentFolder = MutableStateFlow(latestRoot)
    val currentFolder: StateFlow<BookmarkFolder> = _currentFolder.asStateFlow()

    private val _pathTitles = MutableStateFlow<List<String>>(listOf(latestRoot.title))
    val pathTitles: StateFlow<List<String>> = _pathTitles.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val visibleEntries: StateFlow<List<BookmarkNode>> =
        combine(_currentFolder, _searchQuery) { folder, query ->
            val entries = folder.entries
            if (query.isBlank()) {
                entries
            } else {
                val keyword = query.trim()
                entries.filter { it.matches(keyword) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, latestRoot.entries)

    init {
        folderStack.addLast(ROOT_FOLDER_ID)
        observeRepository()
    }

    private fun observeRepository() {
        val job = viewModelScope.launch {
            repository.observeRoot().collect { root ->
                latestRoot = root
                val activeId = folderStack.lastOrNull() ?: ROOT_FOLDER_ID
                val target = findFolder(root, activeId) ?: root
                updateCurrentFolder(target)
            }
        }
        addJob(job)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun createFolder(name: String): Boolean {
        return try {
            val parentId = folderStack.lastOrNull() ?: ROOT_FOLDER_ID
            repository.addFolder(parentId, name)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun openFolder(folder: BookmarkFolder) {
        folderStack.addLast(folder.id)
        updateCurrentFolder(folder)
    }

    fun getRootSnapshot(): BookmarkFolder = latestRoot

    fun updateBookmark(siteId: Long, title: String, url: String, parentId: Long): Boolean {
        return repository.updateBookmark(siteId, title, url, parentId)
    }

    fun moveBookmark(siteId: Long, parentId: Long): Boolean {
        return repository.moveBookmark(siteId, parentId)
    }

    fun deleteBookmark(siteId: Long): Boolean {
        return repository.deleteBookmark(siteId)
    }

    fun refreshCurrentFolder() {
        latestRoot = repository.getCurrentRootSnapshot()
        val activeId = folderStack.lastOrNull() ?: ROOT_FOLDER_ID
        val target = findFolder(latestRoot, activeId) ?: latestRoot
        updateCurrentFolder(target)
    }

    fun renameFolder(folderId: Long, title: String): Boolean {
        return repository.renameFolder(folderId, title)
    }

    fun moveFolder(folderId: Long, parentId: Long): Boolean {
        return repository.moveFolder(folderId, parentId)
    }

    fun deleteFolder(folderId: Long): Boolean {
        return repository.deleteFolder(folderId)
    }

    fun navigateBack(): Boolean {
        if (folderStack.size <= 1) {
            return false
        }
        folderStack.removeLast()
        val targetId = folderStack.lastOrNull() ?: ROOT_FOLDER_ID
        val target = findFolder(latestRoot, targetId) ?: latestRoot
        updateCurrentFolder(target)
        return true
    }

    private fun updateCurrentFolder(folder: BookmarkFolder) {
        _currentFolder.value = folder
        _pathTitles.value = folderStack.mapNotNull { id ->
            findFolder(latestRoot, id)?.title
        }
    }

    private fun BookmarkNode.matches(query: String): Boolean {
        if (title.contains(query, ignoreCase = true)) {
            return true
        }
        return this is BookmarkSite && url.contains(query, ignoreCase = true)
    }

    private fun findFolder(root: BookmarkFolder, id: Long): BookmarkFolder? {
        if (root.id == id) {
            return root
        }
        root.entries.forEach { entry ->
            if (entry is BookmarkFolder) {
                val target = findFolder(entry, id)
                if (target != null) {
                    return target
                }
            }
        }
        return null
    }

    class Factory(
        private val repository: BookmarkRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BookmarkModel::class.java)) {
                return BookmarkModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
