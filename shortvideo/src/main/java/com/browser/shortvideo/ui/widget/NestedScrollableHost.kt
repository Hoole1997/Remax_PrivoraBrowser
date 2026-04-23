package com.browser.shortvideo.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.absoluteValue

/**
 * 解决 ViewPager2 嵌套竖向滚动组件时的滑动冲突问题
 * 
 * 官方推荐方案：https://developer.android.com/develop/ui/views/animations/vp2-migration#nested-scrollables
 * 
 * 使用方式：将此 NestedScrollableHost 包裹在 ViewPager2 内部的可滚动组件外层
 */
class NestedScrollableHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var touchSlop = 0
    private var initialX = 0f
    private var initialY = 0f
    private val parentViewPager: ViewPager2?
        get() {
            var v: View? = parent as? View
            while (v != null && v !is ViewPager2) {
                v = v.parent as? View
            }
            return v as? ViewPager2
        }

    private val child: View? get() = if (childCount > 0) getChildAt(0) else null

    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    private fun canChildScroll(orientation: Int, delta: Float): Boolean {
        val direction = -delta.toInt()
        return when (orientation) {
            0 -> child?.canScrollHorizontally(direction) ?: false
            1 -> child?.canScrollVertically(direction) ?: false
            else -> throw IllegalArgumentException()
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        handleInterceptTouchEvent(e)
        return super.onInterceptTouchEvent(e)
    }

    private fun handleInterceptTouchEvent(e: MotionEvent) {
        val orientation = parentViewPager?.orientation ?: return

        // 如果子 View 无法在该方向滚动，不拦截
        if (!canChildScroll(orientation, -1f) && !canChildScroll(orientation, 1f)) {
            return
        }

        if (e.action == MotionEvent.ACTION_DOWN) {
            initialX = e.x
            initialY = e.y
            parent.requestDisallowInterceptTouchEvent(true)
        } else if (e.action == MotionEvent.ACTION_MOVE) {
            val dx = e.x - initialX
            val dy = e.y - initialY
            val isVpHorizontal = orientation == ViewPager2.ORIENTATION_HORIZONTAL

            // 判断主要滑动方向
            val scaledDx = dx.absoluteValue * if (isVpHorizontal) .5f else 1f
            val scaledDy = dy.absoluteValue * if (isVpHorizontal) 1f else .5f

            if (scaledDx > touchSlop || scaledDy > touchSlop) {
                if (isVpHorizontal == (scaledDy > scaledDx)) {
                    // 竖直滑动 on 水平 VP, 或 水平滑动 on 垂直 VP
                    // 允许所有父容器拦截
                    parent.requestDisallowInterceptTouchEvent(false)
                } else {
                    // 主要是 VP 方向的滑动
                    if (canChildScroll(orientation, if (isVpHorizontal) dx else dy)) {
                        // 子 View 还能在该方向滚动，不让 VP 拦截
                        parent.requestDisallowInterceptTouchEvent(true)
                    } else {
                        // 子 View 已到边界，让 VP 拦截
                        parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
        }
    }
}
