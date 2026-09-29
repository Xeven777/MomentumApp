package com.anish.momentum.data

/** Completion status of a single calendar day. */
data class DayStat(
    val date: String,
    val finishedTasks: Int,
    val totalTasks: Int
)

/** Aggregates for the stats screen. */
data class HabitStats(
    val habitId: Long,
    /** Days in the window the habit was actually scheduled for. */
    val totalPossible: Int,
    val totalDone: Int
)
