package com.example.browser.ui.tabs

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.R
import com.google.android.material.card.MaterialCardView
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.tabstray.thumbnail.TabThumbnailView

/**
 * 标签页 ViewHolder
 * 用于展示单个标签页的信息，包括标题、URL、图标等
 *
 * 功能：
 * 1. 显示标签页的标题和URL
 * 2. 显示网站图标
 * 3. 处理标签页点击和关闭事件
 * 4. 可选显示页面缩略图
 */
class TabViewHolder(
    itemView: View,
    private val onTabClick: (TabSessionState) -> Unit,
    private val onTabClose: (TabSessionState) -> Unit
) : RecyclerView.ViewHolder(itemView) {

    // MaterialCardView（用于更新边框）
    private val cardView: MaterialCardView = itemView as MaterialCardView

    // UI 组件
    private val ivTabIcon: ImageView = itemView.findViewById(R.id.ivTabIcon)
    private val tvTabTitle: TextView = itemView.findViewById(R.id.tvTabTitle)
    private val tvTabUrl: TextView = itemView.findViewById(R.id.tvTabUrl)
    private val ivTabClose: ImageView = itemView.findViewById(R.id.ivTabClose)
    private val tabThumbnail: TabThumbnailView? = itemView.findViewById(R.id.tabThumbnail)

    // 当前绑定的标签页
    private var currentTab: TabSessionState? = null

    init {
        // 点击标签页项 - 切换到该标签页
        itemView.setOnClickListener {
            currentTab?.let { tab ->
                onTabClick(tab)
            }
        }

        // 点击关闭按钮 - 关闭该标签页
        ivTabClose.setOnClickListener {
            currentTab?.let { tab ->
                onTabClose(tab)
            }
        }
    }

    /**
     * 绑定标签页数据到视图
     *
     * @param tab 标签页状态
     * @param isSelected 是否为当前选中的标签页
     */
    fun bind(tab: TabSessionState, isSelected: Boolean) {
        currentTab = tab

        // 显示标题，如果标题为空则显示 URL
        val title = if (tab.content.title.isNotEmpty()) {
            tab.content.title
        } else {
            tab.content.url.ifEmpty { "新标签页" }
        }
        tvTabTitle.text = title

        // 显示 URL
        tvTabUrl.text = tab.content.url.ifEmpty { "about:blank" }

        // 显示网站图标（由 BrowserIcons 组件自动管理）
        val icon = tab.content.icon
        if (icon != null) {
            ivTabIcon.setImageBitmap(icon)
            ivTabIcon.visibility = View.VISIBLE
        } else {
            // 没有图标时隐藏，由 BrowserIcons 自动加载后显示
            ivTabIcon.visibility = View.GONE
        }

        // 更新选中状态的视觉效果
        updateSelectedState(isSelected)

        // 如果有缩略图视图，可以在这里加载缩略图
        // tabThumbnail 的加载会在 Adapter 中通过 ThumbnailLoader 处理
    }

    /**
     * 更新标签页的选中状态显示
     *
     * @param isSelected 是否选中
     */
    fun updateSelectedState(isSelected: Boolean) {
        if (isSelected) {
            // 选中状态：显示蓝色边框
            cardView.strokeColor = ContextCompat.getColor(itemView.context, android.R.color.holo_blue_light)
            cardView.strokeWidth = 4
            itemView.alpha = 1.0f
        } else {
            // 未选中状态：无边框
            cardView.strokeWidth = 0
            itemView.alpha = 1.0f
        }
    }

    /**
     * 获取缩略图视图
     * 用于在 Adapter 中加载缩略图
     */
    fun getThumbnailView(): TabThumbnailView? = tabThumbnail
}
