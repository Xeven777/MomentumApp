package com.anish.momentum.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Canvas primitives shared by the home screen widget and the in-app views, so a
 * ring or a contribution cell looks identical in both places.
 */
object Artwork {

    /**
     * Empty day, then four intensities of the brand yellow (level 4 == streak_yellow).
     * Level 0 sits *below* the #313131 card surface so an empty day reads as
     * recessed rather than disappearing into the card.
     */
    val HEAT_LEVELS = intArrayOf(
        0xFF262626.toInt(), // level 0: nothing done (or no habits that day)
        0xFF54481A.toInt(),
        0xFF8F7426.toInt(),
        0xFFC9A93B.toInt(),
        0xFFFFF4B2.toInt()  // level 4: the day was fully met
    )

    // Neutral greys, matched to values/colors.xml. Previously these were the
    // cool blue-tinted GitHub palette (#161B22 / #30363D / #8B949E), which put
    // a cold hue next to the warm yellow everywhere the canvas views draw.
    const val EMPTY_CELL = 0xFF1F1F1F.toInt()
    const val OUTLINE = 0xFF3A3A3A.toInt()
    const val MUTED_TEXT = 0xFF9E9E9E.toInt()
    val FLAME = 0xFFF64F2F.toInt()
    val STREAK_YELLOW = 0xFFFFF4B2.toInt()

    const val DAYS_PER_WEEK = 7

    /** Maps a 0..1 completion ratio onto one of the five heat colours. */
    fun levelColor(ratio: Float): Int {
        val clamped = ratio.coerceIn(0f, 1f)
        val level = when {
            clamped <= 0f -> 0
            clamped < 0.34f -> 1
            clamped < 0.67f -> 2
            clamped < 1f -> 3
            else -> 4
        }
        return HEAT_LEVELS[level]
    }

    // Reused across calls: a calendar frame draws one ring per day, so building a
    // fresh Paint each time is what makes the grid stutter while swiping.
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dayBounds = RectF()

    /** Progress arc starting at 12 o'clock, drawn over a full track ring. */
    fun drawRing(
        canvas: Canvas,
        bounds: RectF,
        progress: Float,
        strokeWidth: Float,
        trackColor: Int,
        progressColor: Int
    ) {
        ringPaint.strokeWidth = strokeWidth
        ringPaint.color = trackColor
        canvas.drawArc(bounds, 0f, 360f, false, ringPaint)
        if (progress > 0f) {
            ringPaint.color = progressColor
            canvas.drawArc(bounds, -90f, 360f * progress.coerceIn(0f, 1f), false, ringPaint)
        }
    }

    /**
     * A contribution grid: one column per week, one row per weekday.
     * Future days ([present] false) are skipped rather than drawn as empty.
     */
    fun drawHeatmap(
        canvas: Canvas,
        weeks: Int,
        values: FloatArray,
        present: BooleanArray,
        left: Float,
        top: Float,
        cell: Float,
        cornerRadius: Float = 4f
    ) {
        val gap = (cell * 0.16f).coerceAtLeast(1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF()
        for (day in 0 until weeks * DAYS_PER_WEEK) {
            if (!present.getOrElse(day) { false }) continue
            val columnLeft = left + (day / DAYS_PER_WEEK) * cell + gap / 2f
            val rowTop = top + (day % DAYS_PER_WEEK) * cell + gap / 2f
            rect.set(
                columnLeft, rowTop,
                columnLeft + cell - gap, rowTop + cell - gap
            )
            paint.color = levelColor(values.getOrElse(day) { 0f })
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }
    }

    /**
     * A single month-grid day: a ring showing the day's completion, with the
     * day number inside.
     */
    fun drawDayCell(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        ratio: Float,
        hasHabits: Boolean,
        text: String,
        textPaint: Paint,
        ringColor: Int
    ) {
        dayBounds.set(
            centerX - radius, centerY - radius,
            centerX + radius, centerY + radius
        )
        if (!hasHabits) {
            ringPaint.strokeWidth = radius * 0.14f
            ringPaint.color = OUTLINE
            canvas.drawCircle(centerX, centerY, radius * 0.86f, ringPaint)
        } else {
            drawRing(
                canvas, dayBounds, ratio,
                strokeWidth = radius * 0.14f,
                trackColor = OUTLINE,
                progressColor = ringColor
            )
        }

        textPaint.textAlign = Paint.Align.CENTER
        val metrics = textPaint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, centerX, baseline, textPaint)
    }
}
