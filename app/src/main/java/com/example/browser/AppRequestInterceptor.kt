package com.example.browser

import android.content.Context
import android.util.Log
import mozilla.components.browser.errorpages.ErrorPages
import mozilla.components.browser.errorpages.ErrorType
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.RequestInterceptor

/**
 * 应用请求拦截器
 * 负责拦截和处理特殊的 URL 请求，包括：
 * 1. 第三方应用 scheme 协议（如 weixin://、alipay:// 等）
 * 2. 特殊的 about: 页面
 * 3. 错误页面处理
 */
class AppRequestInterceptor(
    private val context: Context
) : RequestInterceptor {

    /**
     * 拦截 URL 加载请求
     * 在 GeckoView 加载 URL 之前被调用，可以决定是否拦截该请求
     *
     * @param engineSession 引擎会话
     * @param uri 要加载的 URL
     * @param lastUri 上一个 URL
     * @param hasUserGesture 是否由用户手势触发（点击链接）
     * @param isSameDomain 是否同域名
     * @param isRedirect 是否是重定向
     * @param isDirectNavigation 是否是直接导航（用户输入 URL）
     * @param isSubframeRequest 是否是子框架请求（iframe）
     * @return 拦截响应，null 表示不拦截，继续正常加载
     */
    override fun onLoadRequest(
        engineSession: EngineSession,
        uri: String,
        lastUri: String?,
        hasUserGesture: Boolean,
        isSameDomain: Boolean,
        isRedirect: Boolean,
        isDirectNavigation: Boolean,
        isSubframeRequest: Boolean
    ): RequestInterceptor.InterceptionResponse? {
        // 记录拦截的 URL，方便调试
        Log.d(TAG, "onLoadRequest: uri=$uri, hasUserGesture=$hasUserGesture, isDirectNavigation=$isDirectNavigation")
        
        // 使用 AppLinksInterceptor 处理第三方应用链接
        // 这会自动识别 scheme 协议（如 weixin://、market://、intent:// 等）
        // 并尝试启动对应的第三方应用
        val response = context.components.appLinksInterceptor.onLoadRequest(
            engineSession = engineSession,
            uri = uri,
            lastUri = lastUri,
            hasUserGesture = hasUserGesture,
            isSameDomain = isSameDomain,
            isRedirect = isRedirect,
            isDirectNavigation = isDirectNavigation,
            isSubframeRequest = isSubframeRequest
        )
        
        if (response != null) {
            Log.d(TAG, "AppLinksInterceptor intercepted: $uri -> ${response.javaClass.simpleName}")
        }
        
        return response
    }
    
    companion object {
        private const val TAG = "AppRequestInterceptor"
    }

    /**
     * 处理错误请求
     * 当页面加载失败时被调用，返回自定义的错误页面
     *
     * @param session 引擎会话
     * @param errorType 错误类型
     * @param uri 出错的 URL
     * @return 错误响应，包含错误页面的 HTML
     */
    override fun onErrorRequest(
        session: EngineSession,
        errorType: ErrorType,
        uri: String?
    ): RequestInterceptor.ErrorResponse {
        // 使用 Mozilla Components 提供的标准错误页面
        val errorPage = ErrorPages.createUrlEncodedErrorPage(
            context = context,
            errorType = errorType,
            uri = uri
        )
        return RequestInterceptor.ErrorResponse(errorPage)
    }

    /**
     * 是否拦截应用发起的请求
     * 返回 true 表示也会拦截应用内部发起的请求（不仅仅是用户点击的链接）
     */
    override fun interceptsAppInitiatedRequests() = true
}
