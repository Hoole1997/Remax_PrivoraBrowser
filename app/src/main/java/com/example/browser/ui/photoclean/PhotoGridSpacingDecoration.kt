package com.example.browser.ui.photoclean

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class PhotoGridSpacingDecoration(
    private val spanCount: Int,
    private val spacingPx: Int,
    private val isFullSpan: (position: Int) -> Boolean
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        if (isFullSpan(position)) {
            outRect.set(0, 0, 0, 0)
            return
        }

        // 计算照片在当前分组中的列位置
        var photoIndexInGroup = 0
        var count = 0
        for (i in 0..position) {
            if (!isFullSpan(i)) {
                if (i == position) {
                    photoIndexInGroup = count % spanCount
                }
                count++
            }
        }

        val column = photoIndexInGroup
        outRect.left = column * spacingPx / spanCount
        outRect.right = spacingPx - (column + 1) * spacingPx / spanCount
        outRect.bottom = spacingPx
    }
}
