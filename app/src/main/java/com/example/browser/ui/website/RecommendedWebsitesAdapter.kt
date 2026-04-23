package com.example.browser.ui.website

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.browser.R
import com.example.browser.data.website.RecommendedWebsite

class RecommendedWebsitesAdapter(
    private val onAddClick: (RecommendedWebsite, Boolean) -> Unit
) : ListAdapter<RecommendedListItem, RecyclerView.ViewHolder>(DiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is RecommendedListItem.SectionHeader -> VIEW_TYPE_HEADER
            is RecommendedListItem.WebsiteItem -> VIEW_TYPE_WEBSITE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_recommended_section_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_recommended_website, parent, false)
                WebsiteViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RecommendedListItem.SectionHeader -> (holder as HeaderViewHolder).bind(item)
            is RecommendedListItem.WebsiteItem -> (holder as WebsiteViewHolder).bind(item)
        }
    }

    fun findFirstPositionOfCategory(key: String): Int? {
        return currentList.indexOfFirst {
            it is RecommendedListItem.SectionHeader && it.category.key == key
        }.takeIf { it >= 0 }
    }

    private inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val iconView: ImageView = view.findViewById(R.id.ivIcon)
        private val titleView: TextView = view.findViewById(R.id.tvTitle)

        fun bind(item: RecommendedListItem.SectionHeader) {
            iconView.setBackgroundResource(item.category.iconResId)
            titleView.setText(item.category.titleResId)
        }
    }

    private inner class WebsiteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: View = view
        private val iconView: ImageView = view.findViewById(R.id.ivIcon)
        private val nameView: TextView = view.findViewById(R.id.tvName)
        private val actionView: ImageView = view.findViewById(R.id.ivAction)

        fun bind(item: RecommendedListItem.WebsiteItem) {
            val website = item.website
            nameView.text = website.name

            Glide.with(iconView.context)
                .clear(iconView)

            val assetPath = website.iconAsset?.let { "file:///android_asset/weblogo/$it" }
            if (assetPath != null) {
                Glide.with(iconView.context)
                    .load(assetPath)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerInside()
                    .error(R.drawable.ic_bookmark_site)
                    .into(iconView)
            } else {
                iconView.setImageResource(R.drawable.ic_bookmark_site)
            }

            val isAdded = item.isAdded
            actionView.setImageResource(if (isAdded) R.drawable.ic_recommended_check else R.drawable.ic_recommended_plus)
            actionView.contentDescription = if (isAdded) {
                iconView.context.getString(R.string.recommended_item_added)
            } else {
                iconView.context.getString(R.string.recommended_item_add)
            }
            container.alpha = if (isAdded) 0.65f else 1f

            val listener = View.OnClickListener {
                onAddClick(website, isAdded)
            }
            actionView.setOnClickListener(listener)
            container.setOnClickListener(listener)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RecommendedListItem>() {
        override fun areItemsTheSame(oldItem: RecommendedListItem, newItem: RecommendedListItem): Boolean {
            return oldItem.stableId == newItem.stableId
        }

        override fun areContentsTheSame(oldItem: RecommendedListItem, newItem: RecommendedListItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_WEBSITE = 1
    }
}
