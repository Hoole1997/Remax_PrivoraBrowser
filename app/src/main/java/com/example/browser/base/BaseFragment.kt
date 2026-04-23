package com.example.browser.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.blankj.utilcode.util.LogUtils

abstract class BaseFragment<Binding : ViewBinding, VM : BaseModel> : Fragment() {

    // 是否已经加载过数据
    var isDataLoaded = false

    var binding: Binding? = null
    
    lateinit var viewModel: VM

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = initBinding()
        viewModel = initViewModel()
        initView()
        return binding?.root
    }

    abstract fun initBinding(): Binding

    abstract fun initViewModel(): VM

    abstract fun initView()

    open fun lazyLoad() {
        LogUtils.d("${this.javaClass.simpleName} lazyLoad")
        // 子类实现数据加载逻辑
    }

    override fun onStart() {
        super.onStart()
//        LogUtils.d("onStart")
    }
    override fun onStop() {
        super.onStop()
//        LogUtils.d("onStop")
    }

    override fun onResume() {
        super.onResume()
        // 懒加载逻辑：
        // 1. 在 ViewPager 中：只有当 Fragment 可见时才加载（isVisible = true）
        // 2. 直接添加到 Activity：Fragment 始终可见，直接加载
        if (!isDataLoaded && (isVisible || !isInViewPager())) {
            lazyLoad()
            isDataLoaded = true
        }
//        LogUtils.d("onResume")
    }

    /**
     * 判断 Fragment 是否在 ViewPager 中
     * ViewPager 会设置 Fragment 的 userVisibleHint，而直接添加到 Activity 的 Fragment 不会
     */
    private fun isInViewPager(): Boolean {
        // 检查父 Fragment 是否存在（嵌套 Fragment 场景）
        // 或者检查是否有 ViewPager 作为父容器
        return parentFragment != null || 
               view?.parent?.parent?.javaClass?.simpleName?.contains("ViewPager") == true
    }

    override fun onPause() {
        super.onPause()
//        LogUtils.d("onPause")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null // 清理binding引用，防止内存泄漏
        isDataLoaded = false // 重置数据加载标记，确保重新创建时能重新加载数据
//        LogUtils.d("onDestroyView")
    }

    override fun onDestroy() {
        super.onDestroy()
//        LogUtils.d("onDestroy")
    }

}