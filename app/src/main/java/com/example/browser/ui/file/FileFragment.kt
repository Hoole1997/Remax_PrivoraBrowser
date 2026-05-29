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
import com.browser.common.loadInterstitial
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
import com.example.browser.ui.junk.ProcessCleanActivity
import com.example.browser.ui.photoclean.PhotoCleanActivity
import com.example.browser.ui.photoclean.PhotoScanDialogFragment
import com.example.browser.ui.photoclean.model.PhotoCleanMode
import com.example.browser.ui.speed.SpeedTestActivity
import com.example.browser.utils.FileManagerUtils
import com.example.browser.utils.StorageUtils
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.corekit.core.report.ReportDataManager

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
                    onFeatureClick = ::handleFeatureClick,
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

    /** 5 个功能入口的统一入口。需要存储权限的入口会先弹权限对话框。 */
    private fun handleFeatureClick(action: FileFeatureAction) {
        when (action) {
            FileFeatureAction.CLEAN -> requireStorageThen {
                ReportDataManager.reportData("CleanIcon_ Click",mapOf("Entry_Position" to "file"))
                JunkScanActivity.start(activity ?: return@requireStorageThen)
            }

            FileFeatureAction.SPEED -> {
                ReportDataManager.reportData("speed_test_Click",mapOf())
                activity?.let { SpeedTestActivity.start(it) }
            }

            FileFeatureAction.PROCESS -> {
                ReportDataManager.reportData("ProcessManage_Click",mapOf())
                activity?.let { ProcessCleanActivity.start(it) }
            }

            FileFeatureAction.DUPLICATE_PHOTO -> requireStorageThen {
                activity?.loadInterstitial {
                    launchPhotoClean(PhotoCleanMode.DUPLICATE)
                }
            }

            FileFeatureAction.SIMILAR_PHOTO -> requireStorageThen {
                activity?.loadInterstitial {
                    launchPhotoClean(PhotoCleanMode.SIMILAR)
                }
            }
        }
    }

    private fun openDownloads() {
        DownloadActivity.start(activity ?: return)
    }

    private fun openCategory(fileType: FileType) {
        requireStorageThen {
            FileListActivity.start(
                activity ?: return@requireStorageThen,
                fileType,
                getCategoryTitle(fileType),
            )
        }
    }

    /** 检查存储权限：已授权直接执行 [block]；否则弹权限对话框。 */
    private fun requireStorageThen(block: () -> Unit) {
        if (hasStoragePermission()) {
            block()
        } else {
            StoragePermissionDialog(
                context = activity ?: return,
                onGoNowClick = ::requestPermission,
            ).show()
        }
    }

    private fun launchPhotoClean(mode: PhotoCleanMode) {
        if (mode == PhotoCleanMode.DUPLICATE) {
            ReportDataManager.reportData("Duplicate_Photo_Click",mapOf("Entry_Position" to "file"))
        } else {
            ReportDataManager.reportData("Similar_Photo_Click",mapOf("Entry_Position" to "file"))
        }
        val dialog = PhotoScanDialogFragment.newInstance(mode)
        dialog.setOnResultReadyListener { groups ->
            val ctx = activity ?: return@setOnResultReadyListener
            ReportDataManager.reportData(if (mode == PhotoCleanMode.DUPLICATE) "View_Result_Click" else "View_SimilarResult_Click",mapOf())
            PhotoCleanActivity.start(ctx, mode, groups)
        }
        dialog.show(childFragmentManager, "photo_scan_dialog")
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
        val usedSpace = storageInfo.usedSpace.coerceIn(0L, totalSpace)

        // 仅展示 Apps / Videos / Photos / Music 四类占用，"其他" 不在指示器中体现，
        // 避免出现灰色未知段落
        val segments = listOf(
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
            usedFraction = (usedSpace.toFloat() / totalSpace.toFloat()).coerceIn(0f, 1f),
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
                    "0"
                } else {
                    FileManagerUtils.getFileCountByType(context, type).toString()
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
                count = placeholder ?: if (zeroCounts) "0" else "...",
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
