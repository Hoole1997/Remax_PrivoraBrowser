package com.example.browser.ui.file

import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FileScreen(
    hasPermission: Boolean,
    storageUiState: FileStorageUiState,
    categories: List<FileCategoryCardUiState>,
    recentFiles: List<RecentFile>,
    onCleanClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCategoryClick: (FileType) -> Unit,
    onRecentFileClick: (RecentFile) -> Unit,
    onRecentFileDeleted: (RecentFile) -> Unit,
    onPermissionClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA)),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Title
        item("title") {
            Text(
                text = stringResource(R.string.files_my_files),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                color = Color(0xFF1A1A1A),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Storage overview + quick actions
        item("storage") {
            StorageOverviewSection(
                state = storageUiState,
                onCleanClick = onCleanClick,
                onDownloadClick = onDownloadClick,
            )
        }

        // Categories
        item("categories") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.files_my_files),
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                    color = Color(0xFF1A1A1A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                FileCategoryGrid(
                    categories = categories,
                    onCategoryClick = onCategoryClick,
                )
            }
        }

        // Recent files
        item("recent_files") {
            RecentFilesSection(
                hasPermission = hasPermission,
                recentFiles = recentFiles,
                onFileClick = onRecentFileClick,
                onFileDeleted = onRecentFileDeleted,
                onPermissionClick = onPermissionClick,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Storage Overview: Ring chart + quick action chips
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StorageOverviewSection(
    state: FileStorageUiState,
    onCleanClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Storage card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Ring chart
                StorageRingChart(
                    segments = state.segments,
                    modifier = Modifier.size(56.dp),
                )

                // Text info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                ) {
                    Text(
                        text = state.usedLabel,
                        color = Color(0xFF1A1A1A),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "/ ${state.totalLabel}",
                        color = Color(0xFF999999),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // Legend dots
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StorageLegendDot(Color(0xFFFEBE42), stringResource(R.string.files_legend_apps))
                    StorageLegendDot(Color(0xFFFC4643), stringResource(R.string.files_legend_videos))
                    StorageLegendDot(Color(0xFF6DC882), stringResource(R.string.files_legend_photos))
                    StorageLegendDot(Color(0xFF706EF6), stringResource(R.string.files_legend_music))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick action chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickActionChip(
                iconContent = {
                    Image(
                        painter = painterResource(R.drawable.ic_file_redesign_download_inner),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(Color(0xFF1276FD)),
                    )
                },
                label = stringResource(R.string.files_download_title),
                accentColor = Color(0xFF1276FD),
                modifier = Modifier.weight(1f),
                onClick = onDownloadClick,
            )
            QuickActionChip(
                iconContent = { CleanIcon() },
                label = stringResource(R.string.files_clean),
                accentColor = Color(0xFF52DD90),
                modifier = Modifier.weight(1f),
                onClick = onCleanClick,
            )
        }
    }
}

@Composable
private fun StorageRingChart(
    segments: List<FileStorageSegmentUiState>,
    modifier: Modifier = Modifier,
) {
    var animationTriggered by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "ring_progress",
    )
    LaunchedEffect(Unit) { animationTriggered = true }

    Canvas(modifier = modifier) {
        val strokeWidth = 9.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2f
        val topLeft = androidx.compose.ui.geometry.Offset(
            (size.width - radius * 2) / 2f,
            (size.height - radius * 2) / 2f,
        )
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)

        // Background ring — use a visible light gray instead of near-white
        drawArc(
            color = Color(0xFFE4E6EB),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Colored segments (skip white segments — "other" storage uses gray instead)
        var startAngle = -90f
        segments.forEach { segment ->
            val sweep = segment.fraction * 360f * animatedProgress
            if (sweep > 0f) {
                // Replace white/near-white color with a visible neutral gray
                val drawColor = if (segment.color == Color.White ||
                    segment.color == Color(0xFFFFFFFF)
                ) {
                    Color(0xFFB0B3BA)
                } else {
                    segment.color
                }
                drawArc(
                    color = drawColor,
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
}

@Composable
private fun StorageLegendDot(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            color = Color(0xFF666666),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun QuickActionChip(
    iconContent: @Composable () -> Unit,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            iconContent()
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = Color(0xFF333333),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Custom-drawn broom/clean icon — a simple sweep shape drawn with Canvas
 */
@Composable
private fun CleanIcon() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val color = Color(0xFF52DD90)

        // Broom handle (diagonal line)
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.08f),
            end = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.52f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Broom bristles (fan shape at bottom)
        val bristleTop = h * 0.50f
        val bristleBottom = h * 0.92f
        val bristleLeft = w * 0.12f
        val bristleRight = w * 0.88f
        val bristlePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, bristleTop)
            lineTo(bristleLeft, bristleBottom)
            lineTo(bristleRight, bristleBottom)
            lineTo(w * 0.45f, bristleTop)
            close()
        }
        drawPath(
            path = bristlePath,
            color = color,
        )

        // Bristle lines
        val lineCount = 4
        for (i in 0 until lineCount) {
            val fraction = (i + 1).toFloat() / (lineCount + 1)
            val x = bristleLeft + (bristleRight - bristleLeft) * fraction
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = androidx.compose.ui.geometry.Offset(x, bristleTop + 4.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(x, bristleBottom - 3.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category Grid: 2-column horizontal items
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FileCategoryGrid(
    categories: List<FileCategoryCardUiState>,
    onCategoryClick: (FileType) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        categories.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { category ->
                    FileCategoryItem(
                        category = category,
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryClick(category.fileType) },
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FileCategoryItem(
    category: FileCategoryCardUiState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val iconTint = category.fileType.iconColor()
    val iconBg = iconTint.copy(alpha = 0.10f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(category.iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(iconTint),
            )
        }

        Column(
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(
                text = category.title,
                color = Color(0xFF1A1A1A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = category.count,
                color = Color(0xFF999999),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recent Files Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentFilesSection(
    hasPermission: Boolean,
    recentFiles: List<RecentFile>,
    onFileClick: (RecentFile) -> Unit,
    onFileDeleted: (RecentFile) -> Unit,
    onPermissionClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.files_recent_files),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            color = Color(0xFF1A1A1A),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

        when {
            !hasPermission -> {
                PermissionRequestCard(onClick = onPermissionClick)
            }

            recentFiles.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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

            else -> {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White),
                ) {
                    recentFiles.forEachIndexed { index, file ->
                        RecentFileRow(
                            file = file,
                            onClick = { onFileClick(file) },
                            onDeleted = { onFileDeleted(file) },
                        )
                        if (index < recentFiles.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 76.dp, end = 16.dp)
                                    .height(0.5.dp)
                                    .background(Color(0xFFF0F0F0)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestCard(
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp)) {
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
                .padding(start = 12.dp, end = 8.dp),
        ) {
            Text(
                text = file.name,
                color = Color(0xFF1A1A1A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = file.getFormattedSize(),
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
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
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp)),
        update = { imageView ->
            when (file.fileType) {
                FileType.IMAGE -> {
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(context)
                        .load(file.file)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .error(R.mipmap.ic_file_image)
                        .into(imageView)
                }

                FileType.VIDEO -> {
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(context)
                        .load(file.file)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .placeholder(R.mipmap.ic_file_video)
                        .error(R.mipmap.ic_file_video)
                        .into(imageView)
                }

                else -> {
                    imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    Glide.with(context).clear(imageView)
                    imageView.setImageResource(file.fileType.thumbnailRes())
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

private fun FileType.iconColor(): Color {
    return when (this) {
        FileType.IMAGE -> Color(0xFFF561FD)
        FileType.VIDEO -> Color(0xFFFFA840)
        FileType.DOCUMENT -> Color(0xFF3DBCF7)
        FileType.APK -> Color(0xFF52DD90)
        FileType.AUDIO -> Color(0xFF7747FB)
        FileType.ZIP -> Color(0xFF3D94F7)
        else -> Color(0xFF6FACFF)
    }
}

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
