package com.example.browser.ui.tabs

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.browser.base.BaseModel

/**
 * 浏览器标签页 Activity 级 ViewModel。
 *
 * 持有 [BrowserTabsFragment] 的视图状态，最关键的是 [currentMode]：
 * 顶部分段控件、底部 ViewPager 都从该 LiveData 派生，避免两边自行
 * 维护选中态导致的不一致问题。
 */
class BrowserTabsModel : BaseModel() {

    /**
     * 当前显示的标签段：[TabsPagerAdapter.TAB_NORMAL] / [TabsPagerAdapter.TAB_PRIVATE]。
     * `null` 表示尚未初始化，[BrowserTabsFragment] 首次进入时会根据
     * 当前选中标签的隐私属性派生默认值。
     */
    private val _currentMode = MutableLiveData<Int>()
    val currentMode: LiveData<Int> get() = _currentMode

    /**
     * 仅在值发生改变时更新，防止「观察者 → UI → 监听器」回写自身造成死循环。
     */
    fun setMode(mode: Int) {
        if (_currentMode.value != mode) {
            _currentMode.value = mode
        }
    }
}
