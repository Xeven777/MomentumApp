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
    val totalPossible: Int,
    val totalDone: Int
) {
    val rate: Float
        get() = if (totalPossible == 0) 0f else totalDone.toFloat() / totalPossible
}
