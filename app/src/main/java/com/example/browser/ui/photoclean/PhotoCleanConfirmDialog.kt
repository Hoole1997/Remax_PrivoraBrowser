package com.example.browser.ui.photoclean

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.browser.databinding.DialogPhotoCleanConfirmBinding

class PhotoCleanConfirmDialog : DialogFragment() {

    private var _binding: DialogPhotoCleanConfirmBinding? = null
    private val binding get() = _binding!!

    private var onConfirm: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_CONFIRM_TEXT = "confirm_text"
        private const val ARG_CANCEL_TEXT = "cancel_text"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            message: String,
            confirmText: String,
            cancelText: String,
            onConfirm: (() -> Unit)? = null,
            onCancel: (() -> Unit)? = null
        ): PhotoCleanConfirmDialog {
            val dialog = PhotoCleanConfirmDialog()
            dialog.arguments = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_MESSAGE, message)
                putString(ARG_CONFIRM_TEXT, confirmText)
                putString(ARG_CANCEL_TEXT, cancelText)
            }
            dialog.onConfirm = onConfirm
            dialog.onCancel = onCancel
            dialog.show(fragmentManager, "photo_clean_confirm")
            return dialog
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPhotoCleanConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let { args ->
            binding.tvDialogTitle.text = args.getString(ARG_TITLE, "")
            binding.tvDialogMessage.text = args.getString(ARG_MESSAGE, "")
            binding.btnDialogConfirm.text = args.getString(ARG_CONFIRM_TEXT, "")
            binding.btnDialogCancel.text = args.getString(ARG_CANCEL_TEXT, "")
        }

        binding.btnDialogConfirm.setOnClickListener {
            dismiss()
            onConfirm?.invoke()
        }

        binding.btnDialogCancel.setOnClickListener {
            dismiss()
            onCancel?.invoke()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            (decorView as? ViewGroup)?.let { decor ->
                decor.clipChildren = false
                decor.clipToPadding = false
                for (i in 0 until decor.childCount) {
                    (decor.getChildAt(i) as? ViewGroup)?.clipChildren = false
                }
            }
        }
        dialog?.setCanceledOnTouchOutside(false)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
