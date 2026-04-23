package com.example.browser.ui.download.widget

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

class DownloadProgressView @JvmOverloads constructor(
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

    private val drawRect = RectF()

    private var progressFraction = 0f
    private var strokeWidth = dpToPx(6f)
    private var trackColor = ContextCompat.getColor(context, R.color.storage_progress_bg)
    private var progressColor = ContextCompat.getColor(context, R.color.main_nav_check_color)

    private var indeterminate = false
    private var animator: ValueAnimator? = null
    private var indeterminateStartAngle = -90f
    private val indeterminateSweep = 120f

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DownloadProgressView)
            strokeWidth = typedArray.getDimension(
                R.styleable.DownloadProgressView_dpStrokeWidth,
                strokeWidth
            )
            trackColor = typedArray.getColor(
                R.styleable.DownloadProgressView_dpTrackColor,
                trackColor
            )
            progressColor = typedArray.getColor(
                R.styleable.DownloadProgressView_dpProgressColor,
                progressColor
            )
            typedArray.recycle()
        }

        trackPaint.color = trackColor
        trackPaint.strokeWidth = strokeWidth

        progressPaint.color = progressColor
        progressPaint.strokeWidth = strokeWidth
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

        if (indeterminate) {
            canvas.drawArc(drawRect, indeterminateStartAngle, indeterminateSweep, false, progressPaint)
        } else {
            if (progressFraction <= 0f) {
                return
            }
            canvas.drawArc(drawRect, -90f, progressFraction * 360f, false, progressPaint)
        }
    }

    fun setProgress(progress: Int) {
        val clamped = progress.coerceIn(0, 100)
        progressFraction = clamped / 100f
        if (indeterminate) {
            stopIndeterminateAnimation()
            indeterminate = false
        }
        invalidate()
    }

    fun setIndeterminate(isIndeterminate: Boolean) {
        if (indeterminate == isIndeterminate) {
            return
        }
        indeterminate = isIndeterminate
        if (indeterminate) {
            startIndeterminateAnimation()
        } else {
            stopIndeterminateAnimation()
            invalidate()
        }
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

    fun setStrokeWidth(widthPx: Float) {
        strokeWidth = widthPx
        progressPaint.strokeWidth = strokeWidth
        trackPaint.strokeWidth = strokeWidth
        onSizeChanged(width, height, width, height)
        invalidate()
    }

    private fun startIndeterminateAnimation() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                indeterminateStartAngle = -90f + animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopIndeterminateAnimation() {
        animator?.cancel()
        animator = null
        indeterminateStartAngle = -90f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopIndeterminateAnimation()
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
