package com.example.browser.ui.bookmark

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.PopupWindow
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.PopupWindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.blankj.utilcode.util.ToastUtils
import com.blankj.utilcode.util.Utils
import com.example.browser.R
import com.example.browser.base.BaseFragment
import com.example.browser.components
import com.example.browser.data.bookmark.BookmarkFolder
import com.example.browser.data.bookmark.BookmarkNode
import com.example.browser.data.bookmark.BookmarkRepository
import com.example.browser.data.bookmark.BookmarkSite
import com.example.browser.databinding.FragmentBookmarkBinding
import com.example.browser.databinding.PopupBookmarkActionsBinding
import com.example.browser.ui.bookmark.BookmarkEditActivity
import com.example.browser.ui.bookmark.BookmarkFolderEditActivity
import com.example.browser.ui.bookmark.BookmarkFolderPickerActivity
import com.example.browser.ui.bookmark.collectDescendantFolderIds
import com.example.browser.ui.web.WebActivity
import com.example.browser.view.ConfirmDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class BookmarkFragment : BaseFragment<FragmentBookmarkBinding, BookmarkModel>() {

    private val bookmarksAdapter by lazy {
        BookmarkListAdapter(
            icons = Utils.getApp().components.icons,
            onFolderClick = { openFolder(it) },
            onSiteClick = { openBookmark(it) },
            onMoreClick = { anchor, node -> showBookmarkActions(anchor, node) }
        )
    }

    private var actionsPopup: PopupWindow? = null
    private var pendingMoveBookmarkId: Long? = null
    private var pendingMoveFolderId: Long? = null

    private val editBookmarkLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                viewModel.refreshCurrentFolder()
            }
        }

    private val editFolderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                viewModel.refreshCurrentFolder()
            }
        }
    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val folderId = result.data?.getLongExtra(
                    BookmarkFolderPickerActivity.EXTRA_SELECTED_FOLDER_ID,
                    BookmarkRepository.ROOT_FOLDER_ID
                )
                if (folderId != null) {
                    handleFolderSelection(folderId)
                }
            }
            pendingMoveBookmarkId = null
            pendingMoveFolderId = null
        }

    private var searchWatcher: TextWatcher? = null

    override fun initBinding(): FragmentBookmarkBinding {
        return FragmentBookmarkBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): BookmarkModel {
        val repository = BookmarkRepository.getInstance(requireContext())
        return ViewModelProvider(this, BookmarkModel.Factory(repository))
            .get(BookmarkModel::class.java)
    }

    override fun initView() {
        binding?.rvBookmarks?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bookmarksAdapter
        }
        setupSearch()
        binding?.tvPath?.isVisible = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
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
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.visibleEntries.collect { entries ->
                        bookmarksAdapter.submitList(entries)
                    }
                }
                launch {
                    viewModel.pathTitles.collect { titles ->
                        binding?.tvPath?.isVisible = titles.size > 1
                        binding?.tvPath?.text = titles.joinToString(" / ")
                    }
                }
            }
        }
    }

    private fun openFolder(folder: BookmarkFolder) {
        viewModel.openFolder(folder)
    }

    private fun openBookmark(site: BookmarkSite) {
        val intent = Intent(requireContext(), WebActivity::class.java).apply {
            putExtra(WebActivity.EXTRA_URL, site.url)
        }
        startActivity(intent)
        activity?.finish()
    }

    fun handleBackPressed(): Boolean {
        return viewModel.navigateBack()
    }

    private fun showBookmarkActions(anchor: View, node: BookmarkNode) {
        actionsPopup?.dismiss()

        val binding = PopupBookmarkActionsBinding.inflate(layoutInflater)

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

        val isFolder = node is BookmarkFolder
        val isRootFolder = isFolder && node.id == BookmarkRepository.ROOT_FOLDER_ID

        binding.actionShare.isVisible = !isFolder
        binding.actionEdit.isVisible = if (isFolder) !isRootFolder else true
        binding.actionMove.isVisible = !isFolder
        binding.actionDelete.isVisible = !isRootFolder
        binding.dividerBottom.isVisible = binding.actionDelete.isVisible

        if (isFolder) {
            binding.iconOpen.setImageResource(R.drawable.ic_bookmark_folder)
            binding.iconOpen.contentDescription = getString(R.string.bookmark_action_open)
            binding.tvEdit.text = getString(R.string.bookmark_action_rename)
            binding.iconEdit.contentDescription = getString(R.string.bookmark_action_rename)
        } else {
            binding.iconOpen.setImageResource(R.drawable.ic_bookmark_site)
            binding.iconOpen.contentDescription = getString(R.string.bookmark_action_open)
            binding.tvEdit.text = getString(R.string.bookmark_action_edit)
            binding.iconEdit.contentDescription = getString(R.string.bookmark_action_edit)
        }

        binding.actionOpen.setOnClickListener {
            popup.dismiss()
            if (isFolder) {
                openFolder(node as BookmarkFolder)
            } else {
                openBookmark(node as BookmarkSite)
            }
        }

        binding.actionShare.setOnClickListener {
            popup.dismiss()
            shareBookmark(node as BookmarkSite)
        }

        binding.actionEdit.setOnClickListener {
            popup.dismiss()
            if (isFolder) {
                openFolderEditor(node as BookmarkFolder)
            } else {
                openBookmarkEditor(node as BookmarkSite)
            }
        }

        binding.actionMove.setOnClickListener {
            popup.dismiss()
            if (!isFolder) {
                startMoveBookmark(node as BookmarkSite)
            }
        }

        binding.actionDelete.setOnClickListener {
            popup.dismiss()
            if (isFolder) {
                confirmDeleteFolder(node as BookmarkFolder)
            } else {
                confirmDeleteBookmark(node as BookmarkSite)
            }
        }

        actionsPopup = popup
        PopupWindowCompat.showAsDropDown(popup, anchor, 0, 0, android.view.Gravity.END)
    }

    private fun shareBookmark(site: BookmarkSite) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (site.title.isNotBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, site.title)
            }
            putExtra(Intent.EXTRA_TEXT, site.url)
        }
        kotlin.runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.bookmark_menu_share)))
        }.onFailure {
            ToastUtils.showShort(R.string.bookmark_menu_share)
        }
    }

    private fun openBookmarkEditor(site: BookmarkSite) {
        val intent = BookmarkEditActivity.createIntentForEdit(requireContext(), site.id)
        editBookmarkLauncher.launch(intent)
    }

    private fun openFolderEditor(folder: BookmarkFolder) {
        val intent = BookmarkFolderEditActivity.createIntentForEdit(requireContext(), folder.id)
        editFolderLauncher.launch(intent)
    }

    private fun startMoveBookmark(site: BookmarkSite) {
        pendingMoveBookmarkId = site.id
        pendingMoveFolderId = null
        val intent = BookmarkFolderPickerActivity.createIntent(
            context = requireContext(),
            initialFolderId = site.parentId ?: BookmarkRepository.ROOT_FOLDER_ID
        )
        folderPickerLauncher.launch(intent)
    }

    private fun startMoveFolder(folder: BookmarkFolder) {
        if (folder.id == BookmarkRepository.ROOT_FOLDER_ID) {
            return
        }
        pendingMoveFolderId = folder.id
        pendingMoveBookmarkId = null
        val exclude = mutableSetOf<Long>().apply {
            add(folder.id)
            collectDescendantFolderIds(folder, this)
        }.toLongArray()
        val intent = BookmarkFolderPickerActivity.createIntent(
            context = requireContext(),
            initialFolderId = folder.parentId ?: BookmarkRepository.ROOT_FOLDER_ID,
            excludedIds = exclude
        )
        folderPickerLauncher.launch(intent)
    }

    private fun handleFolderSelection(targetFolderId: Long) {
        pendingMoveBookmarkId?.let { bookmarkId ->
            val success = viewModel.moveBookmark(bookmarkId, targetFolderId)
            ToastUtils.showShort(if (success) R.string.bookmark_move_success else R.string.bookmark_move_failed)
            if (success) {
                viewModel.refreshCurrentFolder()
            }
            return
        }
        pendingMoveFolderId?.let { folderId ->
            if (folderId == targetFolderId) {
                ToastUtils.showShort(R.string.bookmark_folder_move_failed)
                return
            }
            val success = viewModel.moveFolder(folderId, targetFolderId)
            ToastUtils.showShort(if (success) R.string.bookmark_folder_moved else R.string.bookmark_folder_move_failed)
            if (success) {
                viewModel.refreshCurrentFolder()
            }
        }
    }

    fun getCurrentFolderId(): Long = viewModel.currentFolder.value.id

    fun refreshAfterExternalChange(pathText: String? = null) {
        if (!pathText.isNullOrEmpty()) {
            binding?.tvPath?.text = pathText
            binding?.tvPath?.isVisible = true
        }
        viewModel.refreshCurrentFolder()
    }

    private fun confirmDeleteBookmark(site: BookmarkSite) {
        ConfirmDialog.show(
            supportFragmentManager = childFragmentManager,
            title = getString(R.string.bookmark_confirm_delete),
            content = getString(R.string.bookmark_confirm_delete_site_message),
            button = getString(R.string.bookmark_dialog_confirm)
        ).apply {
            setOnConfirmListener {
                val success = viewModel.deleteBookmark(site.id)
                ToastUtils.showShort(if (success) R.string.bookmark_deleted else R.string.bookmark_delete_failed)
            }
            setOnCancelListener {

            }
        }
    }

    private fun confirmDeleteFolder(folder: BookmarkFolder) {
        ConfirmDialog.show(
            supportFragmentManager = childFragmentManager,
            title = getString(R.string.bookmark_confirm_delete),
            content = getString(R.string.bookmark_confirm_delete_folder_message),
            button = getString(R.string.bookmark_dialog_confirm)
        ).apply {
            setOnConfirmListener {
                val success = viewModel.deleteFolder(folder.id)
                ToastUtils.showShort(if (success) R.string.bookmark_folder_deleted else R.string.bookmark_folder_delete_failed)
            }
            setOnCancelListener {

            }
        }
    }
}
