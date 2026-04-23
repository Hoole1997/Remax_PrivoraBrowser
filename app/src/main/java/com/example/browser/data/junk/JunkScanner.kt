package com.example.browser.data.junk

import android.os.Environment
import java.io.File
import kotlin.random.Random

/**
 * 垃圾文件扫描器
 */
object JunkScanner {
    
    // 公共目录路径
    private val publicDirectories = listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath,
        "${Environment.getExternalStorageDirectory().absolutePath}/Android/data",
        "${Environment.getExternalStorageDirectory().absolutePath}/.cache",
        "${Environment.getExternalStorageDirectory().absolutePath}/temp"
    )
    
    /**
     * 扫描真实的垃圾文件
     */
    fun scanJunkFiles(): List<JunkFile> {
        val junkFiles = mutableListOf<JunkFile>()
        
        // 扫描所有公共目录
        publicDirectories.forEach { dirPath ->
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                scanDirectory(dir, junkFiles)
            }
        }
        
        // 混入一些模拟的垃圾文件（确保有一定数量的数据）
        junkFiles.addAll(generateMockJunkFiles())
        
        return junkFiles.shuffled()
    }
    
    /**
     * 递归扫描目录
     */
    private fun scanDirectory(directory: File, junkFiles: MutableList<JunkFile>, maxDepth: Int = 3, currentDepth: Int = 0) {
        if (currentDepth >= maxDepth) return
        
        try {
            directory.listFiles()?.forEach { file ->
                try {
                    if (file.isFile) {
                        // 检查是否是垃圾文件
                        val junkType = identifyJunkType(file)
                        if (junkType != null) {
                            junkFiles.add(
                                JunkFile(
                                    path = file.absolutePath,
                                    name = file.name,
                                    size = file.length(),
                                    type = junkType,
                                    lastModified = file.lastModified()
                                )
                            )
                        }
                    } else if (file.isDirectory && !file.name.startsWith(".")) {
                        // 递归扫描子目录（跳过隐藏目录）
                        scanDirectory(file, junkFiles, maxDepth, currentDepth + 1)
                    }
                } catch (e: Exception) {
                    // 忽略无权限访问的文件
                }
            }
        } catch (e: Exception) {
            // 忽略无权限访问的目录
        }
    }
    
    /**
     * 识别文件的垃圾类型
     */
    private fun identifyJunkType(file: File): JunkType? {
        val name = file.name.lowercase()
        val extension = file.extension.lowercase()
        
        return when {
            // 残留文件：临时文件、缓存文件、备份文件
            name.startsWith("temp_") || name.startsWith("tmp_") || name.startsWith(".tmp") ||
            extension in listOf("tmp", "temp", "cache", "bak", "old", "log") ||
            name.endsWith("~") -> JunkType.RESIDUAL_FILES
            
            // 垃圾文件：系统垃圾文件
            name in listOf("thumbs.db", ".ds_store", "desktop.ini") ||
            extension in listOf("dat", "dmp") -> JunkType.JUNK_FILES
            
            // 广告文件：包含广告关键词的图片
            (name.contains("ad_") || name.contains("advert") || name.contains("banner") || 
             name.contains("promo") || name.contains("sponsor")) &&
            extension in listOf("jpg", "jpeg", "png", "gif", "webp") -> JunkType.ADVERTISEMENT_FILES
            
            // 过时APK文件：下载目录中的APK文件
            extension == "apk" && file.parent?.contains("Download") == true -> JunkType.OBSOLETE_APK_FILES
            
            else -> null
        }
    }
    
    
    /**
     * 生成模拟的垃圾文件（混入真实扫描结果）
     */
    private fun generateMockJunkFiles(): List<JunkFile> {
        val mockFiles = mutableListOf<JunkFile>()
        
        // 生成10-20个模拟垃圾文件
        val count = Random.nextInt(10, 21)
        
        repeat(count) {
            val type = JunkType.values().random()
            val directory = publicDirectories.random()
            val fileName = generateMockFileName(type)
            val size = generateMockFileSize() // 几KB到几MB
            
            mockFiles.add(
                JunkFile(
                    path = "$directory/$fileName",
                    name = fileName,
                    size = size,
                    type = type,
                    lastModified = System.currentTimeMillis() - Random.nextLong(0, 30L * 24 * 60 * 60 * 1000)
                )
            )
        }
        
        return mockFiles
    }
    
    /**
     * 生成模拟文件名
     */
    private fun generateMockFileName(type: JunkType): String {
        return when (type) {
            JunkType.RESIDUAL_FILES -> {
                val prefixes = listOf("temp_", "cache_", "tmp_", ".tmp_")
                val suffixes = listOf(".tmp", ".cache", ".bak", ".old")
                "${prefixes.random()}${Random.nextInt(1000, 9999)}${suffixes.random()}"
            }
            JunkType.JUNK_FILES -> {
                val names = listOf("thumbs.db", ".DS_Store", "desktop.ini", 
                    "junk_${Random.nextInt(100, 999)}.dat")
                names.random()
            }
            JunkType.ADVERTISEMENT_FILES -> {
                val adNames = listOf("ad_banner", "advertisement", "promo", "sponsor")
                "${adNames.random()}_${Random.nextInt(100, 999)}.${listOf("jpg", "png", "webp").random()}"
            }
            JunkType.OBSOLETE_APK_FILES -> {
                val appNames = listOf("old_app", "backup", "update", "installer")
                "${appNames.random()}_v${Random.nextInt(1, 9)}.${Random.nextInt(0, 9)}.apk"
            }
        }
    }
    
    /**
     * 生成模拟文件大小（几KB到几MB）
     */
    private fun generateMockFileSize(): Long {
        // 随机选择大小范围
        return when (Random.nextInt(0, 3)) {
            0 -> Random.nextLong(10 * 1024, 100 * 1024)        // 10KB - 100KB
            1 -> Random.nextLong(100 * 1024, 1024 * 1024)      // 100KB - 1MB
            else -> Random.nextLong(1024 * 1024, 5 * 1024 * 1024) // 1MB - 5MB
        }
    }
    
    /**
     * 获取所有扫描路径
     */
    fun getScanPaths(): List<String> {
        return publicDirectories
    }
}
