package com.example.browser.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.WindowCompat
import com.example.browser.R
import com.example.browser.databinding.DialogDefaultBrowserBinding
import com.example.browser.utils.DefaultBrowserHelper

/**
 * 默认浏览器引导弹框（全屏样式）
 *
 * 设计稿：Figma node 19145-12448
 * 顶部 Skip / 中部 Logo + 系统设置默认浏览器示意卡片 / 底部 Set as Default + Later。
 *
 * @param defaultBrowserLauncher 由宿主 Activity 通过 registerForActivityResult 提供，
 *  用于接收"设为默认浏览器"系统弹框关闭后的结果。Activity 在 launcher 回调里再
 *  通过 [DefaultBrowserHelper.isDefaultBrowser] 判断是否设置成功并埋点。
 */
class DefaultBrowserDialog(
    context: Context,
    private val defaultBrowserLauncher: ActivityResultLauncher<Intent>? = null,
    private val onLaterClick: () -> Unit,
    private val onSetDefaultClick: () -> Unit,
    private val onDialogShow: () -> Unit,
    private val onDialogDismiss: () -> Unit
) : Dialog(context, R.style.FullScreenDialogTheme) {

    private lateinit var binding: DialogDefaultBrowserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogDefaultBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 全屏铺满 + 透明状态栏
        window?.let { w ->
            w.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            w.setBackgroundDrawableResource(android.R.color.transparent)
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.statusBarColor = Color.TRANSPARENT
            // 浅色背景下使用深色状态栏图标
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                w.decorView.systemUiVisibility =
                    w.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }

        setCanceledOnTouchOutside(false)
        setupClickListeners()
    }

    override fun show() {
        super.show()
        onDialogShow.invoke()
    }

    private fun setupClickListeners() {
        // 顶部 Skip 与底部 Later 行为一致
        val laterAction = View.OnClickListener {
            dismiss()
            onLaterClick()
        }
        binding.btnSkip.setOnClickListener(laterAction)
        binding.btnLater.setOnClickListener(laterAction)

        // Set as default 按钮
        binding.btnSetDefault.setOnClickListener {
            dismiss()
            // 优先使用宿主 Activity 注册的 launcher，确保系统弹框关闭后能拿到回调。
            // 没有 launcher 时回退到 helper 内部的 startActivityForResult 旧路径。
            val activityContext = getActivityFromContext(context)
            DefaultBrowserHelper.requestDefaultBrowser(
                context = activityContext ?: context,
                launcher = defaultBrowserLauncher
            )
            onSetDefaultClick()
        }
    }

    /**
     * 从 Context 中获取 Activity
     */
    private fun getActivityFromContext(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    override fun dismiss() {
        super.dismiss()
        onDialogDismiss.invoke()
    }
}
