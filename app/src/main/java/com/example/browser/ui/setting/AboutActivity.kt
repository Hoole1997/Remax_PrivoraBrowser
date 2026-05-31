package com.example.browser.ui.setting

import android.content.Context
import android.content.Intent
import android.view.MenuItem
import androidx.activity.viewModels
import com.blankj.utilcode.util.ClickUtils
import com.example.browser.BuildConfig
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivityAboutBinding
import com.example.browser.ui.MainModel
import com.example.browser.ui.web.WebActivity

class AboutActivity : BaseActivity<ActivityAboutBinding, MainModel>() {

    companion object {
        private const val TAG = "AboutActivity"
        fun start(context: Context) {
            val intent = Intent(context, AboutActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun initBinding(): ActivityAboutBinding {
        return ActivityAboutBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): MainModel {
        return viewModels<MainModel>().value
    }

    override fun initView() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        ClickUtils.applyGlobalDebouncing(binding.tvPrivacyPolicy) {
            val webIntent = Intent(this, WebActivity::class.java).apply {
                putExtra(WebActivity.EXTRA_URL, "https://gravitonlumina.com/privacy.html")
            }
            startActivity(webIntent)
        }
        ClickUtils.applyGlobalDebouncing(binding.tvTermsOfService) {
            val webIntent = Intent(this, WebActivity::class.java).apply {
                putExtra(WebActivity.EXTRA_URL, "https://gravitonlumina.com/privacy.html")
            }
            startActivity(webIntent)
        }
        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

}