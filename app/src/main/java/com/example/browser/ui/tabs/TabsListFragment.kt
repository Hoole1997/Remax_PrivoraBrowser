package com.example.browser.ui.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.browser.components
import com.example.browser.databinding.FragmentTabsListBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.selector.privateTabs
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader
import mozilla.components.lib.state.ext.flowScoped

/**
 * 标签列表 Fragment - 显示普通标签或无痕标签
 */
class TabsListFragment : Fragment() {

    companion object {
        private const val ARG_IS_PRIVATE = "is_private"
        private const val GRID_SPAN_COUNT = 2

        fun newInstance(isPrivate: Boolean): TabsListFragment {
            return TabsListFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_PRIVATE, isPrivate)
                }
            }
        }
    }

    private var _binding: FragmentTabsListBinding? = null
    private val binding get() = _binding!!

    private val isPrivateMode: Boolean by lazy {
        arguments?.getBoolean(ARG_IS_PRIVATE, false) ?: false
    }

    private lateinit var tabsAdapter: TabsAdapter
    private val thumbnailLoader: ThumbnailLoader by lazy {
        ThumbnailLoader(requireContext().components.thumbnailStorage)
    }

    private var storeScope: CoroutineScope? = null

    // 回调
    var onTabClick: ((TabSessionState) -> Unit)? = null
    var onTabClose: ((TabSessionState) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTabsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeBrowserState()
    }

    private fun setupRecyclerView() {
        val gridLayoutManager = GridLayoutManager(context, GRID_SPAN_COUNT)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (::tabsAdapter.isInitialized && tabsAdapter.isFullSpanPosition(position)) {
                    GRID_SPAN_COUNT
                } else {
                    1
                }
            }
        }

        tabsAdapter = TabsAdapter(
            thumbnailLoader = thumbnailLoader,
            onTabClick = { tab ->
                onTabClick?.invoke(tab)
            },
            onTabClose = { tab ->
                onTabClose?.invoke(tab)
            }
        )

        binding.rvTabs.apply {
            layoutManager = gridLayoutManager
            adapter = tabsAdapter
            addItemDecoration(GridSpacingItemDecoration(GRID_SPAN_COUNT, 4, true))
        }
    }

    private fun observeBrowserState() {
        storeScope = activity?.components?.store?.flowScoped(
            dispatcher = kotlinx.coroutines.Dispatchers.Main.immediate,
        ) { flow ->
            flow.collect { state ->
                if (isAdded && !isDetached && view != null) {
                    updateTabsList()
                }
            }
        }
    }

    private fun updateTabsList() {
        val state = activity?.components?.store?.state ?: return

        val tabs = if (isPrivateMode) {
            state.privateTabs
        } else {
            state.normalTabs
        }

        tabsAdapter.updateTabs(
            tabs = tabs,
            selectedId = state.selectedTabId,
            isPrivateMode = isPrivateMode
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        storeScope?.cancel()
        storeScope = null
        _binding = null
    }
}
