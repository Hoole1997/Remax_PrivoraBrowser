package com.example.browser.view

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.browser.databinding.DialogConfirmBinding
import net.corekit.core.report.ReportDataManager

class ConfirmDialog : DialogFragment() {

    companion object {
        fun show(
            supportFragmentManager: FragmentManager,
            title: String,
            content: String,
            button: String
        ) : ConfirmDialog{
            val dialog = ConfirmDialog()
            dialog.arguments = Bundle().apply {
                putString("title", title)
                putString("content", content)
                putString("button", button)
            }
            dialog.show(supportFragmentManager, "confirm_dialog")
            return dialog
        }
    }

    private var binding: DialogConfirmBinding? = null
    private var onConfirm: (() -> Unit)? = {}
    private var onCancel: (() -> Unit)? = {}
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DialogConfirmBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            binding?.tvNoticeDialogTitle?.text = it.getString("title")
            binding?.tvNoticeDialogContent?.text = it.getString("content")
            binding?.btnNoticeDialogConfirm?.text = it.getString("button")
        }
        binding?.btnNoticeDialogConfirm?.setOnClickListener {
            onConfirm?.invoke()
            dismiss()
        }
        binding?.ivClose?.setOnClickListener {
            onCancel?.invoke()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        // Set transparent background
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        ReportDataManager.reportData("dialog_show", mapOf("dialog_name" to javaClass.simpleName))
    }

    override fun dismiss() {
        super.dismiss()
        ReportDataManager.reportData("dialog_dismiss", mapOf("dialog_name" to javaClass.simpleName))
    }

    fun setOnConfirmListener(listener: () -> Unit) {
        onConfirm = listener
    }

    fun setOnCancelListener(listener: () -> Unit) {
        onCancel = listener
    }

}