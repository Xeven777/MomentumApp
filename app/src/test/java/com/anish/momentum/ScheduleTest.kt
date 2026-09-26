package com.anish.momentum

import com.anish.momentum.data.DayStat
import com.anish.momentum.data.Schedule
import com.anish.momentum.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleTest {

    /** A "yyyy-MM-dd" date [offset] days from today. */
    private fun date(offset: Int) = DateUtils.plusDays(DateUtils.today(), offset)

    private fun weekdayOf(dateStr: String): Int {
        val cal = Calendar.getInstance()
        cal.time = DateUtils.parse(dateStr)!!
        return cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }

    @Test
    fun `empty mask means every day`() {
        assertTrue(Schedule.isEveryDay(Schedule.EVERY_DAY))
        assertEquals(7, Schedule.daysOfWeek(Schedule.EVERY_DAY).size)
    }

    @Test
    fun `a habit scheduled for all seven days is also every day`() {
        val all = Schedule.maskFor(Schedule.ALL_DAYS.toSet())
        assertTrue(Schedule.isEveryDay(all))
    }

    @Test
    fun `mask round trips through the weekday set`() {
        val days = setOf(Schedule.MONDAY, Schedule.WEDNESDAY, Schedule.FRIDAY)
        val mask = Schedule.maskFor(days)
        assertEquals(days, Schedule.daysOfWeek(mask))
    }

    @Test
    fun `habit runs on the days it is scheduled for`() {
        val mask = Schedule.maskFor(setOf(Schedule.MONDAY, Schedule.WEDNESDAY, Schedule.FRIDAY))
        for (offset in 0..20) {
            val date = date(offset)
            val weekday = weekdayOf(date)
            // Monday, Wednesday, Friday -> indices 1, 3, 5
            val expected = weekday == 1 || weekday == 3 || weekday == 5
            assertEquals(
                "wrong answer for $date (weekday $weekday)",
                expected,
                Schedule.isScheduledOn(mask, date)
            )
        }
    }

    @Test
    fun `an unparseable date is treated as scheduled rather than silently skipped`() {
        assertTrue(Schedule.isScheduledOn(Schedule.maskFor(setOf(Schedule.MONDAY)), "not-a-date"))
    }

    @Test
    fun `describe reads as every day for the default`() {
        assertEquals("Every day", Schedule.describe(Schedule.EVERY_DAY))
    }

    @Test
    fun `describe lists the chosen weekdays`() {
        val mask = Schedule.maskFor(setOf(Schedule.MONDAY, Schedule.FRIDAY))
        assertEquals("Mon, Fri", Schedule.describe(mask))
    }

    @Test
    fun `a daily habit is complete on any day with no habits`() {
        // Sanity check that an empty day never counts as complete.
        val stat = DayStat(date(0), 0, 0)
        assertFalse(
            com.anish.momentum.data.StreakCalculator.isComplete(stat, Schedule.EVERY_DAY)
        )
    }
}
