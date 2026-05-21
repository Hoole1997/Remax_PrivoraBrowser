package com.example.browser.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.browser.data.website.QuickWebsite
import com.example.browser.databinding.ItemHomeRedesignAddBinding
import com.example.browser.databinding.ItemHomeRedesignWebsiteBinding

class HomeRedesignQuickWebsiteAdapter(
    private val onWebsiteClick: (QuickWebsite) -> Unit,
    private val onWebsiteLongClick: (QuickWebsite) -> Unit,
    private val onAddClick: () -> Unit,
) : ListAdapter<HomeRedesignQuickWebsiteAdapter.HomeWebsiteItem, RecyclerView.ViewHolder>(DiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HomeWebsiteItem.WebsiteItem -> VIEW_TYPE_WEBSITE
            HomeWebsiteItem.AddItem -> VIEW_TYPE_ADD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_WEBSITE -> WebsiteViewHolder(ItemHomeRedesignWebsiteBinding.inflate(inflater, parent, false))
            else -> AddViewHolder(ItemHomeRedesignAddBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HomeWebsiteItem.WebsiteItem -> (holder as WebsiteViewHolder).bind(item.website)
            HomeWebsiteItem.AddItem -> (holder as AddViewHolder).bind()
        }
    }

    fun submitWebsites(websites: List<QuickWebsite>) {
        val items = buildList {
            websites.take(MAX_WEBSITE_COUNT).forEach { add(HomeWebsiteItem.WebsiteItem(it)) }
            add(HomeWebsiteItem.AddItem)
        }
        submitList(items)
    }

    private inner class WebsiteViewHolder(
        private val binding: ItemHomeRedesignWebsiteBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(website: QuickWebsite) {
            binding.tvTitle.text = website.title
            Glide.with(binding.ivIcon.context).clear(binding.ivIcon)

            val iconUrl = website.iconUrl
            if (iconUrl != null && iconUrl.startsWith("mipmap:")) {
                // Local mipmap resource for feature shortcuts
                val resName = iconUrl.removePrefix("mipmap:")
                val resId = binding.ivIcon.context.resources.getIdentifier(
                    resName, "mipmap", binding.ivIcon.context.packageName,
                )
                if (resId != 0) {
                    binding.ivIcon.setImageResource(resId)
                }
            } else {
                val iconPath = "file:///android_asset/weblogo/$iconUrl"
                Glide.with(binding.ivIcon.context)
                    .load(iconPath)
                    .transform(
                        MultiTransformation(
                            CenterCrop(),
                            RoundedCorners(binding.ivIcon.context.resources.displayMetrics.density.times(12).toInt()),
                        ),
                    )
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(binding.ivIcon)
            }

            itemView.setOnClickListener { onWebsiteClick(website) }
            itemView.setOnLongClickListener {
                onWebsiteLongClick(website)
                true
            }
        }
    }

    private inner class AddViewHolder(
        private val binding: ItemHomeRedesignAddBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.root.setOnClickListener { onAddClick() }
        }
    }

    sealed interface HomeWebsiteItem {
        val id: Long

        data class WebsiteItem(val website: QuickWebsite) : HomeWebsiteItem {
            override val id: Long = website.id
        }

        object AddItem : HomeWebsiteItem {
            override val id: Long = Long.MIN_VALUE
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<HomeWebsiteItem>() {
        override fun areItemsTheSame(oldItem: HomeWebsiteItem, newItem: HomeWebsiteItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HomeWebsiteItem, newItem: HomeWebsiteItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_WEBSITE = 0
        private const val VIEW_TYPE_ADD = 1
        private const val MAX_WEBSITE_COUNT = 7
    }
}
