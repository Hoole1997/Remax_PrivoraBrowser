package com.example.browser.ui.insets

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 将底部弹框的内容放进系统栏安全区。
 *
 * 初始 padding 只在 View 创建时保存一次，之后每次 Insets 变化都基于初始值计算，
 * 避免切换手势导航/三键导航或窗口尺寸时重复累加 padding。
 */
internal fun View.applyBottomDialogSafeAreaPadding() {
    val initialPaddingLeft = paddingLeft
    val initialPaddingTop = paddingTop
    val initialPaddingRight = paddingRight
    val initialPaddingBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val safeInsets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout(),
        )

        // 底部弹框不需要避让状态栏顶部，只叠加可能遮挡内容的左、右、下安全区。
        view.setPadding(
            initialPaddingLeft + safeInsets.left,
            initialPaddingTop,
            initialPaddingRight + safeInsets.right,
            initialPaddingBottom + safeInsets.bottom,
        )
        windowInsets
    }
}
