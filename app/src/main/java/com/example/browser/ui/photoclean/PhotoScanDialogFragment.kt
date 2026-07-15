package com.example.browser.ui.photoclean

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.browser.common.loadNative
import com.example.browser.R
import com.example.browser.databinding.DialogPhotoScanBinding
import com.example.browser.ui.photoclean.model.PhotoCleanGroup
import com.example.browser.ui.photoclean.model.PhotoCleanMode
import com.example.browser.ui.photoclean.scanner.DuplicatePhotoFinder
import com.example.browser.ui.photoclean.scanner.SimilarPhotoFinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PhotoScanDialogFragment : DialogFragment() {

    private var _binding: DialogPhotoScanBinding? = null
    private val binding get() = _binding!!

    private var scanJob: Job? = null
    private var scanResult: List<PhotoCleanGroup> = emptyList()
    private var onResultReady: ((List<PhotoCleanGroup>) -> Unit)? = null
    private val scanUiState = MutableStateFlow<PhotoScanUiState>(PhotoScanUiState.Scanning())

    private val cleanMode: PhotoCleanMode
        get() {
            val modeStr = arguments?.getString(ARG_MODE) ?: PhotoCleanMode.DUPLICATE.name
            return PhotoCleanMode.valueOf(modeStr)
        }

    companion object {
        private const val TAG = "PhotoScanDialog"
        private const val ARG_MODE = "clean_mode"

        fun newInstance(mode: PhotoCleanMode): PhotoScanDialogFragment {
            return PhotoScanDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, mode.name)
                }
            }
        }
    }

    fun setOnResultReadyListener(listener: (List<PhotoCleanGroup>) -> Unit) {
        onResultReady = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPhotoScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeScanUiState()
        startScan()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun setupUI() {
        binding.ivClose.setOnClickListener {
            cancelScan()
            dismiss()
        }

        binding.btnViewResult.setOnClickListener {
            dismiss()
            onResultReady?.invoke(scanResult)
        }

        showScanningState()
        loadNativeAd()
    }

    private fun showScanningState() {
        binding.ivScanSpinner.visibility = View.VISIBLE
        binding.ivDone.visibility = View.GONE
        binding.tvScanTitle.text = getString(R.string.photo_clean_scanning)
        binding.tvFilePath.visibility = View.VISIBLE
        binding.llProgress.visibility = View.VISIBLE
        binding.btnViewResult.visibility = View.GONE
        binding.tvResultInfo.visibility = View.GONE
        binding.tvNoResult.visibility = View.GONE
        binding.pbProgress.progress = 0
        binding.tvPercent.text = "0%"
        startSpinnerAnimation()
    }

    /** 所有 View 更新都在主线程按单一状态渲染，避免后台回调直接排队操作 View。 */
    private fun observeScanUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            scanUiState.collect { state ->
                when (state) {
                    is PhotoScanUiState.Scanning -> showProgressState(state)
                    PhotoScanUiState.Completed -> showCompletedState(scanResult)
                }
            }
        }
    }

    private fun showProgressState(state: PhotoScanUiState.Scanning) {
        val b = _binding ?: return
        b.pbProgress.progress = state.progressPercent
        b.tvPercent.text = "${state.progressPercent}%"
        if (state.currentFile.isNotEmpty()) {
            b.tvFilePath.text = state.currentFile
        }
    }

    private fun showCompletedState(groups: List<PhotoCleanGroup>) {
        stopSpinnerAnimation()
        val b = _binding ?: return
        b.ivScanSpinner.visibility = View.GONE
        b.ivDone.visibility = View.VISIBLE
        b.pbProgress.progress = 100
        b.tvPercent.text = "100%"
        b.tvFilePath.visibility = View.GONE

        if (groups.isNotEmpty()) {
            val totalPhotos = groups.sumOf { it.photoCount }
            b.tvScanTitle.text = getString(R.string.photo_clean_scan_complete)
            b.tvResultInfo.visibility = View.VISIBLE
            b.tvResultInfo.text = getString(
                R.string.photo_clean_found_groups,
                groups.size,
                totalPhotos
            )
            b.btnViewResult.visibility = View.VISIBLE
            b.tvNoResult.visibility = View.GONE
        } else {
            b.tvScanTitle.text = getString(R.string.photo_clean_scan_complete)
            b.tvNoResult.visibility = View.VISIBLE
            b.tvNoResult.text = getString(
                if (cleanMode == PhotoCleanMode.DUPLICATE)
                    R.string.photo_clean_no_duplicates
                else
                    R.string.photo_clean_no_similar
            )
            b.tvResultInfo.visibility = View.GONE
            b.btnViewResult.visibility = View.GONE
        }
    }

    private fun startScan() {
        scanUiState.value = PhotoScanUiState.Scanning()
        scanJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val appContext = requireContext().applicationContext
                scanResult = when (cleanMode) {
                    PhotoCleanMode.DUPLICATE -> {
                        DuplicatePhotoFinder.findDuplicates(appContext) { progress ->
                            updateProgress(progress.progressPercent, progress.currentFile)
                        }
                    }
                    PhotoCleanMode.SIMILAR -> {
                        SimilarPhotoFinder.findSimilar(appContext) { progress ->
                            updateProgress(progress.progressPercent, progress.currentFile)
                        }
                    }
                }
                scanUiState.value = PhotoScanUiState.Completed
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Photo scan failed for mode=$cleanMode", error)
                scanResult = emptyList()
                scanUiState.value = PhotoScanUiState.Completed
            }
        }
    }

    /** StateFlow.update 可从扫描线程安全调用，并会合并低端机来不及渲染的中间进度。 */
    private fun updateProgress(progressPercent: Int, currentFile: String) {
        scanUiState.update { state ->
            state.withProgress(progressPercent, currentFile)
        }
    }

    private fun startSpinnerAnimation() {
        val rotateAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.rotate_scan_spinner)
        binding.ivScanSpinner.startAnimation(rotateAnim)
    }

    private fun stopSpinnerAnimation() {
        _binding?.ivScanSpinner?.clearAnimation()
    }

    private fun loadNativeAd() {
        _binding?.flNativeAd?.let { activity?.loadNative(it) }
    }

    private fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    override fun onDestroyView() {
        cancelScan()
        stopSpinnerAnimation()
        _binding = null
        super.onDestroyView()
    }
}
