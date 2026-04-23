package com.example.browser.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.blankj.utilcode.util.ToastUtils
import com.example.browser.R
import com.example.browser.components
import com.example.browser.data.ClearOption
import com.example.browser.data.ClearType
import com.example.browser.databinding.DialogClearDataBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.Engine
import net.corekit.core.report.ReportDataManager

/**
 * 清除数据底部对话框
 */
class ClearDataBottomDialog : BottomSheetDialogFragment() {
    
    private var _binding: DialogClearDataBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: ClearOptionAdapter
    private val options = mutableListOf<ClearOption>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogClearDataBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupData()
        setupRecyclerView()
        setupButtons()
        ReportDataManager.reportData("dialog_show", mapOf("dialog_name" to javaClass.simpleName))
    }

    override fun dismiss() {
        super.dismiss()
        ReportDataManager.reportData("dialog_dismiss", mapOf("dialog_name" to javaClass.simpleName))
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            
            // 设置高度为屏幕的70%
            bottomSheet.layoutParams.height = (screenHeight * 0.7).toInt()
            
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }
    
    /**
     * 初始化数据
     */
    private fun setupData() {
        options.clear()
        options.add(ClearOption(ClearType.TABS, R.string.clear_option_tabs))
        options.add(ClearOption(ClearType.HISTORY, R.string.clear_option_history))
        options.add(ClearOption(ClearType.COOKIES, R.string.clear_option_cookies))
        options.add(ClearOption(ClearType.CACHE, R.string.clear_option_cache))
    }
    
    /**
     * 设置列表
     */
    private fun setupRecyclerView() {
        adapter = ClearOptionAdapter(options) { 
            updateConfirmButtonState()
        }
        
        binding.rvClearOptions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvClearOptions.adapter = adapter
    }
    
    /**
     * 更新确认按钮状态
     */
    private fun updateConfirmButtonState() {
        val hasSelection = options.any { it.isSelected }
        binding.btnConfirm.isEnabled = hasSelection
        binding.btnConfirm.alpha = if (hasSelection) 1.0f else 0.5f
    }
    
    /**
     * 设置按钮事件
     */
    private fun setupButtons() {
        // 取消按钮
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        
        // 确认按钮
        binding.btnConfirm.setOnClickListener {
            val selectedOptions = options.filter { it.isSelected }
            if (selectedOptions.isEmpty()) {
                ToastUtils.showShort(R.string.clear_data_select_one)
                return@setOnClickListener
            }
            
            performClear(selectedOptions)
        }
    }
    
    /**
     * 执行清除操作
     */
    private fun performClear(selectedOptions: List<ClearOption>) {
        lifecycleScope.launch(Dispatchers.Main) {
            val context = requireContext()
            val components = context.components
            val engine = components.engine
            
            selectedOptions.forEach { option ->
                when (option.type) {
                    ClearType.TABS -> {
                        // 清除所有标签页
                        components.tabsUseCases.removeAllTabs.invoke()
                    }
                    ClearType.HISTORY -> {
                        // 清除所有历史记录
                        withContext(Dispatchers.IO) {
                            components.historyStorage.deleteVisitsSince(0)
                        }
                    }
                    ClearType.COOKIES -> {
                        // 清除 Cookies 和 Auth Sessions
                        engine.clearData(
                            Engine.BrowsingData.select(
                                Engine.BrowsingData.COOKIES,
                                Engine.BrowsingData.AUTH_SESSIONS
                            )
                        )
                    }
                    ClearType.CACHE -> {
                        // 清除缓存
                        engine.clearData(
                            Engine.BrowsingData.select(
                                Engine.BrowsingData.ALL_CACHES
                            )
                        )
                    }
                }
            }
            
            ToastUtils.showShort(R.string.clear_data_success)
            dismiss()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val TAG = "ClearDataBottomDialog"
        
        /**
         * 显示对话框
         */
        fun show(fragmentManager: FragmentManager) {
            val dialog = ClearDataBottomDialog()
            dialog.show(fragmentManager, TAG)
        }
    }
}
