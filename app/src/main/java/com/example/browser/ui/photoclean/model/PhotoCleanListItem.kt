package com.example.browser.ui.photoclean.model

sealed class PhotoCleanListItem {
    data class GroupCard(
        val group: PhotoCleanGroup
    ) : PhotoCleanListItem()
}
