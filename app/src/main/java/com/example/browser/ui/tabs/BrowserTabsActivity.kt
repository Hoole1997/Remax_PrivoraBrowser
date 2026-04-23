package com.example.browser.ui.tabs

import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivityBrowserTabsBinding

/**
 * 浏览器标签页管理界面
 * 使用 BrowserTabsFragment 实现，Activity 只负责容器和菜单管理
 *
 * 功能：
 * 1. 托管 BrowserTabsFragment
 * 2. 提供工具栏和菜单
 * 3. 处理"关闭所有标签页"等全局操作
 */
class BrowserTabsActivity : BaseActivity<ActivityBrowserTabsBinding, BrowserTabsModel>() {

    companion object {
        private const val TAG = "BrowserTabsActivity"
    }

    // Fragment 实例
    private var tabsFragment: BrowserTabsFragment? = null

    override fun initBinding(): ActivityBrowserTabsBinding {
        return ActivityBrowserTabsBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): BrowserTabsModel {
        return viewModels<BrowserTabsModel>().value
    }

    override fun initView() {
        setupFragment()
    }

    /**
     * 设置 Fragment
     */
    private fun setupFragment() {
        // 检查是否已经存在 Fragment（例如配置变更后）
        tabsFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? BrowserTabsFragment

        if (tabsFragment == null) {
            // 创建并添加 Fragment
            tabsFragment = BrowserTabsFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, tabsFragment!!)
                .commit()
        }
    }

    override fun initEdgeToEdge() {
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * 获取Fragment实例供外部调用
     */
    fun getTabsFragment(): BrowserTabsFragment? {
        return tabsFragment
    }
}