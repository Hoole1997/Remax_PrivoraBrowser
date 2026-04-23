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
import com.example.browser.databinding.ActivityBookmarkFolderEditBinding
import com.example.browser.ui.bookmark.BookmarkFolderPickerActivity.Companion.EXTRA_SELECTED_FOLDER_ID
import com.example.browser.ui.bookmark.collectDescendantFolderIds
import com.example.browser.ui.bookmark.formatFolderPath
import com.example.browser.ui.bookmark.findFolderPath

class BookmarkFolderEditActivity :
    BaseActivity<ActivityBookmarkFolderEditBinding, BookmarkFolderEditModel>() {

    private var mode: Int = MODE_EDIT
    private var folderId: Long = BookmarkRepository.ROOT_FOLDER_ID
    private var selectedParentId: Long = BookmarkRepository.ROOT_FOLDER_ID

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val folder = result.data?.getLongExtra(EXTRA_SELECTED_FOLDER_ID, selectedParentId)
                if (folder != null) {
                    selectedParentId = folder
                    val pathText = result.data?.getStringExtra(BookmarkFolderPickerActivity.EXTRA_SELECTED_FOLDER_PATH)
                    if (!pathText.isNullOrEmpty()) {
                        binding.tvParentFolder.text = pathText
                    } else {
                        updateParentFolderText()
                    }
                }
            }
        }

    override fun initBinding(): ActivityBookmarkFolderEditBinding =
        ActivityBookmarkFolderEditBinding.inflate(layoutInflater)

    override fun initViewModel(): BookmarkFolderEditModel {
        val repository = BookmarkRepository.getInstance(applicationContext)
        return ViewModelProvider(this, BookmarkFolderEditModel.Factory(repository))
            .get(BookmarkFolderEditModel::class.java)
    }

    override fun initView() {
        mode = intent.getIntExtra(EXTRA_MODE, MODE_EDIT)
        folderId = intent.getLongExtra(EXTRA_FOLDER_ID, BookmarkRepository.ROOT_FOLDER_ID)
        selectedParentId = intent.getLongExtra(EXTRA_PARENT_ID, BookmarkRepository.ROOT_FOLDER_ID)
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
        binding.tvTitle.setText(R.string.bookmark_folder_edit_title_add)
        updateParentFolderText()
    }

    private fun setupForEdit() {
        binding.tvTitle.setText(R.string.bookmark_folder_edit_title_edit)
        val folder = viewModel.findFolder(folderId)
        if (folder == null) {
            finish()
            return
        }
        binding.editName.setText(folder.title)
        selectedParentId = folder.parentId ?: BookmarkRepository.ROOT_FOLDER_ID
        updateParentFolderText()
        binding.layoutParentFolder.isEnabled = false
        binding.layoutParentFolder.isClickable = false
        binding.ivParentArrow.isVisible = false
    }

    private fun updateParentFolderText() {
        val root = viewModel.getRootFolder()
        val path = findFolderPath(root, selectedParentId)
        val title = formatFolderPath(path ?: listOf(root), getString(R.string.bookmark_root_directory))
        binding.tvParentFolder.text = title
    }

    private fun openFolderPicker() {
        // 移动入口在单独流程触发，此处不提供父目录选择
    }

    private fun onSaveClicked() {
        val name = binding.editName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.inputName.error = getString(R.string.bookmark_add_folder_empty)
            return
        } else {
            binding.inputName.error = null
        }

        when (mode) {
            MODE_CREATE -> handleCreate(name)
            MODE_EDIT -> handleEdit(name)
        }
    }

    private fun handleCreate(name: String) {
        runCatching {
            viewModel.addFolder(selectedParentId, name)
        }.onSuccess { folder ->
            ToastUtils.showShort(R.string.bookmark_folder_created)
            val root = viewModel.getRootFolder()
            val path = findFolderPath(root, folder.id) ?: listOf(root)
            val pathText = formatFolderPath(path, getString(R.string.bookmark_root_directory))
            setResult(RESULT_OK, Intent().apply {
                putExtra(EXTRA_CREATED_FOLDER_ID, folder.id)
                putExtra(EXTRA_CREATED_FOLDER_PATH, pathText)
            })
            finish()
        }.onFailure {
            ToastUtils.showShort(R.string.bookmark_add_failed)
        }
    }

    private fun handleEdit(name: String) {
        val folder = viewModel.findFolder(folderId)
        if (folder == null) {
            finish()
            return
        }

        val renameChanged = folder.title != name
        val success = if (renameChanged) {
            viewModel.renameFolder(folder.id, name)
        } else {
            true
        }

        if (success) {
            ToastUtils.showShort(R.string.bookmark_folder_renamed)
            setResult(RESULT_OK)
            finish()
        } else {
            ToastUtils.showShort(R.string.bookmark_folder_rename_failed)
        }
    }

    companion object {
        private const val EXTRA_MODE = "extra_mode"
        private const val EXTRA_FOLDER_ID = "extra_folder_id"
        private const val EXTRA_PARENT_ID = "extra_parent_id"
        const val EXTRA_CREATED_FOLDER_ID = "extra_created_folder_id"
        const val EXTRA_CREATED_FOLDER_PATH = "extra_created_folder_path"

        const val MODE_EDIT = 0
        const val MODE_CREATE = 1

        fun createIntentForEdit(context: Context, folderId: Long): Intent {
            return Intent(context, BookmarkFolderEditActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_EDIT)
                putExtra(EXTRA_FOLDER_ID, folderId)
            }
        }

        fun createIntentForCreate(context: Context, parentId: Long): Intent {
            return Intent(context, BookmarkFolderEditActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_CREATE)
                putExtra(EXTRA_PARENT_ID, parentId)
            }
        }
    }
}
