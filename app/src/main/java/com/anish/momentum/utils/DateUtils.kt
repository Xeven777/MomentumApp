package com.anish.momentum.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * All dates are stored as "yyyy-MM-dd" strings so they sort lexicographically
 * and need no timezone/serialisation handling in the database.
 */
object DateUtils {

    val FORMAT: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = FORMAT.format(Date())

    fun parse(date: String): Date? = try {
        FORMAT.parse(date)
    } catch (e: Exception) {
        null
    }

    fun format(date: Date): String = FORMAT.format(date)

    fun plusDays(date: String, days: Int): String {
        val cal = Calendar.getInstance()
        parse(date)?.let { cal.time = it }
        cal.add(Calendar.DAY_OF_YEAR, days)
        return format(cal.time)
    }

    /** "yyyy-MM-dd" for `days` days before today (negative lookback window). */
    fun daysAgo(days: Int): String = plusDays(today(), -days)
}
