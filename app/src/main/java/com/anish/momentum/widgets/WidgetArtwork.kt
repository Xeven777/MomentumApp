package com.anish.momentum.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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

    /**
     * How [ring] is painted. The defaults are the original look of the large
     * widget; [MINI] is the hairline, self-lit version the small card uses.
     * Keeping both in one place is what makes the big widget provably untouched.
     */
    data class RingStyle(
        /** Ring thickness as a share of the diameter. */
        val strokeRatio: Float = 0.085f,
        /**
         * Transparent margin kept around the ring, in stroke widths, so the
         * widest halo pass is not cut off by the bitmap edge. Generous values
         * shrink the ring inside its own bitmap, which is wasted space.
         */
        val insetRings: Float = 1.8f,
        /** Scales every halo pass. */
        val glowStrength: Float = 1f,
        /** Colour of the unfilled part of the ring. */
        val trackColor: Int = Artwork.OUTLINE,
        /**
         * Spills a soft light disc out past the ring, the way an activity ring
         * lights the surface it sits on. Fades to nothing before the bitmap
         * edge, so the square boundary is never visible.
         */
        val bloom: Boolean = false
    )

    /**
     * The small widget's ring. Hairline instead of chunky, with the bitmap
     * margin pulled in so the ring actually fills the box it is given, and a
     * faintly lit track: without one, a 0% day shows a dead grey outline
     * instead of an unlit but still-present ring.
     */
    val MINI = RingStyle(
        strokeRatio = 0.072f,
        insetRings = 1.15f,
        glowStrength = 0.75f,
        trackColor = 0x2EF64F2F,
        bloom = true
    )

    /**
     * Progress ring with the streak number in the middle.
     *
     * @param caption micro-line under the number. Pass an empty string to get a
     * bare hero number, centred in the ring with nothing under it.
     */
    fun ring(
        progress: Float,
        streak: Int,
        sizePx: Int,
        centerTextSizePx: Float,
        captionTextSizePx: Float,
        caption: String,
        style: RingStyle = RingStyle()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val center = sizePx / 2f
        if (style.bloom) {
            val spillRadius = center * 0.97f
            val spill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                shader = RadialGradient(
                    center, center, spillRadius,
                    intArrayOf(0x22F64F2F, 0x0BF64F2F, 0x00F64F2F),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(center, center, spillRadius, spill)
        }

        val stroke = sizePx * style.strokeRatio
        val inset = stroke * style.insetRings
        val bounds = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val sweep = 360f * progress.coerceIn(0f, 1f)

        // Neon halo. ADD blending is what makes this read as light rather than
        // paint: each pass genuinely brightens what is under it, so the wide,
        // low-alpha passes stack up into a bloom instead of muddying.
        if (sweep > 0f) {
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // `this.` needed: the ring's style parameter would otherwise
                // shadow Paint.style inside this block.
                this.style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            }
            val base = Artwork.FLAME and 0x00FFFFFF
            // Widest and faintest first, tightening and brightening inward.
            for ((alpha, width) in listOf(0x0E to 3.4f, 0x16 to 2.6f, 0x26 to 1.9f, 0x40 to 1.35f)) {
                glow.color = (scaledAlpha(alpha, style.glowStrength) shl 24) or base
                glow.strokeWidth = stroke * width
                canvas.drawArc(bounds, -90f, sweep, false, glow)
            }
            // A hot white-hot core, like a filament inside the coloured arc.
            glow.color = (scaledAlpha(0x9A, style.glowStrength) shl 24) or 0xFFFFFF
            glow.strokeWidth = stroke * 0.7f
            canvas.drawArc(bounds, -90f, sweep, false, glow)
        }

        Artwork.drawRing(
            canvas, bounds, progress, stroke,
            trackColor = style.trackColor, progressColor = Artwork.FLAME
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
        // With a caption the number rides slightly high so the two lines sit as
        // one block under it; on its own it stays dead centre in the ring.
        val hasCaption = caption.isNotEmpty()
        val numberBaseline = centerY - if (hasCaption) sizePx * 0.04f else 0f
        val captionBaseline = numberBaseline + captionTextSizePx * 1.25f

        canvas.drawText(streak.toString(), sizePx / 2f, numberBaseline, streakPaint)
        if (hasCaption) {
            canvas.drawText(caption, sizePx / 2f, captionBaseline, captionPaint)
        }
        return bitmap
    }

    /** Applies [strength] to a 0..255 alpha, so a glow dial cannot wrap around. */
    private fun scaledAlpha(alpha: Int, strength: Float): Int =
        (alpha * strength).toInt().coerceIn(0, 255)

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
