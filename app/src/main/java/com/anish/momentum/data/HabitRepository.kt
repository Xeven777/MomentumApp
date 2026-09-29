package com.anish.momentum.data

import com.anish.momentum.models.Habit
import com.anish.momentum.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single entry point for habit data. Everything is local: there is no network
 * layer and no remote source of truth.
 */
class HabitRepository(private val dao: HabitDao) {

    // ---------------------------------------------------------------- reads

    fun observeHabits(): Flow<List<Habit>> =
        dao.observeActiveHabits().map { rows -> rows.map { it.toDomain() } }

    fun observeAllHabits(): Flow<List<Habit>> =
        dao.observeAllHabits().map { rows -> rows.map { it.toDomain() } }

    suspend fun getActiveHabits(): List<Habit> = dao.getActiveHabits().map { it.toDomain() }

    suspend fun getHabitsWithReminders(): List<Habit> =
        dao.getHabitsWithReminders().map { it.toDomain() }

    /**
     * Per-day completion counts for the window starting at [since].
     * `totalTasks` counts only habits that already existed on that day, so
     * adding a habit today does not retroactively break past days.
     */
    suspend fun dayStatsSince(since: String): List<DayStat> =
        dayStatsBetween(since, DateUtils.today())

    /** Per-day counts for an inclusive [start]..[end] window. */
    suspend fun dayStatsBetween(start: String, end: String): List<DayStat> {
        val habits = dao.getActiveHabits()
        val completions = dao.getCompletionsSince(start)
        val donePerDay = completions.groupingBy { it.date }.eachCount()

        val days = generateSequence(DateUtils.parse(start)) { date ->
            val cal = java.util.Calendar.getInstance().apply { time = date }
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            cal.time
        }.take(MAX_WINDOW_DAYS)
            .takeWhile { DateUtils.format(it) <= end }
            .toList()

        return days.map { day ->
            val dateStr = DateUtils.format(day)
            // A habit only counts for days it is actually scheduled on, so a
            // Mon/Wed/Fri habit does not drag the total down on a Tuesday.
            val total = habits.count { habit ->
                habit.creationDate <= dateStr && Schedule.isScheduledOn(habit.scheduleMask, dateStr)
            }
            DayStat(dateStr, donePerDay[dateStr] ?: 0, total)
        }
    }

    /**
     * Consecutive completed days ending today (or yesterday, so the streak is
     * not lost before you tick today off).
     */
    suspend fun currentStreak(goal: Int): Int =
        StreakCalculator.current(dayStatsSince(DateUtils.daysAgo(STREAK_WINDOW_DAYS)), goal)

    /** Longest run of completed days in the last [days] days. */
    suspend fun bestStreak(days: Int = STREAK_WINDOW_DAYS, goal: Int = 0): Int =
        StreakCalculator.best(dayStatsSince(DateUtils.daysAgo(days)), goal)

    suspend fun statsSince(since: String): List<HabitStats> {
        val habits = dao.getActiveHabits()
        val completions = dao.getCompletionsSince(since)
        return habits.map { habit ->
            HabitStats(
                habitId = habit.id,
                totalPossible = possibleDays(habit.creationDate, since, habit.scheduleMask),
                totalDone = completions.count { it.habitId == habit.id }
            )
        }
    }

    suspend fun totalCompletions(): Int = dao.totalCompletions()

    /** Habits that apply on [date] (created, not archived, scheduled that weekday). */
    suspend fun habitsScheduledOn(date: String): List<Habit> = getActiveHabits()
        .filter { it.creationDate <= date && Schedule.isScheduledOn(it.scheduleMask, date) }

    suspend fun findHabit(name: String, emoji: String): Habit? =
        dao.findByNameAndEmoji(name, emoji)?.toDomain()

    // --------------------------------------------------------------- writes

    suspend fun addHabit(
        name: String,
        emoji: String,
        hasReminder: Boolean = false,
        reminderTime: String = "",
        scheduleMask: Int = Schedule.EVERY_DAY,
        creationDate: String = DateUtils.today()
    ): Habit {
        val id = dao.insert(
            HabitEntity(
                name = name,
                emoji = emoji,
                creationDate = creationDate,
                hasReminder = hasReminder,
                reminderTime = if (hasReminder) reminderTime else "",
                sortOrder = dao.nextSortOrder(),
                scheduleMask = scheduleMask
            )
        )
        return Habit(
            id = id.toString(),
            name = name,
            emoji = emoji,
            hasReminder = hasReminder,
            reminderTime = if (hasReminder) reminderTime else "",
            creationDate = creationDate,
            scheduleMask = scheduleMask
        )
    }

    /** Adds the habit only if no active habit with the same name + emoji exists. */
    suspend fun addHabitIfAbsent(name: String, emoji: String): Habit? {
        if (dao.findByNameAndEmoji(name, emoji) != null) return null
        return addHabit(name, emoji)
    }

    suspend fun updateHabit(habit: Habit) {
        val id = habit.id.toLongOrNull() ?: return
        val existing = dao.getHabit(id) ?: return
        dao.update(
            existing.copy(
                name = habit.name,
                emoji = habit.emoji,
                hasReminder = habit.hasReminder,
                reminderTime = if (habit.hasReminder) habit.reminderTime else "",
                scheduleMask = habit.scheduleMask
            )
        )
    }

    suspend fun deleteHabit(habit: Habit) {
        val id = habit.id.toLongOrNull() ?: return
        dao.delete(id)
    }

    /** Ticks / unticks a habit for a given day. Returns the new state. */
    suspend fun setCompleted(habit: Habit, date: String, completed: Boolean): Boolean {
        val id = habit.id.toLongOrNull() ?: return false
        if (completed) {
            dao.addCompletion(CompletionEntity(id, date))
        } else {
            dao.removeCompletion(id, date)
        }
        return completed
    }

    suspend fun isCompleted(habit: Habit, date: String): Boolean {
        val id = habit.id.toLongOrNull() ?: return false
        return dao.isCompleted(id, date)
    }

    /** How many days the habit was actually scheduled for within [since]..today. */
    private fun possibleDays(creationDate: String, since: String, scheduleMask: Int): Int {
        var count = 0
        var date = maxOf(creationDate, since)
        val limit = DateUtils.today()
        while (date <= limit) {
            if (Schedule.isScheduledOn(scheduleMask, date)) count++
            date = DateUtils.plusDays(date, 1)
        }
        return count
    }

    private fun HabitWithCompletions.toDomain() = habit.toDomain(
        completions.map { it.date }.toMutableList()
    )

    private fun HabitEntity.toDomain(completionDates: MutableList<String> = mutableListOf()) = Habit(
        id = id.toString(),
        name = name,
        emoji = emoji,
        isDone = false,
        hasReminder = hasReminder,
        reminderTime = reminderTime,
        creationDate = creationDate,
        scheduleMask = scheduleMask,
        completionDates = completionDates
    )

    companion object {
        private const val MAX_WINDOW_DAYS = 400
        private const val STREAK_WINDOW_DAYS = 365
    }
}
