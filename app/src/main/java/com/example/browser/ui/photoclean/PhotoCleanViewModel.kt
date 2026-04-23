package com.example.browser.ui.photoclean

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.browser.base.BaseModel
import com.example.browser.ui.photoclean.model.CleanablePhoto
import com.example.browser.ui.photoclean.model.PhotoCleanGroup
import com.example.browser.ui.photoclean.model.PhotoCleanListItem
import com.example.browser.ui.photoclean.model.PhotoCleanMode
import java.io.File

class PhotoCleanViewModel : BaseModel() {

    private val _groups = MutableLiveData<List<PhotoCleanGroup>>(emptyList())
    val groups: LiveData<List<PhotoCleanGroup>> = _groups

    private val _listItems = MutableLiveData<List<PhotoCleanListItem>>(emptyList())
    val listItems: LiveData<List<PhotoCleanListItem>> = _listItems

    private val _selectedCount = MutableLiveData(0)
    val selectedCount: LiveData<Int> = _selectedCount

    private val _totalPhotoCount = MutableLiveData(0)
    val totalPhotoCount: LiveData<Int> = _totalPhotoCount

    private val _isAllSelected = MutableLiveData(false)
    val isAllSelected: LiveData<Boolean> = _isAllSelected

    var cleanMode: PhotoCleanMode = PhotoCleanMode.DUPLICATE

    fun loadGroups(groupList: List<PhotoCleanGroup>) {
        _groups.value = groupList
        rebuildListItems()
        updateSelectionState()
    }

    fun togglePhotoCheck(groupId: String, photo: CleanablePhoto) {
        val currentGroups = _groups.value ?: return
        val updatedGroups = currentGroups.map { group ->
            if (group.groupId == groupId) {
                group.copy(
                    photos = group.photos.map { p ->
                        if (p.path == photo.path) p.copy(isChecked = !p.isChecked)
                        else p
                    }
                )
            } else group
        }
        _groups.value = updatedGroups
        rebuildListItems()
        updateSelectionState()
    }

    fun toggleSelectAll() {
        val currentGroups = _groups.value ?: return
        val allChecked = isEveryPhotoChecked(currentGroups)
        val updatedGroups = currentGroups.map { group ->
            group.copy(
                photos = group.photos.map { p ->
                    p.copy(isChecked = !allChecked)
                }
            )
        }
        _groups.value = updatedGroups
        rebuildListItems()
        updateSelectionState()
    }

    fun toggleGroupExpand(groupId: String) {
        val currentGroups = _groups.value ?: return
        val updatedGroups = currentGroups.map { group ->
            if (group.groupId == groupId) {
                group.copy(isExpanded = !group.isExpanded)
            } else group
        }
        _groups.value = updatedGroups
        rebuildListItems()
    }

    fun getSelectedFiles(): List<File> {
        val currentGroups = _groups.value ?: return emptyList()
        return currentGroups.flatMap { group ->
            group.photos.filter { it.isChecked }.map { it.file }
        }
    }

    fun getSelectedPhotoCount(): Int {
        return _selectedCount.value ?: 0
    }

    /**
     * 移除已选中的照片，刷新列表（DiffUtil 局部刷新）
     */
    fun removeSelectedPhotos() {
        val currentGroups = _groups.value ?: return
        val updatedGroups = currentGroups.mapNotNull { group ->
            val remaining = group.photos.filter { !it.isChecked }
            if (remaining.isEmpty()) null
            else if (remaining.size == group.photos.size) group
            else group.copy(photos = remaining)
        }
        _groups.value = updatedGroups
        rebuildListItems()
        updateSelectionState()
    }

    private fun isEveryPhotoChecked(groupList: List<PhotoCleanGroup>): Boolean {
        return groupList.all { group -> group.photos.all { it.isChecked } }
    }

    private fun updateSelectionState() {
        val currentGroups = _groups.value ?: return
        val selected = currentGroups.sumOf { it.checkedCount }
        val total = currentGroups.sumOf { it.photoCount }
        _selectedCount.value = selected
        _totalPhotoCount.value = total
        _isAllSelected.value = selected > 0 && selected == total
    }

    private fun rebuildListItems() {
        val currentGroups = _groups.value ?: return
        val items = currentGroups.map { group ->
            PhotoCleanListItem.GroupCard(group = group)
        }
        _listItems.value = items
    }
}
