package com.anish.momentum.data

import com.anish.momentum.utils.DateUtils
import java.util.Calendar

/**
 * Which days of the week a habit is scheduled for.
 *
 * Stored as a 7-bit mask, one bit per weekday. The constants below are weekday
 * *indices* (0 = Sunday, matching `Calendar.DAY_OF_WEEK - Calendar.SUNDAY`);
 * the shifting happens in [maskFor] and [daysOfWeek] so callers never have to
 * think in bit values. A mask of [EVERY_DAY] means the habit applies to every
 * day, which is what habits created before scheduling existed get.
 */
object Schedule {

    const val EVERY_DAY = 0

    const val SUNDAY = 0
    const val MONDAY = 1
    const val TUESDAY = 2
    const val WEDNESDAY = 3
    const val THURSDAY = 4
    const val FRIDAY = 5
    const val SATURDAY = 6

    val ALL_DAYS: List<Int> = (0..6).toList()

    val SHORT_LABELS = listOf("S", "M", "T", "W", "T", "F", "S")

    fun maskFor(weekdays: Set<Int>): Int {
        if (weekdays.isEmpty()) return EVERY_DAY
        return weekdays.fold(0) { acc, day -> acc or (1 shl day) }
    }

    fun daysOfWeek(mask: Int): Set<Int> {
        if (mask == EVERY_DAY) return ALL_DAYS.toSet()
        return (0..6).filter { mask and (1 shl it) != 0 }.toSet()
    }

    fun isEveryDay(mask: Int): Boolean = mask == EVERY_DAY || daysOfWeek(mask).size == 7

    /** True when the habit applies on the given "yyyy-MM-dd" date. */
    fun isScheduledOn(mask: Int, date: String): Boolean {
        if (isEveryDay(mask)) return true
        val parsed = DateUtils.parse(date) ?: return true
        val weekday = Calendar.getInstance().apply { time = parsed }
            .get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        return mask and (1 shl weekday) != 0
    }

    fun describe(mask: Int): String {
        if (isEveryDay(mask)) return "Every day"
        val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return daysOfWeek(mask).sorted().joinToString(", ") { names[it] }
    }
}
