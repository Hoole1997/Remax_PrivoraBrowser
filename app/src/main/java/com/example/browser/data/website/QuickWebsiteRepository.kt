package com.example.browser.data.website

import android.content.Context
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

class QuickWebsiteRepository private constructor(context: Context) {

    companion object {
        private const val STORAGE_DIR = "quick_websites"
        private const val STORAGE_FILE = "websites.json"
        private const val KEY_WEBSITES = "websites"

        /**
         * 旧版本曾经预置过 4 个本地 feature 入口（清理 / 重复照片 / 测速 / 进程）。
         * 现已移除。这里保留前缀用于在加载老用户数据时过滤掉这些遗留条目，
         * 避免升级后首页继续显示这 4 个废弃入口。
         */
        private const val LEGACY_FEATURE_URL_PREFIX = "app://feature/"

        @Volatile
        private var instance: QuickWebsiteRepository? = null

        fun getInstance(context: Context): QuickWebsiteRepository {
            return instance ?: synchronized(this) {
                instance ?: QuickWebsiteRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val appContext = context.applicationContext
    private val storageFile = File(appContext.filesDir, "$STORAGE_DIR/$STORAGE_FILE")
    private val lock = Any()

    private val internalWebsites = mutableListOf<InternalWebsite>()
    private val nextId = AtomicLong(1L)

    private val _websitesFlow = MutableStateFlow<List<QuickWebsite>>(emptyList())
    val websitesFlow: StateFlow<List<QuickWebsite>> = _websitesFlow

    init {
        loadFromDisk()
        // 老用户的 websites.json 里可能仍残留 4 个 app://feature/* 条目，启动时清理掉
        val purgedLegacy = synchronized(lock) {
            val before = internalWebsites.size
            internalWebsites.removeAll { it.url.startsWith(LEGACY_FEATURE_URL_PREFIX) }
            val changed = internalWebsites.size != before
            if (changed) {
                updateStateLocked()
            }
            changed
        }

        if (internalWebsites.isEmpty()) {
            preloadDefaultWebsites()
        } else {
            synchronized(lock) {
                normalizeDefaultWebsitesLocked()
                updateStateLocked()
            }
            persistAsync()
        }

        if (purgedLegacy) {
            persistAsync()
        }
    }

    fun observeWebsites(): StateFlow<List<QuickWebsite>> = websitesFlow

    fun getCurrentSnapshot(): List<QuickWebsite> = websitesFlow.value

    fun addWebsite(
        title: String?,
        url: String,
        iconUrl: String? = null
    ): QuickWebsiteAddResult? {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isEmpty()) {
            return null
        }

        val result = synchronized(lock) {
            val existing = internalWebsites.firstOrNull { it.url.equals(normalizedUrl, true) }
            if (existing != null) {
                val updatedTitle = title?.takeIf { it.isNotBlank() } ?: existing.title
                if (updatedTitle != existing.title || iconUrl != null) {
                    existing.title = updatedTitle
                    existing.iconUrl = iconUrl
                    updateStateLocked()
                    persistAsync()
                }
                return@synchronized QuickWebsiteAddResult(existing.toPublic(), false)
            }

            val finalTitle = title?.takeIf { it.isNotBlank() } ?: normalizedUrl
            val site = InternalWebsite(
                id = generateId(),
                title = finalTitle,
                url = normalizedUrl,
                iconUrl = iconUrl
            )
            internalWebsites.add(site)
            updateStateLocked()
            QuickWebsiteAddResult(site.toPublic(), true)
        }

        if (result?.isNew == true) {
            persistAsync()
        }
        return result
    }

    fun updateWebsite(
        id: Long,
        title: String,
        url: String,
        iconUrl: String?,
        fallbackIconUrl: String?
    ): Boolean {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            return false
        }
        val trimmedTitle = title.trim().ifEmpty { trimmedUrl }

        val updated = synchronized(lock) {
            val site = internalWebsites.firstOrNull { it.id == id } ?: return@synchronized false
            val duplicate = internalWebsites.firstOrNull { it.id != id && it.url.equals(trimmedUrl, true) }
            if (duplicate != null) {
                return@synchronized false
            }
            site.title = trimmedTitle
            site.url = trimmedUrl
            site.iconUrl = iconUrl
            updateStateLocked()
            true
        }

        if (updated) {
            persistAsync()
        }
        return updated
    }

    fun removeWebsite(id: Long): Boolean {
        val removed = synchronized(lock) {
            val iterator = internalWebsites.iterator()
            var hasRemoved = false
            while (iterator.hasNext()) {
                if (iterator.next().id == id) {
                    iterator.remove()
                    hasRemoved = true
                    break
                }
            }
            if (hasRemoved) {
                updateStateLocked()
            }
            hasRemoved
        }
        if (removed) {
            persistAsync()
        }
        return removed
    }

    fun moveWebsite(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex == toIndex) {
            return false
        }
        val moved = synchronized(lock) {
            if (fromIndex !in internalWebsites.indices || toIndex !in internalWebsites.indices) {
                return@synchronized false
            }
            val site = internalWebsites.removeAt(fromIndex)
            internalWebsites.add(toIndex, site)
            updateStateLocked()
            true
        }
        if (moved) {
            persistAsync()
        }
        return moved
    }

    private fun loadFromDisk() {
        if (!storageFile.exists()) {
            return
        }

        val text = try {
            storageFile.readText()
        } catch (e: IOException) {
            null
        } ?: return

        if (text.isBlank()) {
            return
        }

        val json = try {
            JSONObject(text)
        } catch (_: Exception) {
            null
        } ?: return

        val maxId: Long

        synchronized(lock) {
            internalWebsites.clear()
            val array = json.optJSONArray(KEY_WEBSITES) ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                internalWebsites.add(item.toInternal())
            }
            updateStateLocked()
            maxId = internalWebsites.maxOfOrNull { it.id } ?: 0L
        }

        nextId.set(maxId + 1)
    }

    private fun preloadDefaultWebsites() {
        val defaults = listOf(
            DefaultWebsite(
                title = "ChatGPT",
                url = "https://chat.openai.com",
                iconUrl = "web_chatgpt.webp"
            ),
            DefaultWebsite(
                title = "Facebook",
                url = "https://www.facebook.com",
                iconUrl = "web_facebook.webp"
            ),
            DefaultWebsite(
                title = "Instagram",
                url = "https://www.instagram.com",
                iconUrl = "web_instagram.webp"
            ),
        )

        synchronized(lock) {
            internalWebsites.clear()
            defaults.forEach { default ->
                internalWebsites.add(
                    InternalWebsite(
                        id = generateId(),
                        title = default.title,
                        url = default.url,
                        iconUrl = default.iconUrl
                    )
                )
            }
            updateStateLocked()
        }

        persistAsync()
    }

    private fun persistAsync() {
        val snapshot = synchronized(lock) {
            val json = JSONObject()
            val array = JSONArray()
            internalWebsites.forEach { site ->
                array.put(site.toJson())
            }
            json.put(KEY_WEBSITES, array)
            json
        }

        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                storageFile.parentFile?.let { parent ->
                    if (!parent.exists()) {
                        parent.mkdirs()
                    }
                }
                storageFile.writeText(snapshot.toString())
            }
        }
    }

    private fun updateStateLocked() {
        _websitesFlow.value = internalWebsites.map { it.toPublic() }
    }

    private fun normalizeDefaultWebsitesLocked() {
        ensureDefaultWebsiteLocked("ChatGPT", "https://chat.openai.com", "web_chatgpt.webp", 0)
        ensureDefaultWebsiteLocked("Facebook", "https://www.facebook.com", "web_facebook.webp", 1)
        ensureDefaultWebsiteLocked("Instagram", "https://www.instagram.com", "web_instagram.webp", 2)
    }

    private fun ensureDefaultWebsiteLocked(
        title: String,
        url: String,
        iconUrl: String,
        targetIndex: Int
    ) {
        val existingIndex = internalWebsites.indexOfFirst { it.url.equals(url, true) }
        if (existingIndex >= 0) {
            val existing = internalWebsites.removeAt(existingIndex)
            existing.title = title
            existing.iconUrl = iconUrl
            internalWebsites.add(targetIndex.coerceAtMost(internalWebsites.size), existing)
            return
        }

        internalWebsites.add(
            targetIndex.coerceAtMost(internalWebsites.size),
            InternalWebsite(
                id = generateId(),
                title = title,
                url = url,
                iconUrl = iconUrl
            )
        )
    }

    private fun generateId(): Long {
        return nextId.getAndIncrement()
    }

    private fun JSONObject.toInternal(): InternalWebsite {
        val id = optLong("id", generateId())
        val title = optString("title", optString("url"))
        val url = optString("url", "")
        val iconUrl = optString("iconUrl", null)
        return InternalWebsite(id, title, url, iconUrl)
    }

    private fun InternalWebsite.toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("title", title)
        json.put("url", url)
        if (!iconUrl.isNullOrBlank()) {
            json.put("iconUrl", iconUrl)
        }
        return json
    }

    private fun InternalWebsite.toPublic(): QuickWebsite {
        return QuickWebsite(
            id = id,
            title = title,
            url = url,
            iconUrl = iconUrl
        )
    }

    private data class InternalWebsite(
        val id: Long,
        var title: String,
        var url: String,
        var iconUrl: String?
    )

    private data class DefaultWebsite(
        val title: String,
        val url: String,
        val iconUrl: String?
    )
}
