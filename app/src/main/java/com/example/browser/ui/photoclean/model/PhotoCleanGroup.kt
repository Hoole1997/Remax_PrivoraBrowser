package com.example.browser.ui.photoclean.model

data class PhotoCleanGroup(
    val groupId: String,
    val photos: List<CleanablePhoto>,
    val isExpanded: Boolean = false
) {
    val photoCount: Int get() = photos.size

    val totalSize: Long get() = photos.sumOf { it.size }

    val checkedCount: Int get() = photos.count { it.isChecked }

    val isAllChecked: Boolean get() = photos.all { it.isChecked }

    val friendlyTotalSize: String
        get() {
            val kb = totalSize / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.1fGB", gb)
                mb >= 1.0 -> String.format("%.1fMB", mb)
                kb >= 1.0 -> String.format("%.0fKB", kb)
                else -> "${totalSize}B"
            }
        }
}
