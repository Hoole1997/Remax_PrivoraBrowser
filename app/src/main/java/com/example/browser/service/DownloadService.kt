package com.example.browser.service

import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.support.base.android.NotificationsDelegate
import com.example.browser.R
import com.example.browser.components

/**
 * 下载服务
 * 使用 Mozilla Components 的 AbstractFetchDownloadService 实现后台下载
 *
 * 功能：
 * - 后台下载文件
 * - 显示下载通知
 * - 管理下载状态
 */
class DownloadService : AbstractFetchDownloadService() {

    /**
     * HTTP 客户端，用于执行下载请求
     */
    override val httpClient: Client by lazy {
        components.client
    }

    /**
     * 浏览器状态存储
     */
    override val store: BrowserStore by lazy {
        components.store
    }

    /**
     * 通知样式配置
     */
    override val style: Style by lazy {
        Style(R.color.main_nav_check_color)
    }

    /**
     * 文件大小格式化器
     * 用于在通知中显示文件大小
     */
    override val fileSizeFormatter by lazy {
        components.fileSizeFormatter
    }

    /**
     * 日期时间提供器
     * 用于在通知中显示时间
     */
    override val dateTimeProvider by lazy {
        components.dateTimeProvider
    }

    /**
     * 通知代理
     * 用于管理下载通知
     */
    override val notificationsDelegate: NotificationsDelegate by lazy {
        components.notificationsDelegate
    }
}
