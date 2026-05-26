package com.example.browser.ui.uninstall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.view.isVisible
import com.blankj.utilcode.util.ClickUtils
import com.browser.common.loadNative
import com.example.browser.base.BaseActivity
import com.example.browser.base.BaseModel
import com.example.browser.databinding.ActivityUninstallConfirmBinding
import com.example.browser.ui.MainActivity
import net.corekit.core.controller.ChannelUserController

class UninstallConfirmActivity : BaseActivity<ActivityUninstallConfirmBinding, BaseModel>() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, UninstallConfirmActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun initBinding(): ActivityUninstallConfirmBinding {
        return ActivityUninstallConfirmBinding.inflate(layoutInflater)
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
        // 给次要按钮 "Uninstall" 添加下划线，对应 Figma 设计稿中的 underline 样式
        binding.btnUninstall.paintFlags =
            binding.btnUninstall.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG

        // 留下按钮 - 跳转 MainActivity
        ClickUtils.applyGlobalDebouncing(binding.btnStay) {
            goToMainActivity()
        }

        // 卸载按钮 - 跳转系统卸载
        ClickUtils.applyGlobalDebouncing(binding.btnUninstall) {
            openSystemUninstall()
        }
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun openSystemUninstall() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${packageName}")
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finish()
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
            goToMainActivity()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {

    }
}
