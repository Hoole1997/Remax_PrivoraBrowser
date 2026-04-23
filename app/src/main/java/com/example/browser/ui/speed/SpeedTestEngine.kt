package com.example.browser.ui.speed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.LinkedList
import java.util.concurrent.TimeUnit

/**
 * 网速测试引擎
 * 参照 Speedtest 逻辑：滑动窗口实时速度 + 多轮渐进式测试
 */
class SpeedTestEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 获取 ISP 信息
     */
    suspend fun getIspInfo(): IspInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://ipinfo.io/json")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext IspInfo()
            val json = JSONObject(body)
            val org = json.optString("org", "")
            val ip = json.optString("ip", "")
            IspInfo(name = org, ip = ip)
        } catch (e: Exception) {
            IspInfo()
        }
    }

    /**
     * 执行 Ping 测试（HTTP HEAD 方式，带连接预热）
     * @return Pair<ping_ms, jitter_ms>
     */
    suspend fun testPing(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val pingTimes = mutableListOf<Long>()
        val pingUrl = "https://speed.cloudflare.com/__down?bytes=0"

        // 预热：建立 TCP+TLS 连接（不计入统计）
        try {
            val warmup = Request.Builder().url(pingUrl).head().build()
            client.newCall(warmup).execute().close()
        } catch (_: Exception) {
        }

        // 6 次采样（快速完成，减少等待）
        repeat(6) {
            try {
                val request = Request.Builder().url(pingUrl).head().build()
                val startTime = System.nanoTime()
                client.newCall(request).execute().close()
                val ms = (System.nanoTime() - startTime) / 1_000_000
                pingTimes.add(ms)
            } catch (_: Exception) {
            }
        }

        if (pingTimes.isEmpty()) return@withContext Pair(0, 0)

        // 去掉最高最低后取平均
        val sorted = pingTimes.sorted()
        val trimmed = if (sorted.size > 3) sorted.subList(1, sorted.size - 1) else sorted
        val avgPing = trimmed.average().toInt()

        // Jitter = 相邻 ping 差值的平均
        val jitter = if (pingTimes.size > 1) {
            pingTimes.zipWithNext { a, b -> kotlin.math.abs(a - b).toDouble() }.average().toInt()
        } else 0

        Pair(avgPing, jitter)
    }

    /**
     * 执行下载测速（Speedtest 逻辑）
     * - 使用滑动窗口（最近 3 秒）计算实时速度
     * - 多轮从小到大的下载，总测试约 10 秒
     * - onProgress 回调报告实时滑动窗口速度
     * - 返回值为测试期间的峰值稳定速度
     */
    suspend fun testDownload(
        onProgress: (Float) -> Unit
    ): Float = withContext(Dispatchers.IO) {
        // 从小到大递增，让速度逐渐爬升
        val downloadUrls = listOf(
            "https://speed.cloudflare.com/__down?bytes=1000000",
            "https://speed.cloudflare.com/__down?bytes=5000000",
            "https://speed.cloudflare.com/__down?bytes=10000000",
            "https://speed.cloudflare.com/__down?bytes=25000000",
            "https://speed.cloudflare.com/__down?bytes=25000000"
        )

        val windowMs = 3000L // 3 秒滑动窗口
        val samples = LinkedList<Pair<Long, Long>>() // (timestamp_ms, cumulative_bytes)
        var totalBytes = 0L
        val testStartTime = System.currentTimeMillis()
        val maxTestDuration = 15_000L // 最长 15 秒
        var peakSpeed = 0f
        val recentSpeeds = mutableListOf<Float>()

        for (url in downloadUrls) {
            // 超时保护
            if (System.currentTimeMillis() - testStartTime > maxTestDuration) break

            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val inputStream = response.body?.byteStream() ?: continue
                val buffer = ByteArray(32768) // 32KB buffer 更高效
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    val now = System.currentTimeMillis()

                    samples.add(Pair(now, totalBytes))

                    // 移除窗口外的旧样本
                    while (samples.isNotEmpty() && now - samples.first.first > windowMs) {
                        samples.removeFirst()
                    }

                    // 计算滑动窗口速度
                    if (samples.size >= 2) {
                        val oldest = samples.first
                        val deltaBytes = totalBytes - oldest.second
                        val deltaMs = now - oldest.first
                        if (deltaMs > 200) { // 至少 200ms 才计算，避免抖动
                            val speedMbps = (deltaBytes * 8.0 / 1_000_000.0) / (deltaMs / 1000.0)
                            val speed = speedMbps.toFloat()
                            onProgress(speed)
                            recentSpeeds.add(speed)
                            if (speed > peakSpeed) peakSpeed = speed
                        }
                    }

                    // 超时保护
                    if (now - testStartTime > maxTestDuration) break
                }

                inputStream.close()
                response.close()
            } catch (_: Exception) {
            }
        }

        // 最终速度：取最后 30% 样本的平均值（稳定阶段）
        if (recentSpeeds.isEmpty()) return@withContext 0f
        val stableStart = (recentSpeeds.size * 0.7).toInt()
        val stableSpeeds = recentSpeeds.subList(stableStart, recentSpeeds.size)
        if (stableSpeeds.isEmpty()) return@withContext peakSpeed
        stableSpeeds.average().toFloat()
    }

    /**
     * 执行上传测速（Speedtest 逻辑）
     * - 多轮从小到大上传
     * - 每轮结束后计算并报告累积速度
     * - 返回稳定阶段的平均速度
     */
    suspend fun testUpload(
        onProgress: (Float) -> Unit
    ): Float = withContext(Dispatchers.IO) {
        val uploadSizes = listOf(500_000, 1_000_000, 2_000_000, 4_000_000, 4_000_000)
        val roundSpeeds = mutableListOf<Float>()
        val testStartTime = System.currentTimeMillis()
        val maxTestDuration = 15_000L

        for (size in uploadSizes) {
            if (System.currentTimeMillis() - testStartTime > maxTestDuration) break

            try {
                val data = ByteArray(size)
                val requestBody = data.toRequestBody("application/octet-stream".toMediaType())
                val request = Request.Builder()
                    .url("https://speed.cloudflare.com/__up")
                    .post(requestBody)
                    .build()

                val roundStart = System.currentTimeMillis()
                val response = client.newCall(request).execute()
                response.close()
                val roundMs = System.currentTimeMillis() - roundStart

                if (roundMs > 0) {
                    val speedMbps = (size * 8.0 / 1_000_000.0) / (roundMs / 1000.0)
                    val speed = speedMbps.toFloat()
                    roundSpeeds.add(speed)
                    onProgress(speed)
                }
            } catch (_: Exception) {
            }
        }

        if (roundSpeeds.isEmpty()) return@withContext 0f
        // 取最后几轮的平均值（稳定阶段）
        val stableStart = (roundSpeeds.size * 0.5).toInt().coerceAtLeast(0)
        roundSpeeds.subList(stableStart, roundSpeeds.size).average().toFloat()
    }

    fun cancel() {
        client.dispatcher.cancelAll()
    }
}
