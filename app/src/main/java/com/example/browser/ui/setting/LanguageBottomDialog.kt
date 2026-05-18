package com.example.browser.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.browser.common.loadNative
import com.example.browser.data.language.SupportedLanguages
import com.example.browser.databinding.DialogLanguageBottomBinding
import com.example.browser.utils.LanguageUtils
import com.example.browser.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.corekit.core.report.ReportDataManager

/**
 * 语言选择底部对话框 - 小巧精致版
 * 点击即选中并自动切换语言
 */
class LanguageBottomDialog : BottomSheetDialogFragment() {

    private var _binding: DialogLanguageBottomBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LanguageAdapter

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogLanguageBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        activity?.loadNative(container = binding.adsContainer)
        ReportDataManager.reportData("dialog_show", mapOf("dialog_name" to javaClass.simpleName))
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.let { bottomSheet ->
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
    }

    override fun dismiss() {
        super.dismiss()
        ReportDataManager.reportData("dialog_dismiss", mapOf("dialog_name" to javaClass.simpleName))
    }

    private fun setupRecyclerView() {
        val currentLanguage = LanguageUtils.getCurrentLanguage(activity ?: return)

        val languages = SupportedLanguages.getAllLanguages().map { language ->
            language.copy(isSelected = language.code == currentLanguage)
        }.toMutableList()

        adapter = LanguageAdapter(languages) { language ->
            val current = LanguageUtils.getCurrentLanguage(activity ?: return@LanguageAdapter)

            if (language.code != current) {
                LanguageUtils.changeLanguage(activity ?: return@LanguageAdapter, language.code)
            }
            dismiss()
        }

        binding.rvLanguages.layoutManager = LinearLayoutManager(activity ?: return)
        binding.rvLanguages.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "LanguageBottomDialog"

        fun show(fragmentManager: FragmentManager) {
            val dialog = LanguageBottomDialog()
            dialog.show(fragmentManager, TAG)
        }
    }
}
