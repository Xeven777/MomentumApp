package com.anish.momentum.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Circular progress ring with a number in the middle and a caption underneath.
 * Used for per-habit completion rates on the stats screen.
 */
class RingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val arcBounds = RectF()
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Artwork.MUTED_TEXT
        textAlign = Paint.Align.CENTER
    }

    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var valueText: String = "0"
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var caption: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var ringColor: Int = Artwork.FLAME
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    fun set(progress: Float, valueText: String, caption: String) {
        this.progress = progress
        this.valueText = valueText
        this.caption = caption
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return

        val stroke = size * 0.085f
        val inset = stroke / 2f + size * 0.02f
        arcBounds.set(inset, inset, width - inset, height - inset)

        Artwork.drawRing(
            canvas, arcBounds, progress, stroke,
            trackColor = Artwork.OUTLINE, progressColor = ringColor
        )

        numberPaint.textSize = size * 0.30f
        captionPaint.textSize = size * 0.11f

        val metrics = numberPaint.fontMetrics
        val centerY = height / 2f - (metrics.ascent + metrics.descent) / 2f
        val numberBaseline = centerY - (size * 0.04f)
        canvas.drawText(valueText, width / 2f, numberBaseline, numberPaint)

        if (caption.isNotEmpty()) {
            canvas.drawText(caption, width / 2f, numberBaseline + captionPaint.textSize * 1.3f, captionPaint)
        }
    }
}
