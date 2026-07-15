package com.example.browser.ui.tabs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.state.BrowserState

/**
 * Tab 数量在不同入口的统一展示规则。
 *
 * 计数选择器只依赖 [BrowserState.tabs]，避免把 selectedTab 等可能为空的状态
 * 当作刷新条件，确保首页顶部和底部导航始终消费同一份数据。
 */
internal object TabCountUi {
    private const val MAX_DISPLAY_COUNT = 99

    fun format(count: Int): String {
        return count.coerceIn(0, MAX_DISPLAY_COUNT).toString()
    }
}

/** 仅在窗口总数真正变化时通知 UI，减少与数量无关的 BrowserState 刷新。 */
internal fun Flow<BrowserState>.tabCountChanges(): Flow<Int> {
    return map { state -> state.tabs.size }
        .distinctUntilChanged()
}
