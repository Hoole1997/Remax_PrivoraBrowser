package com.example.browser.ui.download.model

/**
 * 下载状态枚举
 */
enum class DownloadStatus {
    /**
     * 下载中
     */
    DOWNLOADING,

    /**
     * 已暂停
     */
    PAUSED,

    /**
     * 下载完成
     */
    COMPLETED,

    /**
     * 下载失败
     */
    FAILED,

    /**
     * 已取消
     */
    CANCELLED
}
