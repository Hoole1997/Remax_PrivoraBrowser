package com.example.browser.ui.photoclean.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object PhotoHashCalculator {

    /**
     * 计算文件的 MD5 哈希值
     */
    fun computeMd5(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 计算图片的差异哈希（dHash），用于相似图片比较
     * 将图片缩放到 9x8，转灰度，比较相邻像素生成 64 位哈希
     */
    fun computeDHash(file: File): Long {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = calculateSampleSize(file, 72, 64)
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return 0L
            val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
            if (scaled != bitmap) bitmap.recycle()

            var hash = 0L
            for (row in 0 until 8) {
                for (col in 0 until 8) {
                    val leftPixel = scaled.getPixel(col, row)
                    val rightPixel = scaled.getPixel(col + 1, row)
                    val leftGray = toGrayscale(leftPixel)
                    val rightGray = toGrayscale(rightPixel)
                    if (leftGray > rightGray) {
                        hash = hash or (1L shl (row * 8 + col))
                    }
                }
            }
            scaled.recycle()
            hash
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 计算两个 dHash 之间的汉明距离
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        var xor = hash1 xor hash2
        var distance = 0
        while (xor != 0L) {
            distance++
            xor = xor and (xor - 1)
        }
        return distance
    }

    private fun toGrayscale(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    private fun calculateSampleSize(file: File, reqWidth: Int, reqHeight: Int): Int {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val (width, height) = options.outWidth to options.outHeight
        var sampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }
}
