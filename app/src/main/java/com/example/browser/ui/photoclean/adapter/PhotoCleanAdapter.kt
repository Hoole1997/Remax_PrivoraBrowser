package com.example.browser.ui.photoclean.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.browser.R
import com.example.browser.ui.photoclean.model.CleanablePhoto
import com.example.browser.ui.photoclean.model.PhotoCleanGroup
import com.example.browser.ui.photoclean.model.PhotoCleanListItem

class PhotoCleanAdapter(
    private val onPhotoClick: (groupId: String, photo: CleanablePhoto) -> Unit,
    private val onExpandClick: (groupId: String) -> Unit
) : ListAdapter<PhotoCleanListItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        const val SPAN_COUNT = 3
        private const val GRID_SPACING_DP = 10
        private const val COLLAPSED_MAX = 3

        const val PAYLOAD_SELECTION = "payload_selection"
        const val PAYLOAD_EXPAND = "payload_expand"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PhotoCleanListItem>() {
            override fun areItemsTheSame(
                oldItem: PhotoCleanListItem,
                newItem: PhotoCleanListItem
            ): Boolean {
                if (oldItem is PhotoCleanListItem.GroupCard && newItem is PhotoCleanListItem.GroupCard) {
                    return oldItem.group.groupId == newItem.group.groupId
                }
                return false
            }

            override fun areContentsTheSame(
                oldItem: PhotoCleanListItem,
                newItem: PhotoCleanListItem
            ): Boolean {
                return oldItem == newItem
            }

            override fun getChangePayload(
                oldItem: PhotoCleanListItem,
                newItem: PhotoCleanListItem
            ): Any? {
                if (oldItem is PhotoCleanListItem.GroupCard && newItem is PhotoCleanListItem.GroupCard) {
                    val oldGroup = oldItem.group
                    val newGroup = newItem.group
                    if (oldGroup.photos.size != newGroup.photos.size) {
                        return null
                    }
                    if (oldGroup.isExpanded != newGroup.isExpanded) {
                        return PAYLOAD_EXPAND
                    }
                    if (oldGroup.photos.map { it.isChecked } != newGroup.photos.map { it.isChecked }) {
                        return PAYLOAD_SELECTION
                    }
                }
                return null
            }
        }
    }

    private var parentWidth = 0

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.post {
            parentWidth = recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight
        }
    }

    override fun getItemViewType(position: Int): Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (parentWidth == 0) {
            parentWidth = parent.width - parent.paddingLeft - parent.paddingRight
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_group_card, parent, false)
        return GroupCardViewHolder(view, onPhotoClick, onExpandClick)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position) as PhotoCleanListItem.GroupCard
        (holder as GroupCardViewHolder).fullBind(item, parentWidth)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        val item = getItem(position) as PhotoCleanListItem.GroupCard
        val vh = holder as GroupCardViewHolder
        for (payload in payloads) {
            when (payload) {
                PAYLOAD_SELECTION -> vh.updateSelections(item.group)
                PAYLOAD_EXPAND -> vh.updateExpand(item, parentWidth)
            }
        }
    }

    class GroupCardViewHolder(
        itemView: View,
        private val onPhotoClick: (groupId: String, photo: CleanablePhoto) -> Unit,
        private val onExpandClick: (groupId: String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_group_title)
        private val ivExpand: ImageView = itemView.findViewById(R.id.iv_expand)
        private val glPhotos: GridLayout = itemView.findViewById(R.id.gl_photos)

        private val density = itemView.context.resources.displayMetrics.density
        private val spacingPx = (GRID_SPACING_DP * density).toInt()
        private val cornerPx = (8 * density).toInt()
        private val cardPaddingHorizontal = (12 * density).toInt() * 2

        private fun calcCellSize(parentWidth: Int): Int {
            val gridWidth = parentWidth - cardPaddingHorizontal
            return (gridWidth - spacingPx * (SPAN_COUNT - 1)) / SPAN_COUNT
        }

        private fun getVisiblePhotos(group: PhotoCleanGroup): List<CleanablePhoto> {
            return if (group.isExpanded) group.photos else group.photos.take(COLLAPSED_MAX)
        }

        fun fullBind(item: PhotoCleanListItem.GroupCard, parentWidth: Int) {
            val group = item.group
            val context = itemView.context

            tvTitle.text = context.getString(R.string.photo_clean_group_title, group.photoCount)
            ivExpand.rotation = if (group.isExpanded) 180f else 0f
            ivExpand.setOnClickListener { onExpandClick(group.groupId) }
            tvTitle.setOnClickListener { onExpandClick(group.groupId) }

            val visiblePhotos = getVisiblePhotos(group)
            val cellSize = calcCellSize(parentWidth)

            glPhotos.removeAllViews()
            glPhotos.columnCount = SPAN_COUNT

            if (cellSize <= 0) return

            visiblePhotos.forEachIndexed { index, photo ->
                addPhotoCell(group.groupId, photo, index, cellSize)
            }
        }

        fun updateSelections(group: PhotoCleanGroup) {
            val visiblePhotos = getVisiblePhotos(group)
            val childCount = glPhotos.childCount
            for (i in 0 until minOf(childCount, visiblePhotos.size)) {
                val cell = glPhotos.getChildAt(i)
                val photo = visiblePhotos[i]
                val vCheckCircle: View = cell.findViewById(R.id.v_check_circle)
                vCheckCircle.setBackgroundResource(
                    if (photo.isChecked) R.drawable.ic_photo_checked
                    else R.drawable.bg_photo_check_circle
                )
                cell.setOnClickListener {
                    onPhotoClick(group.groupId, photo)
                }
            }
        }

        fun updateExpand(item: PhotoCleanListItem.GroupCard, parentWidth: Int) {
            val group = item.group
            val visiblePhotos = getVisiblePhotos(group)
            val cellSize = calcCellSize(parentWidth)

            ivExpand.rotation = if (group.isExpanded) 180f else 0f

            val currentCount = glPhotos.childCount
            val targetCount = visiblePhotos.size

            if (cellSize <= 0) return

            if (targetCount > currentCount) {
                // 展开：添加更多cell
                for (i in currentCount until targetCount) {
                    addPhotoCell(group.groupId, visiblePhotos[i], i, cellSize)
                }
            } else if (targetCount < currentCount) {
                // 收缩：移除多余cell
                glPhotos.removeViews(targetCount, currentCount - targetCount)
            }

            // 更新选中状态
            updateSelections(group)
        }

        private fun addPhotoCell(
            groupId: String,
            photo: CleanablePhoto,
            index: Int,
            cellSize: Int
        ) {
            val context = itemView.context
            val row = index / SPAN_COUNT
            val col = index % SPAN_COUNT

            val cell = LayoutInflater.from(context)
                .inflate(R.layout.item_photo_thumbnail, glPhotos, false)

            val lp = GridLayout.LayoutParams().apply {
                width = cellSize
                height = cellSize
                columnSpec = GridLayout.spec(col)
                rowSpec = GridLayout.spec(row)
                setMargins(
                    if (col > 0) spacingPx else 0,
                    if (row > 0) spacingPx else 0,
                    0,
                    0
                )
            }

            val ivPhoto: ImageView = cell.findViewById(R.id.iv_photo)
            val vCheckCircle: View = cell.findViewById(R.id.v_check_circle)
            val tvFileSize: TextView = cell.findViewById(R.id.tv_file_size)

            ivPhoto.layoutParams.height = cellSize

            Glide.with(context)
                .load(photo.file)
                .centerCrop()
                .transform(
                    com.bumptech.glide.load.MultiTransformation(
                        com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                        RoundedCorners(cornerPx)
                    )
                )
                .placeholder(R.color.storage_progress_bg)
                .error(R.color.storage_progress_bg)
                .into(ivPhoto)

            vCheckCircle.setBackgroundResource(
                if (photo.isChecked) R.drawable.ic_photo_checked
                else R.drawable.bg_photo_check_circle
            )

            tvFileSize.text = photo.friendlySize

            cell.setOnClickListener {
                onPhotoClick(groupId, photo)
            }

            glPhotos.addView(cell, lp)
        }
    }
}
