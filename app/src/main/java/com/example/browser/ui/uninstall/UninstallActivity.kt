package com.example.browser.ui.uninstall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import androidx.activity.viewModels
import com.blankj.utilcode.util.ClickUtils
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import com.example.browser.base.BaseActivity
import com.example.browser.base.BaseModel
import com.example.browser.databinding.ActivityUninstallBinding
import com.example.browser.ui.MainActivity

class UninstallActivity : BaseActivity<ActivityUninstallBinding, BaseModel>() {

    companion object {
        private const val EXTRA_FROM_SHORTCUT = "from_shortcut"
        
        fun start(context: Context, fromShortcut: Boolean = false) {
            val intent = Intent(context, UninstallActivity::class.java)
            intent.putExtra(EXTRA_FROM_SHORTCUT, fromShortcut)
            context.startActivity(intent)
        }
    }

    override fun initBinding(): ActivityUninstallBinding {
        return ActivityUninstallBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): BaseModel {
        return viewModels<BaseModel>().value
    }

    override fun initView() {
        setupToolbar()
        setupButtons()
        loadAd()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.BLACK)
    }

    private fun setupButtons() {
        // 留下按钮 - 关闭页面
        ClickUtils.applyGlobalDebouncing(binding.btnStay) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        // 卸载按钮 - 跳转到确认页面
        ClickUtils.applyGlobalDebouncing(binding.btnUninstall) {
            loadInterstitial {
                UninstallConfirmActivity.start(this)
                finish()
            }
        }
    }

    private fun loadAd() {
        loadNative(binding.adContainer)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {

    }
}
