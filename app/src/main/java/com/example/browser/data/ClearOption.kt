package com.example.browser.data

import androidx.annotation.StringRes

/**
 * 清除数据选项数据类
 */
data class ClearOption(
    val type: ClearType,
    @StringRes val nameResId: Int,
    var isSelected: Boolean = true
)

/**
 * 清除数据类型
 */
enum class ClearType {
    TABS,
    HISTORY,
    COOKIES,
    CACHE
}
