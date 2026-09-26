package com.anish.momentum.data

/**
 * Pure streak math, kept free of Android/Room so it can be unit tested.
 *
 * A day counts as complete when the number of ticked habits reaches the target:
 * every habit that existed that day, or the user's daily goal when one is set
 * (never more than the habits that actually existed).
 */
object StreakCalculator {

    fun targetFor(day: DayStat, goal: Int): Int {
        if (day.totalTasks == 0) return 0
        return if (goal <= 0) day.totalTasks else minOf(goal, day.totalTasks)
    }

    fun isComplete(day: DayStat, goal: Int): Boolean =
        day.totalTasks > 0 && day.finishedTasks >= targetFor(day, goal)

    /**
     * Consecutive complete days ending today. Today still being open does not
     * break the streak — the run is counted from yesterday in that case.
     */
    fun current(days: List<DayStat>, goal: Int): Int {
        if (days.isEmpty()) return 0
        var streak = 0
        for (i in days.indices.reversed()) {
            val day = days[i]
            if (isComplete(day, goal)) {
                streak++
            } else if (i == days.lastIndex) {
                continue // today is not finished yet, keep counting backwards
            } else {
                break
            }
        }
        return streak
    }

    /** Longest run of complete days in the given window. */
    fun best(days: List<DayStat>, goal: Int): Int {
        var best = 0
        var run = 0
        for (day in days) {
            if (isComplete(day, goal)) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }
}
