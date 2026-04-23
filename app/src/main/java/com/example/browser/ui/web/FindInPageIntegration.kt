package com.example.browser.ui.web

import android.view.View
import android.view.ViewStub
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.findinpage.FindInPageFeature
import mozilla.components.feature.findinpage.view.FindInPageView
import mozilla.components.support.base.feature.LifecycleAwareFeature

/**
 * Find in Page 集成类
 * 负责管理页面查找功能的延迟加载和生命周期
 */
class FindInPageIntegration(
    private val store: BrowserStore,
    stub: ViewStub,
    private val engineView: EngineView,
    private val onClose: () -> Unit
) : InflationAwareFeature(stub) {

    /**
     * 当 view 被 inflate 时创建 FindInPageFeature
     */
    override fun onViewInflated(view: View): LifecycleAwareFeature {
        return FindInPageFeature(
            store = store,
            view = view as FindInPageView,
            engineView = engineView
        ) {
            // 关闭时隐藏 view 并调用回调
            view.visibility = View.GONE
            onClose()
        }
    }

    /**
     * feature 启动时的操作
     */
    override fun onLaunch(view: View, feature: LifecycleAwareFeature) {
        store.state.selectedTab?.let { tab ->
            view.visibility = View.VISIBLE
            (feature as FindInPageFeature).bind(tab)
        }
    }
}
