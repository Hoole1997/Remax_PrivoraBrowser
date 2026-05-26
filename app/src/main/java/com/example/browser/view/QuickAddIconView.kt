package com.example.browser.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.browser.R

/**
 * 首页"添加"快捷按钮自定义 View。
 *
 * 视觉：
 * - 外层是一个圆形背景（默认 #F3F5F7）；
 * - 内部按 2x2 网格放置 4 个圆形小图标，间距固定。
 *
 * 数据：
 * - 4 个图标可通过 XML 属性 [R.styleable.QuickAddIconView_qaivIconTopLeft] 等静态指定；
 * - 也可通过 [setIcons] 在运行时根据"未添加的推荐网站"动态填充。
 *
 * 对应 Figma 18481-4870 中 col-4 "Add" 按钮。
 */
class QuickAddIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** 4 个图标 slot，按"左上、右上、左下、右下"顺序持有。 */
    private val iconSlots = arrayOfNulls<Drawable>(SLOT_COUNT)

    /** 用于把每个图标限制在圆形区域内绘制。 */
    private val clipPath = Path()
    private val clipBounds = RectF()

    /** 圆底直径（默认 = view 较小边）。 */
    private var backgroundSize: Float = 0f

    /** 圆底背景颜色。 */
    private var backgroundColorInt: Int = DEFAULT_BACKGROUND_COLOR

    /** 单个小图标的直径（px）。 */
    private var iconSize: Float = 0f

    /** 4 个小图标之间的横/纵向间距（px）。 */
    private var iconSpacing: Float = 0f

    init {
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.QuickAddIconView, defStyleAttr, 0)
            try {
                backgroundColorInt = ta.getColor(
                    R.styleable.QuickAddIconView_qaivBackgroundColor,
                    DEFAULT_BACKGROUND_COLOR,
                )
                backgroundSize = ta.getDimension(R.styleable.QuickAddIconView_qaivBackgroundSize, 0f)
                iconSize = ta.getDimension(
                    R.styleable.QuickAddIconView_qaivIconSize,
                    dp(DEFAULT_ICON_DP),
                )
                iconSpacing = ta.getDimension(
                    R.styleable.QuickAddIconView_qaivIconSpacing,
                    dp(DEFAULT_SPACING_DP),
                )

                resolveDrawable(ta, R.styleable.QuickAddIconView_qaivIconTopLeft)?.let { iconSlots[0] = it }
                resolveDrawable(ta, R.styleable.QuickAddIconView_qaivIconTopRight)?.let { iconSlots[1] = it }
                resolveDrawable(ta, R.styleable.QuickAddIconView_qaivIconBottomLeft)?.let { iconSlots[2] = it }
                resolveDrawable(ta, R.styleable.QuickAddIconView_qaivIconBottomRight)?.let { iconSlots[3] = it }
            } finally {
                ta.recycle()
            }
        } else {
            iconSize = dp(DEFAULT_ICON_DP)
            iconSpacing = dp(DEFAULT_SPACING_DP)
        }
        backgroundPaint.color = backgroundColorInt
    }

    /**
     * 用 4 个 drawable resId 设置图标。null 项表示使用默认占位（不绘制该 slot）。
     */
    fun setIcons(@DrawableRes topLeft: Int?, @DrawableRes topRight: Int?, @DrawableRes bottomLeft: Int?, @DrawableRes bottomRight: Int?) {
        iconSlots[0] = topLeft?.let { ContextCompat.getDrawable(context, it) }
        iconSlots[1] = topRight?.let { ContextCompat.getDrawable(context, it) }
        iconSlots[2] = bottomLeft?.let { ContextCompat.getDrawable(context, it) }
        iconSlots[3] = bottomRight?.let { ContextCompat.getDrawable(context, it) }
        invalidate()
    }

    /**
     * 用一组 asset 路径异步加载 4 个图标（不足 4 个时剩余 slot 留空）。
     * 路径会被解析为 `file:///android_asset/{path}`，例如 `weblogo/web_facebook.webp`。
     */
    fun setIconsFromAssets(assetPaths: List<String>) {
        // 先清空 slot，避免上一次的图标残留
        for (i in iconSlots.indices) iconSlots[i] = null
        invalidate()

        assetPaths.take(SLOT_COUNT).forEachIndexed { index, path ->
            val targetSize = iconSize.toInt().coerceAtLeast(1)
            Glide.with(this)
                .load("file:///android_asset/$path")
                .override(targetSize, targetSize)
                .circleCrop()
                .into(object : CustomTarget<Drawable>(targetSize, targetSize) {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        iconSlots[index] = resource
                        invalidate()
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        iconSlots[index] = placeholder
                        invalidate()
                    }
                })
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (backgroundSize <= 0f) {
            backgroundSize = minOf(measuredWidth, measuredHeight).toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = backgroundSize / 2f

        // 1) 圆底
        canvas.drawCircle(cx, cy, radius, backgroundPaint)

        if (iconSize <= 0f) return

        // 2) 计算 2x2 网格中每个图标的左上角坐标
        // 整个网格大小 = 2 * iconSize + iconSpacing
        val gridSize = 2f * iconSize + iconSpacing
        val gridLeft = cx - gridSize / 2f
        val gridTop = cy - gridSize / 2f

        for (index in iconSlots.indices) {
            val drawable = iconSlots[index] ?: continue
            val col = index % 2
            val row = index / 2
            val left = gridLeft + col * (iconSize + iconSpacing)
            val top = gridTop + row * (iconSize + iconSpacing)
            val right = left + iconSize
            val bottom = top + iconSize

            // 用 path 把每个 slot 限制成圆形，避免 drawable 是方形也能展示成圆
            clipBounds.set(left, top, right, bottom)
            clipPath.reset()
            clipPath.addOval(clipBounds, Path.Direction.CW)

            val saveCount = canvas.save()
            canvas.clipPath(clipPath)
            drawable.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            drawable.draw(canvas)
            canvas.restoreToCount(saveCount)
        }
    }

    private fun resolveDrawable(
        ta: android.content.res.TypedArray,
        styleable: Int,
    ): Drawable? {
        val resId = ta.getResourceId(styleable, 0)
        return if (resId != 0) ContextCompat.getDrawable(context, resId) else null
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val SLOT_COUNT = 4
        private const val DEFAULT_BACKGROUND_COLOR = 0xFFF3F5F7.toInt()
        private const val DEFAULT_ICON_DP = 12f
        private const val DEFAULT_SPACING_DP = 3f
    }
}
