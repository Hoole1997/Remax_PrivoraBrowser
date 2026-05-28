package com.example.browser.ui.home

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
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

    /** Adapter 级别的协程作用域，跟随 detach 自动取消，避免广告加载协程泄漏。 */
    private val adapterScope = MainScope()

    /**
     * 按广告位 id 缓存已加载好的原生广告 View。
     * 这样即使 ViewHolder 被回收复用，滑回原位置时也能把同一个广告 View 挂回容器，
     * 避免出现"滑出再滑回，广告没了"的现象。
     */
    private val nativeAdViewCache = mutableMapOf<Int, View>()

    /**
     * 已经向 SDK 请求过的广告位 id 集合。
     * 同一个 adId 只允许请求 SDK 一次，无论后续 ViewHolder 如何被回收/重绑，
     * 都不会再触发广告 SDK 的加载或展示计数，避免广告刷新过频。
     */
    private val loadedAdIds = mutableSetOf<Int>()

    /** 上一次执行进场动画的 adapter position，防止已显示过的 item 反复播放。 */
    private var lastAnimatedPosition = -1

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        // getItemCount() 在数据外还挂了 loading footer / empty / network error 三种占位项，
        // 它们没有对应的数据条目。这里必须先按 viewType/位置兜底，否则在
        // setLoadingFooterVisible(true) 触发 notifyItemInserted(dataCount) 后，
        // RecyclerView 回到这里取 stable id 时，getItem(dataCount) 会
        // 抛 IndexOutOfBoundsException。
        val dataCount = super.getItemCount()
        if (position >= dataCount) {
            return when {
                showNetworkError -> ID_NETWORK_ERROR
                showEmptyView -> ID_EMPTY
                else -> ID_LOADING_FOOTER
            }
        }
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
            VIEW_TYPE_NATIVE_AD -> NativeAdViewHolder(
                ItemNewsNativeAdBinding.inflate(inflater, parent, false),
                adapterScope,
                nativeAdViewCache,
                loadedAdIds,
            )
            VIEW_TYPE_LOADING -> LoadingViewHolder(ItemNewsLoadingBinding.inflate(inflater, parent, false))
            VIEW_TYPE_NETWORK_ERROR -> NetworkErrorViewHolder(
                ItemNewsNetworkErrorBinding.inflate(inflater, parent, false),
                onRetryClick,
            )

            else -> EmptyViewHolder(LayoutEmptyNormalBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // 复用 ViewHolder 时，先把 itemView 视觉状态恢复到默认值，避免上一次进场动画
        // 被中断后残留的 alpha/translation 跟过来，造成"item 看不到"的现象。
        holder.itemView.animate().cancel()
        holder.itemView.alpha = 1f
        holder.itemView.translationY = 0f

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
        playEnterAnimationIfNeeded(holder)
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is NewsViewHolder) {
            holder.clearImage()
        }
        // 取消可能正在进行的进场动画，并把 itemView 还原到稳态，
        // 否则动画播放到一半被中断时，alpha/translationY 会残留在中间值，
        // 滑回时由于 lastAnimatedPosition 判断不会重播动画，导致 item 看起来"消失"。
        holder.itemView.animate().cancel()
        holder.itemView.alpha = 1f
        holder.itemView.translationY = 0f
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterScope.cancel()
        // 清理广告 View 缓存与已加载记录，避免内存泄漏。
        // RecyclerView detach 通常发生在 Fragment view 销毁时，下次重建 Adapter 会重新加载。
        nativeAdViewCache.values.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        nativeAdViewCache.clear()
        loadedAdIds.clear()
    }

    /**
     * 给新进入屏幕的 item 一个克制的"上滑+淡入"进场动画，缓解滑动时 item 突然出现的卡顿观感。
     * 仅对首次进入屏幕的 position 触发；回滚到已展示过的 item 不再播放。
     */
    private fun playEnterAnimationIfNeeded(holder: RecyclerView.ViewHolder) {
        // 加载中、空、错误等占位项不参与动画，避免视觉混乱
        if (holder is LoadingViewHolder || holder is EmptyViewHolder || holder is NetworkErrorViewHolder) return
        val position = holder.bindingAdapterPosition
        if (position <= lastAnimatedPosition) return
        lastAnimatedPosition = position
        val view = holder.itemView
        view.alpha = 0f
        view.translationY = ENTER_ANIMATION_TRANSLATION_PX
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ENTER_ANIMATION_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
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
            // 大部分 title 是纯文本，没有 HTML 转义就跳过 fromHtml，节省主线程 parse 成本
            val rawTitle = newsItem.title ?: "No title"
            binding.tvNewsTitle.text = if (rawTitle.containsHtmlEntity()) {
                Html.fromHtml(rawTitle, Html.FROM_HTML_MODE_COMPACT).toString().trim()
            } else {
                rawTitle.trim()
            }
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

        private fun String.containsHtmlEntity(): Boolean {
            // 简单判定：含 < 或 & 才需要走 HTML 解码，避免对纯文本做无意义解析
            return indexOf('<') >= 0 || indexOf('&') >= 0
        }

        private fun formatDate(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return ""
            return try {
                val date = inputDateFormat.parse(dateString)
                date?.let { outputDateFormat.format(it) } ?: dateString
            } catch (_: Exception) {
                dateString
            }
        }

        companion object {
            // SimpleDateFormat 非线程安全，但这里只在主线程使用，可以安全复用
            private val inputDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
            private val outputDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
    }

    private class NativeAdViewHolder(
        private val binding: ItemNewsNativeAdBinding,
        private val scope: CoroutineScope,
        private val viewCache: MutableMap<Int, View>,
        private val loadedAdIds: MutableSet<Int>,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var boundAdId: Int? = null
        private var loadingJob: Job? = null

        fun bind(adId: Int) {
            if (boundAdId == adId) return
            boundAdId = adId

            // 切换到新的广告位，先把容器清干净（不会销毁广告 View 本身，
            // 因为缓存里仍持有强引用，只是把它从父容器里摘下来）。
            binding.adContainer.removeAllViews()

            // 如果该 adId 已经有缓存好的广告 View，直接挂回去复用，避免重新请求 SDK
            viewCache[adId]?.let { cachedAdView ->
                (cachedAdView.parent as? ViewGroup)?.removeView(cachedAdView)
                binding.adContainer.addView(cachedAdView)
            }
        }

        fun loadAd() {
            val adId = boundAdId ?: return
            // 严格按 adId 去重：只要这个广告位之前请求过 SDK，就不再触发，
            // 防止滑出再滑回 → 进入 RV 缓存命中失败 → 再次调用 SDK → 计数刷新过频
            if (loadedAdIds.contains(adId)) return
            loadedAdIds.add(adId)

            loadingJob = scope.launch {
                AdShowExt.showNativeAdInContainer(
                    context = binding.adContainer.context,
                    container = binding.adContainer,
                    position = "NA_News_List"
                )
                // 渲染完成后把当前展示的广告 View 缓存起来，方便后续滑回时直接复用
                binding.adContainer.getChildAt(0)?.let { rendered ->
                    viewCache[adId] = rendered
                }
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

        /** Item 入场动画时长，控制在 300ms 以内避免拖沓也不会被快速滑动盖掉。 */
        private const val ENTER_ANIMATION_DURATION_MS = 260L

        /** 入场动画的初始 Y 位移（px），约等于 28dp。低端机也不会因位移过大而显眼掉帧。 */
        private const val ENTER_ANIMATION_TRANSLATION_PX = 80f

        // 占位项的稳定 id。普通新闻使用 url.hashCode()（int 范围），广告使用 Long.MIN_VALUE + id，
        // 这里用 Long.MAX_VALUE 附近的常量保证三者互不冲突。
        private const val ID_LOADING_FOOTER = Long.MAX_VALUE
        private const val ID_EMPTY = Long.MAX_VALUE - 1
        private const val ID_NETWORK_ERROR = Long.MAX_VALUE - 2
    }
}
