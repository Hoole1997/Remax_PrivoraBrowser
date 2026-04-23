package com.example.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.ext.AdShowExt
import com.browser.common.loadInterstitial
import com.example.player.databinding.ActivityVideoPlayerBinding
import kotlinx.coroutines.launch
import java.util.Formatter
import java.util.Locale

/**
 * 视频播放器 Activity
 * 
 * 功能：
 * - 播放本地和网络视频
 * - 播放/暂停控制
 * - 进度拖动
 * - 倍速播放（0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x）
 * - 横竖屏切换
 * - 自动隐藏控制器
 */
class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_VIDEO_URI = "video_uri"
        private const val EXTRA_VIDEO_TITLE = "video_title"
        private const val CONTROLLER_HIDE_DELAY = 3000L
        
        /**
         * 启动视频播放器
         * 
         * @param context 上下文
         * @param videoUri 视频 URI（支持 file:// 和 http(s)://）
         * @param title 视频标题
         */
        fun start(context: Context, videoUri: String, title: String = "") {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URI, videoUri)
                putExtra(EXTRA_VIDEO_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityVideoPlayerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var isControllerVisible = true
    private var currentSpeed = 1.0f
    private val speedOptions = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var currentSpeedIndex = 2 // 默认 1.0x
    private var mediaPlayer: MediaPlayer? = null
    
    // 更新进度的 Runnable
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                updateProgress()
                handler.postDelayed(this, 500)
            }
        }
    }
    
    // 自动隐藏控制器的 Runnable
    private val hideControllerRunnable = Runnable {
        hideController()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置全屏模式，隐藏状态栏和导航栏（必须在 setContentView 之后）
        setupFullscreen()
        
        setupViews()
        loadVideo()
    }
    
    private fun setupFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ 使用新的 WindowInsetsController API
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 11 以下使用旧的 API
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    private fun setupViews() {
        val title = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
        binding.tvTitle.text = title
        onBackPressedDispatcher.addCallback(this) {
            loadInterstitial {
                finish()
            }
        }
        // 返回按钮
        binding.btnBack.setOnClickListener {
            loadInterstitial {
                finish()
            }
        }
        
        // 播放/暂停按钮
        binding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }
        
        // 中央播放按钮
        binding.btnCenterPlay.setOnClickListener {
            togglePlayPause()
        }
        
        // 倍速按钮
        binding.btnSpeed.setOnClickListener {
            cycleSpeed()
            resetHideControllerTimer()
        }
        
        // 全屏按钮
        binding.btnFullscreen.setOnClickListener {
            toggleOrientation()
            resetHideControllerTimer()
        }
        
        // 进度条拖动
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(hideControllerRunnable)
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    seekTo(it.progress)
                }
                resetHideControllerTimer()
            }
        })
        
        // 透明点击层：点击屏幕任意位置显示/隐藏控制器
        binding.clickLayer.setOnClickListener {
            toggleController()
        }
    }

    private fun loadVideo() {
        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: return
        
        binding.progressLoading.visibility = View.VISIBLE
        
        binding.videoView.apply {
            setVideoURI(Uri.parse(videoUri))
            
            setOnPreparedListener { mp ->
                onVideoPrepared(mp)
            }
            
            setOnCompletionListener {
                onVideoCompleted()
            }
            
            setOnErrorListener { _, what, extra ->
                binding.progressLoading.visibility = View.GONE
                // 处理错误
                true
            }
        }
    }
    
    private fun onVideoPrepared(mp: MediaPlayer) {
        mediaPlayer = mp
        binding.progressLoading.visibility = View.GONE
        
        // 设置总时长
        val duration = mp.duration
        binding.seekBar.max = duration
        binding.tvTotalTime.text = formatTime(duration)
        
        // 设置倍速
        applyPlaybackSpeed()
        
        // 自动播放
        binding.videoView.start()
        isPlaying = true
        updatePlayPauseButton()
        startProgressUpdate()
        resetHideControllerTimer()
    }
    
    private fun onVideoCompleted() {
        isPlaying = false
        updatePlayPauseButton()
        binding.btnCenterPlay.visibility = View.VISIBLE
        showController()
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    private fun play() {
        binding.videoView.start()
        isPlaying = true
        updatePlayPauseButton()
        startProgressUpdate()
        resetHideControllerTimer()
    }

    private fun pause() {
        binding.videoView.pause()
        isPlaying = false
        updatePlayPauseButton()
        stopProgressUpdate()
        handler.removeCallbacks(hideControllerRunnable)
    }
    
    private fun stopProgressUpdate() {
        handler.removeCallbacks(updateProgressRunnable)
    }

    private fun seekTo(position: Int) {
        val targetPosition = position.coerceIn(0, binding.videoView.duration)
        binding.videoView.seekTo(targetPosition)
        updateProgress()
    }

    private fun cycleSpeed() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
            currentSpeed = speedOptions[currentSpeedIndex]
            applyPlaybackSpeed()
            binding.btnSpeed.text = String.format("%.2fx", currentSpeed)
        }
    }
    
    private fun applyPlaybackSpeed() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            mediaPlayer?.let { mp ->
                try {
                    val params = mp.playbackParams
                    mp.playbackParams = params.setSpeed(currentSpeed)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun toggleOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun updatePlayPauseButton() {
        val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        binding.btnPlayPause.setImageResource(iconRes)
        binding.btnCenterPlay.setImageResource(iconRes)
        binding.btnCenterPlay.visibility = if (isPlaying) View.GONE else View.VISIBLE
    }

    private fun startProgressUpdate() {
        handler.removeCallbacks(updateProgressRunnable)
        handler.post(updateProgressRunnable)
    }

    private fun updateProgress() {
        val currentPosition = binding.videoView.currentPosition
        binding.seekBar.progress = currentPosition
        binding.tvCurrentTime.text = formatTime(currentPosition)
    }

    private fun toggleController() {
        if (isControllerVisible) {
            hideController()
        } else {
            showController()
        }
    }

    private fun showController() {
        binding.controllerContainer.visibility = View.VISIBLE
        binding.clickLayer.isClickable = true
        isControllerVisible = true
        resetHideControllerTimer()
    }

    private fun hideController() {
        if (isPlaying) {
            binding.controllerContainer.visibility = View.GONE
            binding.clickLayer.isClickable = true
            isControllerVisible = false
            handler.removeCallbacks(hideControllerRunnable)
        }
    }

    private fun resetHideControllerTimer() {
        handler.removeCallbacks(hideControllerRunnable)
        if (isPlaying) {
            handler.postDelayed(hideControllerRunnable, CONTROLLER_HIDE_DELAY)
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val formatter = Formatter(StringBuilder(), Locale.getDefault())
        return formatter.format("%02d:%02d", minutes, seconds).toString()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // 更新全屏按钮图标
        val iconRes = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            R.drawable.ic_fullscreen_exit
        } else {
            R.drawable.ic_fullscreen
        }
        binding.btnFullscreen.setImageResource(iconRes)
        
        // 确保全屏状态
        setupFullscreen()
    }

    override fun onResume() {
        super.onResume()
        // 确保全屏状态（防止从其他应用返回时状态栏重新显示）
        setupFullscreen()
    }

    override fun onPause() {
        super.onPause()
        if (isPlaying) {
            pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateProgressRunnable)
        handler.removeCallbacks(hideControllerRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        binding.videoView.stopPlayback()
    }
}
