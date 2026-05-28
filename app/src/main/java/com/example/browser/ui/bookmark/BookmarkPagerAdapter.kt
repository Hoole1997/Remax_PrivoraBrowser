package com.example.browser.ui.bookmark

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * BookmarkActivity 的 ViewPager2 适配器。
 *
 * 注意：[FragmentStateAdapter.createFragment] 必须**每次都返回新的 Fragment 实例**。
 * 调用方不应持有 Fragment 引用——Activity 重建时 FragmentManager 会通过反射重建
 * 出新的 Fragment 实例，外部缓存的引用会变成永远不走生命周期的孤儿对象，
 * 一旦被访问 lateinit `viewModel` 等字段就会抛 NPE。
 *
 * 取当前显示的 Fragment 应通过 `supportFragmentManager.findFragmentByTag("f$itemId")`，
 * 其中 `itemId` 来自 [getItemId]。
 */
class BookmarkPagerAdapter(
    activity: FragmentActivity,
    private val fragmentFactories: List<() -> Fragment>,
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = fragmentFactories.size

    override fun createFragment(position: Int): Fragment = fragmentFactories[position].invoke()
}
