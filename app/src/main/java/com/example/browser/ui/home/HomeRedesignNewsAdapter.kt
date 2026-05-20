package com.example.browser.ui.home

import android.text.Html
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.common.bill.ads.ext.AdShowExt
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.browser.R
import com.example.browser.databinding.ItemHomeRedesignNewsBinding
import com.example.browser.databinding.ItemNewsLoadingBinding
import com.example.browser.databinding.ItemNewsNativeAdBinding
import com.example.browser.databinding.ItemNewsNetworkErrorBinding
import com.example.browser.databinding.LayoutEmptyNormalBinding
import com.example.browser.ui.news.NewsFeedItem
import com.example.browser.ui.news.NewsItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class HomeRedesignNewsAdapter(
    private val onNewsClick: (NewsItem) -> Unit,
    private val onRetryClick: (() -> Unit)? = null,
) : ListAdapter<NewsFeedItem, RecyclerView.ViewHolder>(DiffCallback()) {

    private var showLoadingFooter = false
    private var showEmptyView = false
    private var showNetworkError = false

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is NewsFeedItem.News -> item.newsItem.url?.hashCode()?.toLong() ?: position.toLong()
            is NewsFeedItem.NativeAd -> Long.MIN_VALUE + item.id
        }
    }

    override fun getItemViewType(position: Int): Int {
        val dataCount = super.getItemCount()
        return when {
            dataCount == 0 && showNetworkError -> VIEW_TYPE_NETWORK_ERROR
            dataCount == 0 && showEmptyView -> VIEW_TYPE_EMPTY
            position < dataCount -> {
                when (getItem(position)) {
                    is NewsFeedItem.News -> VIEW_TYPE_NEWS
                    is NewsFeedItem.NativeAd -> VIEW_TYPE_NATIVE_AD
                }
            }
            else -> VIEW_TYPE_LOADING
        }
    }

    override fun getItemCount(): Int {
        val dataCount = super.getItemCount()
        return when {
            dataCount == 0 && showNetworkError -> 1
            dataCount == 0 && showEmptyView -> 1
            else -> dataCount + if (showLoadingFooter && dataCount > 0) 1 else 0
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_NEWS -> NewsViewHolder(ItemHomeRedesignNewsBinding.inflate(inflater, parent, false), onNewsClick)
            VIEW_TYPE_NATIVE_AD -> NativeAdViewHolder(ItemNewsNativeAdBinding.inflate(inflater, parent, false))
            VIEW_TYPE_LOADING -> LoadingViewHolder(ItemNewsLoadingBinding.inflate(inflater, parent, false))
            VIEW_TYPE_NETWORK_ERROR -> NetworkErrorViewHolder(
                ItemNewsNetworkErrorBinding.inflate(inflater, parent, false),
                onRetryClick,
            )

            else -> EmptyViewHolder(LayoutEmptyNormalBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is NewsViewHolder -> {
                val item = getItem(position) as? NewsFeedItem.News ?: return
                holder.bind(item.newsItem)
            }

            is NativeAdViewHolder -> {
                val item = getItem(position) as? NewsFeedItem.NativeAd ?: return
                holder.bind(item.id)
            }
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is NewsViewHolder) {
            holder.loadImage()
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is NewsViewHolder) {
            holder.clearImage()
        }
    }

    fun setLoadingFooterVisible(show: Boolean) {
        if (showLoadingFooter == show) return
        val dataCount = super.getItemCount()
        showLoadingFooter = show
        val footerPosition = dataCount
        if (show && dataCount > 0) {
            notifyItemInserted(footerPosition)
        } else if (!show) {
            notifyItemRemoved(footerPosition)
        }
    }

    fun setEmptyViewVisible(show: Boolean) {
        if (showEmptyView == show) return
        showEmptyView = show
        notifyDataSetChanged()
    }

    fun setNetworkErrorVisible(show: Boolean) {
        if (showNetworkError == show) return
        showNetworkError = show
        notifyDataSetChanged()
    }

    fun loadAdAtPosition(recyclerView: RecyclerView, position: Int) {
        val holder = recyclerView.findViewHolderForAdapterPosition(position)
        if (holder is NativeAdViewHolder) {
            holder.loadAd()
        }
    }

    class NewsViewHolder(
        private val binding: ItemHomeRedesignNewsBinding,
        private val onNewsClick: (NewsItem) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentImageUrl: String? = null

        fun bind(newsItem: NewsItem) {
            binding.tvNewsTitle.text = Html.fromHtml(newsItem.title ?: "No title", Html.FROM_HTML_MODE_COMPACT)
                .toString()
                .trim()
            binding.tvAuthor.text = newsItem.author ?: newsItem.source ?: "Unknown"
            binding.tvNewsTime.text = formatDate(newsItem.publishedAt)

            currentImageUrl = newsItem.image?.replace("\\/", "/")
            binding.cvNews.isVisible = !currentImageUrl.isNullOrEmpty()
            binding.ivNewsImage.setImageResource(R.mipmap.bg_news_default)

            binding.root.setOnClickListener {
                onNewsClick(newsItem)
            }
        }

        fun loadImage() {
            val imageUrl = currentImageUrl ?: return
            Glide.with(binding.ivNewsImage.context)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.mipmap.bg_news_default)
                .error(R.mipmap.bg_news_default)
                .thumbnail(0.15f)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(binding.ivNewsImage)
        }

        fun clearImage() {
            Glide.with(binding.ivNewsImage.context).clear(binding.ivNewsImage)
        }

        private fun formatDate(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return ""
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
                val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                date?.let { outputFormat.format(it) } ?: dateString
            } catch (_: Exception) {
                dateString
            }
        }
    }

    private class NativeAdViewHolder(
        private val binding: ItemNewsNativeAdBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var boundAdId: Int? = null
        private var isAdLoaded = false

        fun bind(adId: Int) {
            if (boundAdId != adId) {
                boundAdId = adId
                isAdLoaded = false
                binding.adContainer.removeAllViews()
            }
        }

        fun loadAd() {
            if (isAdLoaded) return
            isAdLoaded = true
            CoroutineScope(Dispatchers.Main).launch {
                AdShowExt.showNativeAdInContainer(
                    context = binding.adContainer.context,
                    container = binding.adContainer,
                )
            }
        }
    }

    private class LoadingViewHolder(binding: ItemNewsLoadingBinding) : RecyclerView.ViewHolder(binding.root)

    private class EmptyViewHolder(binding: LayoutEmptyNormalBinding) : RecyclerView.ViewHolder(binding.root)

    private class NetworkErrorViewHolder(
        binding: ItemNewsNetworkErrorBinding,
        onRetryClick: (() -> Unit)?,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.btnRetry.setOnClickListener {
                onRetryClick?.invoke()
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<NewsFeedItem>() {
        override fun areItemsTheSame(oldItem: NewsFeedItem, newItem: NewsFeedItem): Boolean {
            return when {
                oldItem is NewsFeedItem.News && newItem is NewsFeedItem.News ->
                    oldItem.newsItem.url == newItem.newsItem.url
                oldItem is NewsFeedItem.NativeAd && newItem is NewsFeedItem.NativeAd ->
                    oldItem.id == newItem.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: NewsFeedItem, newItem: NewsFeedItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_NEWS = 0
        private const val VIEW_TYPE_LOADING = 1
        private const val VIEW_TYPE_EMPTY = 2
        private const val VIEW_TYPE_NETWORK_ERROR = 3
        private const val VIEW_TYPE_NATIVE_AD = 4
    }
}
