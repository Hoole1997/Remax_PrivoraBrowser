package com.example.browser.view

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.browser.BrowserApplication
import com.example.browser.databinding.DialogSearchEnglineBinding
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.browser.state.state.searchEngines
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flowScoped

import com.example.browser.utils.SpUtils
import net.corekit.core.report.ReportDataManager

class ChooseSearchEngineDialog : DialogFragment() {

    companion object {
        fun show(
            supportFragmentManager: FragmentManager,
        ): ChooseSearchEngineDialog {
            val dialog = ChooseSearchEngineDialog()
            dialog.show(supportFragmentManager, "choose_search_engine_dialog")
            return dialog
        }
    }

    private lateinit var binding: DialogSearchEnglineBinding
    private lateinit var adapter: SearchEngineAdapter
    private lateinit var store: BrowserStore
    private var selectedEngineId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogSearchEnglineBinding.inflate(inflater, container, false)
        
        // 获取 BrowserStore
        val application = requireActivity().application as BrowserApplication
        store = application.browserComponents.store
        
        setupRecyclerView()
        setupClickListeners()
        observeSearchEngines()

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Set transparent background
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // 设置 Dialog 宽度为屏幕宽度的 85%
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        ReportDataManager.reportData("dialog_show", mapOf("dialog_name" to javaClass.simpleName))
    }

    private fun setupRecyclerView() {
        // 初始化时使用空字符串，实际数据会在 observeSearchEngines 中更新
        adapter = SearchEngineAdapter(emptyList(), "") { engine ->
            selectedEngineId = engine.id
            adapter.updateSelection(engine.id)
        }
        
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnApply.setOnClickListener {
            // 通过 BrowserStore dispatch Action 更新默认搜索引擎
            selectedEngineId?.let { engineId ->
                // 获取选中的搜索引擎名称
                val selectedEngine = store.state.search.searchEngines.find { it.id == engineId }
                selectedEngine?.let { engine ->
                    // 保存到 SharedPreferences
                    saveSearchEnginePreference(engine.id, engine.name)
                    
                    // 更新 BrowserStore
                    store.dispatch(
                        SearchAction.SelectSearchEngineAction(
                            searchEngineId = engine.id,
                            searchEngineName = engine.name
                        )
                    )
                }
            }
            dismiss()
        }
    }

    override fun dismiss() {
        super.dismiss()
        ReportDataManager.reportData("dialog_dismiss", mapOf("dialog_name" to javaClass.simpleName))
    }

    /**
     * 保存用户选择的搜索引擎到 SharedPreferences
     */
    private fun saveSearchEnginePreference(engineId: String, engineName: String) {
        SpUtils.saveSearchEnginePreference(requireContext(), engineId, engineName)
    }

    /**
     * 观察 BrowserStore 中的搜索引擎状态变化
     */
    private fun observeSearchEngines() {
        lifecycleScope.launch {
            store.flowScoped(viewLifecycleOwner) { flow ->
                flow.map { state -> state.search }
                    .distinctUntilChanged()
                    .collect { searchState ->
                        // 更新搜索引擎列表
                        val engines = searchState.searchEngines
                        val defaultEngineId = searchState.selectedOrDefaultSearchEngine?.id
                        
                        // 初始化选中的搜索引擎
                        if (selectedEngineId == null) {
                            selectedEngineId = defaultEngineId
                        }
                        
                        // 只有当有选中的搜索引擎时才更新 Adapter
                        val currentSelectedId = selectedEngineId ?: defaultEngineId ?: ""
                        if (currentSelectedId.isNotEmpty()) {
                            adapter = SearchEngineAdapter(engines, currentSelectedId) { engine ->
                                selectedEngineId = engine.id
                                adapter.updateSelection(engine.id)
                            }
                            binding.recyclerView.adapter = adapter
                        }
                    }
            }
        }
    }

}