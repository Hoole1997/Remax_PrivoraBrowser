package com.example.browser.ui.search

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import mozilla.components.compose.browser.awesomebar.AwesomeBar
import mozilla.components.compose.browser.awesomebar.AwesomeBarDefaults
import mozilla.components.concept.awesomebar.AwesomeBar as ConceptAwesomeBar

/**
 * AwesomeBar 的 Compose 包装器
 * 将 Mozilla 的 Compose AwesomeBar 封装成 View，方便在 XML 中使用
 */
class AwesomeBarWrapper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr), ConceptAwesomeBar {

    private val providers = mutableStateOf(emptyList<ConceptAwesomeBar.SuggestionProvider>())
    private val text = mutableStateOf("")
    private val hiddenSuggestions = mutableStateOf(emptySet<ConceptAwesomeBar.GroupedSuggestion>())

    private var _onEditSuggestionListener: ((String) -> Unit)? = null
    private var _onStopListener: (() -> Unit)? = null
    private var _onSuggestionClickedListener: ((ConceptAwesomeBar.Suggestion) -> Unit)? = null
    private var _onRemoveSuggestionListener: ((ConceptAwesomeBar.GroupedSuggestion) -> Unit)? = null

    @Composable
    override fun Content() {
        AwesomeBar(
            text = text.value,
            colors = AwesomeBarDefaults.colors(
                background = Color.Transparent,
                title = Color(context.getColor(android.R.color.tab_indicator_text)),
                description = Color(context.getColor(android.R.color.tab_indicator_text)).copy(alpha = 0.6f),
                autocompleteIcon = Color(context.getColor(android.R.color.tab_indicator_text)).copy(alpha = 0.6f)
            ),
            providers = providers.value,
            hiddenSuggestions = hiddenSuggestions.value,
            onSuggestionClicked = { suggestion ->
                // 先调用建议自己的点击回调 (会加载 URL 或执行搜索)
                suggestion.onSuggestionClicked?.invoke()
                // 旧监听器只接收普通建议；新型航班/体育等建议仍执行自己的回调。
                (suggestion as? ConceptAwesomeBar.Suggestion)?.let { clickedSuggestion ->
                    _onSuggestionClickedListener?.invoke(clickedSuggestion)
                }
                // 最后触发停止回调
                _onStopListener?.invoke()
            },
            onAutoComplete = {
                // 自动补全功能（可选）
            },
            onRemoveClicked = { groupedSuggestion ->
                _onRemoveSuggestionListener?.invoke(groupedSuggestion)
            },
            onScroll = {
                // 滚动时收起键盘
            }
        )
    }

    override fun addProviders(vararg providers: ConceptAwesomeBar.SuggestionProvider) {
        val newProviders = this.providers.value.toMutableList()
        newProviders.addAll(providers)
        this.providers.value = newProviders
    }

    override fun removeProviders(vararg providers: ConceptAwesomeBar.SuggestionProvider) {
        val newProviders = this.providers.value.toMutableList()
        newProviders.removeAll(providers.toSet())
        this.providers.value = newProviders
    }

    override fun removeAllProviders() {
        this.providers.value = emptyList()
        // 清理回调避免内存泄漏
        _onStopListener = null
        _onEditSuggestionListener = null
        _onSuggestionClickedListener = null
        _onRemoveSuggestionListener = null
        hiddenSuggestions.value = emptySet()
    }

    fun setOnSuggestionClickedListener(listener: (ConceptAwesomeBar.Suggestion) -> Unit) {
        _onSuggestionClickedListener = listener
    }

    override fun containsProvider(provider: ConceptAwesomeBar.SuggestionProvider): Boolean {
        return providers.value.contains(provider)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // View 从窗口分离时清理资源
        disposeComposition()
        removeAllProviders()
    }

    override fun onInputChanged(text: String) {
        this.text.value = text
    }

    override fun onInputStarted() {
        // 输入开始时的回调
    }

    override fun onInputCancelled() {
        // 输入取消时的回调
        _onStopListener?.invoke()
    }

    override fun setOnStopListener(listener: () -> Unit) {
        _onStopListener = listener
    }

    override fun setOnEditSuggestionListener(listener: (String) -> Unit) {
        _onEditSuggestionListener = listener
    }

    override fun updateHiddenSuggestions(hiddenSuggestions: Set<ConceptAwesomeBar.GroupedSuggestion>) {
        this.hiddenSuggestions.value = hiddenSuggestions
    }

    override fun setOnRemoveSuggestionButtonClicked(
        listener: (ConceptAwesomeBar.GroupedSuggestion) -> Unit,
    ) {
        _onRemoveSuggestionListener = listener
    }
}
