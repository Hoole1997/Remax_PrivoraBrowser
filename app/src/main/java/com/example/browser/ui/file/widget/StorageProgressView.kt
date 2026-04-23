package com.example.browser.ui.file.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.ConvertUtils
import com.example.browser.R

/**
 * 存储空间圆形进度条自定义View
 * 支持多色分段显示（应用、视频、照片、音乐）
 */
class StorageProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 背景圆环画笔
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 22f  // 更粗的圆环
        color = ContextCompat.getColor(context, R.color.white)  // 正确的背景色
        strokeCap = Paint.Cap.ROUND  // 圆角端点
    }

    // 进度圆环画笔
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 22f  // 更粗的圆环
        strokeCap = Paint.Cap.ROUND  // 圆角端点
    }

    // 百分比文字画笔
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = ConvertUtils.dp2px(15f).toFloat()
        isFakeBoldText = true
        color = ContextCompat.getColor(context, R.color.black)
    }

    private val rectF = RectF()

    // 存储数据
    private var percentage = 0  // 总使用百分比
    private var appPercent = 0f     // 应用占比
    private var videoPercent = 0f   // 视频占比
    private var imagePercent = 0f   // 照片占比
    private var audioPercent = 0f   // 音乐占比

    // 颜色定义
    private val colorApp by lazy { ContextCompat.getColor(context, R.color.storage_app) }
    private val colorVideo by lazy { ContextCompat.getColor(context, R.color.storage_video) }
    private val colorImage by lazy { ContextCompat.getColor(context, R.color.storage_image) }
    private val colorAudio by lazy { ContextCompat.getColor(context, R.color.storage_audio) }

    /**
     * 设置存储信息
     * @param percentage 总使用百分比
     * @param appPercent 应用占比（0-1）
     * @param videoPercent 视频占比（0-1）
     * @param imagePercent 照片占比（0-1）
     * @param audioPercent 音乐占比（0-1）
     */
    fun setStorageInfo(
        percentage: Int,
        appPercent: Float,
        videoPercent: Float,
        imagePercent: Float,
        audioPercent: Float
    ) {
        this.percentage = percentage
        this.appPercent = appPercent
        this.videoPercent = videoPercent
        this.imagePercent = imagePercent
        this.audioPercent = audioPercent

        // 调试信息
        android.util.Log.d("StorageProgress", "Percentage: $percentage%, App: $appPercent, Video: $videoPercent, Image: $imagePercent, Audio: $audioPercent")

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) - bgPaint.strokeWidth / 2

        rectF.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // 绘制背景圆环
        canvas.drawArc(rectF, 0f, 360f, false, bgPaint)

        // 绘制多色进度圆环（反向绘制，实现上覆盖下的效果）
        if (percentage > 0) {
            val usedRatio = percentage / 100f  // 实际使用的比例
            val minVisibleAngle = 8f  // 最小可见角度（度数）

            // 收集所有要绘制的段及其原始占比
            val segments = mutableListOf<Triple<Float, Float, Int>>()
            var currentAngle = -90f
            var totalAngleUsed = 0f

            // 先按原始占比计算角度
            val rawAngles = mutableListOf<Pair<Float, Int>>()

            if (appPercent > 0.0001f) {
                val angle = 360f * usedRatio * appPercent
                rawAngles.add(Pair(angle, colorApp))
            }
            if (videoPercent > 0.0001f) {
                val angle = 360f * usedRatio * videoPercent
                rawAngles.add(Pair(angle, colorVideo))
            }
            if (imagePercent > 0.0001f) {
                val angle = 360f * usedRatio * imagePercent
                rawAngles.add(Pair(angle, colorImage))
            }
            if (audioPercent > 0.0001f) {
                val angle = 360f * usedRatio * audioPercent
                rawAngles.add(Pair(angle, colorAudio))
            }

            // 调整角度：确保每个段都有最小可见角度
            val adjustedAngles = mutableListOf<Pair<Float, Int>>()
            var totalRawAngle = rawAngles.sumOf { it.first.toDouble() }.toFloat()

            rawAngles.forEach { (angle, color) ->
                // 如果角度太小，设置为最小可见角度
                val finalAngle = if (angle < minVisibleAngle) {
                    minVisibleAngle
                } else {
                    angle
                }
                adjustedAngles.add(Pair(finalAngle, color))
            }

            // 重新计算总角度，如果超过了实际使用的角度，按比例缩放
            var totalAdjustedAngle = adjustedAngles.sumOf { it.first.toDouble() }.toFloat()
            val targetTotalAngle = 360f * usedRatio

            if (totalAdjustedAngle > targetTotalAngle) {
                // 按比例缩小，但保持最小可见角度
                val scale = targetTotalAngle / totalAdjustedAngle
                adjustedAngles.forEachIndexed { index, (angle, color) ->
                    val scaledAngle = (angle * scale).coerceAtLeast(minVisibleAngle * 0.8f)
                    adjustedAngles[index] = Pair(scaledAngle, color)
                }
            }

            // 构建最终的段列表
            adjustedAngles.forEach { (angle, color) ->
                segments.add(Triple(currentAngle, angle, color))
                currentAngle += angle
            }

            // 使用 ROUND 绘制所有段，让整体呈现圆角效果
            progressPaint.strokeCap = Paint.Cap.ROUND

            // 反向绘制：从最后一段开始，实现上覆盖下的层叠效果
            for (i in segments.indices.reversed()) {
                val (startAngle, sweepAngle, color) = segments[i]
                progressPaint.color = color

                // 为了覆盖效果，稍微扩展一点范围
                val extend = if (i < segments.size - 1) 1f else 0f
                canvas.drawArc(rectF, startAngle, sweepAngle + extend, false, progressPaint)
            }
        }

        // 绘制百分比文字
        val text = "$percentage%"
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, centerX, textY, textPaint)
    }
}
