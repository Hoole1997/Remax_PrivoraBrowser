package com.example.browser.ui.file.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.browser.R
import com.example.browser.databinding.ItemPermissionRequestBinding
import com.example.browser.databinding.ItemRecentFileBinding
import com.example.browser.databinding.LayoutEmptyNormalBinding
import com.example.browser.ui.file.model.FileType
import com.example.browser.ui.file.model.RecentFile
import com.example.browser.ui.file.widget.FileOptionsPopupWindow

/**
 * 最近文件列表适配器
 * 优化：使用 DiffUtil 提高列表更新效率
 */
class RecentFilesAdapter(
    private val onItemClick: (RecentFile) -> Unit,
    private val onFileDeleted: ((RecentFile) -> Unit)? = null,
    private val onPermissionRequest: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_EMPTY = 0
        private const val VIEW_TYPE_FILE = 1
        private const val VIEW_TYPE_PERMISSION = 2
    }

    private val items = mutableListOf<RecentFile>()
    private var showPermissionRequest = false

    /**
     * 删除指定的文件项
     */
    fun removeItem(file: RecentFile) {
        val position = items.indexOfFirst { it.file.absolutePath == file.file.absolutePath }
        if (position != -1) {
            items.removeAt(position)
            
            // 如果删除后列表为空，需要显示空布局
            if (items.isEmpty()) {
                notifyDataSetChanged()
            } else {
                notifyItemRemoved(position)
            }
        }
    }

    /**
     * 设置是否显示权限请求项
     */
    fun setShowPermissionRequest(show: Boolean) {
        if (showPermissionRequest != show) {
            showPermissionRequest = show
            notifyDataSetChanged()
        }
    }

    fun submitList(newItems: List<RecentFile>) {
        val oldEmpty = items.isEmpty()
        val newEmpty = newItems.isEmpty()
        
        items.clear()
        items.addAll(newItems)
        
        // 如果空状态发生变化，直接刷新整个列表
        if (oldEmpty != newEmpty) {
            notifyDataSetChanged()
        } else if (newItems.isNotEmpty()) {
            // 如果都有数据，使用 DiffUtil 优化更新
            val diffCallback = FilesDiffCallback(items, newItems)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            diffResult.dispatchUpdatesTo(this)
        }
    }

    /**
     * DiffUtil 回调，用于比较文件列表的变化
     */
    private class FilesDiffCallback(
        private val oldList: List<RecentFile>,
        private val newList: List<RecentFile>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            // 比较文件路径是否相同
            return oldList[oldItemPosition].file.absolutePath ==
                   newList[newItemPosition].file.absolutePath
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            // 比较文件内容是否相同（名称、大小、修改时间）
            return oldItem.name == newItem.name &&
                   oldItem.size == newItem.size &&
                   oldItem.lastModified == newItem.lastModified
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            showPermissionRequest && position == 0 -> VIEW_TYPE_PERMISSION
            items.isEmpty() && !showPermissionRequest -> VIEW_TYPE_EMPTY
            else -> VIEW_TYPE_FILE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_EMPTY -> {
                val binding = LayoutEmptyNormalBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                EmptyViewHolder(binding)
            }
            VIEW_TYPE_PERMISSION -> {
                val binding = ItemPermissionRequestBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                PermissionViewHolder(binding)
            }
            else -> {
                val binding = ItemRecentFileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                FileViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is FileViewHolder -> {
                val filePosition = if (showPermissionRequest) position - 1 else position
                if (filePosition >= 0 && filePosition < items.size) {
                    holder.bind(items[filePosition])
                }
            }
            is PermissionViewHolder -> {
                holder.bind()
            }
        }
    }

    override fun getItemCount(): Int {
        val permissionCount = if (showPermissionRequest) 1 else 0
        val fileCount = if (items.isEmpty() && !showPermissionRequest) 1 else items.size
        return permissionCount + fileCount
    }

    /**
     * 空布局 ViewHolder
     */
    inner class EmptyViewHolder(binding: LayoutEmptyNormalBinding) :
        RecyclerView.ViewHolder(binding.root)

    /**
     * 权限请求项 ViewHolder
     */
    inner class PermissionViewHolder(private val binding: ItemPermissionRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.btnAllow.setOnClickListener {
                onPermissionRequest?.invoke()
            }
            binding.root.setOnClickListener {
                onPermissionRequest?.invoke()
            }
        }
    }

    /**
     * 文件项 ViewHolder
     */
    inner class FileViewHolder(private val binding: ItemRecentFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecentFile) {
            binding.tvFileName.text = item.name
            binding.tvFileSize.text = item.getFormattedSize()

            // 显示或隐藏视频时长
            if (item.fileType == FileType.VIDEO && item.videoDuration != null) {
                binding.tvVideoDuration.text = item.getFormattedDuration()
                binding.tvVideoDuration.visibility = android.view.View.VISIBLE
            } else {
                binding.tvVideoDuration.visibility = android.view.View.GONE
            }

            // 根据文件类型加载不同的图标
            when (item.fileType) {
                FileType.IMAGE -> {
                    // 图片：加载图片本身
                    loadImage(item)
                }
                FileType.VIDEO -> {
                    // 视频：加载视频第一帧
                    loadVideoThumbnail(item)
                }
                FileType.AUDIO -> {
                    // 音频：显示音频图标
                    loadFileTypeIcon(R.mipmap.ic_file_audio)
                }
                FileType.DOCUMENT -> {
                    // 文档：显示文档图标
                    loadFileTypeIcon(R.mipmap.ic_file_document)
                }
                FileType.APK -> {
                    // APK：显示APK图标
                    loadFileTypeIcon(R.mipmap.ic_file_apk)
                }
                FileType.ZIP -> {
                    // ZIP：显示ZIP图标
                    loadFileTypeIcon(R.mipmap.ic_file_zip)
                }
                else -> {
                    // 其他：显示通用文件图标
                    loadFileTypeIcon(R.mipmap.ic_file_unknown)
                }
            }

            // 点击事件
            binding.root.setOnClickListener {
                onItemClick(item)
            }

            // 更多按钮点击事件 - 显示 PopupWindow
            binding.ivMore.setOnClickListener { view ->
                showFileOptionsPopup(view, item)
            }
        }

        /**
         * 显示文件操作 PopupWindow
         */
        private fun showFileOptionsPopup(anchorView: android.view.View, item: RecentFile) {
            val popup = FileOptionsPopupWindow(
                context = anchorView.context,
                file = item,
                onDelete = {
                    // 删除成功后，通知外部并传递被删除的文件对象
                    onFileDeleted?.invoke(item)
                }
            )
            popup.show(anchorView)
        }

        /**
         * 加载图片文件
         */
        private fun loadImage(item: RecentFile) {
            if (item.file.exists()) {
                Glide.with(binding.root.context)
                    .load(item.file)
                    .apply(RequestOptions()
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL))
                    .error(R.drawable.ic_file_image)
                    .into(binding.ivThumbnail)
            } else {
                loadFileTypeIcon(R.mipmap.ic_file_image)
            }
        }

        /**
         * 加载视频第一帧
         * 优化：使用 Glide 直接加载视频文件，Glide 会自动在后台线程提取第一帧
         */
        private fun loadVideoThumbnail(item: RecentFile) {
            if (item.file.exists()) {
                // 先设置占位符背景，避免闪烁
                binding.ivThumbnail.setBackgroundResource(R.drawable.file_icon_bg_orange)

                // 使用 Glide 直接加载视频文件，它会自动提取第一帧
                // Glide 会在后台线程执行，不会阻塞主线程
                Glide.with(binding.root.context)
                    .load(item.file)
                    .apply(RequestOptions()
                        .centerCrop()
                        .placeholder(R.drawable.ic_file_video)  // 加载时显示占位图标
                        .error(R.drawable.ic_file_video)  // 加载失败显示错误图标
                        .diskCacheStrategy(DiskCacheStrategy.ALL))  // 缓存视频缩略图
                    .into(binding.ivThumbnail)
            } else {
                loadFileTypeIcon(R.mipmap.ic_file_video)
            }
        }

        /**
         * 加载文件类型图标
         */
        private fun loadFileTypeIcon(iconRes: Int) {
            binding.ivThumbnail.setImageResource(iconRes)
            binding.ivThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER
        }
    }
}
