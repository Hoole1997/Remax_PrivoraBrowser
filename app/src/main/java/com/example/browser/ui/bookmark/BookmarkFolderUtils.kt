package com.example.browser.ui.bookmark

import com.example.browser.data.bookmark.BookmarkFolder

/**
 * Finds the path from [root] to the folder with [targetId].
 * Returns a list including the root folder and the target folder.
 */
fun findFolderPath(root: BookmarkFolder, targetId: Long): List<BookmarkFolder>? {
    val path = mutableListOf<BookmarkFolder>()
    return if (collectFolderPath(root, targetId, path)) path else null
}

private fun collectFolderPath(
    current: BookmarkFolder,
    targetId: Long,
    path: MutableList<BookmarkFolder>
): Boolean {
    path.add(current)
    if (current.id == targetId) {
        return true
    }
    current.entries.forEach { entry ->
        if (entry is BookmarkFolder) {
            if (collectFolderPath(entry, targetId, path)) {
                return true
            }
        }
    }
    path.removeAt(path.lastIndex)
    return false
}

/**
 * Formats the folder path for display.
 * 根目录使用固定标识符"ROOT"，显示时会被替换为本地化的名称
 */
fun formatFolderPath(path: List<BookmarkFolder>, rootFallback: String): String {
    if (path.isEmpty()) {
        return rootFallback
    }
    return path.joinToString(" / ") { folder ->
        val title = folder.title
        when {
            title.isNullOrBlank() -> rootFallback
            // 检查是否为根目录标识符，使用本地化名称显示
            title == "ROOT" && folder.id == com.example.browser.data.bookmark.BookmarkRepository.ROOT_FOLDER_ID -> rootFallback
            else -> title
        }
    }
}

/**
 * Returns the direct child folders of this folder.
 */
fun BookmarkFolder.childFolders(): List<BookmarkFolder> {
    return entries.mapNotNull { it as? BookmarkFolder }
}

/**
 * Collects all descendant folder IDs of [folder] into [result].
 */
fun collectDescendantFolderIds(folder: BookmarkFolder, result: MutableSet<Long>) {
    folder.entries.forEach { entry ->
        if (entry is BookmarkFolder) {
            if (result.add(entry.id)) {
                collectDescendantFolderIds(entry, result)
            }
        }
    }
}
