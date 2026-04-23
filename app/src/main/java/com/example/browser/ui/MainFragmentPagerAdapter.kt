@file:Suppress("DEPRECATION")

package com.example.browser.ui

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.browser.shortvideo.ui.ShortVideoContainerFragment
import com.browser.shortvideo.ui.ShortVideoFragment
import com.example.browser.R
import com.example.browser.ui.file.FileFragment
import com.example.browser.ui.home.HomeRedesignFragment
import com.example.browser.ui.setting.SettingFragment
import com.example.browser.ui.tabs.BrowserTabsFragment

class MainFragmentPagerAdapter(private val context: Context, fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    companion object {

        // Short Video Tab 的位置
        const val POSITION_SHORT = 1
        // Tabs Tab 的位置
        const val POSITION_TABS = 3
    }

    private val fragments = mutableMapOf<Int, Fragment>()

    override fun getCount(): Int = 5

    override fun getItem(position: Int): Fragment {
        return fragments.getOrPut(position) {
            when (position) {
                0 -> HomeRedesignFragment()
                1 -> ShortVideoContainerFragment.newInstance()
                2 -> FileFragment()
                3 -> BrowserTabsFragment()
                4 -> SettingFragment()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }

    override fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            0 -> context.getString(R.string.tab_home)
            1 -> context.getString(R.string.tab_short)
            2 -> context.getString(R.string.tab_files)
            3 -> context.getString(R.string.tab_tabs)
            4 -> context.getString(R.string.tab_settings)
            else -> ""
        }
    }
}
