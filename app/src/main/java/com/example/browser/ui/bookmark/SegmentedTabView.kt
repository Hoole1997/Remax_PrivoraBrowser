package com.example.browser.ui.bookmark

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.blankj.utilcode.util.ConvertUtils
import com.example.browser.R

class SegmentedTabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private data class TabHolder(val container: FrameLayout, val titleView: AppCompatTextView)

    private val tabHolders = mutableListOf<TabHolder>()
    private var selectedIndex = 0
    private var onTabSelected: ((Int) -> Unit)? = null

    private val horizontalPadding = resources.getDimensionPixelSize(R.dimen.bookmark_tab_item_horizontal_padding)
    private val verticalPadding = resources.getDimensionPixelSize(R.dimen.bookmark_tab_item_vertical_padding)
    private val horizontalSpacing = resources.getDimensionPixelSize(R.dimen.bookmark_tab_item_margin_horizontal)
    private val verticalSpacing = resources.getDimensionPixelSize(R.dimen.bookmark_tab_item_margin_vertical)
    private val tabHeight = resources.getDimensionPixelSize(R.dimen.bookmark_tab_height)

    init {
        orientation = HORIZONTAL
        background = ContextCompat.getDrawable(context, R.drawable.bg_bookmark_tabs)
        setPadding(horizontalSpacing, verticalSpacing, horizontalSpacing, verticalSpacing)
        clipToPadding = false
        clipChildren = false
    }

    fun setItems(titles: List<String>, defaultIndex: Int = 0) {
        rebuildTabs(titles)
        if (tabHolders.isEmpty()) return
        selectedIndex = defaultIndex.coerceIn(tabHolders.indices)
        refreshSelection()
    }

    /**
     * 仅重建标题，保留当前选中位置（若已超出新列表范围则自动收敛到边界）。
     * 适用于视图重建场景：调用方希望由外部 LiveData/Flow 驱动选中态，
     * 重新 setItems 时不应该把选中粗暴拉回 0。
     */
    fun setItemsKeepingSelection(titles: List<String>) {
        val previous = selectedIndex
        rebuildTabs(titles)
        if (tabHolders.isEmpty()) return
        selectedIndex = previous.coerceIn(tabHolders.indices)
        refreshSelection()
    }

    private fun rebuildTabs(titles: List<String>) {
        removeAllViews()
        tabHolders.clear()
        if (titles.isEmpty()) {
            isVisible = false
            return
        }
        isVisible = true
        titles.forEachIndexed { index, title ->
            val holder = createTabHolder(title, index)
            tabHolders.add(holder)
            addView(holder.container)
        }
    }

    fun setOnTabSelectedListener(listener: (Int) -> Unit) {
        onTabSelected = listener
    }

    fun setSelectedIndex(index: Int, notifyListener: Boolean = false) {
        if (tabHolders.isEmpty()) return
        val newIndex = index.coerceIn(tabHolders.indices)
        if (newIndex == selectedIndex) return
        selectedIndex = newIndex
        refreshSelection()
        if (notifyListener) {
            onTabSelected?.invoke(newIndex)
        }
    }

    private fun createTabHolder(title: String, position: Int): TabHolder {
        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                if (position > 0) {
                    marginStart = horizontalSpacing
                }
            }
            minimumHeight = tabHeight
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            isClickable = true
            isFocusable = true
//            foreground = ContextCompat.getDrawable(context, selectableForeground())
        }

        val textView = AppCompatTextView(context).apply {
            text = title
            setTextAppearance(R.style.BookmarkTabTextStyle)
            gravity = Gravity.CENTER
            isSingleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(ConvertUtils.dp2px(18f), 0, ConvertUtils.dp2px(18f), 0)
        }

        container.addView(
            textView,
            FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        container.setOnClickListener {
            val clickedIndex = tabHolders.indexOfFirst { it.container === container }
            if (clickedIndex != -1 && clickedIndex != selectedIndex) {
                selectedIndex = clickedIndex
                refreshSelection()
                onTabSelected?.invoke(clickedIndex)
            }
        }

        return TabHolder(container, textView)
    }

    private fun refreshSelection() {
        tabHolders.forEachIndexed { index, holder ->
            val selected = index == selectedIndex
            val backgroundRes = if (selected) {
                R.drawable.bg_tab_toggle_selected
            } else {
                R.drawable.bg_tab_toggle_unselected
            }
            holder.container.background = ContextCompat.getDrawable(context, backgroundRes)
            val colorRes = if (selected) {
                R.color.bookmark_tab_text_selected
            } else {
                R.color.bookmark_tab_text_unselected
            }
            holder.titleView.setTextColor(ContextCompat.getColor(context, colorRes))
            holder.titleView.paint.isFakeBoldText = selected
        }
    }

    private fun selectableForeground(): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
        return typedValue.resourceId
    }

    private fun titlesCount(): Int = tabHolders.size.takeIf { it > 0 } ?: 1
}
