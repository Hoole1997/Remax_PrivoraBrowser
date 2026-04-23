package com.example.browser.ui.web

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.example.browser.R
import com.example.browser.data.VideoInfo
import com.example.browser.data.VideoInfoParcelable
import com.example.browser.data.VideoItem
import com.example.browser.data.toParcelable
import com.example.browser.databinding.BottomSheetVideoListBinding
import com.example.browser.databinding.ItemVideoListBinding
import com.example.browser.feature.VideoDetectorFeature
import com.example.player.VideoPlayerActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.corekit.core.report.ReportDataManager
import java.net.HttpURLConnection
import java.net.URL

/**
 * 视频列表底部弹框
 */
class VideoListBottomSheet : BottomSheetDialogFragment() {
    
    companion object {
        private const val ARG_VIDEOS = "arg_videos"
        
        fun newInstance(videos: ArrayList<VideoInfo>): VideoListBottomSheet {
            return VideoListBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_VIDEOS, ArrayList(videos.map { it.toParcelable() }))
                }
            }
        }
    }
    
    private var _binding: BottomSheetVideoListBinding? = null
    private val binding get() = _binding!!
    
    private var adapter: VideoListAdapter? = null
    private val videoItems = mutableListOf<VideoItem>()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // 下载回调
    private var onDownloadClickListener: ((List<VideoInfo>) -> Unit)? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetVideoListBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 设置 BottomSheet 展开高度
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            peekHeight = resources.displayMetrics.heightPixels * 2 / 3
        }
        
        val parcelables = arguments?.getParcelableArrayList<VideoInfoParcelable>(ARG_VIDEOS)
        val videos = parcelables?.map { it.toVideoInfo() } ?: emptyList()
        
        // 初始化视频项，默认全选
        videoItems.clear()
        videoItems.addAll(videos.map { VideoItem(it, isSelected = true, isLoadingSize = it.size == null) })
        
        setupViews()
        updateSelectAllText()
        updateDownloadButton()
        
        // 异步获取文件大小
        fetchMissingSizes()
        activity?.loadNative(binding.adContainer)

        ReportDataManager.reportData("dialog_show", mapOf("dialog_name" to javaClass.simpleName))
    }

    override fun dismiss() {
        super.dismiss()
        ReportDataManager.reportData("dialog_dismiss", mapOf("dialog_name" to javaClass.simpleName))
    }
    private fun setupViews() {
        adapter = VideoListAdapter(
            items = videoItems,
            onPlayClick = { videoItem ->
                // 跳转到播放器
                VideoPlayerActivity.start(
                    requireContext(),
                    videoItem.videoInfo.url,
                    extractFileName(videoItem.videoInfo.url)
                )
            },
            onItemClick = { position ->
                // 切换选中状态
                videoItems[position] = videoItems[position].copy(isSelected = !videoItems[position].isSelected)
                adapter?.notifyItemChanged(position)
                updateSelectAllText()
                updateDownloadButton()
            }
        )
        
        binding.rvVideos.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVideos.adapter = adapter
        
        // 全选/取消全选
        binding.tvSelectAll.setOnClickListener {
            val allSelected = videoItems.all { it.isSelected }
            val newSelectedState = !allSelected
            videoItems.forEachIndexed { index, item ->
                videoItems[index] = item.copy(isSelected = newSelectedState)
            }
            adapter?.notifyDataSetChanged()
            updateSelectAllText()
            updateDownloadButton()
        }
        
        // 下载按钮
        binding.btnDownload.setOnClickListener {
            val selectedVideos = videoItems.filter { it.isSelected }.map { it.videoInfo }
            if (selectedVideos.isNotEmpty()) {
                activity?.loadInterstitial {
                    onDownloadClickListener?.invoke(selectedVideos)
                    dismiss()
                }
            }
        }
    }

    private fun updateSelectAllText() {
        val allSelected = videoItems.all { it.isSelected }
        binding.tvSelectAll.text = if (allSelected) {
            getString(R.string.deselect_all)
        } else {
            getString(R.string.select_all)
        }
    }
    
    private fun updateDownloadButton() {
        val selectedCount = videoItems.count { it.isSelected }
        binding.btnDownload.text = getString(R.string.download_count, selectedCount)
        binding.btnDownload.isEnabled = selectedCount > 0
    }
    
    /**
     * 异步获取缺失的文件大小
     */
    private fun fetchMissingSizes() {
        videoItems.forEachIndexed { index, item ->
            if (item.videoInfo.size == null && item.isLoadingSize) {
                scope.launch {
                    val size = fetchFileSize(item.videoInfo.url)
                    if (size != null && size > 0) {
                        // 更新数据
                        val updatedVideoInfo = item.videoInfo.copy(size = size)
                        videoItems[index] = item.copy(
                            videoInfo = updatedVideoInfo,
                            isLoadingSize = false
                        )
                        // 局部更新 UI
                        adapter?.notifyItemChanged(index)
                    } else {
                        videoItems[index] = item.copy(isLoadingSize = false)
                        adapter?.notifyItemChanged(index)
                    }
                }
            }
        }
    }
    
    /**
     * 通过 HTTP HEAD 请求获取文件大小
     */
    private suspend fun fetchFileSize(url: String): Long? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            
            val contentLength = connection.contentLengthLong
            connection.disconnect()
            
            if (contentLength > 0) contentLength else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun setOnDownloadClickListener(listener: (List<VideoInfo>) -> Unit) {
        onDownloadClickListener = listener
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun extractFileName(url: String): String {
        return try {
            val path = URL(url).path
            val fileName = path.substringAfterLast("/")
            if (fileName.isNotEmpty()) fileName else url
        } catch (e: Exception) {
            url.takeLast(50)
        }
    }
    

    
    /**
     * 视频列表适配器
     */
    private class VideoListAdapter(
        private val items: List<VideoItem>,
        private val onPlayClick: (VideoItem) -> Unit,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemVideoListBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }
        
        override fun getItemCount(): Int = items.size
        
        inner class ViewHolder(
            private val binding: ItemVideoListBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            init {
                binding.root.setOnClickListener {
                    onItemClick(adapterPosition)
                }
                binding.ivPlay.setOnClickListener {
                    onPlayClick(items[adapterPosition])
                }
            }
            
            fun bind(item: VideoItem) {
                val videoInfo = item.videoInfo
                val context = binding.root.context
                
                // 使用 Glide 加载视频封面
                val radius = (8 * context.resources.displayMetrics.density).toInt()
                Glide.with(context)
                    .load(videoInfo.url)
                    .apply(
                        RequestOptions()
                            .transform(CenterCrop(), RoundedCorners(radius))
                            .placeholder(R.drawable.bg_video_thumbnail)
                            .error(R.drawable.bg_video_thumbnail)
                    )
                    .into(binding.ivThumbnail)
                
                // 显示文件名
                val fileName = extractFileName(videoInfo.url)
                binding.tvVideoName.text = fileName
                
                // 显示大小信息
                binding.tvVideoInfo.text = when {
                    item.isLoadingSize -> context.getString(R.string.fetching_size)
                    videoInfo.size != null -> formatFileSize(videoInfo.size)
                    else -> videoInfo.suffix?.uppercase() ?: ""
                }
                
                // 更新选中状态
                val checkIcon = if (item.isSelected) {
                    R.drawable.ic_check_circle
                } else {
                    R.drawable.ic_check_circle_unchecked
                }
                binding.ivCheck.setImageResource(checkIcon)
            }
            
            private fun extractFileName(url: String): String {
                return try {
                    val path = URL(url).path
                    val fileName = path.substringAfterLast("/")
                    // 去掉查询参数
                    val cleanName = fileName.substringBefore("?")
                    if (cleanName.isNotEmpty()) cleanName else url.takeLast(50)
                } catch (e: Exception) {
                    url.takeLast(50)
                }
            }
            
            private fun formatFileSize(bytes: Long): String {
                return when {
                    bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                    bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
                    else -> "$bytes B"
                }
            }
        }
    }
}

