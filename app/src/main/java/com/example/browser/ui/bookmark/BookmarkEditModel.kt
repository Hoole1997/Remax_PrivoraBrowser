package com.example.browser.ui.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.browser.base.BaseModel
import com.example.browser.data.bookmark.BookmarkFolder
import com.example.browser.data.bookmark.BookmarkRepository
import com.example.browser.data.bookmark.BookmarkSite

class BookmarkEditModel(
    private val repository: BookmarkRepository
) : BaseModel() {

    fun loadBookmark(siteId: Long): BookmarkSite? {
        return repository.findSiteSnapshot(siteId)
    }

    fun addBookmark(parentId: Long, title: String, url: String) =
        repository.addBookmark(parentId, title, url)

    fun updateBookmark(siteId: Long, title: String, url: String, parentId: Long): Boolean {
        return repository.updateBookmark(siteId, title, url, parentId)
    }

    fun getRootFolder(): BookmarkFolder = repository.getCurrentRootSnapshot()

    fun findFolder(folderId: Long): BookmarkFolder? = repository.findFolderSnapshot(folderId)

    fun addFolder(parentId: Long, name: String) = repository.addFolder(parentId, name)

    class Factory(private val repository: BookmarkRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BookmarkEditModel::class.java)) {
                return BookmarkEditModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
