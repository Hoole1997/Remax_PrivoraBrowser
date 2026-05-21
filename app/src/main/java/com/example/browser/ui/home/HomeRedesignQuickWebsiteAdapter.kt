package com.example.browser.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.browser.R
import com.example.browser.data.website.QuickWebsite
import com.example.browser.databinding.ItemHomeRedesignAddBinding
import com.example.browser.databinding.ItemHomeRedesignFeatureBinding
import com.example.browser.databinding.ItemHomeRedesignWebsiteBinding

class HomeRedesignQuickWebsiteAdapter(
    private val onWebsiteClick: (QuickWebsite) -> Unit,
    private val onWebsiteLongClick: (QuickWebsite) -> Unit,
    private val onAddClick: () -> Unit,
    private val onFeatureClick: (FeatureType) -> Unit,
) : ListAdapter<HomeRedesignQuickWebsiteAdapter.HomeGridItem, RecyclerView.ViewHolder>(DiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HomeGridItem.WebsiteItem -> VIEW_TYPE_WEBSITE
            HomeGridItem.AddItem -> VIEW_TYPE_ADD
            is HomeGridItem.FeatureItem -> VIEW_TYPE_FEATURE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_WEBSITE -> WebsiteViewHolder(ItemHomeRedesignWebsiteBinding.inflate(inflater, parent, false))
            VIEW_TYPE_FEATURE -> FeatureViewHolder(ItemHomeRedesignFeatureBinding.inflate(inflater, parent, false))
            else -> AddViewHolder(ItemHomeRedesignAddBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HomeGridItem.WebsiteItem -> (holder as WebsiteViewHolder).bind(item.website)
            HomeGridItem.AddItem -> (holder as AddViewHolder).bind()
            is HomeGridItem.FeatureItem -> (holder as FeatureViewHolder).bind(item)
        }
    }

    fun submitWebsites(websites: List<QuickWebsite>) {
        val items = buildList {
            // First row: 3 features + Add
            add(HomeGridItem.FeatureItem(FeatureType.CLEAN))
            add(HomeGridItem.FeatureItem(FeatureType.SIMILAR_PHOTOS))
            add(HomeGridItem.FeatureItem(FeatureType.SPEED_TEST))
            add(HomeGridItem.AddItem)
            // Remaining rows: user websites
            websites.take(MAX_WEBSITE_COUNT).forEach { add(HomeGridItem.WebsiteItem(it)) }
        }
        submitList(items)
    }

    private inner class WebsiteViewHolder(
        private val binding: ItemHomeRedesignWebsiteBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(website: QuickWebsite) {
            binding.tvTitle.text = website.title
            Glide.with(binding.ivIcon.context).clear(binding.ivIcon)

            val iconPath = "file:///android_asset/weblogo/${website.iconUrl}"
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

    private inner class FeatureViewHolder(
        private val binding: ItemHomeRedesignFeatureBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeGridItem.FeatureItem) {
            binding.ivFeatureIcon.setImageResource(item.featureType.iconRes)
            binding.tvFeatureTitle.setText(item.featureType.titleRes)
            itemView.setOnClickListener { onFeatureClick(item.featureType) }
        }
    }

    sealed interface HomeGridItem {
        val id: Long

        data class WebsiteItem(val website: QuickWebsite) : HomeGridItem {
            override val id: Long = website.id
        }

        object AddItem : HomeGridItem {
            override val id: Long = Long.MIN_VALUE
        }

        data class FeatureItem(val featureType: FeatureType) : HomeGridItem {
            override val id: Long = Long.MIN_VALUE + featureType.ordinal + 1
        }
    }

    enum class FeatureType(
        @DrawableRes val iconRes: Int,
        @StringRes val titleRes: Int,
    ) {
        CLEAN(R.mipmap.ic_home_clean, R.string.clean),
        SIMILAR_PHOTOS(R.mipmap.ic_home_duplicate, R.string.duplicate),
        SPEED_TEST(R.mipmap.ic_home_speed, R.string.speed),
    }

    private object DiffCallback : DiffUtil.ItemCallback<HomeGridItem>() {
        override fun areItemsTheSame(oldItem: HomeGridItem, newItem: HomeGridItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HomeGridItem, newItem: HomeGridItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_WEBSITE = 0
        private const val VIEW_TYPE_ADD = 1
        private const val VIEW_TYPE_FEATURE = 2
        private const val MAX_WEBSITE_COUNT = 8
    }
}
