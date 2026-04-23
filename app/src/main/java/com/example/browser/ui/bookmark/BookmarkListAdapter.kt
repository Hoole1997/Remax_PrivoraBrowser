package com.example.browser.ui.bookmark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.R
import com.example.browser.data.bookmark.BookmarkFolder
import com.example.browser.data.bookmark.BookmarkNode
import com.example.browser.data.bookmark.BookmarkSite
import com.example.browser.databinding.ItemBookmarkEntryBinding
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.icons.IconRequest

class BookmarkListAdapter(
    private val icons: BrowserIcons,
    private val onFolderClick: (BookmarkFolder) -> Unit,
    private val onSiteClick: (BookmarkSite) -> Unit,
    private val onMoreClick: (View, BookmarkNode) -> Unit
) : ListAdapter<BookmarkNode?, RecyclerView.ViewHolder>(BookmarkDiffCallback()) {

    override fun submitList(list: List<BookmarkNode?>?) {
        val displayList = if (list.isNullOrEmpty()) {
            listOf(null)
        } else {
            list
        }
        super.submitList(displayList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_FOLDER -> FolderViewHolder(ItemBookmarkEntryBinding.inflate(inflater, parent, false))
            VIEW_TYPE_SITE -> SiteViewHolder(ItemBookmarkEntryBinding.inflate(inflater, parent, false))
            else -> EmptyViewHolder(inflater.inflate(R.layout.layout_empty_normal, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when {
            holder is FolderViewHolder && item is BookmarkFolder -> holder.bind(item)
            holder is SiteViewHolder && item is BookmarkSite -> holder.bind(item)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            null -> VIEW_TYPE_EMPTY
            is BookmarkFolder -> VIEW_TYPE_FOLDER
            is BookmarkSite -> VIEW_TYPE_SITE
            else -> VIEW_TYPE_SITE
        }
    }

    inner class FolderViewHolder(
        private val binding: ItemBookmarkEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(folder: BookmarkFolder) {
            binding.ivIcon.setImageResource(R.drawable.ic_bookmark_folder)
            binding.tvTitle.text = folder.title
            binding.tvSubtitle.isVisible = false
            binding.ivMore.isVisible = true
            binding.contentContainer.setOnClickListener { onFolderClick(folder) }
            binding.ivMore.setOnClickListener { onMoreClick(it, folder) }
        }
    }

    inner class SiteViewHolder(
        private val binding: ItemBookmarkEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(site: BookmarkSite) {
            // Load favicon using BrowserIcons
            val iconRequest = IconRequest(url = site.url)
            icons.loadIntoView(binding.ivIcon, iconRequest)
            
            binding.tvTitle.text = site.title
            binding.tvSubtitle.apply {
                text = site.url
                isVisible = true
            }
            binding.ivMore.isVisible = true
            binding.contentContainer.setOnClickListener { onSiteClick(site) }
            binding.ivMore.setOnClickListener { onMoreClick(it, site) }
        }
    }

    class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class BookmarkDiffCallback : DiffUtil.ItemCallback<BookmarkNode?>() {

        override fun areItemsTheSame(
            oldItem: BookmarkNode,
            newItem: BookmarkNode
        ): Boolean {
            if (oldItem == null && newItem == null) return true
            if (oldItem == null || newItem == null) return false
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: BookmarkNode,
            newItem: BookmarkNode
        ): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_FOLDER = 0
        private const val VIEW_TYPE_SITE = 1
        private const val VIEW_TYPE_EMPTY = 2
    }
}
