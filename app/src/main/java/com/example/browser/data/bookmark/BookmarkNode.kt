package com.example.browser.data.bookmark

sealed interface BookmarkNode {
    val id: Long
    val title: String
    val parentId: Long?
}

data class BookmarkFolder(
    override val id: Long,
    override val title: String,
    override val parentId: Long?,
    val entries: List<BookmarkNode>
) : BookmarkNode

data class BookmarkSite(
    override val id: Long,
    override val title: String,
    override val parentId: Long?,
    val url: String
) : BookmarkNode
