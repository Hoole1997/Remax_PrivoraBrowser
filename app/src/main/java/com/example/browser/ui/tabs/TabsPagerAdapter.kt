package com.example.browser.ui.tabs

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

/**
 * 标签页 ViewPager 适配器
 * 管理普通标签和无痕标签两个 Fragment
 */
class TabsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    companion object {
        const val TAB_NORMAL = 0
        const val TAB_PRIVATE = 1
        const val TAB_COUNT = 2
    }

    private val fragments = mutableMapOf<Int, TabsListFragment>()

    override fun getCount(): Int = TAB_COUNT

    override fun getItem(position: Int): Fragment {
        val isPrivate = position == TAB_PRIVATE
        return TabsListFragment.newInstance(isPrivate).also {
            fragments[position] = it
        }
    }

    /**
     * 获取指定位置的 Fragment
     */
    fun getFragment(position: Int): TabsListFragment? {
        return fragments[position]
    }
}
