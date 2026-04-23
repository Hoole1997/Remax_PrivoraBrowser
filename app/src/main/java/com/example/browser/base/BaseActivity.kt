package com.example.browser.base

import android.os.Bundle
import android.view.Window
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewbinding.ViewBinding

abstract class BaseActivity<Binding : ViewBinding, VM : BaseModel> : AppCompatActivity() {

    private var _binding: Binding? = null
    val binding get() = _binding!!

    private var isResume = false

    lateinit var viewModel: VM

    /**
     * 是否启用窗口内容转换（转场动画）
     * 默认为 false，只在需要的 Activity 中覆盖为 true
     *
     * 注意：启用转场动画可能导致内存泄漏，需要谨慎使用
     * 如果启用，需要确保在 finish 时调用 finishAfterTransition()
     */
    open fun enableContentTransitions(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 只在需要的 Activity 中启用窗口内容转换
        if (enableContentTransitions()) {
            window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
        }

        super.onCreate(savedInstanceState)
        _binding = initBinding()
        initEdgeToEdge()
        setContentView(binding.root)
        viewModel = initViewModel()
        initView()
    }

    abstract fun initBinding(): Binding

    abstract fun initViewModel(): VM

    abstract fun initView()

    override fun onDestroy() {
        super.onDestroy()
        // 清理binding引用，防止内存泄漏
        _binding = null

        // 如果启用了转场动画，显式清理转场动画资源
        // 这可以防止 ExitTransitionCoordinator 持有 Activity 引用导致内存泄漏
        if (enableContentTransitions()) {
            try {
                // 清理窗口的进入和退出转场动画
                window.enterTransition = null
                window.exitTransition = null
                window.returnTransition = null
                window.reenterTransition = null
                window.sharedElementEnterTransition = null
                window.sharedElementExitTransition = null
                window.sharedElementReturnTransition = null
                window.sharedElementReenterTransition = null
            } catch (e: Exception) {
                // 忽略清理时的异常，避免影响正常销毁流程
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isResume = true
    }

    override fun onPause() {
        super.onPause()
        isResume = false
    }

    fun isActivityResumed(): Boolean {
        return isResume
    }
    open fun initEdgeToEdge() {
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}