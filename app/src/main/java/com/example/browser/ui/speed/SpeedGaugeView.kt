package com.example.browser.ui.speed

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * 自定义仪表盘视图，用于网速测试界面
 * 半圆形仪表盘，刻度范围 0~1000 Mbps
 * 标签在弧线内侧，均匀角度分布（每30°一个），指针用分段线性插值定位
 */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 刻度标签及其对应的值（9个标签，8个间隔，均匀分布在240°弧上）
    private val scaleValues = floatArrayOf(0f, 5f, 10f, 50f, 100f, 250f, 500f, 750f, 1000f)
    private val scaleLabels = arrayOf("0", "5", "10", "50", "100", "250", "500", "750", "1,000")
    private val segmentCount = scaleValues.size - 1 // 8

    // 角度范围：从150°（左下）到390°=30°（右下），跨越240°
    private val startAngle = 150f
    private val sweepAngle = 240f

    private val density = resources.displayMetrics.density

    // 当前速度值
    private var currentSpeed: Float = 0f
    private var animatedSpeed: Float = 0f
    private var needleAnimator: ValueAnimator? = null

    // 弧线画笔 - 背景弧（40%透明度白色）
    private val arcBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f * density
        color = Color.WHITE
        alpha = (0.4f * 255).toInt()
        strokeCap = Paint.Cap.ROUND
    }

    // 弧线画笔 - 已走过的弧（100%白色）
    private val arcProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f * density
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }

    // 刻度标签画笔
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    // 指针画笔
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // 指针中心内圈（50%半透明白色）
    private val needleInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = (0.5f * 255).toInt()
    }

    // 指针中心外圈（20%半透明白色）
    private val needleOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = (0.2f * 255).toInt()
    }

    private val arcRect = RectF()
    private val needlePath = Path()

    /**
     * 将速度值映射到比例 (0~1)，使用分段线性插值
     * 标签均匀分布在弧上，所以每个间隔占 1/8
     * 速度值在两个相邻标签之间线性插值
     */
    private fun speedToRatio(speed: Float): Float {
        if (speed <= 0f) return 0f
        if (speed >= 1000f) return 1f
        for (i in 0 until segmentCount) {
            if (speed <= scaleValues[i + 1]) {
                val segStart = scaleValues[i]
                val segEnd = scaleValues[i + 1]
                val segFraction = (speed - segStart) / (segEnd - segStart)
                return (i + segFraction) / segmentCount
            }
        }
        return 1f
    }

    /**
     * 将速度值映射到角度
     */
    private fun speedToAngle(speed: Float): Float {
        return startAngle + sweepAngle * speedToRatio(speed)
    }

    fun setSpeed(speed: Float, animate: Boolean = true) {
        val targetSpeed = speed.coerceIn(0f, 1000f)
        if (animate) {
            needleAnimator?.cancel()
            needleAnimator = ValueAnimator.ofFloat(animatedSpeed, targetSpeed).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    animatedSpeed = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animatedSpeed = targetSpeed
            invalidate()
        }
        currentSpeed = targetSpeed
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Figma 300x230: center at ~(50%, 65%), radius ~42% of width
        val centerX = w * 0.50f
        val centerY = h * 0.58f
        val radius = w * 0.42f

        arcRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // 1. 绘制背景弧线
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, arcBgPaint)

        // 2. 绘制已走过的进度弧线
        val progressSweep = speedToAngle(animatedSpeed) - startAngle
        if (progressSweep > 0) {
            canvas.drawArc(arcRect, startAngle, progressSweep, false, arcProgressPaint)
        }

        // 3. 绘制刻度标签（弧线内侧，均匀角度分布）
        // 锚点紧贴弧线内边缘，通过对齐方向让文字向圆心延伸
        val arcHalfStroke = arcBgPaint.strokeWidth / 2f
        val labelInnerGap = 10f * density
        val labelRadius = radius - arcHalfStroke - labelInnerGap
        val fm = labelPaint.fontMetrics
        val textCenterOffsetY = -(fm.ascent + fm.descent) / 2f

        for (i in scaleValues.indices) {
            val angle = startAngle + sweepAngle * (i.toFloat() / segmentCount)
            val radians = Math.toRadians(angle.toDouble())
            val cosA = cos(radians).toFloat()
            val sinA = sin(radians).toFloat()

            // 文字对齐方向：让文字向圆心方向延伸，远离弧线
            labelPaint.textAlign = when {
                cosA < -0.25f -> Paint.Align.LEFT   // 左侧：锚点在左边缘，文字向右（朝圆心）
                cosA > 0.25f -> Paint.Align.RIGHT    // 右侧：锚点在右边缘，文字向左（朝圆心）
                else -> Paint.Align.CENTER            // 顶部：居中
            }

            val labelX = centerX + labelRadius * cosA
            val labelY = centerY + labelRadius * sinA + textCenterOffsetY

            canvas.drawText(scaleLabels[i], labelX, labelY, labelPaint)
        }
        // 恢复默认对齐
        labelPaint.textAlign = Paint.Align.CENTER

        // 4. 绘制指针
        drawNeedle(canvas, centerX, centerY, radius)
    }

    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val angle = speedToAngle(animatedSpeed)
        val radians = Math.toRadians(angle.toDouble())

        val outerR = 20f * density
        val innerR = 13f * density

        // 外圈（半透明）
        canvas.drawCircle(cx, cy, outerR, needleOuterPaint)
        // 内圈
        canvas.drawCircle(cx, cy, innerR, needleInnerPaint)

        // 指针长度
        val needleLength = radius * 0.72f
        val needleEndX = cx + needleLength * cos(radians).toFloat()
        val needleEndY = cy + needleLength * sin(radians).toFloat()

        // 渐变（从中心半透明到尖端白色）
        needlePaint.shader = LinearGradient(
            cx, cy, needleEndX, needleEndY,
            intArrayOf(Color.argb(102, 255, 255, 255), Color.WHITE, Color.WHITE),
            floatArrayOf(0f, 0.33f, 1f),
            Shader.TileMode.CLAMP
        )

        // 锥形指针
        val perpRadians = radians + Math.PI / 2
        val halfWidth = 3.5f * density
        needlePath.reset()
        needlePath.moveTo(
            cx + halfWidth * cos(perpRadians).toFloat(),
            cy + halfWidth * sin(perpRadians).toFloat()
        )
        needlePath.lineTo(
            cx - halfWidth * cos(perpRadians).toFloat(),
            cy - halfWidth * sin(perpRadians).toFloat()
        )
        needlePath.lineTo(needleEndX, needleEndY)
        needlePath.close()
        canvas.drawPath(needlePath, needlePaint)
        needlePaint.shader = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        needleAnimator?.cancel()
    }
}
