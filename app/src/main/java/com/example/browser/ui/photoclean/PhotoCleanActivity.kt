package com.example.browser.ui.photoclean

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.ActivityUtils
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivityPhotoCleanBinding
import com.example.browser.ui.photoclean.adapter.PhotoCleanAdapter
import com.example.browser.ui.photoclean.model.PhotoCleanGroup
import com.example.browser.ui.photoclean.model.PhotoCleanMode
import kotlinx.coroutines.launch
import net.corekit.core.report.ReportDataManager
import java.io.File

class PhotoCleanActivity : BaseActivity<ActivityPhotoCleanBinding, PhotoCleanViewModel>() {

    private lateinit var photoAdapter: PhotoCleanAdapter

    private val cleanMode: PhotoCleanMode
        get() {
            val modeStr = intent.getStringExtra(EXTRA_MODE) ?: PhotoCleanMode.DUPLICATE.name
            return PhotoCleanMode.valueOf(modeStr)
        }

    companion object {
        private const val EXTRA_MODE = "clean_mode"
        private const val EXTRA_GROUP_KEY = "group_key"
        private var pendingGroups: List<PhotoCleanGroup>? = null

        fun start(context: Context, mode: PhotoCleanMode, groups: List<PhotoCleanGroup>) {
            pendingGroups = groups
            val intent = Intent(context, PhotoCleanActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode.name)
            }
            context.startActivity(intent)
        }
    }

    override fun initBinding(): ActivityPhotoCleanBinding {
        return ActivityPhotoCleanBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): PhotoCleanViewModel {
        return viewModels<PhotoCleanViewModel>().value
    }

    override fun initView() {
        viewModel.cleanMode = cleanMode

        setupToolbar()
        setupRecyclerView()
        setupBottomBar()
        setupObservers()
        setupBackPress()

        // 加载数据
        val groups = pendingGroups ?: emptyList()
        pendingGroups = null
        viewModel.loadGroups(groups)

        loadBottomNativeAd()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.navigationIcon?.setTint(Color.parseColor("#333333"))
        binding.tvTitle.text = getString(
            if (cleanMode == PhotoCleanMode.DUPLICATE)
                R.string.photo_clean_title_duplicate
            else
                R.string.photo_clean_title_similar
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleBackPress()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoCleanAdapter(
            onPhotoClick = { groupId, photo ->
                viewModel.togglePhotoCheck(groupId, photo)
            },
            onExpandClick = { groupId ->
                viewModel.toggleGroupExpand(groupId)
            }
        )

        binding.rvPhotos.apply {
            layoutManager = LinearLayoutManager(this@PhotoCleanActivity)
            adapter = photoAdapter
            itemAnimator = null
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect, view: View,
                    parent: RecyclerView, state: RecyclerView.State
                ) {
                    val dp12 = (12 * resources.displayMetrics.density).toInt()
                    outRect.bottom = dp12
                }
            })
        }
    }

    private fun loadBottomNativeAd() {
        loadNative(binding.flBottomAd)
    }

    private fun setupBottomBar() {
        binding.btnSelectAll.setOnClickListener {
            viewModel.toggleSelectAll()
        }
        binding.btnDelete.setOnClickListener {
            val selectedFiles = viewModel.getSelectedFiles()
            if (selectedFiles.isEmpty()) return@setOnClickListener
            showDeleteConfirmDialog(selectedFiles)
        }
    }

    private fun setupObservers() {
        viewModel.listItems.observe(this) { items ->
            photoAdapter.submitList(items)
            val isEmpty = items.isNullOrEmpty()
            binding.rvPhotos.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.layoutEmpty.root.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.llBottomBar.visibility = if (isEmpty) View.GONE else View.VISIBLE
            if (isEmpty) binding.flBottomAd.visibility = View.GONE
        }

        viewModel.selectedCount.observe(this) { count ->
            binding.btnDelete.isEnabled = count > 0
            binding.btnDelete.alpha = if (count > 0) 1.0f else 0.5f
        }

        viewModel.isAllSelected.observe(this) { allSelected ->
            binding.btnSelectAll.text = getString(
                if (allSelected) R.string.photo_clean_deselect_all
                else R.string.photo_clean_select_all
            )
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun handleBackPress() {
        val selectedCount = viewModel.getSelectedPhotoCount()
        if (selectedCount > 0) {
            PhotoCleanConfirmDialog.show(
                supportFragmentManager,
                title = getString(R.string.photo_clean_reminder_title),
                message = getString(R.string.photo_clean_back_warning),
                confirmText = getString(R.string.photo_clean_confirm),
                cancelText = getString(R.string.photo_clean_cancel),
                onConfirm = { finishPlayAd() }
            )
        } else {
            finishPlayAd()
        }
    }

    private fun finishPlayAd() {
        loadInterstitial(position = if (cleanMode == PhotoCleanMode.SIMILAR) "IV_Similar_Back" else "IV_Same_Back") {
            finish()
        }
    }

    private fun showDeleteConfirmDialog(files: List<File>) {
        PhotoCleanConfirmDialog.show(
            supportFragmentManager,
            title = getString(R.string.photo_clean_reminder_title),
            message = getString(R.string.photo_clean_delete_warning, files.size),
            confirmText = getString(R.string.photo_clean_confirm),
            cancelText = getString(R.string.photo_clean_cancel),
            onConfirm = {
                if (cleanMode == PhotoCleanMode.DUPLICATE) {
                    ReportDataManager.reportData("Delete_Button_Click",mapOf("result" to "confirm"))
                } else {
                    ReportDataManager.reportData("Delete_SimilarButton_Click",mapOf("result" to "confirm"))
                }
                viewModel.removeSelectedPhotos()
                binding.rvPhotos.postDelayed({
                    PhotoDeleteProgressActivity.start(this, cleanMode, files)
                }, 200)
            },
            onCancel = {
                if (cleanMode == PhotoCleanMode.DUPLICATE) {
                    ReportDataManager.reportData("Delete_Button_Click",mapOf("result" to "cancel"))
                } else {
                    ReportDataManager.reportData("Delete_SimilarButton_Click",mapOf("result" to "cancel"))
                }
            }
        )
    }
}
