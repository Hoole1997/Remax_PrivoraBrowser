package com.example.browser.ui.download

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.ext.AdShowExt
import com.blankj.utilcode.util.ToastUtils
import com.browser.common.loadInterstitial
import com.example.browser.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ironsource.ad
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.support.ktx.kotlin.toShortUrl
import net.corekit.core.report.ReportDataManager

/**
 * 自定义下载确认对话框
 * 使用 BottomSheetDialog 样式，更符合现代 Material Design
 */
class CustomDownloadDialogFragment : BottomSheetDialogFragment() {

    private var downloadState: DownloadState? = null
    private var onDownloadConfirmed: ((DownloadState) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        // 设置为透明背景，去掉默认白色背景
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }
        ReportDataManager.reportData("dialog_show", mapOf("dialog_name" to javaClass.simpleName))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_download_confirm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    override fun dismiss() {
        super.dismiss()
        ReportDataManager.reportData("dialog_dismiss", mapOf("dialog_name" to javaClass.simpleName))
    }
    /**
     * 设置视图
     */
    private fun setupViews(view: View) {
        val download = downloadState ?: return

        // 标题（固定为"是否下载?"）
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        tvTitle.text = "是否下载?"

        // 文件名
        val tvFileName = view.findViewById<TextView>(R.id.tv_file_name)
        tvFileName.text = download.fileName ?: "未知文件"

        // 文件大小
        val tvFileSize = view.findViewById<TextView>(R.id.tv_file_size)
        tvFileSize.text = formatFileSize(download.contentLength)

        // 文件图标（统一使用未知文件图标v2）
        val ivFileIcon = view.findViewById<ImageView>(R.id.iv_file_icon)
        ivFileIcon.setImageResource(R.drawable.ic_file_unknown_v2)

        // 下载按钮
        val btnDownload = view.findViewById<Button>(R.id.btn_download)
        btnDownload.setOnClickListener {
            activity?.loadInterstitial {
                onDownloadConfirmed?.invoke(download)
                dismiss()
            }
        }
        ReportDataManager.reportData("ConfirmDownloadDialog",mapOf())
        loadNativeAd(view.findViewById(R.id.adContainer))
    }

    private fun loadNativeAd(adContainer: ViewGroup) {
        lifecycleScope.launch {
            try {
                val success = AdShowExt.showNativeAdInContainer(activity?:return@launch,adContainer)
                adContainer.visibility = if (success) View.VISIBLE else View.GONE
            } catch (_: Exception) {
                adContainer.visibility = View.GONE
            }
        }
    }

    /**
     * 根据文件类型设置图标
     */
    private fun setupFileIcon(imageView: ImageView, fileName: String?) {
        val name = fileName ?: ""
        when {
            name.endsWith(".apk", ignoreCase = true) -> {
                imageView.setImageResource(R.drawable.ic_file_apk)
                imageView.setBackgroundResource(R.drawable.bg_file_icon_circle)
            }
            name.endsWith(".jpg", ignoreCase = true) ||
            name.endsWith(".jpeg", ignoreCase = true) ||
            name.endsWith(".png", ignoreCase = true) ||
            name.endsWith(".gif", ignoreCase = true) -> {
                imageView.setImageResource(R.drawable.ic_file_image)
                imageView.setBackgroundResource(R.drawable.bg_file_icon_circle)
            }
            name.endsWith(".mp4", ignoreCase = true) ||
            name.endsWith(".avi", ignoreCase = true) ||
            name.endsWith(".mkv", ignoreCase = true) -> {
                imageView.setImageResource(R.drawable.ic_file_video)
                imageView.setBackgroundResource(R.drawable.bg_file_icon_circle)
            }
            name.endsWith(".pdf", ignoreCase = true) ||
            name.endsWith(".doc", ignoreCase = true) ||
            name.endsWith(".docx", ignoreCase = true) ||
            name.endsWith(".txt", ignoreCase = true) -> {
                imageView.setImageResource(R.drawable.ic_file_document)
                imageView.setBackgroundResource(R.drawable.bg_file_icon_circle)
            }
            name.endsWith(".mp3", ignoreCase = true) ||
            name.endsWith(".wav", ignoreCase = true) ||
            name.endsWith(".flac", ignoreCase = true) -> {
                imageView.setImageResource(R.drawable.ic_file_audio)
                imageView.setBackgroundResource(R.drawable.bg_file_icon_circle)
            }
            name.endsWith(".zip", ignoreCase = true) ||
            name.endsWith(".rar", ignoreCase = true) ||
            name.endsWith(".7z", ignoreCase = true) -> {
                imageView.setImageResource(R.drawable.ic_file_zip)
                imageView.setBackgroundResource(R.drawable.bg_file_icon_circle)
            }
            else -> {
                imageView.setImageResource(R.drawable.ic_file_document)
                imageView.setBackgroundResource(R.drawable.bg_file_icon_circle)
            }
        }
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0) return "未知大小"

        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            bytes >= gb -> String.format("%.2f GB", bytes / gb)
            bytes >= mb -> String.format("%.1f MB", bytes / mb)
            bytes >= kb -> String.format("%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    companion object {
        private const val TAG = "CustomDownloadDialog"

        /**
         * 创建对话框实例
         */
        fun newInstance(
            downloadState: DownloadState,
            onConfirm: (DownloadState) -> Unit
        ): CustomDownloadDialogFragment {
            return CustomDownloadDialogFragment().apply {
                this.downloadState = downloadState
                this.onDownloadConfirmed = onConfirm
            }
        }
    }
}
