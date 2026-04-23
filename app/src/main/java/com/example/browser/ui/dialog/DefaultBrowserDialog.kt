package com.example.browser.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.view.View
import com.example.browser.R
import com.example.browser.databinding.DialogDefaultBrowserBinding
import com.example.browser.utils.DefaultBrowserHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 默认浏览器引导弹框
 */
class DefaultBrowserDialog(
    context: Context,
    private val onLaterClick: () -> Unit,
    private val onSetDefaultClick: () -> Unit,
    private val onDialogShow: () -> Unit,
    private val onDialogDismiss: () -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialogTheme) {

    private val binding: DialogDefaultBrowserBinding

    init {
        binding = DialogDefaultBrowserBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        // 设置底部弹框行为
        behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        // 设置背景透明
        window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            setBackgroundResource(android.R.color.transparent)
        }
        setCanceledOnTouchOutside(false)
        setupClickListeners()
        onDialogShow.invoke()
    }

    private fun setupClickListeners() {
        // Later 按钮
        binding.btnLater.setOnClickListener {
            dismiss()
            onLaterClick()
        }

        // Set as default 按钮
        binding.btnSetDefault.setOnClickListener {
            dismiss()
            // 获取底层的 Activity context
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
