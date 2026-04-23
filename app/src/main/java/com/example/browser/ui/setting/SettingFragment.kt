package com.example.browser.ui.setting

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.bidding.AdSourceController
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.Utils
import com.example.browser.BrowserApplication
import com.example.browser.BuildConfig
import com.example.browser.R
import com.example.browser.base.BaseFragment
import com.example.browser.databinding.FragmentSettingsBinding
import com.example.browser.utils.DefaultBrowserHelper
import com.example.browser.view.ChooseSearchEngineDialog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.lib.state.ext.flowScoped

class SettingFragment : BaseFragment<FragmentSettingsBinding, SettingsModel>() {
    
    // 默认浏览器设置结果监听
    private val defaultBrowserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 检查是否成功设置为默认浏览器
        updateDefaultBrowserSwitch()
    }
    
    override fun initBinding(): FragmentSettingsBinding {
        return FragmentSettingsBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): SettingsModel {
        return activityViewModels<SettingsModel>().value
    }

    override fun initView() {
        binding?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it.root) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
                insets
            }
        }
    }

    override fun lazyLoad() {
        super.lazyLoad()
        setupClickListeners()
        observeSearchEngine()
        setupDefaultBrowserSwitch()
        setupDebugAdSource()
    }

    private fun setupClickListeners() {
        ClickUtils.applyGlobalDebouncing(binding?.llSearchEngine) {
            ChooseSearchEngineDialog.show(childFragmentManager)
        }
        ClickUtils.applyGlobalDebouncing(binding?.llLanguage) {
            LanguageBottomDialog.show(childFragmentManager)
        }
        ClickUtils.applyGlobalDebouncing(binding?.llAbout) {
            AboutActivity.start(activity?:return@applyGlobalDebouncing)
        }
        ClickUtils.applyGlobalDebouncing(binding?.llClearHistory) {
            ClearDataBottomDialog.show(childFragmentManager)
        }
        ClickUtils.applyGlobalDebouncing(binding?.llFeedback) {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:123456@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback))
                }
                startActivity(Intent.createChooser(intent, getString(R.string.feedback)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 设置默认浏览器开关
     */
    private fun setupDefaultBrowserSwitch() {
        // 初始化开关状态
        updateDefaultBrowserSwitch()
        
        // 监听开关变化
        binding?.switchDefaultBrowser?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 用户打开开关，申请设置为默认浏览器
                DefaultBrowserHelper.requestDefaultBrowser(requireContext(), defaultBrowserLauncher)
            } else {
                // 用户关闭开关，跳转到系统设置让用户手动取消
                DefaultBrowserHelper.openDefaultAppSettings(requireContext(), defaultBrowserLauncher)
            }
        }
    }
    
    /**
     * 更新默认浏览器开关状态
     */
    private fun updateDefaultBrowserSwitch() {
        val isDefaultBrowser = DefaultBrowserHelper.isDefaultBrowser(requireContext())
        // 移除监听器，避免触发 onCheckedChanged
        binding?.switchDefaultBrowser?.setOnCheckedChangeListener(null)
        binding?.switchDefaultBrowser?.isChecked = isDefaultBrowser
        // 重新设置监听器
        binding?.switchDefaultBrowser?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                DefaultBrowserHelper.requestDefaultBrowser(requireContext(), defaultBrowserLauncher)
            } else {
                DefaultBrowserHelper.openDefaultAppSettings(requireContext(), defaultBrowserLauncher)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 每次恢复时检查默认浏览器状态
        updateDefaultBrowserSwitch()
    }

    /**
     * 设置调试广告源切换功能（仅 Debug 模式显示）
     */
    private fun setupDebugAdSource() {
        // 仅在 Debug 模式下显示
        if (!BuildConfig.DEBUG) {
            binding?.llAdSource?.visibility = android.view.View.GONE
            return
        }
        
        binding?.llAdSource?.visibility = android.view.View.VISIBLE
        
        // 更新当前广告源显示
        updateAdSourceDisplay()
        
        // 点击切换广告源
        ClickUtils.applyGlobalDebouncing(binding?.llAdSource) {
            AdSourceController.showAdSourceSelection(requireContext()) {
                updateAdSourceDisplay()
                binding?.llAdSource?.postDelayed({
                    AppUtils.relaunchApp(true)
                },1000)
            }
        }
    }
    
    /**
     * 更新广告源显示
     */
    private fun updateAdSourceDisplay() {
        val currentSource = AdSourceController.getCurrentSource()
        binding?.tvAdSourceValue?.text = AdSourceController.getSourceDisplayName(currentSource)
    }

    /**
     * 观察 BrowserStore 中的搜索引擎状态变化，更新 UI 显示
     */
    private fun observeSearchEngine() {
        val application = requireActivity().application as BrowserApplication
        val store = application.browserComponents.store

        lifecycleScope.launch {
            store.flowScoped(viewLifecycleOwner) { flow ->
                flow.map { state -> state.search }
                    .distinctUntilChanged()
                    .collect { searchState ->
                        // 获取当前选中的搜索引擎
                        val selectedEngine = searchState.selectedOrDefaultSearchEngine
                        
                        // 更新 UI 显示
                        selectedEngine?.let { engine ->
                            binding?.tvSearchEngineValue?.text = engine.name
                            
                            // 更新图标（如果有）
                            engine.icon?.let { icon ->
                                binding?.ivSearchEngineIcon?.setImageBitmap(icon)
                            }
                        }
                    }
            }
        }
    }
}