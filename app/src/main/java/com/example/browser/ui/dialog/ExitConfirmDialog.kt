package com.example.browser.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.ext.AdShowExt
import com.example.browser.R
import com.example.browser.databinding.DialogExitConfirmBinding
import com.example.browser.ui.insets.applyBottomDialogSafeAreaPadding
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

        // Dialog 拥有独立 Window，必须在自己的内容层处理系统导航栏安全区。
        binding.exitDialogContent.applyBottomDialogSafeAreaPadding()

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
            // targetSdk 35+ 会强制 edge-to-edge；旧系统也统一使用同一套 Insets 逻辑。
            WindowCompat.setDecorFitsSystemWindows(this, false)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.7f)
            WindowCompat.getInsetsController(this, decorView)
                .isAppearanceLightNavigationBars = true
        }

        // Window 参数在 onStart 才稳定，主动请求一次以覆盖首次展示和导航模式切换。
        _binding?.exitDialogContent?.let(ViewCompat::requestApplyInsets)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
