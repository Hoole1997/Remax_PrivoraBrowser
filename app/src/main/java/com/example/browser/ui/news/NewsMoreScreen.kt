package com.example.browser.ui.news

import android.text.Html
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.common.bill.ads.ext.AdShowExt
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.browser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun NewsMoreScreen(
    title: String,
    feedItems: List<NewsFeedItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onBackClick: () -> Unit,
    onNewsClick: (NewsItem) -> Unit,
    onRetryClick: () -> Unit,
    onLoadMore: () -> Unit,
    canLoadMore: () -> Boolean,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val adController = remember { ComposeNativeAdController() }
    val showBackToTop = listState.firstVisibleItemIndex > 1 ||
        (listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset > 120)

    LaunchedEffect(feedItems) {
        if (feedItems.isNotEmpty() && !listState.isScrollInProgress) {
            delay(120)
            adController.loadVisibleAds(context, feedItems, listState.layoutInfo.visibleItemsInfo.map { it.index })
        }
    }

    LaunchedEffect(listState, feedItems) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { isScrolling -> !isScrolling }
            .collect {
                delay(120)
                adController.loadVisibleAds(context, feedItems, listState.layoutInfo.visibleItemsInfo.map { it.index })
            }
    }

    LaunchedEffect(listState, feedItems, isLoadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .map { lastVisibleIndex ->
                lastVisibleIndex >= feedItems.lastIndex - 4
            }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (feedItems.isNotEmpty() && !isLoadingMore && canLoadMore()) {
                    onLoadMore()
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NewsMoreTopBar(
                title = title,
                onBackClick = onBackClick,
            )

            when {
                isLoading && feedItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4C4CFF),
                            strokeWidth = 3.dp,
                        )
                    }
                }

                feedItems.isEmpty() -> {
                    NewsMoreEmptyState(
                        message = if (!error.isNullOrEmpty()) error else context.getString(R.string.news_empty_list),
                        showRetry = !error.isNullOrEmpty(),
                        onRetryClick = onRetryClick,
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(
                            items = feedItems,
                            key = { _, item ->
                                when (item) {
                                    is NewsFeedItem.News -> "news_${item.newsItem.url ?: item.newsItem.key.orEmpty()}"
                                    is NewsFeedItem.NativeAd -> "ad_${item.id}"
                                }
                            },
                        ) { _, item ->
                            when (item) {
                                is NewsFeedItem.News -> NewsArticleCard(
                                    newsItem = item.newsItem,
                                    onClick = { onNewsClick(item.newsItem) },
                                )

                                is NewsFeedItem.NativeAd -> ComposeNativeAdCard(
                                    adId = item.id,
                                    controller = adController,
                                )
                            }
                        }

                        if (isLoadingMore) {
                            item("loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFF4C4CFF),
                                        strokeWidth = 2.5.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showBackToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(16.dp, CircleShape, clip = false)
                    .clickable {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.94f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.ic_news_back_to_top),
                        contentDescription = "Back to top",
                        tint = Color(0xFF1F1F1F),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsMoreTopBar(
    title: String,
    onBackClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_back_arrow),
                contentDescription = "Back",
                tint = Color(0xFF333333),
            )
        }

        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            color = Color(0xFF333333),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NewsArticleCard(
    newsItem: NewsItem,
    onClick: () -> Unit,
) {
    val sourceName = newsItem.author ?: newsItem.source ?: "Unknown"
    val imageUrl = newsItem.image?.replace("\\/", "/")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        if (!imageUrl.isNullOrEmpty()) {
            NewsCardImage(
                imageUrl = imageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(193.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }

        Text(
            text = htmlToPlainText(newsItem.title ?: "No title"),
            modifier = Modifier.padding(top = 11.dp),
            color = Color(0xFF333333),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sourceName,
                color = Color(0xFF666666),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatNewsDate(newsItem.publishedAt),
                color = Color(0xFF979797),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun NewsCardImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AppCompatImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { imageView ->
            Glide.with(imageView.context)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.mipmap.bg_news_default)
                .error(R.mipmap.bg_news_default)
                .thumbnail(0.15f)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(imageView)
        },
    )
}

@Composable
private fun ComposeNativeAdCard(
    adId: Int,
    controller: ComposeNativeAdController,
) {
    val context = LocalContext.current
    val container = remember(adId) { FrameLayout(context) }

    DisposableEffect(adId, container) {
        controller.bindContainer(adId, container)
        onDispose {
            controller.unbindContainer(adId, container)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            factory = { container },
            update = {
                controller.bindContainer(adId, it)
            },
        )
    }
}

@Composable
private fun NewsMoreEmptyState(
    message: String,
    showRetry: Boolean,
    onRetryClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                color = Color(0xFF666666),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            if (showRetry) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.clickable(onClick = onRetryClick),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF4C4CFF),
                ) {
                    Text(
                        text = "Retry",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun htmlToPlainText(text: String): String {
    return Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString().trim()
}

private fun formatNewsDate(dateString: String?): String {
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

private class ComposeNativeAdController {
    private val slots = mutableStateMapOf<Int, AdSlot>()

    fun bindContainer(adId: Int, container: FrameLayout) {
        val slot = slots.getOrPut(adId) { AdSlot() }
        if (slot.container !== container) {
            slot.container = container
            slot.isLoading = false
            slot.loadedContainerIdentity = null
            container.removeAllViews()
        }
    }

    fun unbindContainer(adId: Int, container: FrameLayout) {
        val slot = slots[adId] ?: return
        if (slot.container === container) {
            slot.container = null
            slot.isLoading = false
            slot.loadedContainerIdentity = null
            container.removeAllViews()
        }
    }

    fun loadVisibleAds(
        context: android.content.Context,
        feedItems: List<NewsFeedItem>,
        visibleIndexes: List<Int>,
    ) {
        visibleIndexes.forEach { index ->
            val item = feedItems.getOrNull(index) as? NewsFeedItem.NativeAd ?: return@forEach
            loadSlot(context, item.id)
        }
    }

    private fun loadSlot(context: android.content.Context, adId: Int) {
        val slot = slots[adId] ?: return
        val container = slot.container ?: return
        val containerIdentity = System.identityHashCode(container)
        if (slot.isLoading || slot.loadedContainerIdentity == containerIdentity) return

        slot.isLoading = true
        container.removeAllViews()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                AdShowExt.showNativeAdInContainer(
                    context = context,
                    container = container,
                )
                slot.loadedContainerIdentity = containerIdentity
            } finally {
                slot.isLoading = false
            }
        }
    }

    private class AdSlot {
        var container: FrameLayout? = null
        var loadedContainerIdentity: Int? = null
        var isLoading: Boolean = false
    }
}
