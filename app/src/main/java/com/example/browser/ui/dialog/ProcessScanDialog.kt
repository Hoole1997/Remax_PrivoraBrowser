package com.example.browser.ui.dialog

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.browser.R
import com.example.browser.data.process.ProcessManager
import com.example.browser.data.process.RunningAppInfo
import com.example.browser.databinding.DialogProcessScanBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 进程扫描引导弹框
 */
class ProcessScanDialog(
    context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onScanNowClick: () -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialogTheme) {

    private val binding: DialogProcessScanBinding
    private val handler = Handler(Looper.getMainLooper())
    private var scanLineAnimator: ObjectAnimator? = null
    private var scrollAnimator: ValueAnimator? = null
    private var runningApps = mutableListOf<RunningAppInfo>()
    
    // 图标尺寸和间距
    private var iconSize = 0
    private var iconMargin = 0
    // 容器宽度
    private var containerWidth = 0
    // 是否正在扫描
    private var isScanning = false
    // 当前扫描的位置索引（在 LinearLayout 中的位置）
    private var currentPosition = 0
    // 下一个要添加的 APP 索引（在 runningApps 中的索引）
    private var nextAppIndex = 0
    // 扫描循环 Runnable
    private var scanRunnable: Runnable? = null

    init {
        binding = DialogProcessScanBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        // 设置底部弹框行为
        behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        // 点击外部不消失
        setCanceledOnTouchOutside(false)

        // 设置背景透明
        window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            setBackgroundResource(android.R.color.transparent)
        }

        setupClickListeners()
        loadRunningApps()
    }

    private fun setupClickListeners() {
        binding.btnScanNow.setOnClickListener {
            stopAnimations()
            dismiss()
            onScanNowClick()
        }
        binding.root.setOnClickListener {
            stopAnimations()
            dismiss()
            onScanNowClick()
        }
    }

    /**
     * 加载运行中的应用
     */
    private fun loadRunningApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                ProcessManager.getRunningApps(context)
            }
            runningApps.clear()
            runningApps.addAll(apps)

            // 更新 UI
            updateAppIcons()
            updateScanResult()
            startScanAnimation()
        }
    }

    /**
     * 更新 APP 图标
     * 初始化时添加足够的图标填满屏幕
     */
    private fun updateAppIcons() {
        binding.llAppIcons.removeAllViews()

        iconSize = context.resources.getDimensionPixelSize(R.dimen.process_scan_icon_size)
        iconMargin = context.resources.getDimensionPixelSize(R.dimen.process_scan_icon_margin)

        // 等待布局完成后初始化
        binding.flScanContainer.post {
            // 防止 runningApps 为空导致除零错误
            if (runningApps.isEmpty()) return@post
            
            containerWidth = binding.flScanContainer.width
            
            // 计算需要多少个图标才能填满屏幕（左右两边各多加几个）
            val itemWidth = iconSize + iconMargin * 2
            val visibleCount = (containerWidth / itemWidth) + 4
            
            // 添加初始图标
            for (i in 0 until visibleCount) {
                addAppIcon(i % runningApps.size)
            }
            
            // 记录下一个要添加的 APP 索引
            nextAppIndex = visibleCount % runningApps.size
            
            // 初始位置：让第一个 APP 在中间
            val initialOffset = (containerWidth / 2f) - (iconSize / 2f) - iconMargin
            binding.llAppIcons.translationX = initialOffset
        }
    }

    /**
     * 添加一个 APP 图标到列表末尾
     */
    private fun addAppIcon(appIndex: Int) {
        val app = runningApps[appIndex]
        val imageView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginStart = iconMargin
                marginEnd = iconMargin
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(app.icon)
            alpha = 0.5f

            // 设置圆角背景
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 12f * context.resources.displayMetrics.density)
                }
            }
        }
        binding.llAppIcons.addView(imageView)
    }

    /**
     * 更新扫描结果文字
     */
    private fun updateScanResult() {
        val resultText = context.getString(R.string.process_scan_result, runningApps.size)
        binding.tvScanResult.text = Html.fromHtml(resultText, Html.FROM_HTML_MODE_LEGACY)
    }

    /**
     * 开始扫描动画
     */
    private fun startScanAnimation() {
        if (runningApps.isEmpty()) return
        
        isScanning = true
        currentPosition = 0
        
        // 扫描线上下移动动画
        scanLineAnimator = ObjectAnimator.ofFloat(binding.viewScanLine, "translationY", 0f, 50f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }

        // 等待布局完成后开始扫描循环
        binding.flScanContainer.post {
            startScanLoop()
        }
    }

    /**
     * 开始扫描循环：停顿扫描 -> 在末尾添加新图标 -> 移动 -> 检查并移除屏幕外图标 -> 继续
     */
    private fun startScanLoop() {
        val itemWidth = iconSize + iconMargin * 2
        
        // 当前扫描的图标在列表中的索引
        var scanningIndex = 0
        
        scanRunnable = object : Runnable {
            override fun run() {
                if (!isScanning || runningApps.isEmpty()) return
                
                // 高亮当前扫描的 APP
                highlightAppAtIndex(scanningIndex)
                
                // 停顿 1 秒后移动到下一个
                handler.postDelayed({
                    if (!isScanning) return@postDelayed
                    
                    // 在末尾添加下一个 APP 图标
                    addAppIcon(nextAppIndex)
                    nextAppIndex = (nextAppIndex + 1) % runningApps.size
                    
                    // 动画向左移动一个图标的距离
                    animateScroll(itemWidth) {
                        // 移动到下一个扫描位置
                        scanningIndex++
                        
                        // 检查并移除已经移出屏幕左侧的图标
                        removeOffScreenIcons(itemWidth) { removedCount ->
                            // 调整扫描索引
                            scanningIndex -= removedCount
                        }
                        
                        // 继续循环
                        handler.post(this)
                    }
                }, 1000)
            }
        }
        
        // 开始扫描
        handler.post(scanRunnable!!)
    }

    /**
     * 移除已经移出屏幕左侧的图标
     */
    private fun removeOffScreenIcons(itemWidth: Int, onRemoved: (Int) -> Unit) {
        var removedCount = 0
        val currentTranslation = binding.llAppIcons.translationX
        
        // 检查头部的图标是否已经完全移出屏幕左侧
        while (binding.llAppIcons.childCount > 0) {
            // 计算第一个图标的右边缘位置
            val firstIconRightEdge = currentTranslation + (removedCount + 1) * itemWidth
            
            // 如果第一个图标的右边缘已经移出屏幕左侧（小于 0），则移除
            if (firstIconRightEdge < 0) {
                binding.llAppIcons.removeViewAt(0)
                // 调整位置
                binding.llAppIcons.translationX = binding.llAppIcons.translationX + itemWidth
                removedCount++
            } else {
                break
            }
        }
        
        onRemoved(removedCount)
    }

    /**
     * 动画向左滚动指定距离
     */
    private fun animateScroll(distance: Int, onComplete: () -> Unit) {
        val startX = binding.llAppIcons.translationX
        val endX = startX - distance
        
        scrollAnimator?.cancel()
        scrollAnimator = ValueAnimator.ofFloat(startX, endX).apply {
            duration = 300
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                binding.llAppIcons.translationX = animator.animatedValue as Float
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })
            start()
        }
    }

    /**
     * 高亮指定索引的 APP
     */
    private fun highlightAppAtIndex(index: Int) {
        for (i in 0 until binding.llAppIcons.childCount) {
            val child = binding.llAppIcons.getChildAt(i) as? ImageView ?: continue
            
            if (i == index) {
                child.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            } else {
                child.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(0.5f)
                    .setDuration(200)
                    .start()
            }
        }
    }

    /**
     * 停止所有动画
     */
    private fun stopAnimations() {
        isScanning = false
        scanLineAnimator?.cancel()
        scrollAnimator?.cancel()
        scanRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun dismiss() {
        stopAnimations()
        super.dismiss()
    }
}
