package com.example.browser.ui.file

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.blankj.utilcode.util.ToastUtils
import com.browser.common.loadNative
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivityFileListBinding
import com.example.browser.ui.file.adapter.RecentFilesAdapter
import com.example.browser.ui.file.model.FileType
import com.example.browser.utils.FileManagerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文件列表页面
 */
class FileListActivity : BaseActivity<ActivityFileListBinding, FileModel>() {

    private lateinit var filesAdapter: RecentFilesAdapter
    private var fileType: FileType = FileType.OTHER

    companion object {
        private const val EXTRA_FILE_TYPE = "extra_file_type"
        private const val EXTRA_TITLE = "extra_title"

        /**
         * 启动FileListActivity
         */
        fun start(context: Context, fileType: FileType, title: String) {
            val intent = Intent(context, FileListActivity::class.java).apply {
                putExtra(EXTRA_FILE_TYPE, fileType.name)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    override fun initBinding(): ActivityFileListBinding {
        return ActivityFileListBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): FileModel {
        return viewModels<FileModel>().value
    }

    override fun initView() {
        // 获取传入的文件类型和标题
        fileType = FileType.valueOf(
            intent.getStringExtra(EXTRA_FILE_TYPE) ?: FileType.OTHER.name
        )
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        setupToolbar(title)
        setupRecyclerView()
        loadFiles()

        loadNative(binding.adContainer)
    }

    /**
     * 设置Toolbar
     */
    private fun setupToolbar(title: String) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setTitle(title)
        }

        // 处理返回按钮点击
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        filesAdapter = RecentFilesAdapter(
            onItemClick = { file ->
                // 打开文件
                file.open()
            },
            onFileDeleted = { deletedFile ->
                // 文件删除后，直接从列表中移除该项
                filesAdapter.removeItem(deletedFile)
            }
        )

        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(this@FileListActivity)
            adapter = filesAdapter
        }
    }

    /**
     * 加载文件列表
     */
    private fun loadFiles() {
        lifecycleScope.launch {
            try {
                // 在后台线程加载数据
                val files = withContext(Dispatchers.IO) {
                    FileManagerUtils.getFilesByType(this@FileListActivity, fileType)
                }

                // 更新UI
                withContext(Dispatchers.Main) {
                    filesAdapter.submitList(files)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
