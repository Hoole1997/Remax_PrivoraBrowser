package com.example.browser.ui.download

import android.content.Context
import android.content.Intent
import android.view.MenuItem
import androidx.activity.viewModels
import com.blankj.utilcode.util.ToastUtils
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivityDownloadBinding

/**
 * 下载Activity
 * 显示下载列表和管理下载
 */
class DownloadActivity : BaseActivity<ActivityDownloadBinding, DownloadModel>() {

    companion object {
        /**
         * 启动下载页面
         */
        fun start(context: Context) {
            val intent = Intent(context, DownloadActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun initBinding(): ActivityDownloadBinding {
        return ActivityDownloadBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): DownloadModel {
        return viewModels<DownloadModel>().value
    }

    override fun initView() {
        setupToolbar()
        loadFragment()
    }

    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.files_download_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * 加载Fragment
     */
    private fun loadFragment() {
        val fragment = DownloadFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
