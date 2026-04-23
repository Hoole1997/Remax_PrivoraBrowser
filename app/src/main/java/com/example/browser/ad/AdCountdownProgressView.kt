package com.example.browser.ad

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.browser.R
import kotlin.math.min

class AdCountdownProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val drawRect = RectF()

    private var rotationAngle = 0f
    private var strokeWidth = dpToPx(8f)
    private var trackColor = ContextCompat.getColor(context, R.color.ad_progress_track)
    private var progressColor = ContextCompat.getColor(context, R.color.ad_progress_blue)
    private var textColor = ContextCompat.getColor(context, R.color.ad_progress_blue)
    private var textSize = spToPx(24f)
    private var countdownText = ""

    private var rotationAnimator: ValueAnimator? = null
    private val arcSweepAngle = 90f

    init {
        trackPaint.color = trackColor
        trackPaint.strokeWidth = strokeWidth

        progressPaint.color = progressColor
        progressPaint.strokeWidth = strokeWidth

        textPaint.color = textColor
        textPaint.textSize = textSize
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val halfStroke = strokeWidth / 2f
        val size = min(w, h)
        drawRect.set(
            halfStroke,
            halfStroke,
            size.toFloat() - halfStroke,
            size.toFloat() - halfStroke
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawOval(drawRect, trackPaint)

        canvas.drawArc(drawRect, rotationAngle - 90f, arcSweepAngle, false, progressPaint)

        if (countdownText.isNotEmpty()) {
            val textX = width / 2f
            val textY = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(countdownText, textX, textY, textPaint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startRotation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRotation()
    }

    fun startRotation() {
        if (rotationAnimator?.isRunning == true) return

        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                rotationAngle = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    fun setCountdownText(text: String) {
        countdownText = text
        invalidate()
    }

    fun setProgressColor(color: Int) {
        progressColor = color
        progressPaint.color = progressColor
        invalidate()
    }

    fun setTrackColor(color: Int) {
        trackColor = color
        trackPaint.color = trackColor
        invalidate()
    }

    fun setTextColor(color: Int) {
        textColor = color
        textPaint.color = textColor
        invalidate()
    }

    fun setTextSize(sizeSp: Float) {
        textSize = spToPx(sizeSp)
        textPaint.textSize = textSize
        invalidate()
    }

    fun setStrokeWidth(widthDp: Float) {
        strokeWidth = dpToPx(widthDp)
        progressPaint.strokeWidth = strokeWidth
        trackPaint.strokeWidth = strokeWidth
        onSizeChanged(width, height, width, height)
        invalidate()
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun spToPx(sp: Float): Float {
        return sp * resources.displayMetrics.scaledDensity
    }
}
