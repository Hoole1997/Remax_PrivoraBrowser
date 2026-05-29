package com.example.browser.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.example.browser.R
import com.example.browser.databinding.DialogDefaultBrowserBinding
import com.example.browser.utils.DefaultBrowserHelper

/**
 * 默认浏览器引导弹框（全屏样式）
 *
 * 设计稿：Figma node 19145-12448
 * 顶部 Skip / 中部 Logo + 系统设置默认浏览器示意卡片 / 底部 Set as Default + Later。
 */
class DefaultBrowserDialog(
    context: Context,
    private val onLaterClick: () -> Unit,
    private val onSetDefaultClick: () -> Unit,
    private val onDialogShow: () -> Unit,
    private val onDialogDismiss: () -> Unit
) : Dialog(context, R.style.FullScreenDialogTheme) {

    private lateinit var binding: DialogDefaultBrowserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogDefaultBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 全屏铺满 + 透明状态栏
        window?.let { w ->
            w.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            w.setBackgroundDrawableResource(android.R.color.transparent)
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.statusBarColor = Color.TRANSPARENT
            // 浅色背景下使用深色状态栏图标
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                w.decorView.systemUiVisibility =
                    w.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }

        setCanceledOnTouchOutside(false)
        setupClickListeners()
    }

    override fun show() {
        super.show()
        onDialogShow.invoke()
    }

    private fun setupClickListeners() {
        // 顶部 Skip 与底部 Later 行为一致
        val laterAction = View.OnClickListener {
            dismiss()
            onLaterClick()
        }
        binding.btnSkip.setOnClickListener(laterAction)
        binding.btnLater.setOnClickListener(laterAction)

        // Set as default 按钮
        binding.btnSetDefault.setOnClickListener {
            dismiss()
            val activityContext = getActivityFromContext(context)
            DefaultBrowserHelper.requestDefaultBrowser(activityContext ?: context)
            onSetDefaultClick()
        }
    }

    /**
     * 从 Context 中获取 Activity
     */
    private fun getActivityFromContext(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    override fun dismiss() {
        super.dismiss()
        onDialogDismiss.invoke()
    }
}
