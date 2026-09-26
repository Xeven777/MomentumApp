package com.anish.momentum

import com.anish.momentum.data.DayStat
import com.anish.momentum.data.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    private fun days(vararg pairs: Pair<Int, Int>) =
        pairs.map { (finished, total) -> DayStat("2026-01-01", finished, total) }

    @Test
    fun `empty history has no streak`() {
        assertEquals(0, StreakCalculator.current(emptyList(), goal = 0))
    }

    @Test
    fun `counts consecutive complete days ending today`() {
        val stats = days(0 to 0, 2 to 2, 2 to 2, 1 to 2)
        assertEquals(2, StreakCalculator.current(stats, goal = 0))
    }

    @Test
    fun `an unfinished today does not break the streak`() {
        val stats = days(2 to 2, 2 to 2, 0 to 2)
        assertEquals(2, StreakCalculator.current(stats, goal = 0))
    }

    @Test
    fun `a missed day in the middle breaks the streak`() {
        val stats = days(2 to 2, 1 to 2, 2 to 2, 2 to 2)
        assertEquals(2, StreakCalculator.current(stats, goal = 0))
    }

    @Test
    fun `days with no habits are never complete`() {
        val stats = days(0 to 0, 0 to 0)
        assertEquals(0, StreakCalculator.current(stats, goal = 0))
    }

    @Test
    fun `daily goal makes a partial day count`() {
        // Both days are 2/5: incomplete when every habit is required, complete at a goal of 2.
        val stats = days(2 to 5, 2 to 5)
        assertEquals(0, StreakCalculator.current(stats, goal = 0))
        assertEquals(2, StreakCalculator.current(stats, goal = 2))
    }

    @Test
    fun `daily goal cannot exceed the habits that existed`() {
        val stats = days(1 to 1, 1 to 1)
        assertEquals(2, StreakCalculator.current(stats, goal = 3))
    }

    @Test
    fun `best streak scans the whole window`() {
        val stats = days(2 to 2, 2 to 2, 2 to 2, 0 to 2, 2 to 2)
        assertEquals(3, StreakCalculator.best(stats, goal = 0))
    }

    @Test
    fun `best streak is zero when nothing is complete`() {
        assertEquals(0, StreakCalculator.best(days(0 to 3, 1 to 3), goal = 0))
    }
}
