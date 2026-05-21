package com.example.browser.ui.home

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.data.website.QuickWebsite
import com.example.browser.databinding.ItemHomeRedesignHeaderBinding
import com.example.browser.ui.tabs.GridSpacingItemDecoration

class HomeRedesignHeaderAdapter(
    private val onSearchClick: () -> Unit,
    private val onVoiceClick: () -> Unit,
    private val onScanClick: () -> Unit,
    private val onPdfClick: () -> Unit,
    private val onVideoClick: () -> Unit,
    private val onMoreClick: () -> Unit,
    private val onWebsiteClick: (QuickWebsite) -> Unit,
    private val onWebsiteLongClick: (QuickWebsite) -> Unit,
    private val onAddClick: () -> Unit,
    private val onFeatureClick: (HomeRedesignQuickWebsiteAdapter.FeatureType) -> Unit,
) : RecyclerView.Adapter<HomeRedesignHeaderAdapter.HeaderViewHolder>() {

    private var websites: List<QuickWebsite> = emptyList()
    private var searchEngineIcon: Bitmap? = null

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

    fun submitSearchEngineIcon(icon: Bitmap?) {
        searchEngineIcon = icon
        notifyItemChanged(0)
    }

    inner class HeaderViewHolder(
        private val binding: ItemHomeRedesignHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val quickWebsiteAdapter = HomeRedesignQuickWebsiteAdapter(
            onWebsiteClick = onWebsiteClick,
            onWebsiteLongClick = onWebsiteLongClick,
            onAddClick = onAddClick,
            onFeatureClick = onFeatureClick,
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

            binding.searchBarContainer.setOnClickListener { onSearchClick() }
            binding.voiceSearchIcon.setOnClickListener { onVoiceClick() }
            binding.qrCodeScanIcon.setOnClickListener { onScanClick() }
            binding.actionPdf.setOnClickListener { onPdfClick() }
            binding.actionVideo.setOnClickListener { onVideoClick() }
            binding.tvMore.setOnClickListener { onMoreClick() }
        }

        fun bind(websites: List<QuickWebsite>) {
            quickWebsiteAdapter.submitWebsites(websites)
            searchEngineIcon?.let { binding.searchIcon.setImageBitmap(it) }
        }
    }
}
