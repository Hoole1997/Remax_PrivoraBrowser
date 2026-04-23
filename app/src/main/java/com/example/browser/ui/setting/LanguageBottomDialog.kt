package com.example.browser.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.browser.common.loadNative
import com.example.browser.data.language.Language
import com.example.browser.data.language.SupportedLanguages
import com.example.browser.databinding.DialogLanguageBottomBinding
import com.example.browser.utils.LanguageUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import net.corekit.core.report.ReportDataManager

/**
 * 语言选择底部对话框
 */
class LanguageBottomDialog : BottomSheetDialogFragment() {

    private var _binding: DialogLanguageBottomBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LanguageAdapter
    private var selectedLanguage: Language? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLanguageBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupButtons()
        activity?.loadNative(container = binding.adsContainer)
        ReportDataManager.reportData("dialog_show", mapOf("dialog_name" to javaClass.simpleName))
    }
    override fun dismiss() {
        super.dismiss()
        ReportDataManager.reportData("dialog_dismiss", mapOf("dialog_name" to javaClass.simpleName))
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.let { bottomSheet ->
                val displayMetrics = resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels

                // 设置高度为屏幕的70%
                bottomSheet.layoutParams.height = (screenHeight * 0.7).toInt()

                val behavior =
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
                behavior.state =
                    com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
    }

    /**
     * 设置语言列表
     */
    private fun setupRecyclerView() {
        val currentLanguage = LanguageUtils.getCurrentLanguage(activity ?: return)

        // 准备语言列表，标记当前选中的语言
        val languages = SupportedLanguages.getAllLanguages().map { language ->
            language.copy(isSelected = language.code == currentLanguage)
        }.toMutableList()

        // 设置适配器
        adapter = LanguageAdapter(languages) { language ->
            selectedLanguage = language
        }

        binding.rvLanguages.layoutManager = LinearLayoutManager(activity ?: return)
        binding.rvLanguages.adapter = adapter

        // 默认选中当前语言
        selectedLanguage = languages.find { it.isSelected }
    }

    /**
     * 设置按钮点击事件
     */
    private fun setupButtons() {
        // 取消按钮
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        // 确认按钮
        binding.btnConfirm.setOnClickListener {
            selectedLanguage?.let { language ->
                val currentLanguage =
                    LanguageUtils.getCurrentLanguage(activity ?: return@setOnClickListener)

                // 如果选择的语言与当前语言不同，则切换语言
                if (language.code != currentLanguage) {
                    LanguageUtils.changeLanguage(
                        activity ?: return@setOnClickListener,
                        language.code
                    )
                }
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "LanguageBottomDialog"

        /**
         * 显示对话框
         */
        fun show(fragmentManager: FragmentManager) {
            val dialog = LanguageBottomDialog()
            dialog.show(fragmentManager, TAG)
        }
    }
}