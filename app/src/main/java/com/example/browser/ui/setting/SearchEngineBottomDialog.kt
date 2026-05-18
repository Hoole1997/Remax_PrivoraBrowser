package com.example.browser.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.browser.BrowserApplication
import com.example.browser.databinding.DialogSearchEngineBottomBinding
import com.example.browser.utils.SpUtils
import com.example.browser.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.browser.state.state.searchEngines
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flowScoped
import net.corekit.core.report.ReportDataManager

/**
 * 搜索引擎选择底部弹框 - 小巧精致版
 * 点击即选中并自动关闭
 */
class SearchEngineBottomDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSearchEngineBottomBinding? = null
    private val binding get() = _binding!!

    private lateinit var store: BrowserStore
    private lateinit var adapter: SearchEngineBottomAdapter

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogSearchEngineBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val application = requireActivity().application as BrowserApplication
        store = application.browserComponents.store

        setupRecyclerView()
        observeSearchEngines()
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
        val currentEngineId = store.state.search.selectedOrDefaultSearchEngine?.id ?: ""

        adapter = SearchEngineBottomAdapter(currentEngineId) { engine ->
            // 保存到 SharedPreferences
            SpUtils.saveSearchEnginePreference(requireContext(), engine.id, engine.name)

            // 更新 BrowserStore
            store.dispatch(
                SearchAction.SelectSearchEngineAction(
                    searchEngineId = engine.id,
                    searchEngineName = engine.name,
                )
            )

            // 选中后自动关闭
            dismiss()
        }

        binding.rvSearchEngines.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchEngines.adapter = adapter
    }

    private fun observeSearchEngines() {
        lifecycleScope.launch {
            store.flowScoped(viewLifecycleOwner) { flow ->
                flow.map { state -> state.search }
                    .distinctUntilChanged()
                    .collect { searchState ->
                        val engines = searchState.searchEngines
                        val defaultEngineId = searchState.selectedOrDefaultSearchEngine?.id ?: ""
                        adapter.submitList(engines, defaultEngineId)
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "SearchEngineBottomDialog"

        fun show(fragmentManager: FragmentManager) {
            val dialog = SearchEngineBottomDialog()
            dialog.show(fragmentManager, TAG)
        }
    }
}
