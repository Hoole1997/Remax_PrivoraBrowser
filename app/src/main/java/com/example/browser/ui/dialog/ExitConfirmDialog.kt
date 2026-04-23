package com.example.browser.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.ext.AdShowExt
import com.example.browser.R
import com.example.browser.databinding.DialogExitConfirmBinding
import kotlinx.coroutines.launch

class ExitConfirmDialog : DialogFragment() {

    private var _binding: DialogExitConfirmBinding? = null
    private val binding get() = _binding!!

    private var onConfirmExitListener: (() -> Unit)? = null
    private var onCancelListener: (() -> Unit)? = null

    companion object {
        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            onConfirmExit: () -> Unit,
            onCancel: (() -> Unit)? = null
        ): ExitConfirmDialog {
            val dialog = ExitConfirmDialog()
            dialog.onConfirmExitListener = onConfirmExit
            dialog.onCancelListener = onCancel
            dialog.show(fragmentManager, "exit_confirm_dialog")
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.ExitConfirmDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogExitConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnConfirmExit.setOnClickListener {
            dismiss()
            onConfirmExitListener?.invoke()
        }

        binding.btnCancelExit.setOnClickListener {
            dismiss()
            onCancelListener?.invoke()
        }

        loadNativeAd()
    }

    private fun loadNativeAd() {
        lifecycleScope.launch {
            try {
                val adContainer = _binding?.flExitAd ?: return@launch
                val success = AdShowExt.showNativeAdInContainer(activity?:return@launch,adContainer)
                _binding?.flExitAd?.visibility = if (success) View.VISIBLE else View.GONE
            } catch (_: Exception) {
                _binding?.flExitAd?.visibility = View.GONE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.7f)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
