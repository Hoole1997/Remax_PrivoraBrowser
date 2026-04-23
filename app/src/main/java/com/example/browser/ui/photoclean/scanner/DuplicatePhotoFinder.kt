package com.example.browser.ui.photoclean.scanner

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.browser.ui.photoclean.model.CleanablePhoto
import com.example.browser.ui.photoclean.model.PhotoCleanGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

object DuplicatePhotoFinder {

    private const val TAG = "DuplicatePhotoFinder"

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "bmp", "webp", "heic", "heif"
    )

    data class ScanProgress(
        val currentFile: String,
        val progressPercent: Int,
        val foundGroups: Int
    )

    // 视觉重复的汉明距离阈值（比"相似"更严格，≤5 表示几乎一样的图片）
    private const val VISUAL_DUPLICATE_THRESHOLD = 5

    /**
     * 扫描并查找重复照片（混合策略）
     * 阶段1：收集所有图片
     * 阶段2：计算每张图片的 dHash（感知哈希）
     * 阶段3：使用 Union-Find 将汉明距离 ≤ 5 的图片合并为一组
     *        （捕获二次压缩、轻微裁剪、不同格式保存等情况）
     * 阶段4：过滤掉只有 1 张的组
     */
    suspend fun findDuplicates(
        context: Context,
        onProgress: ((ScanProgress) -> Unit)? = null
    ): List<PhotoCleanGroup> = withContext(Dispatchers.IO) {
        val imageFiles = collectImageFiles(context, onProgress)
        Log.d(TAG, "Total image files collected: ${imageFiles.size}")

        if (imageFiles.size < 2) return@withContext emptyList()

        // 阶段2：计算所有图片的 dHash
        val hashEntries = mutableListOf<Pair<File, Long>>()
        val totalFiles = imageFiles.size

        imageFiles.forEachIndexed { index, file ->
            ensureActive()
            val dHash = PhotoHashCalculator.computeDHash(file)
            if (dHash != 0L) {
                hashEntries.add(file to dHash)
            }
            val percent = 30 + (index * 30 / totalFiles)
            onProgress?.invoke(
                ScanProgress(
                    currentFile = file.absolutePath,
                    progressPercent = percent.coerceAtMost(59),
                    foundGroups = 0
                )
            )
        }

        Log.d(TAG, "Computed dHash for ${hashEntries.size} / ${imageFiles.size} files")
        if (hashEntries.size < 2) return@withContext emptyList()

        // 阶段3：Union-Find 分组（汉明距离 ≤ VISUAL_DUPLICATE_THRESHOLD）
        val parent = IntArray(hashEntries.size) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (cur != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        val totalComparisons = hashEntries.size.toLong() * (hashEntries.size - 1) / 2
        var comparisonsDone = 0L

        for (i in hashEntries.indices) {
            ensureActive()
            for (j in i + 1 until hashEntries.size) {
                val dist = PhotoHashCalculator.hammingDistance(
                    hashEntries[i].second,
                    hashEntries[j].second
                )
                if (dist <= VISUAL_DUPLICATE_THRESHOLD) {
                    union(i, j)
                }
                comparisonsDone++
            }
            if (totalComparisons > 0) {
                val percent = 60 + (comparisonsDone * 39 / totalComparisons).toInt()
                onProgress?.invoke(
                    ScanProgress(
                        currentFile = hashEntries[i].first.absolutePath,
                        progressPercent = percent.coerceAtMost(99),
                        foundGroups = 0
                    )
                )
            }
        }

        // 收集分组结果
        val groups = mutableMapOf<Int, MutableList<Int>>()
        for (i in hashEntries.indices) {
            val root = find(i)
            groups.getOrPut(root) { mutableListOf() }.add(i)
        }

        val result = groups.filter { it.value.size > 1 }
            .entries.mapIndexed { groupIndex, (_, indices) ->
                PhotoCleanGroup(
                    groupId = "dup_$groupIndex",
                    photos = indices.mapIndexed { photoIndex, idx ->
                        val (file, hash) = hashEntries[idx]
                        CleanablePhoto(
                            file = file,
                            hash = hash.toString(),
                            isChecked = false
                        )
                    }
                )
            }
            .sortedByDescending { it.photos.size }

        Log.d(TAG, "Found ${result.size} duplicate groups (visual threshold=$VISUAL_DUPLICATE_THRESHOLD)")
        onProgress?.invoke(
            ScanProgress(
                currentFile = "",
                progressPercent = 100,
                foundGroups = result.size
            )
        )
        result
    }

    /**
     * 收集所有图片文件：优先使用 MediaStore，同时使用文件遍历作为补充
     */
    private suspend fun collectImageFiles(
        context: Context,
        onProgress: ((ScanProgress) -> Unit)? = null
    ): List<File> = withContext(Dispatchers.IO) {
        val allFiles = mutableSetOf<String>() // 用路径去重
        val resultFiles = mutableListOf<File>()

        // 方式1：通过 MediaStore 查询（Android 10+ 推荐）
        try {
            val mediaFiles = queryMediaStore(context, onProgress)
            Log.d(TAG, "MediaStore returned ${mediaFiles.size} files")
            for (file in mediaFiles) {
                if (allFiles.add(file.absolutePath)) {
                    resultFiles.add(file)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query failed", e)
        }

        // 方式2：文件遍历补充（确保不遗漏）
        try {
            val fileSystemFiles = scanFileSystem(onProgress)
            Log.d(TAG, "File system scan returned ${fileSystemFiles.size} files")
            for (file in fileSystemFiles) {
                if (allFiles.add(file.absolutePath)) {
                    resultFiles.add(file)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "File system scan failed", e)
        }

        Log.d(TAG, "Total unique image files: ${resultFiles.size}")
        resultFiles
    }

    /**
     * 通过 MediaStore 查询所有图片文件路径
     */
    private fun queryMediaStore(
        context: Context,
        onProgress: ((ScanProgress) -> Unit)? = null
    ): List<File> {
        val files = mutableListOf<File>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media.SIZE} > ?",
            arrayOf("1024"), // 大于 1KB
            sortOrder
        )?.use { cursor ->
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn) ?: continue
                val size = cursor.getLong(sizeColumn)
                val file = File(path)
                if (file.exists() && file.canRead() && size > 1024) {
                    files.add(file)
                    onProgress?.invoke(
                        ScanProgress(
                            currentFile = path,
                            progressPercent = -1,
                            foundGroups = 0
                        )
                    )
                }
            }
        }

        return files
    }

    /**
     * 文件系统遍历扫描
     */
    private fun scanFileSystem(
        onProgress: ((ScanProgress) -> Unit)? = null
    ): List<File> {
        val externalStorage = Environment.getExternalStorageDirectory()
        val scanDirs = listOf(
            File(externalStorage, "DCIM"),
            File(externalStorage, "Pictures"),
            File(externalStorage, "Download"),
            File(externalStorage, "Screenshots"),
            File(externalStorage, "Photo"),
            File(externalStorage, "Camera"),
            File(externalStorage, "Images"),
            File(externalStorage, "Tencent/MicroMsg"),
            File(externalStorage, "Tencent/QQ_Images"),
            File(externalStorage, "Android/media")
        )

        val allFiles = mutableListOf<File>()
        val totalDirs = scanDirs.size
        var scannedDirs = 0

        for (dir in scanDirs) {
            if (dir.exists() && dir.canRead()) {
                Log.d(TAG, "Scanning directory: ${dir.absolutePath}")
                collectFromDirectory(dir, allFiles, onProgress)
                Log.d(TAG, "  -> found ${allFiles.size} files so far")
            } else {
                Log.d(TAG, "Directory not accessible: ${dir.absolutePath} exists=${dir.exists()} canRead=${dir.canRead()}")
            }
            scannedDirs++
            val percent = (scannedDirs * 50 / totalDirs).coerceAtMost(49)
            onProgress?.invoke(
                ScanProgress(
                    currentFile = dir.absolutePath,
                    progressPercent = percent,
                    foundGroups = 0
                )
            )
        }
        return allFiles
    }

    private fun collectFromDirectory(
        directory: File,
        result: MutableList<File>,
        onProgress: ((ScanProgress) -> Unit)? = null
    ) {
        try {
            val files = directory.listFiles() ?: return
            for (file in files) {
                if (file.isFile && isImageFile(file)) {
                    result.add(file)
                    onProgress?.invoke(
                        ScanProgress(
                            currentFile = file.absolutePath,
                            progressPercent = -1,
                            foundGroups = 0
                        )
                    )
                } else if (file.isDirectory && !file.name.startsWith(".")) {
                    collectFromDirectory(file, result, onProgress)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning directory: ${directory.absolutePath}", e)
        }
    }

    private fun isImageFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in IMAGE_EXTENSIONS && file.length() > 1024
    }
}
