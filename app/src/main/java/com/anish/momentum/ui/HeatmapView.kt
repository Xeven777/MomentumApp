package com.anish.momentum.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View

/**
 * GitHub-style contribution grid: one column per week, one row per weekday,
 * five intensity levels. Weeks run left to right, oldest first, so the most
 * recent activity is on the right.
 */
class HeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var weeks: Int = 12
    private var values: FloatArray = FloatArray(0)
    private var present: BooleanArray = BooleanArray(0)

    /** Widest week count the view will shrink cells for before it just stops growing. */
    private var maxWeeks: Int = 0

    var legendVisible: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    fun setData(weeks: Int, values: FloatArray, present: BooleanArray) {
        this.weeks = weeks.coerceAtLeast(1)
        this.values = values
        this.present = present
        maxWeeks = maxOf(maxWeeks, this.weeks)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val columns = maxWeeks.coerceAtLeast(weeks).coerceAtLeast(1)
        val rows = Artwork.DAYS_PER_WEEK

        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val heightFromWidth = (width.toFloat() / columns * rows).toInt()
        val desiredHeight = heightFromWidth + if (legendVisible) {
            // Mirror drawLegend: 6dp gap + one swatch row + breathing room for text descent.
            val cell = width.toFloat() / columns
            (6 * resources.displayMetrics.density + cell * 0.8f + 4 * resources.displayMetrics.density).toInt()
        } else 0

        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val columns = maxWeeks.coerceAtLeast(weeks).coerceAtLeast(1)
        val cell = (width.toFloat() / columns)
        val gridHeight = cell * Artwork.DAYS_PER_WEEK
        val cornerRadius = cell * 0.22f

        Artwork.drawHeatmap(
            canvas = canvas,
            weeks = weeks,
            values = values,
            present = present,
            left = 0f,
            top = 0f,
            cell = cell,
            cornerRadius = cornerRadius
        )

        if (legendVisible) drawLegend(canvas, gridHeight, cell)
    }

    private fun drawLegend(canvas: android.graphics.Canvas, gridHeight: Float, cell: Float) {
        val density = resources.displayMetrics.density
        val swatch = cell * 0.8f
        val gap = swatch * 0.35f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Artwork.MUTED_TEXT
            textSize = 10 * density
        }

        val top = gridHeight + 6 * density
        val count = Artwork.HEAT_LEVELS.size

        // "Less" on the left, the five shades, "More" on the right.
        canvas.drawText("Less", 0f, top + swatch * 0.75f, textPaint)
        for (level in 0 until count) {
            val left = textPaint.measureText("Less") + gap + level * (swatch + gap)
            paint.color = Artwork.HEAT_LEVELS[level]
            canvas.drawRoundRect(
                left, top, left + swatch, top + swatch, swatch * 0.22f, swatch * 0.22f, paint
            )
        }
        canvas.drawText("More", textPaint.measureText("Less") + gap + count * (swatch + gap) + gap, top + swatch * 0.75f, textPaint)
    }
}
