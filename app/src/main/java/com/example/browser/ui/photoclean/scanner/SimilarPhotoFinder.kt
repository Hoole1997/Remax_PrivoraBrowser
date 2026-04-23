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

object SimilarPhotoFinder {

    private const val TAG = "SimilarPhotoFinder"

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "bmp", "webp", "heic", "heif"
    )

    private const val SIMILARITY_THRESHOLD = 10

    data class ScanProgress(
        val currentFile: String,
        val progressPercent: Int,
        val foundGroups: Int
    )

    /**
     * 扫描并查找相似照片
     * 1. 通过 MediaStore 或文件遍历收集所有图片
     * 2. 对每张图片计算 dHash
     * 3. 使用 Union-Find 合并汉明距离 <= 阈值的图片
     * 4. 过滤掉只有 1 张的组
     */
    suspend fun findSimilar(
        context: Context,
        onProgress: ((ScanProgress) -> Unit)? = null
    ): List<PhotoCleanGroup> = withContext(Dispatchers.IO) {
        val imageFiles = collectImageFiles(context, onProgress)
        Log.d(TAG, "Total image files collected: ${imageFiles.size}")

        if (imageFiles.isEmpty()) return@withContext emptyList()

        // 计算所有图片的 dHash
        val hashEntries = mutableListOf<Pair<File, Long>>()
        val totalFiles = imageFiles.size

        imageFiles.forEachIndexed { index, file ->
            ensureActive()
            val dHash = PhotoHashCalculator.computeDHash(file)
            if (dHash != 0L) {
                hashEntries.add(file to dHash)
            }
            val percent = 30 + (index * 40 / totalFiles)
            onProgress?.invoke(
                ScanProgress(
                    currentFile = file.absolutePath,
                    progressPercent = percent.coerceAtMost(69),
                    foundGroups = 0
                )
            )
        }

        Log.d(TAG, "Computed dHash for ${hashEntries.size} files")
        if (hashEntries.size < 2) return@withContext emptyList()

        // Union-Find 分组
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

        // 两两比较汉明距离，合并相似的
        val totalComparisons = hashEntries.size.toLong() * (hashEntries.size - 1) / 2
        var comparisonsDone = 0L

        for (i in hashEntries.indices) {
            ensureActive()
            for (j in i + 1 until hashEntries.size) {
                val dist = PhotoHashCalculator.hammingDistance(
                    hashEntries[i].second,
                    hashEntries[j].second
                )
                if (dist <= SIMILARITY_THRESHOLD) {
                    union(i, j)
                }
                comparisonsDone++
            }
            if (totalComparisons > 0) {
                val percent = 70 + (comparisonsDone * 29 / totalComparisons).toInt()
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
                    groupId = "sim_$groupIndex",
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

        Log.d(TAG, "Found ${result.size} similar groups")
        onProgress?.invoke(
            ScanProgress(
                currentFile = "",
                progressPercent = 100,
                foundGroups = result.size
            )
        )
        result
    }

    private suspend fun collectImageFiles(
        context: Context,
        onProgress: ((ScanProgress) -> Unit)? = null
    ): List<File> = withContext(Dispatchers.IO) {
        val allPaths = mutableSetOf<String>()
        val resultFiles = mutableListOf<File>()

        // 方式1：MediaStore 查询
        try {
            val mediaFiles = queryMediaStore(context, onProgress)
            Log.d(TAG, "MediaStore returned ${mediaFiles.size} files")
            for (file in mediaFiles) {
                if (allPaths.add(file.absolutePath)) {
                    resultFiles.add(file)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query failed", e)
        }

        // 方式2：文件遍历补充
        try {
            val fsFiles = scanFileSystem(onProgress)
            Log.d(TAG, "File system scan returned ${fsFiles.size} files")
            for (file in fsFiles) {
                if (allPaths.add(file.absolutePath)) {
                    resultFiles.add(file)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "File system scan failed", e)
        }

        Log.d(TAG, "Total unique image files: ${resultFiles.size}")
        resultFiles
    }

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

        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media.SIZE} > ?",
            arrayOf("1024"),
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
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
            File(externalStorage, "Images")
        )

        val allFiles = mutableListOf<File>()
        val totalDirs = scanDirs.size
        var scannedDirs = 0

        for (dir in scanDirs) {
            if (dir.exists() && dir.canRead()) {
                collectFromDirectory(dir, allFiles, onProgress)
            }
            scannedDirs++
            val percent = (scannedDirs * 30 / totalDirs).coerceAtMost(29)
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
