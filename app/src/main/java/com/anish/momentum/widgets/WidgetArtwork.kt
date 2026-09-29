package com.anish.momentum.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.anish.momentum.ui.Artwork

/**
 * Renders the widget artwork into bitmaps.
 *
 * RemoteViews cannot host a custom drawing View safely, so the ring and the
 * contribution grid are painted here and handed to plain ImageViews via
 * [android.widget.RemoteViews.setImageViewBitmap]. The drawing itself is
 * delegated to [Artwork] so the widget and the in-app views stay identical.
 */
object WidgetArtwork {

    /**
     * @param values completion ratio per cell, oldest column first
     * @param present whether that day has happened yet; future days stay blank
     * @param widthPx target bitmap width; height is derived (7 rows) so cells
     * stay square and the grid fills the widget instead of shrinking into a
     * tiny square.
     */
    fun heatmap(
        weeks: Int,
        values: FloatArray,
        present: BooleanArray,
        widthPx: Int
    ): Bitmap {
        val safeWeeks = weeks.coerceAtLeast(1)
        val cell = widthPx.toFloat() / safeWeeks
        val heightPx = (cell * Artwork.DAYS_PER_WEEK).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        Artwork.drawHeatmap(
            canvas = canvas,
            weeks = safeWeeks,
            values = values,
            present = present,
            left = 0f,
            top = 0f,
            cell = cell,
            cornerRadius = cell * 0.22f
        )
        return bitmap
    }

    /** Progress ring with the streak number in the middle. */
    fun ring(
        progress: Float,
        streak: Int,
        sizePx: Int,
        centerTextSizePx: Float,
        captionTextSizePx: Float,
        caption: String
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val stroke = sizePx * 0.085f
        val inset = stroke / 2f + sizePx * 0.02f
        val bounds = RectF(inset, inset, sizePx - inset, sizePx - inset)
        Artwork.drawRing(
            canvas, bounds, progress, stroke,
            trackColor = Artwork.OUTLINE, progressColor = Artwork.FLAME
        )

        val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = centerTextSizePx
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Artwork.MUTED_TEXT
            textSize = captionTextSizePx
            textAlign = Paint.Align.CENTER
        }

        val metrics = streakPaint.fontMetrics
        val centerY = sizePx / 2f - (metrics.ascent + metrics.descent) / 2f
        val numberBaseline = centerY - (sizePx * 0.04f)
        val captionBaseline = numberBaseline + captionTextSizePx * 1.25f

        canvas.drawText(streak.toString(), sizePx / 2f, numberBaseline, streakPaint)
        canvas.drawText(caption, sizePx / 2f, captionBaseline, captionPaint)
        return bitmap
    }

    /** Sunday-first weekday index (0 = Sunday) for a "yyyy-MM-dd" date string. */
    fun weekdayIndex(date: String, format: java.text.SimpleDateFormat): Int {
        val parsed = try {
            format.parse(date)
        } catch (e: Exception) {
            Log.w("WidgetArtwork", "Unparseable date $date", e)
            return 0
        } ?: return 0
        val cal = java.util.Calendar.getInstance().apply { time = parsed }
        return cal.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.SUNDAY
    }
}
