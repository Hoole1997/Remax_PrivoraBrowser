package com.example.browser.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.R
import com.example.browser.databinding.ItemHistoryEntryBinding
import com.example.browser.databinding.ItemHistoryHeaderBinding
import com.example.browser.ui.history.HistoryModel.HistoryListItem
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.icons.IconRequest
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryListAdapter(
    private val icons: BrowserIcons,
    private val onItemClick: (HistoryModel.HistoryEntry) -> Unit,
    private val onMoreClick: (View, HistoryModel.HistoryEntry) -> Unit
) : ListAdapter<HistoryListItem?, RecyclerView.ViewHolder>(HistoryDiffCallback()) {

    override fun submitList(list: List<HistoryListItem?>?) {
        val displayList = if (list.isNullOrEmpty()) {
            listOf(null)
        } else {
            list
        }
        super.submitList(displayList)
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            null -> VIEW_TYPE_EMPTY
            is HistoryListItem.Header -> VIEW_TYPE_HEADER
            is HistoryListItem.Entry -> VIEW_TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemHistoryHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            VIEW_TYPE_ENTRY -> {
                val binding = ItemHistoryEntryBinding.inflate(inflater, parent, false)
                EntryViewHolder(binding)
            }
            else -> EmptyViewHolder(inflater.inflate(R.layout.layout_empty_normal, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is HistoryListItem.Entry -> (holder as EntryViewHolder).bind(item.entry)
            null -> {

            }
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemHistoryHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: HistoryListItem.Header) {
            val context = binding.root.context
            val text = when (header.type) {
                HistoryModel.HeaderType.TODAY -> context.getString(R.string.history_header_today)
                HistoryModel.HeaderType.YESTERDAY -> context.getString(R.string.history_header_yesterday)
                HistoryModel.HeaderType.OTHER -> DateTimeFormatter.ofPattern(
                    "yyyy/MM/dd",
                    Locale.getDefault()
                ).format(header.date)
            }
            binding.root.text = text
        }
    }

    inner class EntryViewHolder(
        private val binding: ItemHistoryEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: HistoryModel.HistoryEntry) {
            binding.tvTitle.text = entry.title
            binding.tvSubtitle.text = entry.url
            binding.tvTime.text = entry.timeFormatted
            binding.contentContainer.setOnClickListener {
                onItemClick(entry)
            }
            
            // Load favicon using BrowserIcons
            val iconRequest = IconRequest(url = entry.url)
            icons.loadIntoView(binding.ivIcon, iconRequest)
            
            binding.ivMore.isVisible = true
            binding.ivMore.setOnClickListener {
                onMoreClick(it, entry)
            }
        }
    }

    class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryListItem?>() {

        override fun areItemsTheSame(
            oldItem: HistoryListItem,
            newItem: HistoryListItem
        ): Boolean {
            return when {
                oldItem == null && newItem == null -> true
                oldItem == null || newItem == null -> false
                oldItem is HistoryListItem.Header && newItem is HistoryListItem.Header ->
                    oldItem.date == newItem.date && oldItem.type == newItem.type
                oldItem is HistoryListItem.Entry && newItem is HistoryListItem.Entry ->
                    oldItem.entry.id == newItem.entry.id
                else -> false
            }
        }

        override fun areContentsTheSame(
            oldItem: HistoryListItem,
            newItem: HistoryListItem
        ): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1
        private const val VIEW_TYPE_EMPTY = 2
    }
}
