package com.example.browser.ui.history

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.PopupWindow
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.PopupWindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.blankj.utilcode.util.ToastUtils
import com.blankj.utilcode.util.Utils
import com.example.browser.R
import com.example.browser.base.BaseFragment
import com.example.browser.components
import com.example.browser.data.bookmark.BookmarkRepository
import com.example.browser.data.history.HistoryRepository
import com.example.browser.databinding.FragmentHistoryBinding
import com.example.browser.databinding.PopupHistoryActionsBinding
import com.example.browser.ui.bookmark.BookmarkEditActivity
import com.example.browser.ui.web.WebActivity
import com.example.browser.view.ConfirmDialog
import kotlinx.coroutines.launch

class HistoryFragment : BaseFragment<FragmentHistoryBinding, HistoryModel>() {

    private val historyAdapter by lazy {
        HistoryListAdapter(
            icons = Utils.getApp().components.icons,
            onItemClick = { entry ->
                openHistoryEntry(entry)
            },
            onMoreClick = { anchor, entry ->
                showHistoryActions(anchor, entry)
            }
        )
    }

    private var actionsPopup: PopupWindow? = null
    private var searchWatcher: TextWatcher? = null

    override fun initBinding(): FragmentHistoryBinding {
        return FragmentHistoryBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): HistoryModel {
        val repository = HistoryRepository(Utils.getApp().applicationContext)
        return ViewModelProvider(this, HistoryModel.Factory(repository))
            .get(HistoryModel::class.java)
    }

    override fun initView() {
        binding?.rvHistory?.apply {
            layoutManager = LinearLayoutManager(Utils.getApp())
            adapter = historyAdapter
        }
        setupSearch()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHistory()
    }

    override fun onDestroyView() {
        binding?.etSearch?.removeTextChangedListener(searchWatcher)
        super.onDestroyView()
        searchWatcher = null
        actionsPopup?.dismiss()
        actionsPopup = null
    }

    private fun setupSearch() {
        val searchField = binding?.etSearch ?: return
        searchWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString().orEmpty())
                binding?.ivClear?.isVisible = !s.isNullOrEmpty()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        searchField.addTextChangedListener(searchWatcher)
        searchField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchField.clearFocus()
                true
            } else {
                false
            }
        }
        binding?.ivClear?.setOnClickListener {
            searchField.text?.clear()
            viewModel.clearSearch()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.listItems.collect { items ->
                        historyAdapter.submitList(items)
                    }
                }
            }
        }
    }

    private fun openHistoryEntry(entry: HistoryModel.HistoryEntry) {
        val intent = Intent(requireContext(), WebActivity::class.java).apply {
            putExtra(WebActivity.EXTRA_URL, entry.url)
        }
        startActivity(intent)
        activity?.finish()
    }

    private fun showHistoryActions(anchor: View, entry: HistoryModel.HistoryEntry) {
        actionsPopup?.dismiss()

        val binding = PopupHistoryActionsBinding.inflate(layoutInflater)

        val popup = PopupWindow(
            binding.root,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = resources.getDimension(R.dimen.bookmark_popup_elevation)
            }
            setOnDismissListener { actionsPopup = null }
        }

        binding.actionAddBookmark.setOnClickListener {
            popup.dismiss()
            addToBookmark(entry)
        }

        binding.actionDelete.setOnClickListener {
            popup.dismiss()
            confirmDeleteHistory(entry)
        }

        actionsPopup = popup
        PopupWindowCompat.showAsDropDown(popup, anchor, 0, 0, Gravity.END)
    }

    private fun addToBookmark(entry: HistoryModel.HistoryEntry) {
        val intent = BookmarkEditActivity.createIntentForCreate(
            context = requireContext(),
            defaultTitle = entry.title,
            defaultUrl = entry.url,
            parentId = BookmarkRepository.ROOT_FOLDER_ID
        )
        startActivity(intent)
    }

    private fun confirmDeleteHistory(entry: HistoryModel.HistoryEntry) {
        ConfirmDialog.show(
            supportFragmentManager = childFragmentManager,
            title = getString(R.string.history_delete_title),
            content = getString(R.string.history_delete_message),
            button = getString(R.string.history_delete_confirm)
        ).apply {
            setOnConfirmListener {
                viewModel.deleteHistoryEntry(entry.url)
                ToastUtils.showShort(R.string.history_deleted)
            }
            setOnCancelListener {

            }
        }
    }

    fun confirmClearHistory() {
        if (!viewModel.hasHistory()) {
            ToastUtils.showShort(R.string.history_empty_state)
            return
        }
        ConfirmDialog.show(
            supportFragmentManager = childFragmentManager,
            title = getString(R.string.history_clear_title),
            content = getString(R.string.history_clear_message),
            button = getString(R.string.history_clear_confirm)
        ).apply {
            setOnConfirmListener {
                viewModel.clearHistory()
                ToastUtils.showShort(R.string.history_cleared)
            }
            setOnCancelListener {

            }
        }
    }
}
