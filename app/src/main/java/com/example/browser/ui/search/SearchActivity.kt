package com.example.browser.ui.search

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.browser.BrowserApplication
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivitySearchBinding
import com.example.browser.search.DefaultSearchEngines
import com.example.browser.ui.web.WebActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.feature.awesomebar.provider.HistoryStorageSuggestionProvider
import mozilla.components.feature.awesomebar.provider.SearchActionProvider
import mozilla.components.feature.awesomebar.provider.SearchSuggestionProvider
import mozilla.components.feature.search.SearchUseCases
import mozilla.components.feature.session.SessionUseCases
import java.util.Locale
import androidx.core.graphics.drawable.toDrawable
import com.browser.common.loadNative
import net.corekit.core.report.ReportDataManager

/**
 * 搜索页面
 * 功能：
 * 1. 搜索关键词或输入URL
 * 2. 显示搜索历史记录建议 (通过 HistoryStorageSuggestionProvider)
 * 3. 显示搜索引擎联想建议 (通过 SearchSuggestionProvider)
 * 4. 快捷输入（www., m., wap., ., /）
 */
class SearchActivity : BaseActivity<ActivitySearchBinding, SearchModel>() {

    companion object {
        private const val TAG = "SearchActivity"
        const val REQUEST_CODE_SEARCH_KEYWORD = 1001
        const val EXTRA_SEARCH_TEXT = "extra_search_text"
        const val EXTRA_START_VOICE_SEARCH = "extra_start_voice_search"
    }

    private lateinit var components: BrowserApplication
    private lateinit var searchEngine: SearchEngine
    private var currentSearchUrl: String? = null

    // 语音识别 ActivityResultLauncher
    private lateinit var voiceSearchLauncher: ActivityResultLauncher<Intent>

    /**
     * 禁用转场动画以避免 ExitTransitionCoordinator 内存泄漏
     * ExitTransitionCoordinator 会持有 Activity 引用导致无法释放
     */
    override fun enableContentTransitions(): Boolean = false

    override fun initBinding(): ActivitySearchBinding {
        return ActivitySearchBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): SearchModel {
        return viewModels<SearchModel>().value
    }

    override fun initView() {
        components = application as BrowserApplication

        // 获取默认搜索引擎
        searchEngine = getDefaultSearchEngine()

        // 初始化语音识别 Launcher
        setupVoiceSearchLauncher()

        setupAwesomeBar()
        setupListeners()
        observeSearchEngine()
        
        // 初始化时设置搜索引擎图标
        updateSearchIcon(searchEngine)

        // 如果有传入的搜索文本，自动填充到输入框
        val initialText = intent.getStringExtra(EXTRA_SEARCH_TEXT)
        if (!initialText.isNullOrEmpty()) {
            binding.etKeyword.setText(initialText)
            // 全选文本，方便用户直接编辑或替换
            binding.etKeyword.selectAll()
        }

        // 检查是否需要启动语音搜索
        if (intent.getBooleanExtra(EXTRA_START_VOICE_SEARCH, false)) {
            startVoiceSearch()
        } else {
            focusSearchInput()
        }
    }

    /**
     * 设置语音识别 Launcher
     */
    private fun setupVoiceSearchLauncher() {
        voiceSearchLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { data ->
                    val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (!results.isNullOrEmpty()) {
                        val recognizedText = results[0]
                        // 将识别的文本填充到输入框
                        binding.etKeyword.setText(recognizedText)
                        // 将光标移到末尾
                        binding.etKeyword.setSelection(recognizedText.length)
                        // 聚焦输入框
                        focusSearchInput()
                    }
                }
            }
        }
    }

    /**
     * 获取默认搜索引擎
     * 从 BrowserStore 中读取用户选择的搜索引擎
     */
    private fun getDefaultSearchEngine(): SearchEngine {
        // 从 BrowserStore 获取当前选中的搜索引擎
        val selectedEngine = components.browserComponents.store.state.search.selectedOrDefaultSearchEngine
        
        // 如果没有选中的搜索引擎，创建应用配置的默认搜索引擎作为后备
        return selectedEngine ?: DefaultSearchEngines.createDefaultSearchEngine(this)
    }

    /**
     * 设置 AwesomeBar 搜索建议
     */
    private fun setupAwesomeBar() {
        val store = components.browserComponents.store
        val historyStorage = components.browserComponents.historyStorage
        val icons = components.browserComponents.icons
        val client = components.browserComponents.client
        val engine = components.browserComponents.engine
        binding.awesomeBar.removeAllProviders()

        // 创建搜索图标
        val searchBitmap = ContextCompat.getDrawable(this, R.drawable.ic_round_search)?.toBitmap()

        // 自定义 loadUrlUseCase - 点击历史记录时跳转到 WebActivity
        val loadUrlUseCase = object : SessionUseCases.LoadUrlUseCase {
            override fun invoke(
                url: String,
                flags: EngineSession.LoadUrlFlags,
                additionalHeaders: Map<String, String>?,
                originalInput: String?
            ) {
                openWebActivity(url)
                finishSearch()
            }
        }

        // 自定义 searchUseCase - 点击搜索建议时跳转到 WebActivity
        val searchUseCase = object : SearchUseCases.SearchUseCase {
            override fun invoke(searchTerms: String, searchEngine: SearchEngine?, parentSessionId: String?) {
                ReportDataManager.reportData("Search_Submit",mapOf("search_keyword" to searchTerms))
                val encodedQuery = Uri.encode(searchTerms)
                // 使用当前选中的搜索引擎
                val url = this@SearchActivity.searchEngine.resultUrls.firstOrNull()?.replace("{searchTerms}", encodedQuery)
                    ?: "https://www.google.com/search?q=$encodedQuery" // 后备方案
                openWebActivity(url)
                finishSearch()
            }
        }

        // 历史记录建议 Provider
        val historyProvider = HistoryStorageSuggestionProvider(
            historyStorage = historyStorage,
            loadUrlUseCase = loadUrlUseCase,
            icons = icons,
            engine = engine,
            maxNumberOfSuggestions = 5,
            showEditSuggestion = false
        )

        // 搜索建议 Provider
        val searchSuggestionProvider = SearchSuggestionProvider(
            searchEngine = getSuggestionSearchEngine(),
            searchUseCase = searchUseCase,
            fetchClient = client,
            limit = 5,
            mode = SearchSuggestionProvider.Mode.MULTIPLE_SUGGESTIONS,
            icon = searchBitmap,
            showDescription = false,
            engine = engine,
            filterExactMatch = true,
            private = false
        )

        // 搜索操作 Provider
        val searchActionProvider = SearchActionProvider(
            store = store,
            searchUseCase = searchUseCase,
            icon = searchBitmap,
            showDescription = false
        )

        // 添加所有 Provider 到 AwesomeBar
        binding.awesomeBar.addProviders(
            historyProvider,
            searchSuggestionProvider,
            searchActionProvider
        )

        // UseCase 会处理点击,不需要额外监听

        // 监听自动补全回调
        binding.awesomeBar.setOnEditSuggestionListener { text ->
            binding.etKeyword.setText(text)
            binding.etKeyword.setSelection(text.length)
        }
    }

    private fun getSuggestionSearchEngine(): SearchEngine {
        if (searchEngine.suggestUrl != null) {
            return searchEngine
        }

        return DefaultSearchEngines.getDefaultSearchEngines(this)
            .firstOrNull { it.suggestUrl != null }
            ?: searchEngine
    }

    /**
     * 自动聚焦搜索框
     */
    private fun focusSearchInput() {
        binding.etKeyword.requestFocus()
        // 可以选择自动弹出键盘
        // binding.etKeyword.postDelayed({
        //     val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        //     imm.showSoftInput(binding.etKeyword, InputMethodManager.SHOW_IMPLICIT)
        // }, 100)
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 取消按钮 - 关闭页面
        binding.tvSearchCancel.setOnClickListener {
            finish()
        }

        // 搜索提交按钮（输入框有内容时显示）
        binding.ivSearchSubmit.setOnClickListener {
            performSearch()
        }

        // 清空输入按钮
        binding.ivInputClear.setOnClickListener {
            binding.etKeyword.text?.clear()
        }

        // 清空历史记录按钮
        binding.ivClearHistory.setOnClickListener {
            clearAllHistory()
        }

        // 监听输入框变化 - 控制清空按钮显示，并触发搜索建议
        binding.etKeyword.addTextChangedListener { text ->
            val keyword = text?.toString() ?: ""

            // 控制清空按钮显示
            binding.ivInputClear.visibility = if (text.isNullOrEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

            // 切换右侧按钮：有内容时显示提交箭头，无内容时显示取消
            if (text.isNullOrEmpty()) {
                binding.tvSearchCancel.visibility = View.VISIBLE
                binding.ivSearchSubmit.visibility = View.GONE
            } else {
                binding.tvSearchCancel.visibility = View.GONE
                binding.ivSearchSubmit.visibility = View.VISIBLE
            }

            // 更新 AwesomeBar 输入内容，触发搜索建议
            if (keyword.isNotEmpty()) {
                binding.awesomeBar.onInputChanged(keyword)
                showAwesomeBar()
            } else {
                hideAwesomeBar()
            }
        }

        // 监听键盘搜索按钮
        binding.etKeyword.setOnEditorActionListener { _, actionId, event ->
            val isImeAction = actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEND

            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)

            if (isImeAction || isEnterKey) {
                performSearch()
            }
            true
        }

        // 快捷输入按钮
        setupShortcutButtons()

        // 麦克风按钮 - 启动语音搜索
        binding.ivVoiceSearch.setOnClickListener {
            ReportDataManager.reportData("Voice_Input_Click",mapOf("Entry_Position" to "search_bar"))
            startVoiceSearch()
        }
    }

    /**
     * 启动语音搜索
     */
    private fun startVoiceSearch() {
        ReportDataManager.reportData("Voice_Input_Click",mapOf())
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.home_search_text_hint))
            }
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()

        }
    }

    /**
     * 显示 AwesomeBar
     */
    private fun showAwesomeBar() {
        binding.awesomeBar.visibility = View.VISIBLE
        binding.llKeywordHistory.visibility = View.GONE
        binding.awesomeBar.onInputStarted()
    }

    /**
     * 隐藏 AwesomeBar
     */
    private fun hideAwesomeBar() {
        binding.awesomeBar.visibility = View.GONE
        binding.llKeywordHistory.visibility = View.VISIBLE
    }

    /**
     * 设置快捷输入按钮
     */
    private fun setupShortcutButtons() {
        binding.tvShortcut1.setOnClickListener { insertText(getString(R.string.search_shortcut_www)) }
        binding.tvShortcut2.setOnClickListener { insertText(getString(R.string.search_shortcut_m)) }
        binding.tvShortcut3.setOnClickListener { insertText(getString(R.string.search_shortcut_wap)) }
        binding.tvShortcut4.setOnClickListener { insertText(getString(R.string.search_shortcut_dot)) }
        binding.tvShortcut5.setOnClickListener { insertText(getString(R.string.search_shortcut_slash)) }
    }

    /**
     * 插入文本到光标位置
     */
    private fun insertText(text: String) {
        val editText = binding.etKeyword
        val start = editText.selectionStart.coerceAtLeast(0)
        val end = editText.selectionEnd.coerceAtLeast(0)
        editText.text?.replace(start.coerceAtMost(end), start.coerceAtLeast(end), text)
    }

    /**
     * 观察 BrowserStore 中的搜索引擎状态变化
     */
    private fun observeSearchEngine() {
        lifecycleScope.launch {
            components.browserComponents.store.flowScoped(
                owner = this@SearchActivity,
                dispatcher = kotlinx.coroutines.Dispatchers.Main.immediate,
            ) { flow ->
                flow.map { state -> state.search }
                    .distinctUntilChanged()
                    .collect { searchState ->
                        // 获取当前选中的搜索引擎
                        val selectedEngine = searchState.selectedOrDefaultSearchEngine
                        
                        // 如果搜索引擎发生变化，更新
                        if (selectedEngine != null && selectedEngine.id != searchEngine.id) {
                            searchEngine = selectedEngine
                            // 重新设置 AwesomeBar 以使用新的搜索引擎
                            setupAwesomeBar()
                            // 更新搜索框左侧图标
                            updateSearchIcon(selectedEngine)
                        }
                    }
            }
        }
    }

    /**
     * 更新搜索框左侧的搜索引擎图标
     */
    private fun updateSearchIcon(engine: SearchEngine) {
        engine.icon.let { icon ->
            // 设置 EditText 左侧的 drawable
            binding.ivSearchEngineIcon.setImageDrawable(
                icon.toDrawable(resources)
            )

        }
    }

    /**
     * 执行搜索
     */
    private fun performSearch() {
        val keyword = binding.etKeyword.text?.toString()?.trim()
        if (keyword.isNullOrEmpty()) {
            return
        }

        // 判断是URL还是搜索关键词
        val url = if (isUrl(keyword)) {
            if (!keyword.startsWith("http://") && !keyword.startsWith("https://")) {
                "https://$keyword"
            } else {
                keyword
            }
        } else {
            // 使用当前选中的搜索引擎搜索
            // 对关键词进行编码，防止空格等特殊字符导致请求失败
            val encodedKeyword = Uri.encode(keyword)
            // 使用搜索引擎的 resultUrls 模板
            searchEngine.resultUrls.firstOrNull()?.replace("{searchTerms}", encodedKeyword)
                ?: "https://www.google.com/search?q=$encodedKeyword" // 后备方案
        }

        currentSearchUrl = url
        ReportDataManager.reportData("Search_Submit",mapOf("search_keyword" to keyword))
        // 打开 WebActivity，交给 GeckoView 完成页面渲染
        openWebActivity(url)

        finishSearch()
    }

    /**
     * 打开 WebActivity
     */
    private fun openWebActivity(url: String) {
        ReportDataManager.reportData("Search_Result_Show",mapOf())
        val intent = Intent(this, WebActivity::class.java).apply {
            putExtra(WebActivity.EXTRA_URL, url)
        }
        startActivity(intent)
    }

    /**
     * 完成搜索，返回结果
     */
    private fun finishSearch() {
        val keyword = binding.etKeyword.text?.toString()?.trim() ?: ""

        // 返回结果
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(EXTRA_SEARCH_TEXT, keyword)
        })
        finish()
    }

    /**
     * 清空所有历史记录
     */
    private fun clearAllHistory() {
        lifecycleScope.launch {
            try {
                components.browserComponents.historyStorage.deleteEverything()
                // 可以显示一个 Toast 提示
                // Toast.makeText(this@SearchActivity, "历史记录已清空", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 判断是否为URL
     */
    private fun isUrl(text: String): Boolean {
        return text.contains(".") &&
                !text.contains(" ") &&
                (text.startsWith("http://") ||
                        text.startsWith("https://") ||
                        text.matches(Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*")))
    }

    /**
     * 处理返回键
     */
    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        // 清理 providers 和回调
        try {
            binding.awesomeBar.disposeComposition()
            binding.awesomeBar.removeAllProviders()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}
