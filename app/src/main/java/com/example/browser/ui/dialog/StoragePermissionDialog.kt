package com.example.browser.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.example.browser.R
import com.example.browser.databinding.DialogStoragePermissionBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import net.corekit.core.report.ReportDataManager

/**
 * 存储权限引导弹框
 */
class StoragePermissionDialog(
    context: Context,
    private val onLaterClick: (() -> Unit)? = null,
    private val onGoNowClick: () -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialogTheme) {

    private val binding: DialogStoragePermissionBinding

    init {
        binding = DialogStoragePermissionBinding.inflate(LayoutInflater.from(context))
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
        
        setCanceledOnTouchOutside(true)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Later 按钮
        binding.btnLater.setOnClickListener {
            ReportDataManager.reportData("Storage_Permission_Later_Click",mapOf())
            dismiss()
            onLaterClick?.invoke()
        }

        // Go Now 按钮
        binding.btnGoNow.setOnClickListener {
            ReportDataManager.reportData("Storage_Permission_Agree_Click",mapOf())
            dismiss()
            onGoNowClick()
        }
    }
}
