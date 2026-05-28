package com.example.browser.ui.photoclean

import com.example.browser.ui.photoclean.model.PhotoCleanGroup
import java.io.File
import java.util.UUID

/**
 * 进程级内存中转站，负责在 [PhotoCleanActivity] 与 [PhotoDeleteProgressActivity] 之间
 * 传递无法塞进 Intent 的大对象（分组数据、待删文件列表）。
 *
 * 替换原先基于 cacheDir + JSON 的 PhotoCleanSessionStore 实现：
 * - 主线程不再做磁盘 IO 和 json 序列化，低端机滑动/点击体验更顺。
 * - 数据生命周期跟随宿主进程；进程被杀后即丢弃，由调用方走启动页重新发起扫描即可。
 *
 * ⚠️ 这里只覆盖「Activity 之间」的数据交接。"进程死亡 → 系统恢复 task 直接重建 A"
 * 这种极端场景不在本类承诺范围内；如有需要，给 [PhotoCleanViewModel] 注入
 * SavedStateHandle 单独兜底。
 */
object PhotoCleanSession {

    private data class Snapshot(
        val groups: List<PhotoCleanGroup>? = null,
        val files: List<File>? = null,
    )

    private val store = mutableMapOf<String, Snapshot>()

    /** 写入分组数据，返回新 key。后续可调用 [updateGroups] 用这个 key 覆盖更新。 */
    @Synchronized
    fun putGroups(groups: List<PhotoCleanGroup>): String {
        val key = UUID.randomUUID().toString()
        store[key] = Snapshot(groups = groups)
        return key
    }

    /** 用同一个 key 覆盖更新分组数据。key 不存在时会自动建一条快照。 */
    @Synchronized
    fun updateGroups(key: String?, groups: List<PhotoCleanGroup>) {
        if (key.isNullOrBlank()) return
        val previous = store[key] ?: Snapshot()
        store[key] = previous.copy(groups = groups)
    }

    /** 读取分组数据；key 为空或快照已被清理时返回空列表。 */
    @Synchronized
    fun getGroups(key: String?): List<PhotoCleanGroup> {
        if (key.isNullOrBlank()) return emptyList()
        return store[key]?.groups.orEmpty()
    }

    /** 写入待删文件列表，返回新 key。 */
    @Synchronized
    fun putFiles(files: List<File>): String {
        val key = UUID.randomUUID().toString()
        store[key] = Snapshot(files = files)
        return key
    }

    /** 读取待删文件列表；key 为空或快照已被清理时返回空列表。 */
    @Synchronized
    fun getFiles(key: String?): List<File> {
        if (key.isNullOrBlank()) return emptyList()
        return store[key]?.files.orEmpty()
    }

    /** 清理该 key 对应的快照。Activity finish 时调用，避免长期占用内存。 */
    @Synchronized
    fun clear(key: String?) {
        if (key.isNullOrBlank()) return
        store.remove(key)
    }
}
