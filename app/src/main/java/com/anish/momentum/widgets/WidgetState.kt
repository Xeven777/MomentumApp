package com.anish.momentum.widgets

import com.anish.momentum.data.DayStat
import com.anish.momentum.data.StreakCalculator
import com.anish.momentum.ui.Artwork
import com.anish.momentum.utils.DateUtils
import java.util.Calendar

/** Everything the widget needs, read once and shared across all widget instances. */
class WidgetState(
    val streak: Int,
    val bestStreak: Int,
    val goal: Int,
    val todayDone: Int,
    val todayTotal: Int,
    val totalCompletions: Int,
    val heatWeeks: Int,
    /** completion ratio per grid cell, oldest column first */
    val ratios: FloatArray,
    /** whether that grid cell is a day that has already happened */
    val present: BooleanArray
) {
    val todayProgress: Float
        get() = if (todayTotal == 0) 0f else todayDone.toFloat() / todayTotal

    companion object {
        const val DEFAULT_WEEKS = 12
    }
}

object WidgetStateFactory {

    /**
     * Lays the contribution grid out the way GitHub does: one column per week,
     * one row per weekday, with the current week in the rightmost column and
     * days that have not happened yet left blank.
     */
    fun build(
        stats: List<DayStat>,
        goal: Int,
        today: String = DateUtils.today(),
        heatWeeks: Int = WidgetState.DEFAULT_WEEKS
    ): WidgetState {
        val byDate = stats.associateBy { it.date }
        val cellCount = heatWeeks * Artwork.DAYS_PER_WEEK
        val ratios = FloatArray(cellCount)
        val present = BooleanArray(cellCount)

        // Sunday that starts the oldest column.
        val todayDate = DateUtils.parse(today) ?: java.util.Date()
        val cal = Calendar.getInstance().apply {
            time = todayDate
            firstDayOfWeek = Calendar.SUNDAY
        }
        cal.add(Calendar.DAY_OF_YEAR, -(cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))
        cal.add(Calendar.WEEK_OF_YEAR, -(heatWeeks - 1))
        val firstDate = cal.time

        for (cell in 0 until cellCount) {
            cal.time = firstDate
            cal.add(Calendar.DAY_OF_YEAR, cell)
            val dateStr = DateUtils.format(cal.time)
            val stat = byDate[dateStr]
            present[cell] = stat != null
            ratios[cell] = when {
                stat == null || stat.totalTasks == 0 -> 0f
                else -> stat.finishedTasks.toFloat() / stat.totalTasks
            }
        }

        val todayStat = byDate[today]
        val window = stats.takeLast(cellCount)

        return WidgetState(
            streak = StreakCalculator.current(stats, goal),
            bestStreak = StreakCalculator.best(window, goal),
            goal = goal,
            todayDone = todayStat?.finishedTasks ?: 0,
            todayTotal = todayStat?.totalTasks ?: 0,
            totalCompletions = stats.sumOf { it.finishedTasks },
            heatWeeks = heatWeeks,
            ratios = ratios,
            present = present
        )
    }
}
