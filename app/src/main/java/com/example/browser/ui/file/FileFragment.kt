package com.example.browser.ui.file

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ToastUtils
import com.example.browser.R
import com.example.browser.base.BaseFragment
import com.example.browser.databinding.FragmentFileBinding
import com.example.browser.ui.MainActivity
import com.example.browser.ui.dialog.StoragePermissionDialog
import com.example.browser.ui.download.DownloadActivity
import com.example.browser.ui.file.model.FileType
import com.example.browser.ui.file.model.RecentFile
import com.example.browser.ui.file.model.StorageInfo
import com.example.browser.ui.junk.JunkScanActivity
import com.example.browser.utils.FileManagerUtils
import com.example.browser.utils.StorageUtils
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileFragment : BaseFragment<FragmentFileBinding, FileModel>() {

    private var hasStoragePermissionState by mutableStateOf(false)
    private var storageUiState by mutableStateOf(FileStorageUiState())
    private var fileCategoriesState by mutableStateOf(emptyList<FileCategoryCardUiState>())
    private var recentFilesState by mutableStateOf(emptyList<RecentFile>())

    override fun initBinding(): FragmentFileBinding {
        return FragmentFileBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): FileModel {
        return activityViewModels<FileModel>().value
    }

    override fun initView() {
        binding?.composeContent?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FileScreen(
                    hasPermission = hasStoragePermissionState,
                    storageUiState = storageUiState,
                    categories = fileCategoriesState,
                    recentFiles = recentFilesState,
                    onCleanClick = ::openClean,
                    onDownloadClick = ::openDownloads,
                    onCategoryClick = ::openCategory,
                    onRecentFileClick = { file -> file.open(requireContext()) },
                    onRecentFileDeleted = ::handleFileDeleted,
                    onPermissionClick = ::requestPermission,
                )
            }
        }
    }

    override fun lazyLoad() {
        super.lazyLoad()
        refreshPageData()
    }

    override fun onResume() {
        super.onResume()
        refreshPageData()
    }

    private fun refreshPageData() {
        loadStorageInfo()
        hasStoragePermissionState = hasStoragePermission()
        if (hasStoragePermissionState) {
            loadFilesData()
        } else {
            recentFilesState = emptyList()
            fileCategoriesState = defaultCategories(zeroCounts = true)
        }
    }

    private fun hasStoragePermission(): Boolean {
        return XXPermissions.isGrantedPermissions(
            activity ?: return false,
            arrayOf(PermissionLists.getManageExternalStoragePermission()),
        )
    }

    private fun openClean() {
        if (hasStoragePermission()) {
            JunkScanActivity.start(activity ?: return)
        } else {
            showStoragePermissionDialog()
        }
    }

    private fun openDownloads() {
        DownloadActivity.start(activity ?: return)
    }

    private fun openCategory(fileType: FileType) {
        if (hasStoragePermission()) {
            FileListActivity.start(
                activity ?: return,
                fileType,
                getCategoryTitle(fileType),
            )
        } else {
            showStoragePermissionDialog()
        }
    }

    private fun showStoragePermissionDialog() {
        StoragePermissionDialog(
            context = activity ?: return,
            onGoNowClick = ::requestPermission,
        ).show()
    }

    private fun requestPermission() {
        XXPermissions.with(this)
            .permission(PermissionLists.getManageExternalStoragePermission())
            .request { _, deniedList ->
                val granted = deniedList.isEmpty()
                hasStoragePermissionState = granted
                if (granted) {
                    loadFilesData()
                } else {
                    recentFilesState = emptyList()
                    fileCategoriesState = defaultCategories(zeroCounts = true)
                }
            }

        lifecycleScope.launch(Dispatchers.IO) {
            repeat(25) {
                delay(200)
                if (XXPermissions.isGrantedPermissions(
                        activity ?: ActivityUtils.getTopActivity(),
                        arrayOf(PermissionLists.getManageExternalStoragePermission()),
                    )
                ) {
                    ActivityUtils.startActivity(MainActivity::class.java)
                    return@launch
                }
            }
        }
    }

    private fun loadStorageInfo() {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    StorageUtils.getStorageInfo(requireContext())
                }
            }.onSuccess { info ->
                storageUiState = buildStorageUiState(info)
            }
        }
    }

    private fun loadFilesData() {
        fileCategoriesState = defaultCategories(zeroCounts = false, placeholder = "...")

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val context = requireContext()
                    val categories = buildCategoriesWithCounts(context, zeroCounts = false)
                    val recentFiles = FileManagerUtils.getRecentFiles(context, 50)
                    categories to recentFiles
                }
            }.onSuccess { (categories, recentFiles) ->
                fileCategoriesState = categories
                recentFilesState = recentFiles
            }.onFailure {
                ToastUtils.showShort(R.string.files_load_failed)
                fileCategoriesState = defaultCategories(zeroCounts = true)
                recentFilesState = emptyList()
            }
        }
    }

    private fun handleFileDeleted(file: RecentFile) {
        recentFilesState = recentFilesState.filterNot { it.file.absolutePath == file.file.absolutePath }
        if (hasStoragePermission()) {
            lifecycleScope.launch {
                fileCategoriesState = withContext(Dispatchers.IO) {
                    buildCategoriesWithCounts(requireContext(), zeroCounts = false)
                }
            }
        }
    }

    private fun buildStorageUiState(storageInfo: StorageInfo): FileStorageUiState {
        val totalSpace = storageInfo.totalSpace.coerceAtLeast(1L)
        val otherUsed = (storageInfo.usedSpace -
            storageInfo.appSize -
            storageInfo.videoSize -
            storageInfo.imageSize -
            storageInfo.audioSize).coerceAtLeast(0L)

        val segments = listOf(
            otherUsed to Color.White,
            storageInfo.appSize to Color(0xFFFEBE42),
            storageInfo.videoSize to Color(0xFFFC4643),
            storageInfo.imageSize to Color(0xFF6DC882),
            storageInfo.audioSize to Color(0xFF706EF6),
        ).mapNotNull { (value, color) ->
            if (value <= 0L) {
                null
            } else {
                FileStorageSegmentUiState(
                    fraction = (value.toFloat() / totalSpace.toFloat()).coerceIn(0f, 1f),
                    color = color,
                )
            }
        }

        return FileStorageUiState(
            usedLabel = StorageUtils.formatSize(storageInfo.usedSpace),
            totalLabel = StorageUtils.formatSize(storageInfo.totalSpace),
            segments = segments,
        )
    }

    private fun buildCategoriesWithCounts(
        context: android.content.Context,
        zeroCounts: Boolean,
    ): List<FileCategoryCardUiState> {
        return categoryDefinitions().map { (type, title, iconRes) ->
            FileCategoryCardUiState(
                fileType = type,
                title = title,
                count = if (zeroCounts) {
                    context.getString(R.string.files_items_count, 0)
                } else {
                    context.getString(
                        R.string.files_items_count,
                        FileManagerUtils.getFileCountByType(context, type),
                    )
                },
                iconRes = iconRes,
            )
        }
    }

    private fun defaultCategories(
        zeroCounts: Boolean,
        placeholder: String? = null,
    ): List<FileCategoryCardUiState> {
        return categoryDefinitions().map { (type, title, iconRes) ->
            FileCategoryCardUiState(
                fileType = type,
                title = title,
                count = placeholder ?: if (zeroCounts) {
                    getString(R.string.files_items_count, 0)
                } else {
                    "..."
                },
                iconRes = iconRes,
            )
        }
    }

    private fun getCategoryTitle(type: FileType): String {
        return categoryDefinitions().first { it.type == type }.title
    }

    private fun categoryDefinitions(): List<CategoryDefinition> {
        return listOf(
            CategoryDefinition(FileType.IMAGE, getString(R.string.file_category_image), R.drawable.ic_file_redesign_category_images),
            CategoryDefinition(FileType.VIDEO, getString(R.string.file_category_video), R.drawable.ic_file_redesign_category_video),
            CategoryDefinition(FileType.DOCUMENT, getString(R.string.file_category_document), R.drawable.ic_file_redesign_category_documents),
            CategoryDefinition(FileType.APK, getString(R.string.file_category_apk), R.drawable.ic_file_redesign_category_apks),
            CategoryDefinition(FileType.AUDIO, getString(R.string.file_category_audio), R.drawable.ic_file_redesign_category_music),
            CategoryDefinition(FileType.ZIP, getString(R.string.file_category_zip), R.drawable.ic_file_redesign_category_zip),
        )
    }

    private data class CategoryDefinition(
        val type: FileType,
        val title: String,
        val iconRes: Int,
    )
}
