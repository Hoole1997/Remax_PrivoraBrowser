package com.example.browser.ui.photoclean

/**
 * 扫描弹窗的单一 UI 状态。
 *
 * 扫描线程只提交状态，不直接操作 View。进度更新会被 StateFlow 合并，避免低性能设备
 * 因主线程积压大量 View.post 任务而在完成后显示旧百分比。
 */
internal sealed interface PhotoScanUiState {
    data class Scanning(
        val progressPercent: Int = 0,
        val currentFile: String = "",
    ) : PhotoScanUiState

    data object Completed : PhotoScanUiState
}

/**
 * 合并一次扫描进度。扫描阶段最多展示 99%，只有 Completed 状态可以展示 100%。
 * Completed 是终态，迟到的后台回调不能再覆盖完成页面。
 */
internal fun PhotoScanUiState.withProgress(
    progressPercent: Int,
    currentFile: String,
): PhotoScanUiState {
    if (this !is PhotoScanUiState.Scanning) return this

    return copy(
        progressPercent = if (progressPercent >= 0) {
            progressPercent.coerceIn(0, 99)
        } else {
            this.progressPercent
        },
        currentFile = currentFile.ifEmpty { this.currentFile },
    )
}
