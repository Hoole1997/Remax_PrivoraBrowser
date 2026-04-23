package com.android.common.bill.ads.tracker

import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.util.PositionGet
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import net.corekit.core.report.ReportDataManager
import java.util.UUID

/**
 * 广告事件类型枚举
 * 统一定义所有广告事件名称，确保一致性
 */
enum class AdEventType(val eventName: String) {
    // 加载相关
    START_LOAD("ad_start_load"),
    LOADED("ad_loaded"),
    LOAD_FAIL("ad_load_fail"),
    
    // 展示相关
    POSITION("ad_position"),
    IMPRESSION("ad_impression"),
    SHOW_FAIL("ad_show_fail"),
    
    // 交互相关
    CLICK("ad_click"),
    CLOSE("ad_close"),
    
    // 缓存相关
    NO_CACHE("ad_no_cache"),
    TIMEOUT_CACHE("ad_timeout_cache"),
    
    // 激励广告专用
    REWARD_EARNED("ad_reward_earned")
}

/**
 * 广告事件上报器
 * 统一管理所有广告事件的上报，使用 Builder 模式构建事件数据
 * 
 * 使用示例:
 * ```
 * AdEventReporter.builder(AdEventType.IMPRESSION)
 *     .adType(AdType.APP_OPEN)
 *     .platform(AdPlatform.ADMOB)
 *     .adUnitId("ca-app-pub-xxx")
 *     .adUniqueId(uuid)
 *     .adSource("AdMob")
 *     .value(0.001)
 *     .currency("USD")
 *     .report()
 * ```
 */
object AdEventReporter {

    private const val TAG = "AdEventReporter"
    private const val MAX_REASON_LENGTH = 64
    private const val MAX_TERMINAL_SESSION_CACHE_SIZE = 2048
    private const val MAX_TERMINAL_REQUEST_CACHE_SIZE = 4096
    private const val MAX_SESSION_POSITION_CACHE_SIZE = 2048
    private const val MAX_REQUEST_POSITION_CACHE_SIZE = 4096
    private val terminalSessionIds = LinkedHashSet<String>()
    private val terminalRequestIds = LinkedHashSet<String>()
    private val sessionTerminalListeners = LinkedHashMap<String, (String) -> Unit>()
    private val sessionPositions = LinkedHashMap<String, String>()
    private val requestPositions = LinkedHashMap<String, String>()
    private val loadPositionThreadLocal = ThreadLocal<String?>()

    /**
     * 创建事件构建器
     */
    fun builder(eventType: AdEventType): EventBuilder {
        return EventBuilder(eventType)
    }

    // ==================== 便捷方法（常用场景）====================

    /**
     * 上报广告位触发（ad_position）
     * @return 生成的 session_id
     */
    fun reportPosition(adType: AdType, number: Int, position: String? = null): String {
        val sessionId = UUID.randomUUID().toString()
        builder(AdEventType.POSITION)
            .adType(adType)
            .number(number)
            .sessionId(sessionId)
            .apply { position?.takeIf { it.isNotBlank() }?.let { position(it) } }
            .report()
        return sessionId
    }

    /**
     * 上报开始加载（ad_start_load）
     * @return 生成的 request_id
     */
    fun reportStartLoad(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int,
        position: String? = null
    ): String {
        val requestId = UUID.randomUUID().toString()
        val resolvedPosition = position
            ?.takeIf { it.isNotBlank() }
            ?: currentLoadPosition()
        builder(AdEventType.START_LOAD)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .requestId(requestId)
            .apply { resolvedPosition?.let { position(it) } }
            .report()
        return requestId
    }

    /**
     * 上报加载成功（ad_loaded）
     */
    fun reportLoaded(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int,
        adSource: String,
        passTime: Int,
        requestId: String = ""
    ) {
        if (!markRequestTerminalOnce(requestId, AdEventType.LOADED)) return
        builder(AdEventType.LOADED)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .adSource(adSource)
            .passTime(passTime)
            .requestId(requestId)
            .report()
    }

    /**
     * 上报加载失败（ad_load_fail）
     */
    fun reportLoadFail(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int,
        adSource: String,
        passTime: Int,
        reason: String,
        requestId: String = ""
    ) {
        if (!markRequestTerminalOnce(requestId, AdEventType.LOAD_FAIL)) return
        builder(AdEventType.LOAD_FAIL)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .adSource(adSource)
            .passTime(passTime)
            .reason(reason)
            .requestId(requestId)
            .report()
    }

    /**
     * 上报展示成功（ad_impression）
     */
    fun reportImpression(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        adUniqueId: String,
        number: Int,
        adSource: String,
        value: Double,
        currency: String,
        sessionId: String = "",
        isPreload: Boolean = false
    ) {
        if (!markSessionTerminal(sessionId, AdEventType.IMPRESSION)) return
        builder(AdEventType.IMPRESSION)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .adUniqueId(adUniqueId)
            .number(number)
            .adSource(adSource)
            .value(value)
            .currency(currency)
            .sessionId(sessionId)
            .isPreload(isPreload)
            .report()
    }

    /**
     * 上报展示失败（ad_show_fail）
     */
    fun reportShowFail(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int,
        reason: String,
        adSource: String? = null,
        sessionId: String = "",
        isPreload: Boolean = false
    ) {
        if (!markSessionTerminal(sessionId, AdEventType.SHOW_FAIL)) return
        builder(AdEventType.SHOW_FAIL)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .reason(reason)
            .sessionId(sessionId)
            .isPreload(isPreload)
            .apply { adSource?.let { adSource(it) } }
            .report()
    }

    /**
     * 上报展示失败（ad_show_fail）- 无平台信息版本
     * 用于 checkCanShow 提前返回时（全局开关关闭、总控限制等）
     */
    fun reportShowFailNoAd(
        adType: AdType,
        reason: String,
        sessionId: String = "",
        isPreload: Boolean = false
    ) {
        if (!markSessionTerminal(sessionId, AdEventType.SHOW_FAIL)) return
        builder(AdEventType.SHOW_FAIL)
            .adType(adType)
            .reason(reason)
            .sessionId(sessionId)
            .isPreload(isPreload)
            .report()
    }

    /**
     * 判断某个 session 是否已产生终态（impression/show_fail）
     */
    fun isSessionTerminal(sessionId: String): Boolean {
        if (sessionId.isBlank()) return false
        synchronized(terminalSessionIds) {
            return terminalSessionIds.contains(sessionId)
        }
    }

    /**
     * 注册 session 终态监听器（仅会在该 session 首次进入终态时触发）
     */
    fun registerSessionTerminalListener(key: String, listener: (String) -> Unit) {
        if (key.isBlank()) return
        synchronized(sessionTerminalListeners) {
            sessionTerminalListeners[key] = listener
        }
    }

    /**
     * 反注册 session 终态监听器
     */
    fun unregisterSessionTerminalListener(key: String) {
        if (key.isBlank()) return
        synchronized(sessionTerminalListeners) {
            sessionTerminalListeners.remove(key)
        }
    }

    private fun markSessionTerminal(sessionId: String, eventType: AdEventType): Boolean {
        if (sessionId.isBlank()) return true
        val isNewTerminal: Boolean
        synchronized(terminalSessionIds) {
            isNewTerminal = terminalSessionIds.add(sessionId)
            if (isNewTerminal && terminalSessionIds.size > MAX_TERMINAL_SESSION_CACHE_SIZE) {
                val oldest = terminalSessionIds.iterator()
                if (oldest.hasNext()) {
                    oldest.next()
                    oldest.remove()
                }
            }
        }
        if (!isNewTerminal) {
            AdLogger.w(
                "忽略重复展示终态事件: event=%s, session_id=%s",
                eventType.eventName,
                sessionId
            )
            return false
        }
        if (isNewTerminal) {
            notifySessionTerminalListeners(sessionId)
        }
        return true
    }

    private fun notifySessionTerminalListeners(sessionId: String) {
        val listeners = synchronized(sessionTerminalListeners) {
            sessionTerminalListeners.values.toList()
        }
        listeners.forEach { listener ->
            try {
                listener.invoke(sessionId)
            } catch (e: Exception) {
                AdLogger.e("session terminal listener 执行异常: session_id=$sessionId", e)
            }
        }
    }

    private fun cacheSessionPosition(sessionId: String, position: String) {
        if (sessionId.isBlank() || position.isBlank()) return
        synchronized(sessionPositions) {
            sessionPositions[sessionId] = position
            while (sessionPositions.size > MAX_SESSION_POSITION_CACHE_SIZE) {
                val oldest = sessionPositions.entries.iterator()
                if (oldest.hasNext()) {
                    oldest.next()
                    oldest.remove()
                }
            }
        }
    }

    private fun findSessionPosition(sessionId: String): String? {
        if (sessionId.isBlank()) return null
        synchronized(sessionPositions) {
            return sessionPositions[sessionId]
        }
    }

    fun findTrackedPositionBySessionId(sessionId: String): String? {
        return findSessionPosition(sessionId)
    }

    private fun cacheRequestPosition(requestId: String, position: String) {
        if (requestId.isBlank() || position.isBlank()) return
        synchronized(requestPositions) {
            requestPositions[requestId] = position
            while (requestPositions.size > MAX_REQUEST_POSITION_CACHE_SIZE) {
                val oldest = requestPositions.entries.iterator()
                if (oldest.hasNext()) {
                    oldest.next()
                    oldest.remove()
                }
            }
        }
    }

    private fun findRequestPosition(requestId: String): String? {
        if (requestId.isBlank()) return null
        synchronized(requestPositions) {
            return requestPositions[requestId]
        }
    }

    private fun currentLoadPosition(): String? {
        return loadPositionThreadLocal.get()?.takeIf { it.isNotBlank() }
    }

    suspend fun <T> withLoadPosition(
        position: String?,
        block: suspend () -> T
    ): T {
        val normalizedPosition = position?.takeIf { it.isNotBlank() } ?: return block()
        return withContext(loadPositionThreadLocal.asContextElement(normalizedPosition)) {
            block()
        }
    }

    private fun markRequestTerminalOnce(requestId: String, eventType: AdEventType): Boolean {
        if (requestId.isBlank()) return true
        synchronized(terminalRequestIds) {
            if (!terminalRequestIds.add(requestId)) {
                AdLogger.w(
                    "忽略重复加载终态事件: event=%s, request_id=%s",
                    eventType.eventName,
                    requestId
                )
                return false
            }
            if (terminalRequestIds.size > MAX_TERMINAL_REQUEST_CACHE_SIZE) {
                val oldest = terminalRequestIds.iterator()
                if (oldest.hasNext()) {
                    oldest.next()
                    oldest.remove()
                }
            }
            return true
        }
    }

    /**
     * 上报无缓存（ad_no_cache）
     */
    fun reportNoCache(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        position: String? = null
    ) {
        builder(AdEventType.NO_CACHE)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .apply { position?.takeIf { it.isNotBlank() }?.let { position(it) } }
            .report()
    }

    /**
     * 上报缓存过期（ad_timeout_cache）
     */
    fun reportTimeoutCache(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        position: String? = null
    ) {
        builder(AdEventType.TIMEOUT_CACHE)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .apply { position?.takeIf { it.isNotBlank() }?.let { position(it) } }
            .report()
    }

    /**
     * 上报点击（ad_click）
     */
    fun reportClick(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        adUniqueId: String,
        number: Int,
        adSource: String,
        value: Double,
        currency: String,
        sessionId: String = ""
    ) {
        builder(AdEventType.CLICK)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .adUniqueId(adUniqueId)
            .number(number)
            .adSource(adSource)
            .value(value)
            .currency(currency)
            .sessionId(sessionId)
            .report()
    }

    /**
     * 上报关闭（ad_close）
     */
    fun reportClose(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int,
        adSource: String,
        value: Double,
        currency: String,
        sessionId: String = ""
    ) {
        builder(AdEventType.CLOSE)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .adSource(adSource)
            .value(value)
            .currency(currency)
            .sessionId(sessionId)
            .report()
    }

    /**
     * 上报激励获得（ad_reward_earned）
     */
    fun reportRewardEarned(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int,
        adSource: String,
        rewardType: String,
        rewardAmount: Int,
        sessionId: String = ""
    ) {
        builder(AdEventType.REWARD_EARNED)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .adSource(adSource)
            .param("reward_label", rewardType)
            .param("reward_amount", rewardAmount)
            .sessionId(sessionId)
            .report()
    }

    /**
     * 事件构建器
     * 使用 Builder 模式灵活构建事件参数
     */
    class EventBuilder(private val eventType: AdEventType) {
        private val params = mutableMapOf<String, Any>()
        private var adType: AdType? = null
        private var platform: AdPlatform? = null

        fun adType(adType: AdType): EventBuilder {
            this.adType = adType
            return this
        }

        fun platform(platform: AdPlatform): EventBuilder {
            this.platform = platform
            return this
        }

        fun adUnitId(adUnitId: String): EventBuilder {
            params["ad_unit_name"] = adUnitId
            return this
        }

        fun adUniqueId(adUniqueId: String): EventBuilder {
            params["ad_unique_id"] = adUniqueId
            return this
        }

        fun number(number: Int): EventBuilder {
            params["number"] = number
            return this
        }

        fun adSource(adSource: String): EventBuilder {
            params["ad_source"] = adSource
            return this
        }

        fun passTime(passTime: Int): EventBuilder {
            params["pass_time"] = passTime
            return this
        }

        fun reason(reason: String): EventBuilder {
            params["reason"] = normalizeReason(reason)
            return this
        }

        fun sessionId(sessionId: String): EventBuilder {
            if (sessionId.isNotEmpty()) {
                params["session_id"] = sessionId
            }
            return this
        }

        fun requestId(requestId: String): EventBuilder {
            if (requestId.isNotEmpty()) {
                params["request_id"] = requestId
            }
            return this
        }

        fun isPreload(isPreload: Boolean): EventBuilder {
            params["is_preload"] = isPreload
            return this
        }

        fun value(value: Double): EventBuilder {
            params["value"] = value
            return this
        }

        fun currency(currency: String): EventBuilder {
            params["currency"] = currency
            return this
        }

        fun position(position: String): EventBuilder {
            params["position"] = position
            return this
        }

        /**
         * 添加自定义参数
         */
        fun param(key: String, value: Any): EventBuilder {
            params[key] = value
            return this
        }

        /**
         * 执行上报
         */
        fun report() {
            val data = mutableMapOf<String, Any>()
            val sanitizedParams = params.toMutableMap().apply {
                val rawPosition = this["position"] as? String
                if (rawPosition != null && rawPosition.isBlank()) {
                    remove("position")
                }
            }
            val sessionId = (sanitizedParams["session_id"] as? String).orEmpty()
            val requestId = (sanitizedParams["request_id"] as? String).orEmpty()
            val explicitPosition = (sanitizedParams["position"] as? String)?.takeIf { it.isNotBlank() }
            val finalPosition = explicitPosition
                ?: findSessionPosition(sessionId)
                ?: findRequestPosition(requestId)
                ?: currentLoadPosition()
                ?: PositionGet.get()

            // 添加基础参数
            adType?.let { data["ad_format"] = it.configKey }
            platform?.let { data["ad_platform"] = it.key }
            data["position"] = finalPosition

            // 合并自定义参数
            data.putAll(sanitizedParams)

            explicitPosition?.let { cacheSessionPosition(sessionId, it) }
            explicitPosition?.let { cacheRequestPosition(requestId, it) }

            // 上报事件
            val eventName = eventType.eventName
            AdLogger.d("$TAG 上报事件: $eventName, 参数: $data")

            // ad_impression 需要单独通过 ThinkingData 上报
            if (eventType == AdEventType.IMPRESSION) {
                ReportDataManager.reportDataByName("ThinkingData", eventName, data)
            } else {
                ReportDataManager.reportData(eventName, data)
            }
        }

        private fun normalizeReason(rawReason: String): String {
            val trimmed = rawReason.trim()
            if (trimmed.isEmpty()) return "unknown"

            val firstLine = trimmed.lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()
            if (firstLine.isEmpty()) return "unknown"

            normalizeStructuredReason(firstLine)?.let { normalized ->
                return normalized.take(MAX_REASON_LENGTH)
            }

            // 已经是短码/短文本则直接使用，避免改动已有埋点语义
            if (firstLine.length <= MAX_REASON_LENGTH && !trimmed.contains('\n')) {
                return firstLine
            }

            val lower = trimmed.lowercase()
            val mapped = when {
                lower.contains("job was cancelled") -> AdFailReason.AD_SHOW_INTERRUPTED
                lower.contains("activity destroyed") || trimmed.contains("Activity已销毁") -> AdFailReason.ACTIVITY_DESTROYED
                trimmed.contains("展示被取消") -> AdFailReason.AD_SHOW_INTERRUPTED
                trimmed.contains("被新的展示请求覆盖") -> AdFailReason.REPLACED_BY_NEW_SHOW_REQUEST
                lower.contains("in the background are no longer supported") -> AdFailReason.APP_IN_BACKGROUND_NOT_SUPPORTED
                lower.contains("ad unit") && lower.contains("invalid or disabled") -> "invalid_or_disabled_ad_unit"
                lower.contains("ad unit") && lower.contains("invalid") -> "invalid_ad_unit"
                lower.contains("cannot load ads until") -> "ad_unit_not_ready"
                lower.contains("no eligible ads") -> "no_fill"
                lower.contains("no fill") -> "no_fill"
                lower.contains("return ad is empty") -> "no_fill"
                lower.contains("timed out") || lower.contains("timeout") -> "timeout"
                lower.contains("activity destroyed") -> "activity_destroyed"
                lower.contains("not ready") -> "ad_not_ready"
                lower.contains("network error") -> "network_error"
                lower.contains("show exception") -> "show_exception"
                lower.contains("load exception") -> "load_exception"
                else -> {
                    firstLine.replace(Regex("\\s+"), " ")
                }
            }

            return mapped
                .trim()
                .ifEmpty { "unknown" }
                .take(MAX_REASON_LENGTH)
        }

        private fun normalizeStructuredReason(reason: String): String? {
            val parts = reason.split("|", limit = 2)
            val head = normalizeReasonHead(parts.firstOrNull().orEmpty()) ?: return null
            val detail = parts.getOrNull(1)?.trim().orEmpty()
            return if (detail.isEmpty()) head else "$head|$detail"
        }

        private fun normalizeReasonHead(reason: String): String? {
            val trimmed = reason.trim()
            if (trimmed.isEmpty()) return "unknown"

            val lower = trimmed.lowercase()
            return when {
                lower == "job was cancelled" -> AdFailReason.AD_SHOW_INTERRUPTED
                lower == "activity destroyed" || trimmed == "Activity已销毁" -> AdFailReason.ACTIVITY_DESTROYED
                trimmed == "展示被取消" -> AdFailReason.AD_SHOW_INTERRUPTED
                trimmed == "被新的展示请求覆盖" -> AdFailReason.REPLACED_BY_NEW_SHOW_REQUEST
                lower == "fullscreen ads that show when your app is in the background are no longer supported." ->
                    AdFailReason.APP_IN_BACKGROUND_NOT_SUPPORTED
                else -> null
            }
        }
    }
}
