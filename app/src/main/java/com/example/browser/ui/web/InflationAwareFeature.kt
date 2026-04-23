package com.example.browser.ui.web

import android.view.View
import android.view.ViewStub
import androidx.annotation.UiThread
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import java.lang.ref.WeakReference

/**
 * 支持延迟加载 View 的 Feature 基类
 * 当需要启动功能时（如用户交互），调用 [launch] 会在那时才 inflate view，
 * 启动 feature，然后执行 [onLaunch] 进行特定的启动操作
 */
abstract class InflationAwareFeature(
    private val stub: ViewStub
) : LifecycleAwareFeature, UserInteractionHandler {

    internal lateinit var view: WeakReference<View>
    internal var feature: LifecycleAwareFeature? = null
    
    private val stubListener = ViewStub.OnInflateListener { _, inflated ->
        view = WeakReference(inflated)
        feature = onViewInflated(inflated).also {
            it.start()
            onLaunch(inflated, it)
        }
    }

    /**
     * 启动功能
     * 如果 view 已经 inflate，立即启动；否则先 inflate view
     */
    @UiThread
    fun launch() {
        // 如果已经有 feature 和 view，可以立即启动
        if (feature != null && view.get() != null) {
            onLaunch(view.get()!!, feature!!)
        } else {
            stub.apply {
                setOnInflateListener(stubListener)
                inflate()
            }
        }
    }

    /**
     * 由于只在 view inflate 时才启动 feature，这个方法什么都不做
     */
    override fun start() {
        feature?.start()
    }

    override fun stop() {
        feature?.stop()
    }

    /**
     * 处理返回键
     */
    override fun onBackPressed(): Boolean {
        return (feature as? UserInteractionHandler)?.onBackPressed() ?: false
    }

    /**
     * 当 view 被 inflate 时调用，用于创建 feature
     * @param view 新创建的 view
     * @return 使用该 view 初始化的 feature
     */
    abstract fun onViewInflated(view: View): LifecycleAwareFeature

    /**
     * feature 实例化后调用。如果 feature 已存在，立即调用
     * @param view 附加到 feature 的 view
     * @param feature 实例化的 feature
     */
    abstract fun onLaunch(view: View, feature: LifecycleAwareFeature)
}
