package com.example.browser.ui.file

import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.browser.R
import com.example.browser.ui.file.model.FileType
import com.example.browser.ui.file.model.RecentFile
import com.example.browser.ui.file.widget.FileOptionsPopupWindow

data class FileStorageUiState(
    val usedLabel: String = "0B",
    val totalLabel: String = "0B",
    val usedFraction: Float = 0f,
    val segments: List<FileStorageSegmentUiState> = emptyList(),
)

data class FileStorageSegmentUiState(
    val fraction: Float,
    val color: Color,
)

data class FileCategoryCardUiState(
    val fileType: FileType,
    val title: String,
    val count: String,
    val iconRes: Int,
)

/** 5 个功能入口（清理 / 测速 / 进程 / 重复照片 / 相似照片）的 UI key。 */
enum class FileFeatureAction { CLEAN, SPEED, PROCESS, DUPLICATE_PHOTO, SIMILAR_PHOTO }

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FileScreen(
    hasPermission: Boolean,
    storageUiState: FileStorageUiState,
    categories: List<FileCategoryCardUiState>,
    recentFiles: List<RecentFile>,
    onFeatureClick: (FileFeatureAction) -> Unit,
    onDownloadClick: () -> Unit,
    onCategoryClick: (FileType) -> Unit,
    onRecentFileClick: (RecentFile) -> Unit,
    onRecentFileDeleted: (RecentFile) -> Unit,
    onPermissionClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // 顶部 "My files" 标题（对应 Figma 导航栏）
        item("title") {
            Text(
                text = stringResource(R.string.files_my_files),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                color = Color(0xFF333333),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // 存储概览（蓝底卡片 + 半圆百分比 + Clean 按钮）
        item("storage") {
            StorageOverviewCard(
                state = storageUiState,
                onCleanClick = { onFeatureClick(FileFeatureAction.CLEAN) },
            )
        }

        // 5 个功能入口（3x2 grid）
        item("features") {
            FeatureGridCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onFeatureClick = onFeatureClick,
            )
        }

        // Download 横向卡片
        item("download") {
            DownloadEntryCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onClick = onDownloadClick,
            )
        }

        // 6 个文件分类（image/video/document/apk/music/zip）
        item("categories") {
            FileCategoryCard(
                categories = categories,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onCategoryClick = onCategoryClick,
            )
        }

        // Recent files 标题
        item("recent_files_header") {
            Text(
                text = stringResource(R.string.files_recent_files),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 12.dp),
                color = Color(0xFF333333),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Recent files 内容：根据权限/数据状态分别展示
        // 注意：把 recentFiles 平铺为 LazyColumn 的 items，
        //      让 RecyclerView 风格的回收复用真正生效，避免一次性 compose 50 个 AndroidView 导致卡顿。
        when {
            !hasPermission -> {
                item("recent_no_permission") {
                    PermissionRequestCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onClick = onPermissionClick,
                    )
                }
            }

            recentFiles.isEmpty() -> {
                item("recent_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.files_recent_empty),
                            color = Color(0xFFBBBBBB),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            else -> {
                items(
                    items = recentFiles,
                    key = { it.file.absolutePath },
                ) { file ->
                    RecentFileRow(
                        file = file,
                        onClick = { onRecentFileClick(file) },
                        onDeleted = { onRecentFileDeleted(file) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 20.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Storage Overview Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StorageOverviewCard(
    state: FileStorageUiState,
    onCleanClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEFF5FF))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 左侧：使用量文字 + Clean 按钮 + Legend
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF333333),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        ) {
                            append(state.usedLabel)
                        }
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF999999),
                                fontSize = 16.sp,
                            ),
                        ) {
                            append("/${state.totalLabel}")
                        }
                    },
                )

                // Clean 红色胶囊按钮
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFC4A4A))
                        .clickable(onClick = onCleanClick)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.files_clean),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Legend dots（Apps / Videos / Image / Music）
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageLegendDot(Color(0xFFFEBE42), stringResource(R.string.files_legend_apps))
                    StorageLegendDot(Color(0xFFFC4643), stringResource(R.string.files_legend_videos))
                    StorageLegendDot(Color(0xFF6DC882), stringResource(R.string.files_legend_photos))
                    StorageLegendDot(Color(0xFF706EF6), stringResource(R.string.files_legend_music))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 右侧：84dp 多段彩色存储环
            StoragePercentRing(
                fraction = state.usedFraction,
                segments = state.segments,
                modifier = Modifier.size(84.dp),
            )
        }
    }
}

/**
 * 圆形存储环：按 [segments] 多色绘制各类型占用，未填满部分用浅灰底色表示空闲。
 * 中间显示总占用百分比文字。
 */
@Composable
private fun StoragePercentRing(
    fraction: Float,
    segments: List<FileStorageSegmentUiState>,
    modifier: Modifier = Modifier,
) {
    var triggered by remember { mutableStateOf(false) }
    val animated by animateFloatAsState(
        targetValue = if (triggered) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "ring",
    )
    LaunchedEffect(Unit) { triggered = true }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val topLeft = Offset(
                (size.width - radius * 2) / 2f,
                (size.height - radius * 2) / 2f,
            )
            val arcSize = Size(radius * 2, radius * 2)

            // 1. 浅灰底环（空闲容量）
            drawArc(
                color = Color(0xFFE3ECFA),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )

            // 2. 各类型分段彩色环：依次绘制 apps/videos/photos/music
            //    使用 Round cap 让段与段衔接处呈现圆角效果
            var startAngle = -90f
            segments.forEach { segment ->
                val sweep = segment.fraction * 360f * animated
                if (sweep > 0f) {
                    drawArc(
                        color = segment.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    startAngle += sweep
                }
            }
        }

        // 中间百分比文字 (16sp 数字 + 12sp 百分号)
        val percent = (fraction * 100).toInt().coerceIn(0, 100)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)) {
                    append(percent.toString())
                }
                withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)) {
                    append("%")
                }
            },
            color = Color(0xFF333333),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StorageLegendDot(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            color = Color(0xFF999999),
            fontSize = 11.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5 Feature Entries Grid (Clean / Speed / Process / Duplicate / Similar)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FeatureGridCard(
    modifier: Modifier = Modifier,
    onFeatureClick: (FileFeatureAction) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFEFF1F3), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 第 1 行：Clean / Speed / Process（3 列均分）
        Row(modifier = Modifier.fillMaxWidth()) {
            FeatureCell(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_file_feature_clean,
                label = stringResource(R.string.files_clean),
                onClick = { onFeatureClick(FileFeatureAction.CLEAN) },
            )
            FeatureCell(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_file_feature_speed,
                label = stringResource(R.string.files_speed),
                onClick = { onFeatureClick(FileFeatureAction.SPEED) },
            )
            FeatureCell(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_file_feature_process,
                label = stringResource(R.string.files_process),
                onClick = { onFeatureClick(FileFeatureAction.PROCESS) },
            )
        }
        // 第 2 行：Duplicate / Similar 占据 col-1/col-2，col-3 留空保持对齐
        Row(modifier = Modifier.fillMaxWidth()) {
            FeatureCell(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_file_feature_duplicate,
                label = stringResource(R.string.files_duplicate),
                onClick = { onFeatureClick(FileFeatureAction.DUPLICATE_PHOTO) },
            )
            FeatureCell(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_file_feature_similar,
                label = stringResource(R.string.files_similar),
                onClick = { onFeatureClick(FileFeatureAction.SIMILAR_PHOTO) },
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FeatureCell(
    modifier: Modifier = Modifier,
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
        )
        Text(
            text = label,
            color = Color(0xFF333333),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Download entry card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DownloadEntryCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 严格按 Figma 18472:3488 的 80dp 卡 + 16/18/70/22 等定位还原
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFEFF1F3), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        // 左侧 44dp 蓝色圆形下载图标，距左 16dp / 距上 18dp
        Image(
            painter = painterResource(R.drawable.ic_file_redesign_download_inner),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 16.dp, top = 18.dp)
                .size(44.dp),
            contentScale = ContentScale.Fit,
        )

        // 标题 + 描述：距左 70dp / 距上 22dp，标题与描述之间 8dp 间距
        Column(
            modifier = Modifier.padding(start = 70.dp, top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.files_download_title),
                color = Color(0xFF333333),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.files_download_description),
                color = Color(0xFF666666),
                fontSize = 12.sp,
            )
        }

        // 右侧 30dp 蓝色 details 箭头，距右 16dp，垂直居中
        Image(
            painter = painterResource(R.drawable.ic_file_redesign_more),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(30.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6 Categories card (Image/Video/Document/APK/Music/Zip)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FileCategoryCard(
    categories: List<FileCategoryCardUiState>,
    modifier: Modifier = Modifier,
    onCategoryClick: (FileType) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFEFF1F3), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        categories.chunked(3).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { category ->
                    CategoryCell(
                        modifier = Modifier.weight(1f),
                        category = category,
                        onClick = { onCategoryClick(category.fileType) },
                    )
                }
                // 当一行不足 3 个 cell 时，剩余位置用空 weight 占位，保证对齐
                if (rowItems.size < 3) {
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    modifier: Modifier = Modifier,
    category: FileCategoryCardUiState,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(category.iconRes),
            contentDescription = null,
            modifier = Modifier.size(34.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = category.title,
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = category.count,
                color = Color(0xFF999999),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recent Files
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionRequestCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_file_permission_icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            contentScale = ContentScale.Fit,
        )

        Text(
            text = stringResource(R.string.files_no_permission_title),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            color = Color(0xFF666666),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1276FD))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.files_permission_allow),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RecentFileRow(
    file: RecentFile,
    onClick: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(60.dp)) {
            RecentFileThumbnail(file = file)

            if (file.fileType == FileType.VIDEO) {
                file.getFormattedDuration()?.let { duration ->
                    Text(
                        text = duration,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 2.dp, bottom = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        color = Color.White,
                        fontSize = 8.sp,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = file.name,
                color = Color(0xFF333333),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = file.getFormattedSize(),
                color = Color(0xFF999999),
                fontSize = 12.sp,
            )
        }

        FileMoreButton(
            file = file,
            onDeleted = onDeleted,
        )
    }
}

@Composable
private fun RecentFileThumbnail(
    file: RecentFile,
) {
    val context = LocalContext.current
    // 只在文件路径真正变化时才让 update 块重新触发 Glide.load，
    // 避免快速滑动时 LazyColumn 回收复用 ViewHolder 时反复加载同一张图，
    // 引起卡顿和磁盘/网络 IO 浪费
    val filePath = remember(file.file.absolutePath) { file.file.absolutePath }
    val fileType = file.fileType
    AndroidView(
        factory = { viewContext ->
            AppCompatImageView(viewContext).apply {
                val density = resources.displayMetrics.density
                background = GradientDrawable().apply {
                    cornerRadius = 10.dp.value * density
                    setColor(android.graphics.Color.parseColor("#F5F6F8"))
                }
                clipToOutline = true
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(10.dp)),
        update = { imageView ->
            // 给 ImageView 打 tag，update 在同一路径下不重复加载
            if (imageView.getTag(R.id.tag_file_thumbnail_path) == filePath) return@AndroidView
            imageView.setTag(R.id.tag_file_thumbnail_path, filePath)
            when (fileType) {
                FileType.IMAGE -> {
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(context)
                        .load(file.file)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(180, 180)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .error(R.mipmap.ic_file_image)
                        .into(imageView)
                }

                FileType.VIDEO -> {
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(context)
                        .load(file.file)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(180, 180)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .placeholder(R.mipmap.ic_file_video)
                        .error(R.mipmap.ic_file_video)
                        .into(imageView)
                }

                else -> {
                    imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    Glide.with(context).clear(imageView)
                    imageView.setImageResource(fileType.thumbnailRes())
                }
            }
        },
    )
}

@Composable
private fun FileMoreButton(
    file: RecentFile,
    onDeleted: () -> Unit,
) {
    AndroidView(
        factory = { context ->
            AppCompatImageView(context).apply {
                setImageResource(R.drawable.ic_file_redesign_more)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(0, 0, 0, 0)
                setOnClickListener { anchor ->
                    FileOptionsPopupWindow(
                        context = anchor.context,
                        file = file,
                        onDelete = onDeleted,
                    ).show(anchor)
                }
            }
        },
        modifier = Modifier.size(24.dp),
        update = { view ->
            view.setOnClickListener { anchor ->
                FileOptionsPopupWindow(
                    context = anchor.context,
                    file = file,
                    onDelete = onDeleted,
                ).show(anchor)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun FileType.thumbnailRes(): Int {
    return when (this) {
        FileType.IMAGE -> R.mipmap.ic_file_image
        FileType.VIDEO -> R.mipmap.ic_file_video
        FileType.DOCUMENT -> R.mipmap.ic_file_document
        FileType.APK -> R.mipmap.ic_file_apk
        FileType.AUDIO -> R.mipmap.ic_file_audio
        FileType.ZIP -> R.mipmap.ic_file_zip
        else -> R.mipmap.ic_file_unknown
    }
}
