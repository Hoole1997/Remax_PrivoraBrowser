package com.browser.shortvideo.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.GuardedBy
import com.browser.shortvideo.R
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

private class YouTubePlayerImpl(
    private val webView: WebView
) : YouTubePlayer {
    private val mainThread: Handler = Handler(Looper.getMainLooper())

    private val lock = Any()
    @GuardedBy("lock")
    private val listeners = mutableSetOf<YouTubePlayerListener>()

    @Volatile
    private var isReleased = false

    override fun loadVideo(videoId: String, startSeconds: Float) = webView.invoke("loadVideo", videoId, startSeconds)
    override fun cueVideo(videoId: String, startSeconds: Float) = webView.invoke("cueVideo", videoId, startSeconds)
    override fun play() = webView.invoke("playVideo")
    override fun pause() = webView.invoke("pauseVideo")
    override fun mute() = webView.invoke("mute")
    override fun unMute() = webView.invoke("unMute")
    override fun setVolume(volumePercent: Int) {
        require(volumePercent in 0..100) { "Volume must be between 0 and 100" }
        webView.invoke("setVolume", volumePercent)
    }
    override fun seekTo(time: Float) = webView.invoke("seekTo", time)
    override fun setPlaybackRate(playbackRate: PlayerConstants.PlaybackRate) = webView.invoke("setPlaybackRate", playbackRate.toFloat())
    override fun addListener(listener: YouTubePlayerListener): Boolean {
        if (isReleased) return false
        return synchronized(lock) {
            if (isReleased) false else listeners.add(listener)
        }
    }
    override fun removeListener(listener: YouTubePlayerListener) = synchronized(lock) { listeners.remove(listener) }

    fun getListeners(): Collection<YouTubePlayerListener> {
        if (isReleased) return emptyList()
        return synchronized(lock) { listeners.toList() }
    }

    fun release() {
        isReleased = true
        synchronized(lock) { listeners.clear() }
        mainThread.removeCallbacksAndMessages(null)
    }

    private fun WebView.invoke(function: String, vararg args: Any) {
        if (isReleased) return

        val stringArgs = args.map {
            if (it is String) {
                "'$it'"
            } else {
                it.toString()
            }
        }
        val command = Runnable {
            if (!isReleased) {
                loadUrl("javascript:$function(${stringArgs.joinToString(",")})")
            }
        }

        // Adapter 和 JS Bridge 的调用通常已经在主线程，直接执行可减少低端机命令重排。
        if (Looper.myLooper() == Looper.getMainLooper()) {
            command.run()
        } else {
            mainThread.post(command)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
class WebViewYouTubePlayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr), YouTubePlayerBridge.YouTubePlayerBridgeCallbacks {

    private val _youTubePlayer = YouTubePlayerImpl(this)
    val youtubePlayer: YouTubePlayer get() = _youTubePlayer

    private var youTubePlayerInitListener: ((YouTubePlayer) -> Unit)? = null
    private val youTubePlayerBridge = YouTubePlayerBridge(this)
    private var isReleased = false

    internal var isBackgroundPlaybackEnabled = false

    init {
        setupWebView()
    }

    private fun setupWebView() {
        settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            domStorageEnabled = true
            // 允许跨域访问（用于访问 YouTube iframe 内容）
            allowFileAccess = true
            allowContentAccess = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
        }

        addJavascriptInterface(youTubePlayerBridge, "YouTubePlayerBridge")

        webChromeClient = object : WebChromeClient() {
            override fun getDefaultVideoPoster(): Bitmap? {
                val result = super.getDefaultVideoPoster()
                return result ?: Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
            }
        }

        setBackgroundColor(0xFF000000.toInt())
    }

    fun initialize(initListener: (YouTubePlayer) -> Unit, playerOptions: IFramePlayerOptions? = null, videoId: String? = null) {
        youTubePlayerInitListener = initListener
        loadPlayer(playerOptions ?: IFramePlayerOptions.getDefault(context), videoId)
    }

    private fun loadPlayer(playerOptions: IFramePlayerOptions, videoId: String?) {
        val playerVarsJson = playerOptions.toString()
        android.util.Log.d("WebViewYouTubePlayer", "playerVars: $playerVarsJson")
        
        val htmlPage = readHTMLFromUTF8File(resources.openRawResource(R.raw.youtube_player))
            .replace("<<injectedVideoId>>", if (videoId != null) { "'$videoId'" } else { "undefined" })
            .replace("<<injectedPlayerVars>>", playerVarsJson)

        loadDataWithBaseURL(playerOptions.getOrigin(), htmlPage, "text/html", "utf-8", null)
    }

    override val listeners: Collection<YouTubePlayerListener> get() = _youTubePlayer.getListeners()
    override fun getInstance(): YouTubePlayer = _youTubePlayer
    
    // onYouTubeIFrameAPIReady 只表示 IFrame API 加载完成，播放器还没准备好
    // 不在这里触发回调
    override fun onYouTubeIFrameAPIReady() {
        // 等待 onReady 事件
    }
    
    // 当播放器真正准备好时触发（由 YouTubePlayerBridge.sendReady 调用）
    override fun onPlayerReady() {
        if (!isReleased) {
            youTubePlayerInitListener?.invoke(_youTubePlayer)
        }
    }

    fun addListener(listener: YouTubePlayerListener) = _youTubePlayer.addListener(listener)
    fun removeListener(listener: YouTubePlayerListener) = _youTubePlayer.removeListener(listener)

    override fun destroy() {
        if (isReleased) return

        isReleased = true
        youTubePlayerInitListener = null
        _youTubePlayer.release()
        youTubePlayerBridge.release()
        stopLoading()
        removeJavascriptInterface("YouTubePlayerBridge")
        webChromeClient = null
        removeAllViews()
        super.destroy()
    }

    /**
     * 终止释放。调用前必须先将本 WebView 所在 Adapter 从 RecyclerView/ViewPager 移除。
     */
    fun release() {
        destroy()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (isBackgroundPlaybackEnabled && (visibility == View.GONE || visibility == View.INVISIBLE)) {
            return
        }
        super.onWindowVisibilityChanged(visibility)
    }
}

internal fun readHTMLFromUTF8File(inputStream: InputStream): String {
    inputStream.use { stream ->
        BufferedReader(InputStreamReader(stream, "utf-8")).use { bufferedReader ->
            try {
                return bufferedReader.readLines().joinToString("\n")
            } catch (_: Exception) {
                throw RuntimeException("Can't parse HTML file.")
            }
        }
    }
}
