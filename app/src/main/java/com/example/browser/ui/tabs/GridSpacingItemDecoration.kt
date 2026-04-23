package com.example.browser.ui.tabs

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Grid 布局的间距装饰器
 * 用于为 GridLayoutManager 添加统一的 item 间距
 *
 * @param spanCount Grid 的列数
 * @param spacing 间距大小（单位：dp）
 * @param includeEdge 是否在边缘也添加间距
 */
class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacing: Int,
    private val includeEdge: Boolean
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        // 将 dp 转换为 px
        val spacingPx = (spacing * view.context.resources.displayMetrics.density).toInt()

        // item position
        val position = parent.getChildAdapterPosition(view)
        // item column
        val column = position % spanCount

        if (includeEdge) {
            // 左间距 = spacing - column * ((1f / spanCount) * spacing)
            outRect.left = spacingPx - column * spacingPx / spanCount
            // 右间距 = (column + 1) * ((1f / spanCount) * spacing)
            outRect.right = (column + 1) * spacingPx / spanCount

            if (position < spanCount) {
                // 第一行顶部间距
                outRect.top = spacingPx
            }
            // 每个 item 底部间距
            outRect.bottom = spacingPx
        } else {
            // 左间距 = column * ((1f / spanCount) * spacing)
            outRect.left = column * spacingPx / spanCount
            // 右间距 = spacing - (column + 1) * ((1f / spanCount) * spacing)
            outRect.right = spacingPx - (column + 1) * spacingPx / spanCount

            if (position >= spanCount) {
                // 非第一行顶部间距
                outRect.top = spacingPx
            }
        }
    }
}
