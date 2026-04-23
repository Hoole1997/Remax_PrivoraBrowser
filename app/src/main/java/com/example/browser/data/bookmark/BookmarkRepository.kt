package com.example.browser.data.bookmark

import android.content.Context
import com.example.browser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

class BookmarkRepository private constructor(context: Context) {

    companion object {
        const val ROOT_FOLDER_ID: Long = 0L
        private const val TYPE_FOLDER = "folder"
        private const val TYPE_SITE = "site"

        @Volatile
        private var instance: BookmarkRepository? = null

        fun getInstance(context: Context): BookmarkRepository {
            return instance ?: synchronized(this) {
                instance ?: BookmarkRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val appContext = context.applicationContext
    private val storageFile: File = File(appContext.filesDir, "bookmarks/bookmarks.json")
    private val lock = Any()

    private val root = InternalFolder(
        id = ROOT_FOLDER_ID,
        title = "ROOT", // 固定标识符，不使用国际化字符串，避免切换语言后显示问题
        parent = null
    )

    private val folderIndex = mutableMapOf<Long, InternalFolder>()
    private val siteIndex = mutableMapOf<String, MutableList<InternalSite>>()
    private val siteIdIndex = mutableMapOf<Long, InternalSite>()
    private val nextId = AtomicLong(ROOT_FOLDER_ID + 1)

    private val _rootFlow = MutableStateFlow(root.toPublicFolder())
    val rootFlow: StateFlow<BookmarkFolder> = _rootFlow

    init {
        folderIndex[root.id] = root
        loadFromDisk()
    }

    fun observeRoot(): StateFlow<BookmarkFolder> = rootFlow

    data class BookmarkAddResult(
        val bookmark: BookmarkSite,
        val isNew: Boolean
    )

    fun getCurrentRootSnapshot(): BookmarkFolder = _rootFlow.value

    fun findFolderSnapshot(folderId: Long): BookmarkFolder? {
        return synchronized(lock) {
            val folder = folderIndex[folderId] ?: return null
            folder.toPublicFolder()
        }
    }

    fun findSiteSnapshot(siteId: Long): BookmarkSite? {
        return synchronized(lock) {
            val pair = findSiteWithParent(root, siteId) ?: return null
            pair.second.toPublicSite()
        }
    }

    fun addFolder(parentId: Long, name: String): BookmarkFolder {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Folder name cannot be blank")
        }

        val newFolder = synchronized(lock) {
            val parent = folderIndex[parentId] ?: root
            val folder = InternalFolder(
                id = generateId(),
                title = trimmed,
                parent = parent
            )
            parent.children.add(0, folder)
            folderIndex[folder.id] = folder
            updateStateLocked()
            folder
        }

        persistAsync()
        return newFolder.toPublicFolder()
    }

    fun addBookmark(parentId: Long, title: String?, url: String): BookmarkAddResult? {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isEmpty()) {
            return null
        }

        val result = synchronized(lock) {
            val parent = folderIndex[parentId] ?: root

            val existing = siteIndex[normalizedUrl]?.firstOrNull()
            if (existing != null) {
                return@synchronized BookmarkAddResult(existing.toPublicSite(), isNew = false)
            }

            val finalTitle = title?.takeIf { it.isNotBlank() } ?: normalizedUrl
            val site = InternalSite(
                id = generateId(),
                title = finalTitle,
                parent = parent,
                url = normalizedUrl
            )
            parent.children.add(0, site)
            registerSite(site)
            updateStateLocked()
            BookmarkAddResult(site.toPublicSite(), isNew = true)
        }

        if (result.isNew) {
            persistAsync()
        }
        return result
    }

    fun isBookmarked(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        return synchronized(lock) {
            siteIndex[url]?.isNotEmpty() == true
        }
    }

    fun findBookmarkByUrl(url: String?): BookmarkSite? {
        if (url.isNullOrBlank()) {
            return null
        }
        return synchronized(lock) {
            siteIndex[url]?.firstOrNull()?.toPublicSite()
        }
    }

    fun updateBookmark(siteId: Long, title: String?, url: String, parentId: Long?): Boolean {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            return false
        }
        val trimmedTitle = title?.trim().orEmpty()
        val finalTitle = if (trimmedTitle.isBlank()) trimmedUrl else trimmedTitle

        val updated = synchronized(lock) {
            val (currentParent, site) = findSiteWithParent(root, siteId) ?: return@synchronized false
            siteIdIndex[siteId] = site
            val targetParent = parentId?.let { folderIndex[it] } ?: site.parent ?: root

            if (trimmedUrl != site.url) {
                val duplicate = siteIndex[trimmedUrl]?.firstOrNull { it.id != siteId }
                if (duplicate != null) {
                    return@synchronized false
                }
            }

            if (currentParent.id != targetParent.id) {
                currentParent.children.remove(site)
                targetParent.children.add(0, site)
                site.parent = targetParent
            }

            if (trimmedUrl != site.url) {
                unregisterSite(site)
                site.url = trimmedUrl
                registerSite(site)
            }

            site.title = finalTitle
            updateStateLocked()
            true
        }

        if (updated) {
            persistAsync()
        }
        return updated
    }

    fun moveBookmark(siteId: Long, parentId: Long): Boolean {
        val moved = synchronized(lock) {
            val (currentParent, site) = findSiteWithParent(root, siteId) ?: return@synchronized false
            siteIdIndex[siteId] = site
            val targetParent = folderIndex[parentId]
                ?: if (parentId == ROOT_FOLDER_ID) folderIndex[ROOT_FOLDER_ID] ?: root else return@synchronized false
            if (currentParent.id == targetParent.id) {
                return@synchronized false
            }

            currentParent.children.remove(site)
            targetParent.children.add(0, site)
            site.parent = targetParent

            updateStateLocked()
            true
        }
        if (moved) {
            persistAsync()
        }
        return moved
    }

    fun deleteBookmark(siteId: Long): Boolean {
        val removed = synchronized(lock) {
            val (parent, site) = findSiteWithParent(root, siteId) ?: return@synchronized false
            parent.children.remove(site)
            siteIdIndex.remove(site.id)
            unregisterSite(site)
            updateStateLocked()
            true
        }
        if (removed) {
            persistAsync()
        }
        return removed
    }

    fun renameFolder(folderId: Long, title: String): Boolean {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            return false
        }
        val renamed = synchronized(lock) {
            val folder = folderIndex[folderId] ?: return@synchronized false
            if (folder.id == ROOT_FOLDER_ID) {
                return@synchronized false
            }
            folder.title = trimmed
            updateStateLocked()
            true
        }
        if (renamed) {
            persistAsync()
        }
        return renamed
    }

    fun moveFolder(folderId: Long, parentId: Long): Boolean {
        if (folderId == ROOT_FOLDER_ID) {
            return false
        }
        val moved = synchronized(lock) {
            val folder = folderIndex[folderId] ?: return@synchronized false
            val target = folderIndex[parentId] ?: return@synchronized false
            if (folder == target) {
                return@synchronized false
            }
            if (isDescendant(target, folder)) {
                return@synchronized false
            }
            val currentParent = folder.parent ?: return@synchronized false
            if (currentParent.id == target.id) {
                return@synchronized false
            }
            currentParent.children.remove(folder)
            target.children.add(0, folder)
            folder.parent = target
            updateStateLocked()
            true
        }
        if (moved) {
            persistAsync()
        }
        return moved
    }

    fun deleteFolder(folderId: Long): Boolean {
        if (folderId == ROOT_FOLDER_ID) {
            return false
        }
        val deleted = synchronized(lock) {
            val folder = folderIndex[folderId] ?: return@synchronized false
            val parent = folder.parent ?: return@synchronized false
            parent.children.remove(folder)
            removeFolderReferences(folder)
            updateStateLocked()
            true
        }
        if (deleted) {
            persistAsync()
        }
        return deleted
    }

    private fun loadFromDisk() {
        if (!storageFile.exists()) {
            updateStateLocked()
            return
        }

        val text = try {
            storageFile.readText()
        } catch (e: IOException) {
            null
        } ?: run {
            updateStateLocked()
            return
        }

        if (text.isBlank()) {
            updateStateLocked()
            return
        }

        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            null
        } ?: return

        synchronized(lock) {
            folderIndex.clear()
            siteIndex.clear()
            siteIdIndex.clear()

            val loadedRoot = parseFolder(json, null)

            root.title = loadedRoot.title
            root.children.clear()
            root.children.addAll(loadedRoot.children)

            folderIndex[root.id] = root
            rebuildIndexes(root)

            val maxId = determineMaxId(root)
            nextId.set(maxId + 1)

            updateStateLocked()
        }
    }

    private fun rebuildIndexes(folder: InternalFolder) {
        folderIndex[folder.id] = folder
        folder.children.forEach { child ->
            when (child) {
                is InternalFolder -> {
                    child.parent = folder
                    rebuildIndexes(child)
                }
                is InternalSite -> {
                    child.parent = folder
                    registerSite(child)
                }
            }
        }
    }

    private fun determineMaxId(folder: InternalFolder): Long {
        var maxId = folder.id
        folder.children.forEach { child ->
            val childMax = when (child) {
                is InternalFolder -> determineMaxId(child)
                is InternalSite -> child.id
            }
            if (childMax > maxId) {
                maxId = childMax
            }
        }
        return maxId
    }

    private fun registerSite(site: InternalSite) {
        siteIdIndex[site.id] = site
        siteIndex.getOrPut(site.url) { mutableListOf() }.add(site)
    }

    private fun unregisterSite(site: InternalSite) {
        siteIdIndex.remove(site.id)
        siteIndex[site.url]?.let { list ->
            list.remove(site)
            if (list.isEmpty()) {
                siteIndex.remove(site.url)
            }
        }
    }

    private fun parseFolder(json: JSONObject, parent: InternalFolder?): InternalFolder {
        val id = json.optLong("id", generateId())
        val title = json.optString("title", "")
        val folder = InternalFolder(id = id, title = title, parent = parent)
        val children = json.optJSONArray("children") ?: JSONArray()
        for (i in 0 until children.length()) {
            val child = children.optJSONObject(i) ?: continue
            when (child.optString("type")) {
                TYPE_FOLDER -> {
                    folder.children.add(parseFolder(child, folder))
                }
                TYPE_SITE -> {
                    folder.children.add(parseSite(child, folder))
                }
            }
        }
        return folder
    }

    private fun parseSite(json: JSONObject, parent: InternalFolder): InternalSite {
        val id = json.optLong("id", generateId())
        val url = json.optString("url", "")
        val title = json.optString("title", url)
        return InternalSite(id = id, title = title, parent = parent, url = url)
    }

    private fun isDescendant(target: InternalFolder, ancestor: InternalFolder): Boolean {
        var current: InternalFolder? = target
        while (current != null) {
            if (current == ancestor) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun removeFolderReferences(folder: InternalFolder) {
        val childrenSnapshot = folder.children.toList()
        childrenSnapshot.forEach { child ->
            when (child) {
                is InternalFolder -> removeFolderReferences(child)
                is InternalSite -> unregisterSite(child)
            }
        }
        folder.children.clear()
        folder.parent = null
        folderIndex.remove(folder.id)
    }

    private fun findSiteWithParent(folder: InternalFolder, siteId: Long): Pair<InternalFolder, InternalSite>? {
        folder.children.forEach { child ->
            when (child) {
                is InternalFolder -> {
                    val found = findSiteWithParent(child, siteId)
                    if (found != null) {
                        return found
                    }
                }
                is InternalSite -> if (child.id == siteId) {
                    return folder to child
                }
            }
        }
        return null
    }

    private fun updateStateLocked() {
        _rootFlow.value = root.toPublicFolder()
    }

    private fun persistAsync() {
        val snapshot = synchronized(lock) {
            root.toJson()
        }
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                storageFile.parentFile?.let {
                    if (!it.exists()) {
                        it.mkdirs()
                    }
                }
                storageFile.writeText(snapshot.toString())
            }
        }
    }

    private fun generateId(): Long {
        var value = nextId.getAndIncrement()
        if (value == ROOT_FOLDER_ID) {
            value = nextId.getAndIncrement()
        }
        return value
    }

    private fun InternalFolder.toJson(): JSONObject {
        val json = JSONObject()
        json.put("type", TYPE_FOLDER)
        json.put("id", id)
        json.put("title", title)
        val array = JSONArray()
        children.forEach { child ->
            when (child) {
                is InternalFolder -> array.put(child.toJson())
                is InternalSite -> array.put(child.toJson())
            }
        }
        json.put("children", array)
        return json
    }

    private fun InternalSite.toJson(): JSONObject {
        val json = JSONObject()
        json.put("type", TYPE_SITE)
        json.put("id", id)
        json.put("title", title)
        json.put("url", url)
        return json
    }

    private fun InternalFolder.toPublicFolder(): BookmarkFolder {
        val childNodes = children.map { child ->
            when (child) {
                is InternalFolder -> child.toPublicFolder()
                is InternalSite -> child.toPublicSite()
            }
        }
        return BookmarkFolder(
            id = id,
            title = title,
            parentId = parent?.id,
            entries = childNodes
        )
    }

    private fun InternalSite.toPublicSite(): BookmarkSite {
        return BookmarkSite(
            id = id,
            title = title,
            parentId = parent?.id,
            url = url
        )
    }

    private sealed class InternalEntry(
        open val id: Long,
        open var title: String,
        open var parent: InternalFolder?
    )

    private class InternalFolder(
        override val id: Long,
        override var title: String,
        override var parent: InternalFolder?
    ) : InternalEntry(id, title, parent) {
        val children: MutableList<InternalEntry> = mutableListOf()
    }

    private class InternalSite(
        override val id: Long,
        override var title: String,
        override var parent: InternalFolder?,
        var url: String
    ) : InternalEntry(id, title, parent)
}
