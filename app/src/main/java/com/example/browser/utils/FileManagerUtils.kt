package com.example.browser.utils

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ImageUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.example.browser.ui.file.ImageActivity
import com.example.browser.ui.file.model.FileType
import com.example.browser.ui.file.model.RecentFile
import com.example.browser.ui.web.WebActivity
import com.example.player.VideoPlayerActivity
import java.io.File

/**
 * 文件管理工具类
 */
object FileManagerUtils {

    /**
     * 获取最近文件列表（所有类型）
     * @param limit 返回的最大文件数量
     */
    fun getRecentFiles(context: Context, limit: Int = 20): List<RecentFile> {
        val recentFiles = mutableListOf<RecentFile>()

        try {
            // 1. 从 MediaStore 获取图片
            recentFiles.addAll(getRecentMediaFiles(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.DATE_MODIFIED
            ))

            // 2. 从 MediaStore 获取视频
            recentFiles.addAll(getRecentMediaFiles(
                context,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.DATE_MODIFIED,
                isVideo = true
            ))

            // 3. 从 MediaStore 获取音频
            recentFiles.addAll(getRecentMediaFiles(
                context,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.DATE_MODIFIED
            ))

            // 4. 从 MediaStore.Downloads 获取文档、APK、ZIP (Android 10+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                recentFiles.addAll(getRecentDownloadFiles(context))
            }

            // 5. 从下载目录扫描文件（补充）
            recentFiles.addAll(getRecentFilesFromDownloadDir())

            // 按时间排序并去重（根据文件路径）
            return recentFiles
                .distinctBy { it.file.absolutePath }
                .sortedByDescending { it.lastModified }
                .take(limit)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return emptyList()
    }

    /**
     * 从 MediaStore 获取最近的媒体文件
     */
    private fun getRecentMediaFiles(
        context: Context,
        uri: android.net.Uri,
        dateColumn: String,
        isVideo: Boolean = false
    ): List<RecentFile> {
        val files = mutableListOf<RecentFile>()
        try {
            // 根据是否是视频，动态添加 DURATION 列
            val projection = if (isVideo) {
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.Video.Media.DURATION,  // 视频时长（毫秒）
                    dateColumn
                )
            } else {
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.MIME_TYPE,
                    dateColumn
                )
            }

            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "$dateColumn DESC"
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val mimeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateColumnIndex = it.getColumnIndexOrThrow(dateColumn)

                // 只有视频才获取 DURATION 列
                val durationColumn = if (isVideo) {
                    it.getColumnIndex(MediaStore.Video.Media.DURATION)
                } else -1

                while (it.moveToNext()) {
                    val filePath = it.getString(dataColumn)
                    val file = File(filePath)

                    // 只添加存在的文件，不包括目录
                    if (file.exists() && file.isFile) {
                        val mimeType = it.getString(mimeColumn)
                        val fileType = getFileTypeFromMimeType(mimeType)

                        // 从 MediaStore 直接获取视频时长，无需使用 MediaMetadataRetriever
                        val videoDuration = if (isVideo && durationColumn != -1) {
                            try {
                                it.getLong(durationColumn)
                            } catch (e: Exception) {
                                null
                            }
                        } else null

                        files.add(
                            RecentFile(
                                file = file,
                                name = it.getString(nameColumn),
                                size = it.getLong(sizeColumn),
                                lastModified = it.getLong(dateColumnIndex) * 1000, // 转换为毫秒
                                mimeType = mimeType,
                                thumbnailPath = filePath,
                                fileType = fileType,
                                videoDuration = videoDuration
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return files
    }

    /**
     * 从 MediaStore.Downloads 获取最近的下载文件
     */
    private fun getRecentDownloadFiles(context: Context): List<RecentFile> {
        val files = mutableListOf<RecentFile>()
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.DATA,
                    MediaStore.Downloads.MIME_TYPE,
                    MediaStore.Downloads.DATE_MODIFIED
                )

                val cursor = context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )

                cursor?.use {
                    val nameColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                    val dataColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.DATA)
                    val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
                    val dateColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

                    while (it.moveToNext()) {
                        val filePath = it.getString(dataColumn)
                        val file = File(filePath)

                        // 只添加存在的文件，不包括目录
                        if (file.exists() && file.isFile) {
                            val mimeType = it.getString(mimeColumn)
                            files.add(
                                RecentFile(
                                    file = file,
                                    name = it.getString(nameColumn),
                                    size = it.getLong(sizeColumn),
                                    lastModified = it.getLong(dateColumn) * 1000, // 转换为毫秒
                                    mimeType = mimeType,
                                    thumbnailPath = filePath,
                                    fileType = getFileTypeFromMimeType(mimeType)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return files
    }

    /**
     * 从下载目录扫描最近文件（补充方案）
     */
    private fun getRecentFilesFromDownloadDir(): List<RecentFile> {
        val files = mutableListOf<RecentFile>()
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir.exists() && downloadDir.canRead()) {
                downloadDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val mimeType = getMimeType(file.absolutePath)
                        files.add(
                            RecentFile(
                                file = file,
                                name = file.name,
                                size = file.length(),
                                lastModified = file.lastModified(),
                                mimeType = mimeType,
                                thumbnailPath = file.absolutePath,
                                fileType = getFileTypeFromMimeType(mimeType)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return files
    }

    /**
     * 根据文件类型获取文件列表
     */
    fun getFilesByType(context: Context, fileType: FileType): List<RecentFile> {
        return when (fileType) {
            FileType.IMAGE -> getImageFiles(context)
            FileType.VIDEO -> getVideoFiles(context)
            FileType.AUDIO -> getAudioFiles(context)
            FileType.DOCUMENT -> getDocumentFiles(context)
            FileType.APK -> getApkFiles(context)
            FileType.ZIP -> getZipFiles(context)
            FileType.DOWNLOAD -> getDownloadFiles()
            else -> emptyList()
        }
    }

    /**
     * 获取图片文件列表
     */
    private fun getImageFiles(context: Context): List<RecentFile> {
        return getRecentMediaFiles(
            context,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.DATE_MODIFIED
        )
    }

    /**
     * 获取视频文件列表
     */
    private fun getVideoFiles(context: Context): List<RecentFile> {
        return getRecentMediaFiles(
            context,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.DATE_MODIFIED,
            isVideo = true
        )
    }

    /**
     * 获取音频文件列表
     */
    private fun getAudioFiles(context: Context): List<RecentFile> {
        return getRecentMediaFiles(
            context,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
    }

    /**
     * 获取文档文件列表
     */
    private fun getDocumentFiles(context: Context): List<RecentFile> {
        val files = mutableListOf<RecentFile>()

        // 从 MediaStore.Downloads 获取文档类型文件
        files.addAll(getFilesByMimeType(context, listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain"
        )))

        // 从下载目录扫描文档
        files.addAll(getFilesByExtensionsInDownloads(listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")))

        return files.distinctBy { it.file.absolutePath }.sortedByDescending { it.lastModified }
    }

    /**
     * 获取APK文件列表
     */
    private fun getApkFiles(context: Context): List<RecentFile> {
        val files = mutableListOf<RecentFile>()

        files.addAll(getFilesByMimeType(context, listOf("application/vnd.android.package-archive")))
        files.addAll(getFilesByExtensionsInDownloads(listOf("apk")))

        return files.distinctBy { it.file.absolutePath }.sortedByDescending { it.lastModified }
    }

    /**
     * 获取ZIP文件列表
     */
    private fun getZipFiles(context: Context): List<RecentFile> {
        val files = mutableListOf<RecentFile>()

        files.addAll(getFilesByMimeType(context, listOf(
            "application/zip",
            "application/x-rar-compressed",
            "application/x-7z-compressed"
        )))
        files.addAll(getFilesByExtensionsInDownloads(listOf("zip", "rar", "7z")))

        return files.distinctBy { it.file.absolutePath }.sortedByDescending { it.lastModified }
    }

    /**
     * 获取下载目录的所有文件
     */
    private fun getDownloadFiles(): List<RecentFile> {
        return getRecentFilesFromDownloadDir()
    }

    /**
     * 通过 MediaStore 根据 MIME 类型获取文件列表
     */
    private fun getFilesByMimeType(context: Context, mimeTypes: List<String>): List<RecentFile> {
        val files = mutableListOf<RecentFile>()
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.DATA,
                    MediaStore.Downloads.MIME_TYPE,
                    MediaStore.Downloads.DATE_MODIFIED
                )

                // 构建 selection 语句
                val selectionArgs = mutableListOf<String>()
                val selections = mimeTypes.map {
                    selectionArgs.add(it)
                    "${MediaStore.Downloads.MIME_TYPE} = ?"
                }
                val selection = selections.joinToString(" OR ")

                val cursor = context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs.toTypedArray(),
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )

                cursor?.use {
                    val nameColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                    val dataColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.DATA)
                    val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
                    val dateColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

                    while (it.moveToNext()) {
                        val filePath = it.getString(dataColumn)
                        val file = File(filePath)

                        if (file.exists() && file.isFile) {
                            val mimeType = it.getString(mimeColumn)
                            files.add(
                                RecentFile(
                                    file = file,
                                    name = it.getString(nameColumn),
                                    size = it.getLong(sizeColumn),
                                    lastModified = it.getLong(dateColumn) * 1000,
                                    mimeType = mimeType,
                                    thumbnailPath = filePath,
                                    fileType = getFileTypeFromMimeType(mimeType)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return files
    }

    /**
     * 根据文件扩展名获取文件列表（仅下载目录，包含子目录）
     */
    private fun getFilesByExtensionsInDownloads(extensions: List<String>): List<RecentFile> {
        val files = mutableListOf<RecentFile>()
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir.exists() && downloadDir.canRead()) {
                scanDirectoryForFiles(downloadDir, extensions, files)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return files
    }

    /**
     * 递归扫描目录查找指定扩展名的文件并添加到列表
     */
    private fun scanDirectoryForFiles(
        dir: File,
        extensions: List<String>,
        files: MutableList<RecentFile>,
        depth: Int = 0,
        maxDepth: Int = 3
    ) {
        if (depth > maxDepth) return

        try {
            dir.listFiles()?.forEach { file ->
                when {
                    file.isFile -> {
                        val ext = file.extension.lowercase()
                        if (extensions.contains(ext)) {
                            val mimeType = getMimeType(file.absolutePath)
                            files.add(
                                RecentFile(
                                    file = file,
                                    name = file.name,
                                    size = file.length(),
                                    lastModified = file.lastModified(),
                                    mimeType = mimeType,
                                    thumbnailPath = file.absolutePath,
                                    fileType = getFileTypeFromMimeType(mimeType)
                                )
                            )
                        }
                    }
                    file.isDirectory -> {
                        if (!file.name.startsWith(".") &&
                            file.name != "Android" &&
                            file.canRead()) {
                            scanDirectoryForFiles(file, extensions, files, depth + 1, maxDepth)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 根据文件类型获取文件数量
     */
    fun getFileCountByType(context: Context, fileType: FileType): Int {
        return when (fileType) {
            FileType.IMAGE -> getImageCount(context)
            FileType.VIDEO -> getVideoCount(context)
            FileType.AUDIO -> getAudioCount(context)
            FileType.DOCUMENT -> getDocumentCount(context)
            FileType.APK -> getApkCount(context)
            FileType.ZIP -> getZipCount(context)
            FileType.DOWNLOAD -> getDownloadCount()
            else -> 0
        }
    }

    /**
     * 获取图片数量（通过 MediaStore 查询）
     */
    private fun getImageCount(context: Context): Int {
        var count = 0
        try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            count = cursor?.count ?: 0
            cursor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    /**
     * 获取视频数量
     */
    private fun getVideoCount(context: Context): Int {
        var count = 0
        try {
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            count = cursor?.count ?: 0
            cursor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    /**
     * 获取音频数量
     */
    private fun getAudioCount(context: Context): Int {
        var count = 0
        try {
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            count = cursor?.count ?: 0
            cursor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    /**
     * 获取文档数量（使用MediaStore和Downloads扫描）
     */
    private fun getDocumentCount(context: Context): Int {
        return getFileCountByMimeType(context, listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain"
        )) + getFileCountByExtensionsInDownloads(listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt"))
    }

    /**
     * 获取APK数量
     */
    private fun getApkCount(context: Context): Int {
        return getFileCountByMimeType(context, listOf("application/vnd.android.package-archive")) +
                getFileCountByExtensionsInDownloads(listOf("apk"))
    }

    /**
     * 获取ZIP数量
     */
    private fun getZipCount(context: Context): Int {
        return getFileCountByMimeType(context, listOf(
            "application/zip",
            "application/x-rar-compressed",
            "application/x-7z-compressed"
        )) + getFileCountByExtensionsInDownloads(listOf("zip", "rar", "7z"))
    }

    /**
     * 获取下载目录文件数量
     */
    private fun getDownloadCount(): Int {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir.exists()) {
                return downloadDir.listFiles()?.size ?: 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    /**
     * 通过 MediaStore 根据 MIME 类型获取文件数量
     */
    private fun getFileCountByMimeType(context: Context, mimeTypes: List<String>): Int {
        var count = 0
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore.Downloads
                val projection = arrayOf(MediaStore.Downloads._ID)

                // 构建 selection 语句
                val selectionArgs = mutableListOf<String>()
                val selections = mimeTypes.map {
                    selectionArgs.add(it)
                    "${MediaStore.Downloads.MIME_TYPE} = ?"
                }
                val selection = selections.joinToString(" OR ")

                val cursor = context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs.toTypedArray(),
                    null
                )
                count = cursor?.count ?: 0
                cursor?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    /**
     * 根据文件扩展名获取文件数量（仅下载目录，包含子目录）
     */
    private fun getFileCountByExtensionsInDownloads(extensions: List<String>): Int {
        var count = 0
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir.exists() && downloadDir.canRead()) {
                count = scanDirectoryForExtensions(downloadDir, extensions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    /**
     * 递归扫描目录查找指定扩展名的文件
     */
    private fun scanDirectoryForExtensions(
        dir: File,
        extensions: List<String>,
        depth: Int = 0,
        maxDepth: Int = 3
    ): Int {
        if (depth > maxDepth) return 0

        var count = 0
        try {
            dir.listFiles()?.forEach { file ->
                when {
                    file.isFile -> {
                        val ext = file.extension.lowercase()
                        if (extensions.contains(ext)) {
                            count++
                        }
                    }
                    file.isDirectory -> {
                        // 跳过隐藏目录和系统目录
                        if (!file.name.startsWith(".") &&
                            file.name != "Android" &&
                            file.canRead()) {
                            count += scanDirectoryForExtensions(file, extensions, depth + 1, maxDepth)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    /**
     * 获取文件的 MIME 类型
     */
    fun getMimeType(filePath: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(filePath)
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        } else {
            null
        }
    }

    /**
     * 判断是否为图片文件
     */
    private fun isImageFile(file: File): Boolean {
        val mimeType = getMimeType(file.absolutePath)
        return mimeType?.startsWith("image/") == true
    }

    /**
     * 判断是否为视频文件
     */
    fun isVideo(file: File): Boolean {
        val mimeType = getMimeType(file.absolutePath)
        return mimeType?.startsWith("video/") == true
    }

    /**
     * 判断是否为视频文件（通过文件路径）
     */
    fun isVideo(filePath: String): Boolean {
        return isVideo(File(filePath))
    }

    /**
     * 判断是否为 PDF 文件
     */
    fun isPdfFile(file: File): Boolean {
        val mimeType = getMimeType(file.absolutePath)
        return mimeType == "application/pdf" || file.extension.lowercase() == "pdf"
    }

    /**
     * 判断是否为 PDF 文件（通过文件路径）
     */
    fun isPdfFile(filePath: String): Boolean {
        return isPdfFile(File(filePath))
    }

    /**
     * 根据MIME类型获取文件类型
     */
    fun getFileTypeFromMimeType(mimeType: String?): FileType {
        return when {
            mimeType == null -> FileType.OTHER
            mimeType.startsWith("image/") -> FileType.IMAGE
            mimeType.startsWith("video/") -> FileType.VIDEO
            mimeType.startsWith("audio/") -> FileType.AUDIO
            mimeType == "application/vnd.android.package-archive" -> FileType.APK
            mimeType == "application/zip" ||
            mimeType == "application/x-rar-compressed" ||
            mimeType == "application/x-7z-compressed" -> FileType.ZIP
            mimeType == "application/pdf" ||
            mimeType == "application/msword" ||
            mimeType.contains("officedocument") ||
            mimeType == "text/plain" -> FileType.DOCUMENT
            else -> FileType.OTHER
        }
    }

    /**
     * 获取视频时长（毫秒）
     */
    fun getVideoDuration(context: Context, filePath: String): Long? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            duration
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 使用系统默认应用打开文件
     * @param context 上下文
     * @param file 要打开的文件
     * @param mimeType 文件的 MIME 类型
     * @return 是否成功打开
     */
    fun openFileWithIntent(context: Context, file: File, mimeType: String?): Boolean {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            
            // Android 7.0+ 使用 FileProvider
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                android.net.Uri.fromFile(file)
            }
            
            intent.setDataAndType(uri, mimeType)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)  // 授予读取权限
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 打开文件（自动判断文件类型）
     * @param context 上下文
     * @param file 要打开的文件
     */
    fun openFile(context: Context, file: File) {
        if (!file.exists()) {
            return
        }
        
        val mimeType = getMimeType(file.absolutePath)
        openFileWithIntent(context, file, mimeType)
    }

    fun openFile(filePath: String?, contentType: String?) {
        if (!File(filePath?:return).exists()) {
            ToastUtils.showShort("文件不存在")
            return
        }

        val context = ActivityUtils.getTopActivity()
        if (ImageUtils.isImage(filePath)) {
            ImageActivity.start(context, filePath)
            return
        }

        if (FileManagerUtils.isVideo(filePath)) {
            // 使用视频播放器打开
            val fileName = File(filePath).name
            VideoPlayerActivity.start(context, filePath, fileName)
            return
        }

        if (FileManagerUtils.isPdfFile(File(filePath))) {
            // PDF 文件使用 WebActivity 打开（GeckoView 支持查看 PDF）
            val intent = Intent(context, WebActivity::class.java).apply {
                putExtra(WebActivity.EXTRA_URL, filePath)
                putExtra(WebActivity.EXTRA_IS_LOCAL_FILE, true)
                putExtra(WebActivity.EXTRA_MIME_TYPE, contentType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        if (filePath.endsWith("apk")) {
            // APK 文件使用系统默认应用打开
            ApkUtils.installApk(context, filePath) { success, message ->
                if (success) {
                    LogUtils.d("install success")
                } else {
                    ToastUtils.showShort(message)
                    LogUtils.d("install failed: $message")
                }
            }
            return
        }
        openFileWithIntent(context, File(filePath), contentType)
    }
}
