package com.example.browser.feature

import android.util.Log
import com.example.browser.data.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.lib.state.ext.flow
import org.json.JSONObject

/**
 * 视频检测功能
 * 
 * 通过 WebExtension 监听网页中的视频链接，并打印到日志
 * 后续可以扩展为下载功能
 */
class VideoDetectorFeature(
    private val engine: Engine,
    private val store: BrowserStore
) {
    companion object {
        private const val TAG = "VideoDetector"
        private const val EXTENSION_ID = "video-detector@example.browser"
        private const val EXTENSION_URL = "resource://android/assets/extensions/video_detector/"
        private const val NATIVE_APP_NAME = "video_detector"
    }
    
    private val scope = CoroutineScope(Dispatchers.Main)



    /**
     * 视频检测回调接口
     */
    interface VideoDetectorCallback {
        fun onVideoDetected(videoInfo: VideoInfo)
    }

    private var callback: VideoDetectorCallback? = null
    private var installedExtension: WebExtension? = null

    /**
     * 设置视频检测回调
     */
    fun setCallback(callback: VideoDetectorCallback?) {
        this.callback = callback
    }

    /**
     * 安装并启动视频检测扩展
     */
    fun install() {
        Log.d(TAG, "Installing video detector extension...")
        
        engine.installBuiltInWebExtension(
            id = EXTENSION_ID,
            url = EXTENSION_URL,
            onSuccess = { extension ->
                Log.d(TAG, "Extension installed successfully: ${extension.id}")
                installedExtension = extension
                registerBackgroundMessageHandler(extension)
                subscribeToSessions(extension)
            },
            onError = { throwable ->
                Log.e(TAG, "Failed to install extension", throwable)
                // 如果已安装，尝试获取已有扩展
                tryGetInstalledExtension()
            }
        )
    }

    /**
     * 尝试获取已安装的扩展
     */
    private fun tryGetInstalledExtension() {
        engine.listInstalledWebExtensions(
            onSuccess = { extensions ->
                val extension = extensions.find { it.id == EXTENSION_ID }
                if (extension != null) {
                    Log.d(TAG, "Found existing extension: ${extension.id}")
                    installedExtension = extension
                    registerBackgroundMessageHandler(extension)
                    subscribeToSessions(extension)
                } else {
                    Log.w(TAG, "Extension not found in installed list")
                }
            },
            onError = { throwable ->
                Log.e(TAG, "Failed to list extensions", throwable)
            }
        )
    }

    /**
     * 创建消息处理器
     */
    private fun createMessageHandler(): MessageHandler {
        return object : MessageHandler {
            override fun onMessage(
                message: Any,
                source: EngineSession?
            ): Any? {
                handleMessage(message)
                return null
            }

            override fun onPortConnected(port: Port) {
                Log.d(TAG, "Port connected: ${port.name()}")
            }

            override fun onPortDisconnected(port: Port) {
                Log.d(TAG, "Port disconnected: ${port.name()}")
            }

            override fun onPortMessage(message: Any, port: Port) {
                handleMessage(message)
            }
        }
    }

    /**
     * 注册 background script 消息处理器
     */
    private fun registerBackgroundMessageHandler(extension: WebExtension) {
        extension.registerBackgroundMessageHandler(NATIVE_APP_NAME, createMessageHandler())
        Log.d(TAG, "Background message handler registered for: $NATIVE_APP_NAME")
    }

    /**
     * 监听 store 中的 session 变化，为每个新 session 注册 content script 消息处理器
     */
    private fun subscribeToSessions(extension: WebExtension) {
        // 首先为所有现有的 session 注册
        val currentTabs = store.state.tabs + store.state.customTabs
        Log.d(TAG, "Registering content handlers for ${currentTabs.size} existing tabs")
        currentTabs.forEach { tab ->
            registerContentMessageHandlerIfNeeded(extension, tab)
        }
        
        // 然后监听新的 session
        scope.launch {
            store.flow()
                .mapNotNull { state -> state.tabs + state.customTabs }
                .distinctUntilChangedBy { tabs -> tabs.map { it.engineState.engineSession } }
                .collect { tabs ->
                    tabs.forEach { tab ->
                        registerContentMessageHandlerIfNeeded(extension, tab)
                    }
                }
        }
    }

    /**
     * 为指定 session 注册 content script 消息处理器（如果尚未注册）
     */
    private fun registerContentMessageHandlerIfNeeded(extension: WebExtension, tab: SessionState) {
        val engineSession = tab.engineState.engineSession ?: return
        
        if (extension.hasContentMessageHandler(engineSession, NATIVE_APP_NAME)) {
            return
        }
        
        extension.registerContentMessageHandler(engineSession, NATIVE_APP_NAME, createMessageHandler())
        Log.d(TAG, "Content message handler registered for tab: ${tab.id}")
    }

    /**
     * 处理来自扩展的消息
     */
    private fun handleMessage(message: Any) {
        Log.d(TAG, "Received message: $message (type: ${message.javaClass.name})")
        
        try {
            val json = when (message) {
                is JSONObject -> message
                is String -> JSONObject(message)
                else -> {
                    Log.w(TAG, "Unknown message type: ${message.javaClass.name}, trying toString")
                    JSONObject(message.toString())
                }
            }

            val action = json.optString("action")
            if (action == "video_detected") {
                val data = json.optJSONObject("data") ?: return
                val source = json.optString("source", "unknown")
                
                val videoInfo = VideoInfo(
                    url = data.optString("url"),
                    type = data.optString("type", "video"),
                    mime = data.optString("mime").takeIf { it.isNotEmpty() },
                    suffix = data.optString("suffix").takeIf { it.isNotEmpty() },
                    size = data.optLong("size").takeIf { it > 0 },
                    source = source,
                    tabId = data.optInt("tabId", -1).takeIf { it >= 0 },
                    pageUrl = data.optString("pageUrl").takeIf { it.isNotEmpty() }
                )

                // 打印日志
                logVideoDetected(videoInfo)

                // 回调通知
                callback?.onVideoDetected(videoInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
        }
    }

    /**
     * 打印检测到的视频信息
     */
    private fun logVideoDetected(videoInfo: VideoInfo) {
        val sizeStr = videoInfo.size?.let { formatFileSize(it) } ?: "unknown"
        
        Log.i(TAG, """
            |
            |========== VIDEO DETECTED ==========
            | URL: ${videoInfo.url}
            | Type: ${videoInfo.type}
            | MIME: ${videoInfo.mime ?: "N/A"}
            | Suffix: ${videoInfo.suffix ?: "N/A"}
            | Size: $sizeStr
            | Source: ${videoInfo.source}
            | Tab ID: ${videoInfo.tabId ?: "N/A"}
            | Page URL: ${videoInfo.pageUrl ?: "N/A"}
            |====================================
        """.trimMargin())
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * 卸载扩展
     */
    fun uninstall() {
        installedExtension?.let { extension ->
            engine.uninstallWebExtension(
                ext = extension,
                onSuccess = {
                    Log.d(TAG, "Extension uninstalled successfully")
                    installedExtension = null
                },
                onError = { _, throwable ->
                    Log.e(TAG, "Failed to uninstall extension", throwable)
                }
            )
        }
    }
}
