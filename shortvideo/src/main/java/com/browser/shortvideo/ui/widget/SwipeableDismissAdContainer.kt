package com.browser.shortvideo.ui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 可滑动关闭的广告容器
 * 
 * 用于包装全屏原生广告，允许用户通过垂直滑动手势关闭广告
 * 类似抖音的广告关闭体验
 */
class SwipeableDismissAdContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface OnDismissListener {
        fun onDismiss()
    }

    private var dismissListener: OnDismissListener? = null
    private var initialY = 0f
    private var isDragging = false
    
    // 滑动阈值 - 超过此距离触发关闭 (增大以防止误触)
    private val dismissThreshold = 400f
    // 最小滑动速度阈值 (增大以防止误触)
    private val minVelocityThreshold = 1500f
    
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            // 检测快速垂直滑动
            if (abs(velocityY) > minVelocityThreshold && abs(velocityY) > abs(velocityX)) {
                animateDismiss(velocityY > 0)
                return true
            }
            return false
        }
    })

    fun setOnDismissListener(listener: OnDismissListener?) {
        this.dismissListener = listener
    }
    
    fun setOnDismissListener(listener: () -> Unit) {
        this.dismissListener = object : OnDismissListener {
            override fun onDismiss() {
                listener()
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialY = ev.rawY
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = ev.rawY - initialY
                // 如果垂直滑动距离超过触摸阈值，开始拦截
                if (abs(deltaY) > 30 && !isDragging) {
                    isDragging = true
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.rawY - initialY
                // 跟随手指移动
                translationY = deltaY * 0.5f  // 添加阻尼效果
                
                // 根据移动距离调整透明度
                val progress = abs(deltaY) / dismissThreshold
                alpha = (1f - progress * 0.3f).coerceIn(0.7f, 1f)
                
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val deltaY = event.rawY - initialY
                
                if (abs(deltaY) > dismissThreshold) {
                    // 超过阈值，触发关闭动画
                    animateDismiss(deltaY > 0)
                } else {
                    // 未超过阈值，弹回原位
                    animateBack()
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 关闭动画 - 滑出屏幕
     */
    private fun animateDismiss(slideDown: Boolean) {
        val targetY = if (slideDown) height.toFloat() else -height.toFloat()
        
        animate()
            .translationY(targetY)
            .alpha(0f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = GONE
                    translationY = 0f
                    alpha = 1f
                    dismissListener?.onDismiss()
                }
            })
            .start()
    }

    /**
     * 弹回动画 - 恢复原位
     */
    private fun animateBack() {
        animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(150)
            .setListener(null)
            .start()
    }

    /**
     * 重置状态
     */
    fun reset() {
        translationY = 0f
        alpha = 1f
        visibility = VISIBLE
    }
}
