package com.example.browser.ui.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.browser.base.BaseModel
import com.example.browser.data.bookmark.BookmarkFolder
import com.example.browser.data.bookmark.BookmarkRepository

class BookmarkFolderEditModel(
    private val repository: BookmarkRepository
) : BaseModel() {

    fun getRootFolder(): BookmarkFolder = repository.getCurrentRootSnapshot()

    fun findFolder(folderId: Long): BookmarkFolder? = repository.findFolderSnapshot(folderId)

    fun addFolder(parentId: Long, name: String) = repository.addFolder(parentId, name)

    fun renameFolder(folderId: Long, name: String): Boolean = repository.renameFolder(folderId, name)

    fun moveFolder(folderId: Long, parentId: Long): Boolean = repository.moveFolder(folderId, parentId)

    class Factory(private val repository: BookmarkRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BookmarkFolderEditModel::class.java)) {
                return BookmarkFolderEditModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
