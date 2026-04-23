package com.example.browser.ui.file

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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

private data class FileCategoryVisualStyle(
    val colors: List<Color>,
    val shadowColor: Color,
)

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
            .background(Color(0xFFF3F3F3)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("title") {
            Text(
                text = stringResource(R.string.files_my_files),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp),
                color = Color(0xFF333333),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        item("storage") {
            FileStorageCard(
                state = storageUiState,
                onCleanClick = onCleanClick,
            )
        }

        item("download") {
            FileDownloadCard(onClick = onDownloadClick)
        }

        item("categories") {
            FileCategoryGrid(
                categories = categories,
                onCategoryClick = onCategoryClick,
            )
        }

        item("recent_files") {
            RecentFilesCard(
                hasPermission = hasPermission,
                recentFiles = recentFiles,
                onFileClick = onRecentFileClick,
                onFileDeleted = onRecentFileDeleted,
                onPermissionClick = onPermissionClick,
            )
        }
    }
}

@Composable
private fun FileStorageCard(
    state: FileStorageUiState,
    onCleanClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(162.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6FACFF),
                        Color(0xFF0770FD),
                    ),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF80B6FF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_file_redesign_manager_inner),
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                Text(
                    text = "File Manager",
                    modifier = Modifier.padding(start = 11.dp),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable(onClick = onCleanClick)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.files_clean),
                    color = Color(0xFF0C73FD),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
        ) {
            StorageProgressBar(
                segments = state.segments,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("Used: ")
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.SemiBold),
                            start = 6,
                            end = 6 + state.usedLabel.length,
                        )
                        append(state.usedLabel)
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                )
                Text(
                    text = state.totalLabel,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                modifier = Modifier.padding(top = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StorageLegendItem(
                    dotColor = Color(0xFFFEBE42),
                    label = stringResource(R.string.files_legend_apps),
                )
                StorageLegendItem(
                    dotColor = Color(0xFFFC4643),
                    label = stringResource(R.string.files_legend_videos),
                )
                StorageLegendItem(
                    dotColor = Color(0xFF6DC882),
                    label = stringResource(R.string.files_legend_photos),
                )
                StorageLegendItem(
                    dotColor = Color(0xFF706EF6),
                    label = stringResource(R.string.files_legend_music),
                )
            }
        }
    }
}

@Composable
private fun StorageProgressBar(
    segments: List<FileStorageSegmentUiState>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .height(8.dp),
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        )

        var startX = 0f
        segments.forEach { segment ->
            val width = size.width * segment.fraction.coerceIn(0f, 1f)
            if (width > 0f) {
                drawRoundRect(
                    color = segment.color,
                    topLeft = androidx.compose.ui.geometry.Offset(startX, 0f),
                    size = androidx.compose.ui.geometry.Size(width, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                )
                startX += width
            }
        }
    }
}

@Composable
private fun StorageLegendItem(
    dotColor: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, CircleShape),
        )
        Text(
            text = label,
            color = Color(0xFFABD4FF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun FileDownloadCard(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEFF1F3)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1276FD)),
                contentAlignment = Alignment.Center,
            ) {
                    Image(
                        painter = painterResource(R.drawable.ic_file_redesign_download_inner),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.files_download_title),
                    color = Color(0xFF333333),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.files_download_description),
                    modifier = Modifier.padding(top = 4.dp),
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                )
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1276FD)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_file_redesign_detail_inner),
                    contentDescription = null,
                    modifier = Modifier
                        .size(6.5.dp, 11.2.dp)
                        .rotate(180f),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun FileCategoryGrid(
    categories: List<FileCategoryCardUiState>,
    onCategoryClick: (FileType) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        categories.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { category ->
                    FileCategoryCard(
                        category = category,
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryClick(category.fileType) },
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FileCategoryCard(
    category: FileCategoryCardUiState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val style = category.visualStyle()
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(10.dp),
                        ambientColor = style.shadowColor.copy(alpha = 0.2f),
                        spotColor = style.shadowColor.copy(alpha = 0.2f),
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(style.colors))
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(category.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = category.title,
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                )
                Text(
                    text = category.count,
                    color = Color.Black.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RecentFilesCard(
    hasPermission: Boolean,
    recentFiles: List<RecentFile>,
    onFileClick: (RecentFile) -> Unit,
    onFileDeleted: (RecentFile) -> Unit,
    onPermissionClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.files_recent_files),
                color = Color(0xFF333333),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )

            when {
                !hasPermission -> {
                    PermissionRequestCard(
                        modifier = Modifier.padding(top = 16.dp),
                        onClick = onPermissionClick,
                    )
                }

                recentFiles.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.files_recent_empty),
                            color = Color(0xFF999999),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        recentFiles.forEach { file ->
                            RecentFileRow(
                                file = file,
                                onClick = { onFileClick(file) },
                                onDeleted = { onFileDeleted(file) },
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEFF1F3)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_file_permission_icon),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Fit,
            )

            Text(
                text = stringResource(R.string.files_no_permission_title),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp),
                color = Color(0xFF333333),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0881FE))
                    .padding(horizontal = 15.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.files_permission_allow),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
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
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(60.dp),
            ) {
                RecentFileThumbnail(file = file)

                if (file.fileType == FileType.VIDEO) {
                    file.getFormattedDuration()?.let { duration ->
                        Text(
                            text = duration,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            color = Color.White,
                            fontSize = 9.sp,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = file.name,
                    color = Color(0xFF333333),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = file.getFormattedSize(),
                    color = Color(0xFF999999),
                    fontSize = 12.sp,
                )
            }
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
                    cornerRadius = 16.dp.value * density
                    setColor(android.graphics.Color.parseColor("#F3F6FF"))
                }
                clipToOutline = true
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(16.dp)),
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

private fun FileCategoryCardUiState.visualStyle(): FileCategoryVisualStyle {
    return when (fileType) {
        FileType.IMAGE -> FileCategoryVisualStyle(
            colors = listOf(Color(0xFFFCB3FF), Color(0xFFF561FD)),
            shadowColor = Color(0xFFF9B4FF),
        )

        FileType.VIDEO -> FileCategoryVisualStyle(
            colors = listOf(Color(0xFFF2C6A3), Color(0xFFFFA840)),
            shadowColor = Color(0xFFF89D35),
        )

        FileType.DOCUMENT -> FileCategoryVisualStyle(
            colors = listOf(Color(0xFFBBDFF5), Color(0xFF3DD0F7)),
            shadowColor = Color(0xFF65C8DB),
        )

        FileType.APK -> FileCategoryVisualStyle(
            colors = listOf(Color(0xFFB3FFD5), Color(0xFF52DD90)),
            shadowColor = Color(0xFFB4FFDE),
        )

        FileType.AUDIO -> FileCategoryVisualStyle(
            colors = listOf(Color(0xFF9D79F4), Color(0xFF7747FB)),
            shadowColor = Color(0xFF9D64EF),
        )

        FileType.ZIP -> FileCategoryVisualStyle(
            colors = listOf(Color(0xFF7BCDFF), Color(0xFF3D94F7)),
            shadowColor = Color(0xFF65AADB),
        )

        else -> FileCategoryVisualStyle(
            colors = listOf(Color(0xFFBBD7FF), Color(0xFF6FACFF)),
            shadowColor = Color(0xFF80B6FF),
        )
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
