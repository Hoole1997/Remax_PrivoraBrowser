package com.example.browser.ui.download

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.ext.AdShowExt
import com.example.browser.R
import com.example.browser.ui.download.widget.DownloadProgressView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import net.corekit.core.report.ReportDataManager

class DownloadStartBottomSheet : BottomSheetDialogFragment() {

    private var fileName: String = ""
    private var onViewClick: (() -> Unit)? = null
    private var onDismissAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileName = arguments?.getString(ARG_FILE_NAME).orEmpty()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }
        ReportDataManager.reportData("dialog_show", mapOf("dialog_name" to javaClass.simpleName))
        return dialog
    }

    override fun dismiss() {
        super.dismiss()
        ReportDataManager.reportData("dialog_dismiss", mapOf("dialog_name" to javaClass.simpleName))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_download_start, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tvFileName = view.findViewById<TextView>(R.id.tv_file_name)
        val tvStatus = view.findViewById<TextView>(R.id.tv_status)
        val progressView = view.findViewById<DownloadProgressView>(R.id.progress_circle)
        val btnView = view.findViewById<View>(R.id.btn_view)

        tvFileName.text = fileName
        tvStatus.text = getString(R.string.download_started)
        progressView.setIndeterminate(true)

        btnView.setOnClickListener {
            onViewClick?.invoke()
            dismissAllowingStateLoss()
        }
        ReportDataManager.reportData("StartDownloadDialog",mapOf())
        lifecycleScope.launch {
            AdShowExt.showInterstitialAd(activity?:return@launch,view.findViewById(R.id.adContainer))
        }
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

    fun updateProgress(statusText: String, progress: Int?, indeterminate: Boolean) {
        view?.let { root ->
            val tvStatus = root.findViewById<TextView>(R.id.tv_status)
            val progressView = root.findViewById<DownloadProgressView>(R.id.progress_circle)

            tvStatus.text = statusText
            progressView.setIndeterminate(indeterminate)
            if (!indeterminate && progress != null) {
                progressView.setProgress(progress)
            }
        }
    }

    fun updateFileName(name: String) {
        fileName = name
        view?.findViewById<TextView>(R.id.tv_file_name)?.text = name
    }

    fun setOnViewClickListener(listener: () -> Unit) {
        onViewClick = listener
    }

    fun setOnDismissListener(listener: () -> Unit) {
        onDismissAction = listener
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissAction?.invoke()
    }

    companion object {
        private const val ARG_FILE_NAME = "arg_file_name"

        fun newInstance(fileName: String): DownloadStartBottomSheet {
            return DownloadStartBottomSheet().apply {
                arguments = bundleOf(ARG_FILE_NAME to fileName)
            }
        }
    }
}
