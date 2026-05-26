package com.example.browser.ui.uninstall

import android.content.Context
import android.content.Intent
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.view.isVisible
import com.blankj.utilcode.util.ClickUtils
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import com.example.browser.base.BaseActivity
import com.example.browser.base.BaseModel
import com.example.browser.databinding.ActivityUninstallBinding
import com.example.browser.ui.MainActivity
import net.corekit.core.controller.ChannelUserController

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
        // 自然渠道用户不展示插屏广告，直接跳转；买量渠道用户先展示插屏后跳转
        ClickUtils.applyGlobalDebouncing(binding.btnUninstall) {
            if (ChannelUserController.isNaturalChannel()) {
                UninstallConfirmActivity.start(this)
                finish()
            } else {
                loadInterstitial {
                    UninstallConfirmActivity.start(this)
                    finish()
                }
            }
        }
    }

    private fun loadAd() {
        // 自然渠道用户不展示广告，仅对买量渠道用户加载原生广告
        if (ChannelUserController.isNaturalChannel()) {
            binding.adContainer.isVisible = false
            return
        }
        binding.adContainer.isVisible = true
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
