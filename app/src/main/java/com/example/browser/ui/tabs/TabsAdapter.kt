package com.example.browser.ui.tabs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.R
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader
import mozilla.components.concept.base.images.ImageLoadRequest

/**
 * 标签页列表适配器
 * 使用 DiffUtil 优化列表更新性能
 *
 * 功能：
 * 1. 显示标签页列表
 * 2. 支持标签页点击和关闭
 * 3. 支持缩略图加载
 * 4. 区分当前选中的标签页
 */
class TabsAdapter(
    private val thumbnailLoader: ThumbnailLoader? = null,
    private val onTabClick: (TabSessionState) -> Unit,
    private val onTabClose: (TabSessionState) -> Unit
) : ListAdapter<TabsAdapter.TabListItem, RecyclerView.ViewHolder>(TabDiffCallback()) {

    // 当前选中的标签页 ID
    private var selectedTabId: String? = null

    /**
     * 创建 ViewHolder
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TAB -> {
                val view = inflater.inflate(R.layout.item_browser_tab, parent, false)
                TabViewHolder(view, onTabClick, onTabClose)
            }
            VIEW_TYPE_EMPTY_NORMAL -> EmptyViewHolder(
                inflater.inflate(R.layout.layout_empty_normal, parent, false)
            )
            else -> EmptyViewHolder(
                inflater.inflate(R.layout.layout_empty_incognito, parent, false)
            )
        }
    }

    /**
     * 绑定数据到 ViewHolder
     * 注意：这个方法会在每次item需要显示时被调用
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TabListItem.TabItem -> {
                val tabHolder = holder as TabViewHolder
                val tab = item.tab
                val isSelected = item.isSelected  // ✅ 使用 TabItem 中的 isSelected
                tabHolder.bind(tab, isSelected)
                thumbnailLoader?.let { loader ->
                    tabHolder.getThumbnailView()?.let { thumbnailView ->
                        val thumbnailSize = (200 * thumbnailView.context.resources.displayMetrics.density).toInt()
                        loader.loadIntoView(
                            thumbnailView,
                            ImageLoadRequest(
                                id = tab.id,
                                size = thumbnailSize,
                                isPrivate = tab.content.private
                            )
                        )
                    }
                }
            }
            is TabListItem.EmptyItem -> {
                // 无需额外绑定逻辑，布局自身展示静态内容
            }
        }
    }

    /**
     * 带 payload 的绑定方法
     * 用于优化局部更新（例如只更新选中状态）
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            // 没有 payload，执行完整绑定
            onBindViewHolder(holder, position)
        } else {
            // 有 payload，只更新选中状态
            when (val item = getItem(position)) {
                is TabListItem.TabItem -> {
                    val tabHolder = holder as TabViewHolder
                    val isSelected = item.isSelected  // ✅ 使用 TabItem 中的 isSelected
                    tabHolder.updateSelectedState(isSelected)
                }
                else -> {
                    // EmptyItem 不需要更新
                }
            }
        }
    }

    /**
     * 更新标签页列表
     *
     * @param tabs 新的标签页列表
     * @param selectedId 当前选中的标签页 ID
     */
    fun updateTabs(tabs: List<TabSessionState>, selectedId: String?, isPrivateMode: Boolean) {
        selectedTabId = selectedId
        
        val items = if (tabs.isEmpty()) {
            listOf(TabListItem.EmptyItem(isPrivateMode))
        } else {
            tabs.map { TabListItem.TabItem(it, it.id == selectedId) }
        }
        
        submitList(items)
    }

    /**
     * 仅更新选中的标签页 ID
     *
     * @param selectedId 当前选中的标签页 ID
     */
    fun updateSelectedId(selectedId: String?) {
        val oldSelectedId = selectedTabId
        if (oldSelectedId == selectedId) {
            return  // 没有变化，不需要更新
        }
        
        selectedTabId = selectedId
        
        // 刷新旧的选中项（取消选中状态）
        oldSelectedId?.let { oldId ->
            val oldPosition = currentList.indexOfFirst { 
                it is TabListItem.TabItem && it.tab.id == oldId 
            }
            if (oldPosition >= 0) {
                notifyItemChanged(oldPosition, PAYLOAD_SELECTION_CHANGED)
            }
        }
        
        // 刷新新的选中项（应用选中状态）
        selectedId?.let { newId ->
            val newPosition = currentList.indexOfFirst { 
                it is TabListItem.TabItem && it.tab.id == newId 
            }
            if (newPosition >= 0) {
                notifyItemChanged(newPosition, PAYLOAD_SELECTION_CHANGED)
            }
        }
    }

    fun isFullSpanPosition(position: Int): Boolean {
        if (position < 0 || position >= itemCount) {
            return false
        }
        return getItemViewType(position) != VIEW_TYPE_TAB
    }

    /**
     * DiffUtil 回调
     * 用于计算列表差异，提高更新性能
     */
    private class TabDiffCallback : DiffUtil.ItemCallback<TabListItem>() {
        override fun areItemsTheSame(oldItem: TabListItem, newItem: TabListItem): Boolean {
            return when {
                oldItem is TabListItem.TabItem && newItem is TabListItem.TabItem ->
                    oldItem.tab.id == newItem.tab.id
                oldItem is TabListItem.EmptyItem && newItem is TabListItem.EmptyItem ->
                    oldItem.isPrivateMode == newItem.isPrivateMode
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: TabListItem, newItem: TabListItem): Boolean {
            return when {
                oldItem is TabListItem.TabItem && newItem is TabListItem.TabItem ->
                    oldItem.tab.content.url == newItem.tab.content.url &&
                            oldItem.tab.content.title == newItem.tab.content.title &&
                            oldItem.tab.content.icon == newItem.tab.content.icon &&
                            oldItem.tab.content.private == newItem.tab.content.private &&
                            oldItem.tab.content.loading == newItem.tab.content.loading &&
                            oldItem.isSelected == newItem.isSelected  // ✅ 检测选中状态变化
                oldItem is TabListItem.EmptyItem && newItem is TabListItem.EmptyItem -> true
                else -> false
            }
        }
    }

    class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view)

    sealed class TabListItem {
        data class TabItem(val tab: TabSessionState, val isSelected: Boolean = false) : TabListItem()
        data class EmptyItem(val isPrivateMode: Boolean) : TabListItem()
    }

    companion object {
        private const val VIEW_TYPE_TAB = 0
        private const val VIEW_TYPE_EMPTY_NORMAL = 1
        private const val VIEW_TYPE_EMPTY_PRIVATE = 2
        
        // Payload 用于局部更新
        private const val PAYLOAD_SELECTION_CHANGED = "selection_changed"
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is TabListItem.TabItem -> VIEW_TYPE_TAB
            is TabListItem.EmptyItem -> if (item.isPrivateMode) VIEW_TYPE_EMPTY_PRIVATE else VIEW_TYPE_EMPTY_NORMAL
        }
    }
}
