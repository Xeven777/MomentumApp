package com.anish.momentum

import com.anish.momentum.data.DayStat
import com.anish.momentum.utils.DateUtils
import com.anish.momentum.ui.Artwork
import com.anish.momentum.widgets.WidgetArtwork
import com.anish.momentum.widgets.WidgetState
import com.anish.momentum.widgets.WidgetStateFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WidgetStateFactoryTest {

    private val weeks = 4
    private val cellCount = weeks * Artwork.DAYS_PER_WEEK

    /** Builds `days` consecutive days ending today, with the given completion counts. */
    private fun statsEndingToday(finished: List<Int>, total: Int = 2): List<DayStat> {
        val today = DateUtils.today()
        return finished.mapIndexed { index, value ->
            val date = DateUtils.plusDays(today, -(finished.size - 1 - index))
            DayStat(date, value, total)
        }
    }

    @Test
    fun `grid has one cell per week per weekday`() {
        val state = WidgetStateFactory.build(emptyList(), goal = 0, heatWeeks = weeks)
        assertEquals(cellCount, state.ratios.size)
        assertEquals(cellCount, state.present.size)
    }

    @Test
    fun `only days up to today are marked present`() {
        val state = WidgetStateFactory.build(emptyList(), goal = 0, heatWeeks = weeks)
        // Nothing in the database means no day is "present", including today.
        assertFalse(state.present.any { it })
    }

    @Test
    fun `today is present when it is in the data`() {
        val stats = statsEndingToday(listOf(2))
        val state = WidgetStateFactory.build(stats, goal = 0, heatWeeks = weeks)
        assertTrue(state.present.any { it })
        assertTrue(state.present.count { it } >= 1)
    }

    @Test
    fun `today progress and counts are reported`() {
        val stats = statsEndingToday(listOf(1, 2, 2))
        val state = WidgetStateFactory.build(stats, goal = 0, heatWeeks = weeks)
        assertEquals(2, state.todayDone)
        assertEquals(2, state.todayTotal)
        assertEquals(1.0f, state.todayProgress, 0.001f)
    }

    @Test
    fun `future days in the current week are not present`() {
        // A Sunday-to-Sunday window always contains days after "today".
        val today = DateUtils.parse(DateUtils.today())!!
        val dayOfWeek = Calendar.getInstance().apply { time = today }
            .get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        if (dayOfWeek == 0) return // today is Sunday: the week is already over

        val state = WidgetStateFactory.build(emptyList(), goal = 0, heatWeeks = weeks)
        // The rightmost column holds the current week; only `dayOfWeek` of its
        // seven cells can be present, and with no data none are.
        assertEquals(cellCount, state.present.size)
    }

    @Test
    fun `completed day lands in the cell matching its weekday`() {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = DateUtils.today()
        val stats = statsEndingToday(listOf(0, 2))
        val state = WidgetStateFactory.build(stats, goal = 0, heatWeeks = weeks)

        val todayIndex = state.present.indexOfLast { it }
        assertTrue("today should be the last present cell", todayIndex >= 0)
        val weekday = WidgetArtwork.weekdayIndex(today, format)
        assertEquals(weekday, todayIndex % Artwork.DAYS_PER_WEEK)
    }

    @Test
    fun `a day with no habits counts as zero rather than full marks`() {
        val today = DateUtils.today()
        val stats = listOf(DayStat(today, 0, 0))
        val state = WidgetStateFactory.build(stats, goal = 0, heatWeeks = weeks)
        val cell = state.present.indexOfLast { it }
        assertTrue(cell >= 0)
        assertEquals(0f, state.ratios[cell], 0.001f)
        assertEquals(0, state.todayTotal)
    }

    @Test
    fun `streak respects the daily goal`() {
        val stats = statsEndingToday(listOf(1, 1, 1), total = 5)
        assertEquals(0, WidgetStateFactory.build(stats, goal = 0, heatWeeks = weeks).streak)
        assertEquals(3, WidgetStateFactory.build(stats, goal = 1, heatWeeks = weeks).streak)
    }

    @Test
    fun `default heatmap covers twelve weeks`() {
        val state = WidgetStateFactory.build(emptyList(), goal = 0)
        assertEquals(WidgetState.DEFAULT_WEEKS, state.heatWeeks)
        assertEquals(WidgetState.DEFAULT_WEEKS * 7, state.ratios.size)
    }
}
