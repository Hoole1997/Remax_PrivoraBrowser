package com.example.browser.ui.bookmark

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.ToastUtils
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.data.bookmark.BookmarkFolder
import com.example.browser.data.bookmark.BookmarkRepository
import com.example.browser.databinding.ActivityBookmarkFolderPickerBinding
import com.example.browser.ui.bookmark.BookmarkFolderEditActivity
import com.example.browser.ui.bookmark.BookmarkFolderEditActivity.Companion.EXTRA_CREATED_FOLDER_ID
import com.example.browser.ui.bookmark.BookmarkFolderEditActivity.Companion.EXTRA_CREATED_FOLDER_PATH
import com.example.browser.ui.bookmark.formatFolderPath
import com.example.browser.ui.bookmark.findFolderPath
import java.util.ArrayDeque

class BookmarkFolderPickerActivity :
    BaseActivity<ActivityBookmarkFolderPickerBinding, BookmarkFolderPickerModel>() {

    private val folderAdapter = FolderAdapter()
    private val stack = ArrayDeque<BookmarkFolder>()
    private var selectedFolderId: Long = BookmarkRepository.ROOT_FOLDER_ID
    private var excludedIds: Set<Long> = emptySet()
    private val addFolderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val newFolderId = result.data?.getLongExtra(EXTRA_CREATED_FOLDER_ID, -1L) ?: -1L
                val pathText = result.data?.getStringExtra(EXTRA_CREATED_FOLDER_PATH)
                if (newFolderId != -1L) {
                    selectedFolderId = newFolderId
                    if (!pathText.isNullOrEmpty()) {
                        binding.tvPath.text = pathText
                    }
                    refreshAfterMutation(newFolderId)
                } else {
                    bindFolderList()
                }
            }
        }

    override fun initBinding(): ActivityBookmarkFolderPickerBinding =
        ActivityBookmarkFolderPickerBinding.inflate(layoutInflater)

    override fun initViewModel(): BookmarkFolderPickerModel {
        val repository = BookmarkRepository.getInstance(applicationContext)
        return ViewModelProvider(this, BookmarkFolderPickerModel.Factory(repository))
            .get(BookmarkFolderPickerModel::class.java)
    }

    override fun initView() {
        selectedFolderId = intent.getLongExtra(EXTRA_INITIAL_FOLDER_ID, BookmarkRepository.ROOT_FOLDER_ID)
        excludedIds = intent.getLongArrayExtra(EXTRA_EXCLUDED_IDS)?.toSet() ?: emptySet()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(null)
            setDisplayShowTitleEnabled(false)
        }
        binding.toolbar.setNavigationOnClickListener {
            if (stack.size > 1) {
                stack.removeLast()
                bindFolderList()
            } else {
                finish()
            }
        }
        binding.btnAddFolder.setOnClickListener {
            val parentId = stack.lastOrNull()?.id ?: BookmarkRepository.ROOT_FOLDER_ID
            val intent = BookmarkFolderEditActivity.createIntentForCreate(this, parentId)
            addFolderLauncher.launch(intent)
        }
        binding.btnDone.setOnClickListener {
            if (excludedIds.contains(selectedFolderId)) {
                ToastUtils.showShort(R.string.bookmark_move_failed)
                return@setOnClickListener
            }
            val root = viewModel.getRootFolder()
            val pathList = findFolderPath(root, selectedFolderId) ?: listOf(root)
            val pathText = formatFolderPath(pathList, getString(R.string.bookmark_root_directory))
            setResult(RESULT_OK, Intent().apply {
                putExtra(EXTRA_SELECTED_FOLDER_ID, selectedFolderId)
                putExtra(EXTRA_SELECTED_FOLDER_PATH, pathText)
            })
            finish()
        }

        binding.rvFolders.layoutManager = LinearLayoutManager(this)
        binding.rvFolders.adapter = folderAdapter

        initializeStack()
        bindFolderList()
    }

    private fun initializeStack() {
        stack.clear()
        val root = viewModel.getRootFolder()
        val path = findFolderPath(root, selectedFolderId) ?: listOf(root)
        stack.addAll(path)
        // 如果当前路径无法选中（被排除），则默认选中根目录
        if (excludedIds.contains(selectedFolderId)) {
            selectedFolderId = BookmarkRepository.ROOT_FOLDER_ID
        }
    }

    private fun bindFolderList() {
        val current = stack.lastOrNull() ?: viewModel.getRootFolder()
        val root = viewModel.getRootFolder()
        val refreshedCurrent = findFolderPath(root, current.id)?.lastOrNull() ?: root

        if (stack.isEmpty() || stack.last().id != refreshedCurrent.id) {
            stack.clear()
            stack.addAll(findFolderPath(root, refreshedCurrent.id) ?: listOf(root))
        }

        binding.tvTitle.setText(R.string.bookmark_select_folder_title)
        binding.tvPath.text = formatFolderPath(stack.toList(), getString(R.string.bookmark_root_directory))

        selectedFolderId = refreshedCurrent.id

        val childFolders = refreshedCurrent.entries
            .filterIsInstance<BookmarkFolder>()
            .sortedBy { 
                val title = it.title
                when {
                    title.isBlank() -> getString(R.string.bookmark_root_directory)
                    title == "ROOT" && it.id == BookmarkRepository.ROOT_FOLDER_ID -> getString(R.string.bookmark_root_directory)
                    else -> title
                }
            }

        val items = mutableListOf<FolderItem>()
        if (stack.size > 1) {
            items.add(FolderItem.Up)
        }
        childFolders.forEach { items.add(FolderItem.Entry(it)) }

        folderAdapter.submitList(items, refreshedCurrent.id)
        
        // 显示或隐藏空布局
        // 当没有子文件夹时显示空布局（无论是否在根目录）
        val hasNoFolders = childFolders.isEmpty()
        binding.emptyView.root.isVisible = hasNoFolders
        // RecyclerView 始终显示（用于显示返回项）
        binding.rvFolders.isVisible = true

        val canSelect = !excludedIds.contains(selectedFolderId)
        binding.btnDone.isEnabled = canSelect
    }

    private fun refreshAfterMutation(targetFolderId: Long) {
        val root = viewModel.getRootFolder()
        val path = findFolderPath(root, targetFolderId) ?: listOf(root)
        stack.clear()
        stack.addAll(path)
        bindFolderList()
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (stack.size > 1) {
            stack.removeLast()
            bindFolderList()
        } else {
            super.onBackPressed()
        }
    }

    private inner class FolderAdapter : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {
        private val items = mutableListOf<FolderItem>()
        private var currentFolderId: Long = BookmarkRepository.ROOT_FOLDER_ID

        fun submitList(
            newItems: List<FolderItem>,
            currentFolderId: Long
        ) {
            items.clear()
            items.addAll(newItems)
            this.currentFolderId = currentFolderId
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_folder_picker_entry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val titleView: TextView = view.findViewById(R.id.tvName)
            private val checkView: ImageView = view.findViewById(R.id.ivSelection)
            private val selectionContainer: View = view.findViewById(R.id.selectionContainer)
            private val iconView: ImageView = view.findViewById(R.id.ivIcon)

            fun bind(item: FolderItem) {
                when (item) {
                    FolderItem.Up -> {
                        titleView.text = getString(R.string.bookmark_action_back)
                        iconView.setImageResource(R.drawable.ic_arrow_up)
                        iconView.setColorFilter(getColor(R.color.bookmark_text_secondary))
                        itemView.alpha = 1f
                        itemView.isEnabled = true
                        checkView.isVisible = false
                        selectionContainer.isVisible = false
                        itemView.setOnClickListener {
                            if (stack.size > 1) {
                                stack.removeLast()
                                bindFolderList()
                            }
                        }
                    }
                    is FolderItem.Entry -> {
                        val folder = item.folder
                        val displayName = when {
                            folder.title.isBlank() -> getString(R.string.bookmark_root_directory)
                            folder.title == "ROOT" && folder.id == BookmarkRepository.ROOT_FOLDER_ID -> 
                                getString(R.string.bookmark_root_directory)
                            else -> folder.title
                        }
                        titleView.text = displayName
                        iconView.setImageResource(R.drawable.ic_bookmark_folder)
                        iconView.setColorFilter(getColor(R.color.bookmark_folder_icon))
                        val disabled = excludedIds.contains(folder.id)
                        val isCurrent = folder.id == currentFolderId

                        itemView.alpha = if (disabled) 0.4f else 1f
                        itemView.isEnabled = true

                        itemView.setOnClickListener {
                            stack.addLast(folder)
                            bindFolderList()
                        }

                        checkView.isVisible = false
                        selectionContainer.isVisible = false
                        itemView.foreground = if (isCurrent) getDrawable(R.drawable.bg_parent_folder_field) else null
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_INITIAL_FOLDER_ID = "extra_initial_folder_id"
        private const val EXTRA_EXCLUDED_IDS = "extra_excluded_ids"
        const val EXTRA_SELECTED_FOLDER_ID = "extra_selected_folder_id"
        const val EXTRA_SELECTED_FOLDER_PATH = "extra_selected_folder_path"

        fun createIntent(
            context: Context,
            initialFolderId: Long,
            excludedIds: LongArray? = null
        ): Intent {
            return Intent(context, BookmarkFolderPickerActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_FOLDER_ID, initialFolderId)
                putExtra(EXTRA_EXCLUDED_IDS, excludedIds)
            }
        }
    }

    private sealed class FolderItem {
        object Up : FolderItem()
        data class Entry(val folder: BookmarkFolder) : FolderItem()
    }
}
