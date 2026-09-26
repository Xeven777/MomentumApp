package com.anish.momentum

import com.anish.momentum.data.BadgeKind
import com.anish.momentum.data.Badges
import com.anish.momentum.data.DayStat
import com.anish.momentum.data.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgesTest {

    /** [finished] per day, oldest first; [total] is the number of habits that day. */
    private fun days(finished: List<Int>, total: Int = 2) =
        finished.map { DayStat("2026-01-01", it, total) }

    private fun badge(id: String) = Badges.ALL.first { it.id == id }

    @Test
    fun `nothing is unlocked with no history`() {
        val progress = Badges.evaluate(bestStreak = 0, days = emptyList(), goal = 0)
        assertTrue(progress.none { it.unlocked })
    }

    @Test
    fun `badges unlock against the best streak not the current one`() {
        val progress = Badges.evaluate(bestStreak = 30, days = days(listOf(0)), goal = 0)
        val thirty = progress.first { it.badge.id == "streak_30" }
        assertTrue(thirty.unlocked)
        assertEquals(30, thirty.current)
    }

    @Test
    fun `current value is capped at the target`() {
        val progress = Badges.evaluate(bestStreak = 250, days = emptyList(), goal = 0)
        val hundred = progress.first { it.badge.id == "streak_100" }
        assertEquals(100, hundred.current)
        assertEquals(1f, hundred.progress, 0.001f)
    }

    @Test
    fun `partially complete progress reports current over target`() {
        val progress = Badges.evaluate(bestStreak = 5, days = days(listOf(0)), goal = 0)
        val seven = progress.first { it.badge.id == "streak_7" }
        assertFalse(seven.unlocked)
        assertEquals(5, seven.current)
        assertEquals(5f / 7f, seven.progress, 0.001f)
    }

    @Test
    fun `perfect week needs seven fully complete days`() {
        val complete = days(List(7) { 2 })
        assertEquals(7, Badges.longestPerfectRun(complete, goal = 0))

        val progress = Badges.evaluate(bestStreak = 7, days = complete, goal = 0)
        assertTrue(progress.first { it.badge.id == "perfect_week" }.unlocked)
    }

    @Test
    fun `a day that misses the goal breaks a perfect run`() {
        // Six full days, one at half.
        val almost = days(listOf(2, 2, 2, 2, 2, 2, 1))
        assertEquals(6, Badges.longestPerfectRun(almost, goal = 0))
    }

    @Test
    fun `a day meeting the goal counts as perfect even when partial`() {
        // 1 of 5 habits: not "everything", but the goal of 1 is met.
        val partialButOnTarget = days(List(7) { 1 }, total = 5)
        assertEquals(0, Badges.longestPerfectRun(partialButOnTarget, goal = 0))
        assertEquals(7, Badges.longestPerfectRun(partialButOnTarget, goal = 1))
    }

    @Test
    fun `perfect week and streak badges are judged independently`() {
        // Long run of goal-meeting days that are never *fully* complete.
        val stats = days(List(10) { 1 }, total = 5)
        val progress = Badges.evaluate(bestStreak = 10, days = stats, goal = 1)
        assertTrue(progress.first { it.badge.id == "streak_7" }.unlocked)
        assertTrue(progress.first { it.badge.id == "perfect_week" }.unlocked)
    }

    @Test
    fun `days with no habits never count toward a perfect run`() {
        val withGap = listOf(
            DayStat("2026-01-01", 0, 0),
            DayStat("2026-01-02", 2, 2),
            DayStat("2026-01-03", 2, 2)
        )
        assertEquals(2, Badges.longestPerfectRun(withGap, goal = 0))
        assertEquals(2, StreakCalculator.best(withGap, goal = 0))
    }

    @Test
    fun `every badge has a positive target and a kind`() {
        Badges.ALL.forEach {
            assertTrue("${it.id} needs a positive target", it.target > 0)
            assertTrue(it.kind in listOf(BadgeKind.STREAK, BadgeKind.PERFECT_WEEK))
        }
    }
}
