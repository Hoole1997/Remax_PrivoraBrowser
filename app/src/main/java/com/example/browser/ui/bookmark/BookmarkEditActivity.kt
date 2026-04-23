package com.example.browser.ui.bookmark

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.blankj.utilcode.util.ToastUtils
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.data.bookmark.BookmarkRepository
import com.example.browser.databinding.ActivityBookmarkEditBinding
import com.example.browser.ui.bookmark.BookmarkFolderEditActivity.Companion.EXTRA_CREATED_FOLDER_ID
import com.example.browser.ui.bookmark.BookmarkFolderEditActivity.Companion.EXTRA_CREATED_FOLDER_PATH
import com.example.browser.ui.bookmark.BookmarkFolderPickerActivity.Companion.EXTRA_SELECTED_FOLDER_ID
import com.example.browser.ui.bookmark.formatFolderPath
import com.example.browser.ui.bookmark.findFolderPath

class BookmarkEditActivity :
    BaseActivity<ActivityBookmarkEditBinding, BookmarkEditModel>() {

    private var mode: Int = MODE_CREATE
    private var bookmarkId: Long = -1L
    private var selectedFolderId: Long = BookmarkRepository.ROOT_FOLDER_ID
    private var isFolderMode: Boolean = false

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val folderId = result.data?.getLongExtra(EXTRA_SELECTED_FOLDER_ID, selectedFolderId)
                if (folderId != null) {
                    selectedFolderId = folderId
                    val pathText =
                        result.data?.getStringExtra(BookmarkFolderPickerActivity.EXTRA_SELECTED_FOLDER_PATH)
                    if (!pathText.isNullOrEmpty()) {
                        binding.tvParentFolder.text = pathText
                    } else {
                        updateParentFolderText()
                    }
                }
            }
        }

    override fun initBinding(): ActivityBookmarkEditBinding =
        ActivityBookmarkEditBinding.inflate(layoutInflater)

    override fun initViewModel(): BookmarkEditModel {
        val repository = BookmarkRepository.getInstance(applicationContext)
        return ViewModelProvider(this, BookmarkEditModel.Factory(repository))
            .get(BookmarkEditModel::class.java)
    }

    override fun initView() {
        mode = intent.getIntExtra(EXTRA_MODE, MODE_CREATE)
        bookmarkId = intent.getLongExtra(EXTRA_BOOKMARK_ID, -1L)
        isFolderMode = intent.getBooleanExtra(EXTRA_IS_FOLDER_MODE, false)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(null)
            setDisplayShowTitleEnabled(false)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSave.setOnClickListener { onSaveClicked() }
        binding.layoutParentFolder.setOnClickListener { openFolderPicker() }

        when (mode) {
            MODE_CREATE -> setupForCreate()
            MODE_EDIT -> setupForEdit()
        }
    }

    private fun setupForCreate() {
        val defaultTitle = intent.getStringExtra(EXTRA_DEFAULT_TITLE).orEmpty()
        val defaultUrl = intent.getStringExtra(EXTRA_DEFAULT_URL).orEmpty()
        selectedFolderId =
            intent.getLongExtra(EXTRA_DEFAULT_FOLDER_ID, BookmarkRepository.ROOT_FOLDER_ID)

        supportActionBar?.title =
            if (isFolderMode) getString(R.string.bookmark_folder_edit_title_add) else getString(R.string.bookmark_edit_title_add)
        binding.editName.setText(defaultTitle.ifBlank { defaultUrl })
        binding.editUrl.setText(defaultUrl)
        binding.inputUrl.isVisible = !isFolderMode
        binding.editUrl.isVisible = !isFolderMode
        updateParentFolderText()
    }

    private fun setupForEdit() {
        binding.tvTitle.setText(if (isFolderMode) R.string.bookmark_folder_edit_title_edit else R.string.bookmark_edit_title_edit)
        if (bookmarkId == -1L) {
            finish()
            return
        }
        val bookmark = viewModel.loadBookmark(bookmarkId)
        if (bookmark == null) {
            finish()
            return
        }
        binding.editName.setText(bookmark.title)
        binding.editUrl.setText(bookmark.url)
        selectedFolderId = bookmark.parentId ?: BookmarkRepository.ROOT_FOLDER_ID
        binding.inputUrl.isVisible = !isFolderMode
        binding.editUrl.isVisible = !isFolderMode
        updateParentFolderText()
    }

    private fun updateParentFolderText() {
        val root = viewModel.getRootFolder()
        val path = findFolderPath(root, selectedFolderId)
        val title =
            formatFolderPath(path ?: listOf(root), getString(R.string.bookmark_root_directory))
        binding.tvParentFolder.text = title
    }

    private fun openFolderPicker() {
        val intent = BookmarkFolderPickerActivity.createIntent(
            context = this,
            initialFolderId = selectedFolderId
        )
        folderPickerLauncher.launch(intent)
    }

    private fun onSaveClicked() {
        val title = binding.editName.text?.toString()?.trim().orEmpty()
        val url = binding.editUrl.text?.toString()?.trim().orEmpty()

        var hasError = false
        if (title.isEmpty()) {
            binding.inputName.error = getString(R.string.bookmark_dialog_error_title_empty)
            hasError = true
        } else {
            binding.inputName.error = null
        }

        if (!isFolderMode) {
            if (url.isEmpty()) {
                binding.inputUrl.error = getString(R.string.bookmark_dialog_error_url_empty)
                hasError = true
            } else {
                binding.inputUrl.error = null
            }
        }

        if (hasError) {
            return
        }

        when (mode) {
            MODE_CREATE -> handleCreate(title, url)
            MODE_EDIT -> handleEdit(title, url)
        }
    }

    private fun handleCreate(title: String, url: String) {
        if (isFolderMode) {
            runCatching { viewModel.addFolder(selectedFolderId, title) }
                .onSuccess { folder ->
                    ToastUtils.showShort(R.string.bookmark_folder_created)
                    val root = viewModel.getRootFolder()
                    val path = findFolderPath(root, folder.id) ?: listOf(root)
                    val pathText =
                        formatFolderPath(path, getString(R.string.bookmark_root_directory))
                    setResult(RESULT_OK, Intent().apply {
                        putExtra(EXTRA_CREATED_FOLDER_ID, folder.id)
                        putExtra(EXTRA_CREATED_FOLDER_PATH, pathText)
                    })
                    finish()
                }
                .onFailure {
                    ToastUtils.showShort(R.string.bookmark_add_failed)
                }
        } else {
            val result = viewModel.addBookmark(selectedFolderId, title, url)
            when {
                result == null -> ToastUtils.showShort(R.string.bookmark_add_failed)
                result.isNew -> ToastUtils.showShort(R.string.bookmark_added)
                else -> ToastUtils.showShort(R.string.bookmark_already_exists)
            }
            if (result != null) {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun handleEdit(title: String, url: String) {
        if (bookmarkId == -1L) {
            finish()
            return
        }
        if (isFolderMode) {
            // For folder mode, editing existing folders handled elsewhere.
            setResult(RESULT_CANCELED)
            finish()
        } else {
            val success = viewModel.updateBookmark(bookmarkId, title, url, selectedFolderId)
            ToastUtils.showShort(if (success) R.string.bookmark_updated else R.string.bookmark_update_failed)
            if (success) {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_MODE = "extra_mode"
        private const val EXTRA_BOOKMARK_ID = "extra_bookmark_id"
        private const val EXTRA_DEFAULT_TITLE = "extra_default_title"
        private const val EXTRA_DEFAULT_URL = "extra_default_url"
        private const val EXTRA_DEFAULT_FOLDER_ID = "extra_default_folder_id"
        private const val EXTRA_IS_FOLDER_MODE = "extra_is_folder_mode"

        const val MODE_CREATE = 0
        const val MODE_EDIT = 1

        fun createIntentForCreate(
            context: Context,
            defaultTitle: String?,
            defaultUrl: String?,
            parentId: Long = BookmarkRepository.ROOT_FOLDER_ID
        ): Intent {
            return Intent(context, BookmarkEditActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_CREATE)
                putExtra(EXTRA_DEFAULT_TITLE, defaultTitle)
                putExtra(EXTRA_DEFAULT_URL, defaultUrl)
                putExtra(EXTRA_DEFAULT_FOLDER_ID, parentId)
            }
        }

        fun createIntentForEdit(
            context: Context,
            bookmarkId: Long
        ): Intent {
            return Intent(context, BookmarkEditActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_EDIT)
                putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
            }
        }

    }
}
