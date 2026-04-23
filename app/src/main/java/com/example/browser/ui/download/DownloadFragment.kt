package com.example.browser.ui.download

import android.app.DownloadManager
import android.content.Intent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.example.browser.R
import com.example.browser.base.BaseFragment
import com.example.browser.components
import com.example.browser.databinding.FragmentDownloadBinding
import com.example.browser.service.DownloadService
import com.example.browser.ui.download.adapter.DownloadAdapter
import com.example.browser.ui.download.model.DownloadItem
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.lib.state.ext.flowScoped

/**
 * 下载Fragment
 * 显示下载列表
 */
class DownloadFragment : BaseFragment<FragmentDownloadBinding, DownloadModel>() {

    private lateinit var downloadAdapter: DownloadAdapter
    private val speedTracker = DownloadSpeedTracker()

    override fun initBinding(): FragmentDownloadBinding {
        return FragmentDownloadBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): DownloadModel {
        return activityViewModels<DownloadModel>().value
    }

    override fun initView() {
        setupRecyclerView()
        observeDownloads()
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        downloadAdapter = DownloadAdapter(
            onItemClick = { download ->
                openDownloadedFile(download)
            },
            onPauseClick = { download ->
                pauseDownload(download)
            },
            onResumeClick = { download ->
                resumeDownload(download)
            },
            onCancelClick = { download ->
                cancelDownload(download)
            }
        )

        binding?.rvDownloads?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = downloadAdapter
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }
    }

    /**
     * 监听下载状态变化
     */
    private fun observeDownloads() {
        val store = requireContext().components.store
        store.flowScoped(viewLifecycleOwner) { flow ->
            flow.mapNotNull { state -> state.downloads }
                .collect { downloadsMap ->
                    // 转换为 DownloadItem 列表，并计算下载速度
                    val downloadItems = downloadsMap.values.map { download ->
                        val item = DownloadItem.fromDownloadState(download)

                        // 只为正在下载的项目计算速度
                        val speed = if (item.isDownloading() && item.downloadedBytes > 0) {
                            speedTracker.calculateSpeed(item.id, item.downloadedBytes)
                        } else {
                            speedTracker.clearSpeed(item.id)
                            0
                        }

                        // 返回包含速度信息的DownloadItem
                        item.copy(downloadSpeed = speed)
                    }

                    // 更新列表
                    downloadAdapter.submitList(downloadItems)

                    // 显示/隐藏空状态
                    if (downloadItems.isEmpty()) {
                        binding?.llEmpty?.visibility = View.VISIBLE
                        binding?.rvDownloads?.visibility = View.GONE
                    } else {
                        binding?.llEmpty?.visibility = View.GONE
                        binding?.rvDownloads?.visibility = View.VISIBLE
                    }
                }
        }
    }

    /**
     * 打开已下载的文件
     */
    private fun openDownloadedFile(download: DownloadItem) {
        LogUtils.d(GsonUtils.toJson(download))
        download.open()
    }

    /**
     * 暂停下载
     */
    private fun pauseDownload(download: DownloadItem) {
        if (!download.isDownloading()) {
            ToastUtils.showShort("当前任务未在下载中")
            return
        }

        try {
            sendDownloadCommand(AbstractFetchDownloadService.ACTION_PAUSE, download.id)
            ToastUtils.showShort("已暂停")
        } catch (e: Exception) {
            e.printStackTrace()
            ToastUtils.showShort("暂停失败")
        }
    }

    /**
     * 恢复下载
     */
    private fun resumeDownload(download: DownloadItem) {
        if (!download.isPaused()) {
            ToastUtils.showShort("当前任务未暂停")
            return
        }

        try {
            sendDownloadCommand(AbstractFetchDownloadService.ACTION_RESUME, download.id)
            ToastUtils.showShort("继续下载")
            lifecycleScope.launch {
                delay(300)
                val storeDownload = requireContext().components.store.state.downloads[download.id]
                if (storeDownload?.status == DownloadState.Status.PAUSED) {
                    ensureDownloadService(download.id)
                    delay(200)
                    sendDownloadCommand(AbstractFetchDownloadService.ACTION_RESUME, download.id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ToastUtils.showShort("继续失败")
        }
    }

    /**
     * 取消/删除下载
     */
    private fun cancelDownload(download: DownloadItem) {
        lifecycleScope.launch {
            try {
                val useCases = requireContext().components.downloadsUseCases

                speedTracker.clearSpeed(download.id)

                if (download.isDownloading() || download.isPaused()) {
                    sendDownloadCommand(AbstractFetchDownloadService.ACTION_CANCEL, download.id)
                    ToastUtils.showShort("已取消下载")
                } else if (download.isCancelled()) {
                    ToastUtils.showShort(getString(R.string.removed_from_list))
                } else {
                    ToastUtils.showShort("已删除")
                }

                useCases.removeDownload(download.id)

                if (download.filePath != null && download.isCompleted()) {
                    val file = java.io.File(download.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort("操作失败")
            }
        }
    }

    private fun sendDownloadCommand(action: String, downloadId: String) {
        val appContext = requireContext().applicationContext
        val intent = Intent(action).apply {
            setPackage(appContext.packageName)
            putExtra("downloadId", downloadId)
        }
        appContext.sendBroadcast(intent)
    }

    private fun ensureDownloadService(downloadId: String) {
        val appContext = requireContext().applicationContext
        val serviceIntent = Intent(appContext, DownloadService::class.java).apply {
            putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId)
            putExtra("downloadId", downloadId)
        }
        ContextCompat.startForegroundService(appContext, serviceIntent)
    }
}
