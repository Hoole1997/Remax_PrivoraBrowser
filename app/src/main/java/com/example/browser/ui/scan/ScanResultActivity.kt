package com.example.browser.ui.scan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.viewModels
import com.blankj.utilcode.util.ToastUtils
import com.browser.common.loadNative
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivityScanResultBinding
import com.example.browser.ui.web.WebActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 扫描结果页面
 * 显示二维码/条形码扫描结果，支持：
 * 1. URL识别：显示"打开网页"按钮
 * 2. 文本内容：不显示"打开网页"按钮
 * 3. 复制、搜索、分享功能
 */
class ScanResultActivity : BaseActivity<ActivityScanResultBinding, ScanResultModel>() {

    companion object {
        const val EXTRA_SCAN_RESULT = "extra_scan_result"

        /**
         * 启动扫描结果页面
         */
        fun start(context: Context, scanResult: String) {
            val intent = Intent(context, ScanResultActivity::class.java).apply {
                putExtra(EXTRA_SCAN_RESULT, scanResult)
            }
            context.startActivity(intent)
        }
    }

    private var scanResult: String = ""
    private var isUrl: Boolean = false

    override fun initBinding(): ActivityScanResultBinding {
        return ActivityScanResultBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): ScanResultModel {
        return viewModels<ScanResultModel>().value
    }

    override fun initView() {
        // 获取扫描结果
        scanResult = intent.getStringExtra(EXTRA_SCAN_RESULT) ?: ""
        if (scanResult.isEmpty()) {
            ToastUtils.showShort(getString(R.string.scan_result_empty))
            finish()
            return
        }

        // 识别是URL还是文本
        isUrl = isValidUrl(scanResult)

        // 设置UI
        setupUI()
        setupListeners()
        loadNative(binding.adContainer)
    }

    /**
     * 设置UI
     */
    private fun setupUI() {
        // 设置扫描时间
        val currentTime = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date())
        binding.tvScanTime.text = currentTime

        // 设置扫描结果文本
        binding.tvScanResult.text = scanResult

        // 根据是否为URL显示/隐藏"打开网页"按钮
        if (isUrl) {
            binding.llOpenUrl.visibility = View.VISIBLE
        } else {
            binding.llOpenUrl.visibility = View.GONE
        }
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 返回按钮
        binding.ivBack.setOnClickListener {
            finish()
        }

        // 打开网页按钮 (仅URL显示)
        binding.llOpenUrl.setOnClickListener {
            if (isUrl) {
                openWebPage()
            }
        }

        // 复制按钮
        binding.llCopy.setOnClickListener {
            copyToClipboard()
        }

        // 搜索按钮
        binding.llSearch.setOnClickListener {
            searchContent()
        }

        // 分享按钮
        binding.llShare.setOnClickListener {
            shareContent()
        }
    }

    /**
     * 判断是否为有效的URL
     */
    private fun isValidUrl(text: String): Boolean {
        return text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) ||
                text.matches(Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*"))
    }

    /**
     * 打开网页
     */
    private fun openWebPage() {
        val url = if (!scanResult.startsWith("http://", ignoreCase = true) &&
            !scanResult.startsWith("https://", ignoreCase = true)) {
            "https://$scanResult"
        } else {
            scanResult
        }

        val intent = Intent(this, WebActivity::class.java).apply {
            putExtra(WebActivity.EXTRA_URL, url)
        }
        startActivity(intent)
    }

    /**
     * 复制到剪贴板
     */
    private fun copyToClipboard() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("scan_result", scanResult)
        clipboardManager.setPrimaryClip(clipData)
        ToastUtils.showShort(getString(R.string.scan_result_copied))
    }

    /**
     * 搜索内容
     * 使用Google搜索扫描结果
     */
    private fun searchContent() {
        val searchUrl = "https://www.google.com/search?q=${android.net.Uri.encode(scanResult)}"
        val intent = Intent(this, WebActivity::class.java).apply {
            putExtra(WebActivity.EXTRA_URL, searchUrl)
        }
        startActivity(intent)
        finish()
    }

    /**
     * 分享内容
     */
    private fun shareContent() {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, scanResult)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.scan_result_share_to)))
    }
}
