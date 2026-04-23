package com.example.browser.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.data.website.QuickWebsite
import com.example.browser.databinding.ItemHomeRedesignHeaderBinding
import com.example.browser.ui.tabs.GridSpacingItemDecoration

class HomeRedesignHeaderAdapter(
    private val onCleanClick: () -> Unit,
    private val onNewsClick: () -> Unit,
    private val onSpeedClick: () -> Unit,
    private val onProcessClick: () -> Unit,
    private val onBookmarkClick: () -> Unit,
    private val onDuplicateClick: () -> Unit,
    private val onEditClick: () -> Unit,
    private val onMoreClick: () -> Unit,
    private val onWebsiteClick: (QuickWebsite) -> Unit,
    private val onWebsiteLongClick: (QuickWebsite) -> Unit,
    private val onAddClick: () -> Unit,
) : RecyclerView.Adapter<HomeRedesignHeaderAdapter.HeaderViewHolder>() {

    private var websites: List<QuickWebsite> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return HeaderViewHolder(ItemHomeRedesignHeaderBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind(websites)
    }

    override fun getItemCount(): Int = 1

    fun submitWebsites(newWebsites: List<QuickWebsite>) {
        websites = newWebsites
        notifyItemChanged(0)
    }

    inner class HeaderViewHolder(
        private val binding: ItemHomeRedesignHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val quickWebsiteAdapter = HomeRedesignQuickWebsiteAdapter(
            onWebsiteClick = onWebsiteClick,
            onWebsiteLongClick = onWebsiteLongClick,
            onAddClick = onAddClick,
        )

        init {
            binding.rvQuickAccess.apply {
                layoutManager = GridLayoutManager(context, 4)
                isNestedScrollingEnabled = false
                adapter = quickWebsiteAdapter
                if (itemDecorationCount == 0) {
                    addItemDecoration(GridSpacingItemDecoration(4, 36, false))
                }
            }

            binding.actionClean.setOnClickListener { onCleanClick() }
            binding.actionNews.setOnClickListener { onNewsClick() }
            binding.actionSpeed.setOnClickListener { onSpeedClick() }
            binding.actionProcess.setOnClickListener { onProcessClick() }
            binding.actionBookmark.setOnClickListener { onBookmarkClick() }
            binding.actionDuplicate.setOnClickListener { onDuplicateClick() }
            binding.tvEdit.setOnClickListener { onEditClick() }
            binding.tvMore.setOnClickListener { onMoreClick() }
        }

        fun bind(websites: List<QuickWebsite>) {
            quickWebsiteAdapter.submitWebsites(websites)
        }
    }
}
