package com.example.browser.ui.news

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.viewModels
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import com.example.browser.base.BaseActivity
import com.example.browser.components
import com.example.browser.databinding.ActivityNewsDetailsBinding
import mozilla.components.concept.engine.EngineSession

/**
 * 新闻详情页
 * 使用独立的 EngineSession，不影响主浏览器的标签页
 * 
 * 功能：
 * 1. 进度条
 * 2. 网页内容
 * 3. 返回按钮（可退回网页则退回，否则退出页面）
 */
class NewsDetailsActivity : BaseActivity<ActivityNewsDetailsBinding, NewsModel>() {

    companion object {
        const val EXTRA_NEWS_URL = "extra_news_url"

        fun start(context: Context, newsUrl: String,isMoreNews:Boolean = false) {
//            if(isMoreNews){
//                NewsMoreActivity.start(context)
//            }
            val intent = Intent(context, NewsDetailsActivity::class.java)
            intent.putExtra(EXTRA_NEWS_URL, newsUrl)
            context.startActivity(intent)
        }
    }

    // 独立的 EngineSession，不使用共享的 BrowserStore tabs
    private var engineSession: EngineSession? = null

    // 是否可以后退
    private var canGoBack = false

    // 初始 URL（用于判断是否应该退出页面）
    private var initialUrl: String? = null

    // 当前 URL
    private var currentUrl: String? = null

    // 覆盖层是否已隐藏（避免重复隐藏）
    private var isOverlayHidden = false

    override fun initBinding(): ActivityNewsDetailsBinding {
        return ActivityNewsDetailsBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): NewsModel {
        return viewModels<NewsModel>().value
    }

    override fun initView() {
        setupToolbar()
        // 设置 Lottie 动画图片资源目录
        binding.lottieLoadingIcon.imageAssetsFolder = "images/"
        loadNewsUrl()
        loadNative(binding.adContainer, position = "NA_News_preview")
        loadInterstitial {

        }
    }

    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationOnClickListener {
            handleBackPress()
        }
    }

    /**
     * 加载新闻URL
     * 使用独立的 EngineSession，不影响主浏览器的标签页
     */
    private fun loadNewsUrl() {
        val url = intent.getStringExtra(EXTRA_NEWS_URL) ?: return

        // 创建独立的 EngineSession
        engineSession = components.engine.createSession().also { session ->
            // 注册观察者监听加载状态
            session.register(object : EngineSession.Observer {
                override fun onProgress(progress: Int) {
                    runOnUiThread {
                        binding.progressBar.progress = progress
                        // 当进度达到 30% 时隐藏覆盖层（作为备用方案）
                        if (progress >= 30) {
                            hideLoadingOverlay()
                        }
                    }
                }

                override fun onLoadingStateChange(loading: Boolean) {
                    runOnUiThread {
                        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }

                override fun onFirstContentfulPaint() {
                    // 首次绘制内容时隐藏覆盖层（最准确的时机）
                    runOnUiThread {
                        hideLoadingOverlay()
                    }
                }

                override fun onNavigationStateChange(canGoBack: Boolean?, canGoForward: Boolean?) {
                    canGoBack?.let { this@NewsDetailsActivity.canGoBack = it }
                }

                override fun onLocationChange(url: String, hasUserGesture: Boolean) {
                    // 记录第一次加载完成后的 URL 作为初始 URL
                    if (initialUrl == null) {
                        initialUrl = url
                    }
                    currentUrl = url
                }
            })

            // 将 EngineSession 渲染到 EngineView
            binding.engineView.render(session)

            // 加载 URL
            session.loadUrl(url)
        }
    }

    /**
     * 处理返回操作
     * 如果当前 URL 是初始 URL 或无法后退，则直接退出页面
     * 否则后退到上一页
     */
    private fun handleBackPress() {
        // 如果当前已经是初始 URL，直接退出
        if (currentUrl == initialUrl || !canGoBack) {
            finish()
            return
        }
        
        // 后退一步
        engineSession?.goBack()
    }

    /**
     * 隐藏加载覆盖层（带淡出动画）
     */
    private fun hideLoadingOverlay() {
        // 避免重复隐藏
        if (isOverlayHidden) return
        isOverlayHidden = true
        
        binding.loadingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                if (!isFinishing && !isDestroyed) {
                    binding.loadingOverlay.visibility = View.GONE
                    // 停止 Lottie 动画
                    binding.lottieLoadingIcon.cancelAnimation()
                }
            }
            .start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackPress()
    }

    override fun onDestroy() {
        // 释放 EngineView
        binding.engineView.release()
        binding.loadingOverlay.clearAnimation()
        // 关闭 EngineSession
        engineSession?.close()
        engineSession = null
        
        super.onDestroy()
    }
}