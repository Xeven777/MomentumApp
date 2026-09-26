package com.anish.momentum.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.anish.momentum.data.DayStat
import com.anish.momentum.utils.DateUtils
import java.util.Calendar

/**
 * A month grid with a completion ring on each day, styled to match the widget's
 * heatmap. Swipe left/right to change month, tap a day to select it.
 *
 * Only months that have data are drawn in full; days outside the month and days
 * in the future are left blank.
 */
class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cellTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val outOfMonthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Artwork.OUTLINE
        textAlign = Paint.Align.CENTER
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Artwork.MUTED_TEXT
        textAlign = Paint.Align.CENTER
    }

    private val density = resources.displayMetrics.density

    private var monthOffset = 0 // 0 = this month, -1 = last month
    private var statsByDate: Map<String, DayStat> = emptyMap()
    private var selectedDate: String? = null
    private var todayString: String = DateUtils.today()

    /** Fired when the user taps a day. */
    var onDateSelected: ((String) -> Unit)? = null

    /** Fired after the displayed month changes, so the host can load its data. */
    var onMonthChanged: (() -> Unit)? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val hit = dateAt(e.x, e.y)
                if (hit != null) {
                    performClick()
                    selectedDate = hit
                    onDateSelected?.invoke(hit)
                    invalidate()
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                if (Math.abs(velocityX) < SWIPE_VELOCITY || Math.abs(velocityX) < Math.abs(velocityY)) {
                    return false
                }
                // Swipe left to go forward in time, right to go back.
                changeMonth(if (velocityX < 0) 1 else -1)
                return true
            }
        }
    )

    init {
        isClickable = true
    }

    fun setData(stats: List<DayStat>, today: String = DateUtils.today()) {
        statsByDate = stats.associateBy { it.date }
        todayString = today
        invalidate()
    }

    fun setSelectedDate(date: String?) {
        selectedDate = date
        invalidate()
    }

    /** Jumps the grid so that [date] is visible, choosing the nearest month. */
    fun showMonthContaining(date: String) {
        monthOffset = 0
        val target = DateUtils.parse(date)
        val now = DateUtils.parse(todayString)
        if (target == null || now == null) return
        val cal = Calendar.getInstance()
        cal.time = now
        val offset = monthsBetween(cal.time, target)
        monthOffset = -offset
        invalidate()
    }

    fun changeMonth(delta: Int) {
        val next = monthOffset + delta
        // Do not scroll past the current month into the future.
        if (next > 0) return
        monthOffset = next
        invalidate()
        onMonthChanged?.invoke()
    }

    fun currentMonthLabel(): String = monthLabel()

    /** First day of the month currently on screen, as "yyyy-MM-dd". */
    fun displayedMonthStart(): String = DateUtils.format(startOfDisplayedMonth())

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        return handled || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val cell = width / DAYS_PER_WEEK
        val height = (headerHeight() + weekdayHeight() + cell * MAX_WEEKS + paddingBottom).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cell = width / DAYS_PER_WEEK
        val cellTextSize = cell * 0.34f
        cellTextPaint.textSize = cellTextSize
        outOfMonthPaint.textSize = cellTextSize
        headerPaint.textSize = 15 * density
        weekdayPaint.textSize = 10 * density

        canvas.drawText(
            monthLabel(),
            width / 2f,
            paddingTop + headerHeight() * 0.62f,
            headerPaint
        )

        val weekdayTop = paddingTop + headerHeight() + weekdayHeight() * 0.7f
        WEEKDAY_LABELS.forEachIndexed { index, label ->
            val centerX = cell * index + cell / 2f
            canvas.drawText(label, centerX, weekdayTop, weekdayPaint)
        }

        val gridTop = paddingTop + headerHeight() + weekdayHeight()
        cellTextPaint.color = Color.WHITE

        // Sunday-first offset for the first of this month.
        val cal = Calendar.getInstance().apply {
            time = startOfDisplayedMonth()
            firstDayOfWeek = Calendar.SUNDAY
        }
        val leadingBlanks = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val monthStart = startOfDisplayedMonth()
        val daysInMonth = Calendar.getInstance().apply { time = monthStart }
            .getActualMaximum(Calendar.DAY_OF_MONTH)

        for (day in 1..daysInMonth) {
            val index = leadingBlanks + day - 1
            val column = index / DAYS_PER_WEEK
            val row = index % DAYS_PER_WEEK
            val centerX = cell * column + cell / 2f
            val centerY = gridTop + cell * row + cell / 2f

            cal.time = startOfDisplayedMonth()
            cal.add(Calendar.DAY_OF_MONTH, day - 1)
            val dateStr = DateUtils.format(cal.time)
            val stat = statsByDate[dateStr]
            val isFuture = dateStr > todayString
            val isToday = dateStr == todayString

            if (isFuture) {
                canvas.drawCircle(
                    centerX, centerY, cell * 0.36f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = cell * 0.05f
                        color = Artwork.OUTLINE
                    }
                )
                cellTextPaint.color = Artwork.MUTED_TEXT
                canvas.drawText(day.toString(), centerX, centerY, cellTextPaint)
                cellTextPaint.color = Color.WHITE
                continue
            }

            val ratio = if (stat == null || stat.totalTasks == 0) 0f
            else stat.finishedTasks.toFloat() / stat.totalTasks

            Artwork.drawDayCell(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                radius = cell * 0.40f,
                ratio = ratio,
                hasHabits = stat != null && stat.totalTasks > 0,
                text = day.toString(),
                textPaint = cellTextPaint,
                ringColor = if (isSelected(dateStr)) Artwork.STREAK_YELLOW else Artwork.FLAME
            )

            if (isToday || isSelected(dateStr)) {
                val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = cell * 0.05f
                    color = Color.WHITE
                }
                canvas.drawCircle(centerX, centerY, cell * 0.48f, marker)
            }
        }
    }

    private fun isSelected(date: String) = selectedDate == date

    private fun headerHeight(): Float = 26 * density
    private fun weekdayHeight(): Float = 16 * density

    private fun startOfDisplayedMonth(): java.util.Date {
        val cal = Calendar.getInstance()
        cal.time = DateUtils.parse(todayString) ?: java.util.Date()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, monthOffset)
        return cal.time
    }

    private fun monthLabel(): String {
        val cal = Calendar.getInstance()
        cal.time = startOfDisplayedMonth()
        val format = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
        return format.format(cal.time)
    }

    private fun monthsBetween(from: java.util.Date, to: java.util.Date): Int {
        val a = Calendar.getInstance().apply { time = from }
        val b = Calendar.getInstance().apply { time = to }
        val months = (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 12 +
            (b.get(Calendar.MONTH) - a.get(Calendar.MONTH))
        val dayAdjust = b.get(Calendar.DAY_OF_MONTH) - a.get(Calendar.DAY_OF_MONTH)
        return months + if (dayAdjust < 0) -1 else 0
    }

    /** Which date (if any) was drawn at these coordinates. */
    private fun dateAt(x: Float, y: Float): String? {
        val cell = width / DAYS_PER_WEEK
        val gridTop = paddingTop + headerHeight() + weekdayHeight()
        if (y < gridTop || y > gridTop + cell * MAX_WEEKS) return null
        if (x < 0 || x > width) return null

        val column = (x / cell).toInt()
        val row = ((y - gridTop) / cell).toInt()
        val index = row * DAYS_PER_WEEK + column
        if (index < 0 || index >= DAYS_PER_WEEK * MAX_WEEKS) return null

        val cal = Calendar.getInstance().apply {
            time = startOfDisplayedMonth()
            firstDayOfWeek = Calendar.SUNDAY
        }
        val leadingBlanks = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val dayOfMonth = index - leadingBlanks + 1
        val c = Calendar.getInstance().apply { time = startOfDisplayedMonth() }
        val daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (dayOfMonth < 1 || dayOfMonth > daysInMonth) return null

        c.add(Calendar.DAY_OF_MONTH, dayOfMonth - 1)
        return DateUtils.format(c.time)
    }

    private companion object {
        const val DAYS_PER_WEEK = 7
        const val MAX_WEEKS = 6
        const val SWIPE_VELOCITY = 400f
        val WEEKDAY_LABELS = listOf("S", "M", "T", "W", "T", "F", "S")
    }
}
