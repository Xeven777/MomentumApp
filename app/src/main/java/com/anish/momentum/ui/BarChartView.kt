package com.anish.momentum.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Simple column chart: one bar per [Bar], scaled to the tallest value. Drawn
 * in-app rather than pulled from a charting library, to keep the APK small.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Bar(val label: String, val value: Float, val highlight: Boolean = false)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Artwork.MUTED_TEXT
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val density = resources.displayMetrics.density

    private var bars: List<Bar> = emptyList()

    fun setBars(bars: List<Bar>) {
        this.bars = bars
        labelPaint.textSize = 10 * density
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = resolveSize((110 * density).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        val labelHeight = 16 * density
        val chartHeight = height - labelHeight - paddingBottom
        val slot = width.toFloat() / bars.size
        val barWidth = slot * 0.55f
        val max = bars.maxOf { it.value }.coerceAtLeast(1f)

        bars.forEachIndexed { index, bar ->
            val centerX = slot * index + slot / 2f
            val barHeight = (bar.value / max) * chartHeight
            rect.set(
                centerX - barWidth / 2f,
                chartHeight - barHeight,
                centerX + barWidth / 2f,
                chartHeight
            )
            barPaint.color = when {
                bar.value <= 0f -> Artwork.EMPTY_CELL
                bar.highlight -> Artwork.FLAME
                else -> Artwork.HEAT_LEVELS[3]
            }
            val radius = barWidth * 0.25f
            canvas.drawRoundRect(rect, radius, radius, barPaint)
            canvas.drawText(bar.label, centerX, height - 2 * density, labelPaint)
        }
    }
}
