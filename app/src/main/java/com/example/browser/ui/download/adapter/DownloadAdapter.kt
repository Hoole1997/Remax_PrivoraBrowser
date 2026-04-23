package com.example.browser.ui.download.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.LogUtils
import com.example.browser.R
import com.example.browser.ui.download.model.DownloadItem
import com.example.browser.ui.download.model.DownloadStatus
import com.example.browser.ui.download.widget.DownloadProgressView
import com.example.browser.utils.StorageUtils
import com.example.browser.utils.getFriendlyTimeSpan

/**
 * 下载列表适配器
 */
class DownloadAdapter(
    private val onItemClick: (DownloadItem) -> Unit,
    private val onPauseClick: (DownloadItem) -> Unit,
    private val onResumeClick: (DownloadItem) -> Unit,
    private val onCancelClick: (DownloadItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    private val items = mutableListOf<Any>()

    /**
     * 提交新数据
     * @param downloads 下载项列表
     */
    fun submitList(downloads: List<DownloadItem>) {
        val newItems = groupByDate(downloads)
        val diffCallback = DownloadDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * 按日期分组
     */
    private fun groupByDate(downloads: List<DownloadItem>): List<Any> {
        val result = mutableListOf<Any>()
        val sortedDownloads = downloads.sortedByDescending { it.createdTime }

        var currentGroup: String? = null
        for (download in sortedDownloads) {
            val group = getDateGroup(download.createdTime)
            if (group != currentGroup) {
                result.add(group)
                currentGroup = group
            }
            result.add(download)
        }

        return result
    }

    /**
     * 获取日期分组标题
     */
    private fun getDateGroup(timestamp: Long): String {
        val today = System.currentTimeMillis()
        val dayInMillis = 24 * 60 * 60 * 1000

        return when {
            timestamp > today - dayInMillis -> "今天"
            timestamp > today - 2 * dayInMillis -> "昨天"
            timestamp > today - 7 * dayInMillis -> "最近7天"
            else -> "更早"
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is String) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_download_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_download, parent, false)
            DownloadViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (holder is DownloadViewHolder && payloads.isNotEmpty()) {
            val payload = payloads.filterIsInstance<DownloadChangePayload>().firstOrNull()
            val item = items[position]
            if (payload != null && item is DownloadItem) {
                holder.bindPartial(item, payload)
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(items[position] as String)
            is DownloadViewHolder -> holder.bind(items[position] as DownloadItem)
        }
    }

    override fun getItemCount(): Int = items.size

    /**
     * 分组标题 ViewHolder
     */
    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeader: TextView = itemView.findViewById(R.id.tv_header)

        fun bind(header: String) {
            tvHeader.text = header
        }
    }

    /**
     * 下载项 ViewHolder
     */
    inner class DownloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById<ImageView>(R.id.iv_icon)!!
        private val tvFileName: TextView = itemView.findViewById<TextView>(R.id.tv_file_name)!!
        private val tvFileSize: TextView = itemView.findViewById<TextView>(R.id.tv_file_size)!!
        private val tvStatus: TextView = itemView.findViewById<TextView>(R.id.tv_status)!!
        private val flAction: View = itemView.findViewById<View>(R.id.fl_action)!!
        private val ivAction: ImageView = itemView.findViewById<ImageView>(R.id.iv_action)!!
        private val ivCancel: ImageView = itemView.findViewById<ImageView>(R.id.iv_cancel)!!
        private val progressIndicator: DownloadProgressView =
            itemView.findViewById<DownloadProgressView>(R.id.progress_circular)!!

        fun bind(item: DownloadItem) {
            LogUtils.d("下载状态：${item.status}")
            bindFileName(item)
            bindIcon(item)
            bindFileSize(item)
            bindStatusAndActions(item)
            bindProgress(item)
            bindListeners(item)
        }

        fun bindPartial(item: DownloadItem, payload: DownloadChangePayload) {
            if (payload.fileNameChanged) {
                bindFileName(item)
                bindIcon(item)
            }

            if (payload.progressChanged || payload.contentLengthChanged) {
                bindFileSize(item)
                bindProgress(item)
            }

            if (payload.statusChanged) {
                bindStatusAndActions(item)
                bindProgress(item)
            } else {
                if (payload.speedChanged && item.isDownloading()) {
                    bindSpeed(item)
                }
            }

            bindListeners(item)
        }

        private fun bindFileName(item: DownloadItem) {
            tvFileName.text = item.fileName
        }

        private fun bindIcon(item: DownloadItem) {
            ivIcon.setImageResource(getFileIcon(item.fileName))
        }

        private fun bindFileSize(item: DownloadItem) {
            val sizeText = when (item.status) {
                DownloadStatus.COMPLETED -> {
                    // 下载完成时，只显示文件大小
                    StorageUtils.formatSize(item.contentLength ?: item.downloadedBytes)
                }
                DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED -> {
                    // 下载中或暂停时，显示进度
                    if (item.contentLength != null && item.contentLength > 0) {
                        "${StorageUtils.formatSize(item.downloadedBytes)} / ${StorageUtils.formatSize(item.contentLength)}"
                    } else {
                        StorageUtils.formatSize(item.downloadedBytes)
                    }
                }
                else -> {
                    // 失败或取消时，显示已下载的大小
                    StorageUtils.formatSize(item.downloadedBytes)
                }
            }
            tvFileSize.text = sizeText
        }

        private fun bindStatusAndActions(item: DownloadItem) {
            when (item.status) {
                DownloadStatus.DOWNLOADING -> {
                    bindSpeed(item)
                    ivAction.setImageResource(R.drawable.ic_pause)
                    flAction.visibility = View.VISIBLE
                    ivCancel.visibility = View.VISIBLE
                    flAction.setOnClickListener { onPauseClick(item) }
                }
                DownloadStatus.PAUSED -> {
                    tvStatus.text = "已暂停"
                    tvStatus.visibility = View.VISIBLE
                    ivAction.setImageResource(R.drawable.ic_download_resume)
                    flAction.visibility = View.VISIBLE
                    ivCancel.visibility = View.VISIBLE
                    flAction.setOnClickListener { onResumeClick(item) }
                }
                DownloadStatus.COMPLETED -> {
                    // 显示下载时间
                    val timeStr = getFriendlyTimeSpan(itemView.context, item.createdTime)
                    tvStatus.text = timeStr
                    tvStatus.visibility = View.VISIBLE
                    flAction.visibility = View.GONE
                    ivCancel.visibility = View.VISIBLE
                }
                DownloadStatus.FAILED -> {
                    tvStatus.text = "下载失败"
                    tvStatus.visibility = View.VISIBLE
                    ivAction.setImageResource(R.drawable.ic_download_resume)
                    flAction.visibility = View.VISIBLE
                    ivCancel.visibility = View.VISIBLE
                    flAction.setOnClickListener { onResumeClick(item) }
                }
                DownloadStatus.CANCELLED -> {
                    tvStatus.text = "已取消"
                    tvStatus.visibility = View.VISIBLE
                    flAction.visibility = View.GONE
                    ivCancel.visibility = View.VISIBLE
                }
            }
        }

        private fun bindSpeed(item: DownloadItem) {
            val speedText = item.getFormattedSpeed()
            tvStatus.text = speedText
            tvStatus.visibility = if (speedText.isNotEmpty()) View.VISIBLE else View.GONE
        }

        private fun bindProgress(item: DownloadItem) {
            val shouldShow = item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED
            progressIndicator.isVisible = shouldShow
            if (shouldShow) {
                val isIndeterminate = item.contentLength == null || item.contentLength <= 0
                progressIndicator.setIndeterminate(isIndeterminate)
                if (!isIndeterminate) {
                    progressIndicator.setProgress(item.getProgress())
                }
            } else {
                progressIndicator.setIndeterminate(false)
                progressIndicator.setProgress(0)
            }
        }

        private fun bindListeners(item: DownloadItem) {
            ivCancel.setOnClickListener { onCancelClick(item) }
            itemView.setOnClickListener {
                if (item.isCompleted()) {
                    onItemClick(item)
                }
            }
        }

        /**
         * 根据文件名获取图标
         */
        private fun getFileIcon(fileName: String): Int {
            return when {
                fileName.endsWith(".apk", ignoreCase = true) -> R.mipmap.ic_file_apk
                fileName.endsWith(".zip", ignoreCase = true) ||
                fileName.endsWith(".rar", ignoreCase = true) -> R.mipmap.ic_file_zip
                fileName.matches(Regex(".*\\.(jpg|jpeg|png|gif|bmp|webp)", RegexOption.IGNORE_CASE)) -> R.mipmap.ic_file_image
                fileName.matches(Regex(".*\\.(mp4|avi|mkv|mov|flv)", RegexOption.IGNORE_CASE)) -> R.mipmap.ic_file_video
                fileName.matches(Regex(".*\\.(mp3|wav|flac|aac|ogg)", RegexOption.IGNORE_CASE)) -> R.mipmap.ic_file_audio
                fileName.matches(Regex(".*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|txt)", RegexOption.IGNORE_CASE)) -> R.mipmap.ic_file_document
                else -> R.mipmap.ic_file_unknown
            }
        }
    }

    /**
     * DiffUtil 回调
     */
    private class DownloadDiffCallback(
        private val oldList: List<Any>,
        private val newList: List<Any>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            return when {
                oldItem is String && newItem is String -> oldItem == newItem
                oldItem is DownloadItem && newItem is DownloadItem -> oldItem.id == newItem.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            return when {
                oldItem is String && newItem is String -> oldItem == newItem
                oldItem is DownloadItem && newItem is DownloadItem -> {
                    oldItem.status == newItem.status &&
                    oldItem.downloadedBytes == newItem.downloadedBytes &&
                    oldItem.fileName == newItem.fileName &&
                    oldItem.downloadSpeed == newItem.downloadSpeed &&
                    oldItem.contentLength == newItem.contentLength
                }
                else -> false
            }
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            if (oldItem is DownloadItem && newItem is DownloadItem) {
                val payload = DownloadChangePayload(
                    fileNameChanged = oldItem.fileName != newItem.fileName,
                    statusChanged = oldItem.status != newItem.status,
                    progressChanged = oldItem.downloadedBytes != newItem.downloadedBytes,
                    contentLengthChanged = oldItem.contentLength != newItem.contentLength,
                    speedChanged = oldItem.downloadSpeed != newItem.downloadSpeed
                )
                return if (payload.hasChanges()) payload else null
            }

            return super.getChangePayload(oldItemPosition, newItemPosition)
        }
    }
}

data class DownloadChangePayload(
    val fileNameChanged: Boolean = false,
    val statusChanged: Boolean = false,
    val progressChanged: Boolean = false,
    val contentLengthChanged: Boolean = false,
    val speedChanged: Boolean = false
) {
    fun hasChanges(): Boolean {
        return fileNameChanged || statusChanged || progressChanged || contentLengthChanged || speedChanged
    }
}
