package com.example.browser.ui.setting

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.bidding.AdSourceController
import com.blankj.utilcode.util.AppUtils
import com.example.browser.BrowserApplication
import com.example.browser.BuildConfig
import com.example.browser.R
import com.example.browser.base.BaseFragment
import com.example.browser.data.language.SupportedLanguages
import com.example.browser.databinding.FragmentSettingsBinding
import com.example.browser.utils.DefaultBrowserHelper
import com.example.browser.utils.LanguageUtils
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.lib.state.ext.flowScoped
import net.corekit.core.report.ReportDataManager

class SettingFragment : BaseFragment<FragmentSettingsBinding, SettingsModel>() {

    private var searchEngineName by mutableStateOf("Google")
    private var searchEngineIcon by mutableStateOf<Bitmap?>(null)
    private var languageName by mutableStateOf("")
    private var isDefaultBrowser by mutableStateOf(false)
    private var adSourceName by mutableStateOf("")

    private val defaultBrowserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        updateDefaultBrowserSwitch()
        ReportDataManager.reportData(if (isDefaultBrowser) "Set_Default_Browser_Success" else "Set_Default_Browser_Fail",mapOf())
    }

    override fun initBinding(): FragmentSettingsBinding {
        return FragmentSettingsBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): SettingsModel {
        return activityViewModels<SettingsModel>().value
    }

    override fun initView() {
        binding?.composeContent?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                SettingScreen(
                    searchEngineName = searchEngineName,
                    searchEngineIcon = searchEngineIcon,
                    languageName = languageName,
                    isDefaultBrowser = isDefaultBrowser,
                    showDebugAdSource = BuildConfig.DEBUG,
                    adSourceName = adSourceName,
                    onSearchEngineClick = { SearchEngineBottomDialog.show(childFragmentManager) },
                    onDefaultBrowserCheckedChange = ::handleDefaultBrowserToggle,
                    onLanguageClick = { LanguageBottomDialog.show(childFragmentManager) },
                    onClearHistoryClick = { ClearDataBottomDialog.show(childFragmentManager) },
                    onAboutClick = { AboutActivity.start(activity ?: return@SettingScreen) },
                    onFeedbackClick = ::openFeedback,
                    onAdSourceClick = ::showAdSourceSelector,
                )
            }
        }
    }

    override fun lazyLoad() {
        super.lazyLoad()
        updateDefaultBrowserSwitch()
        updateLanguageDisplay()
        observeSearchEngine()
        if (BuildConfig.DEBUG) {
            updateAdSourceDisplay()
        }
    }

    override fun onResume() {
        super.onResume()
        updateDefaultBrowserSwitch()
        updateLanguageDisplay()
        if (BuildConfig.DEBUG) {
            updateAdSourceDisplay()
        }
    }

    private fun handleDefaultBrowserToggle(checked: Boolean) {
        isDefaultBrowser = checked
        if (checked) {
            DefaultBrowserHelper.requestDefaultBrowser(requireContext(), defaultBrowserLauncher)
        } else {
            DefaultBrowserHelper.openDefaultAppSettings(requireContext(), defaultBrowserLauncher)
        }
    }

    private fun updateDefaultBrowserSwitch() {
        isDefaultBrowser = DefaultBrowserHelper.isDefaultBrowser(requireContext())
    }

    private fun updateLanguageDisplay() {
        val code = LanguageUtils.getCurrentLanguage(requireContext())
        languageName = SupportedLanguages.getLanguageByCode(code)?.displayName ?: code.uppercase()
    }

    private fun openFeedback() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:gravitonlumina@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback))
            }
            startActivity(Intent.createChooser(intent, getString(R.string.feedback)))
        } catch (_: Exception) {
        }
    }

    private fun showAdSourceSelector() {
        AdSourceController.showAdSourceSelection(requireContext()) {
            updateAdSourceDisplay()
            binding?.composeContent?.postDelayed({
                AppUtils.relaunchApp(true)
            }, 1000)
        }
    }

    private fun updateAdSourceDisplay() {
        val currentSource = AdSourceController.getCurrentSource()
        adSourceName = AdSourceController.getSourceDisplayName(currentSource)
    }

    private fun observeSearchEngine() {
        val application = requireActivity().application as BrowserApplication
        val store = application.browserComponents.store

        lifecycleScope.launch {
            store.flowScoped(viewLifecycleOwner) { flow ->
                flow.map { state -> state.search }
                    .distinctUntilChanged()
                    .collect { searchState ->
                        val selectedEngine = searchState.selectedOrDefaultSearchEngine
                        selectedEngine?.let { engine ->
                            searchEngineName = engine.name
                            searchEngineIcon = engine.icon
                        }
                    }
            }
        }
    }
}
