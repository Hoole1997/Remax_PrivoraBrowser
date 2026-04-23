package com.example.browser.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/**
 * 自定义ViewPager，禁用边界过度滑动
 * 防止滑动到边界后继续滑动到其他区域
 */
class NonSwipeViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewPager(context, attrs) {

    private var startX = 0f
    private var startY = 0f
    private var isViewPagerDragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                isViewPagerDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = abs(event.x - startX)
                val deltaY = abs(event.y - startY)

                // 只有当水平滑动距离明显大于垂直滑动距离时，才认为是ViewPager滑动
                if (deltaX > deltaY && deltaX > 50) {
                    val currentItem = currentItem
                    val adapter = adapter

                    if (adapter != null) {
                        val isSwipeToNext = event.x < startX
                        val isSwipeToPrev = event.x > startX

                        // 检查是否在边界
                        val isAtFirstPage = currentItem == 0
                        val isAtLastPage = currentItem == adapter.count - 1

                        // 如果在第一页向右滑动或在最后一页向左滑动，则不拦截事件
                        if ((isAtFirstPage && isSwipeToPrev) || (isAtLastPage && isSwipeToNext)) {
                            return false
                        }

                        isViewPagerDragging = true
                        return super.onInterceptTouchEvent(event)
                    }
                }
            }
        }

        return if (isViewPagerDragging) {
            super.onInterceptTouchEvent(event)
        } else {
            false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (isViewPagerDragging) {
            try {
                super.onTouchEvent(event)
            } catch (e: IllegalArgumentException) {
                false
            }
        } else {
            false
        }
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        val currentItem = currentItem
        val adapter = adapter ?: return false

        // 检查滑动方向和当前位置
        return when {
            direction < 0 && currentItem <= 0 -> false // 向右滑动但已在第一页
            direction > 0 && currentItem >= adapter.count - 1 -> false // 向左滑动但已在最后一页
            else -> super.canScrollHorizontally(direction)
        }
    }
}