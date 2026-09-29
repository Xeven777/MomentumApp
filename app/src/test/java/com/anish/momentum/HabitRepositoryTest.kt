package com.anish.momentum

import com.anish.momentum.data.CompletionEntity
import com.anish.momentum.data.HabitDao
import com.anish.momentum.data.HabitEntity
import com.anish.momentum.data.HabitRepository
import com.anish.momentum.data.HabitWithCompletions
import com.anish.momentum.data.Schedule
import com.anish.momentum.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Regression test for the dropped `scheduleMask` in the
 * `HabitEntity -> Habit` mapper: every read path must preserve the mask,
 * otherwise scheduling silently degrades to "every day".
 */
class HabitRepositoryTest {

    private class FakeDao(
        var habits: MutableList<HabitEntity> = mutableListOf(),
        var completions: MutableList<CompletionEntity> = mutableListOf()
    ) : HabitDao {
        override fun observeActiveHabits(): Flow<List<HabitWithCompletions>> =
            flowOf(activeWithCompletions())

        override fun observeAllHabits(): Flow<List<HabitWithCompletions>> =
            flowOf(habits.map { HabitWithCompletions(it, completions.filter { c -> c.habitId == it.id }) })

        override suspend fun getActiveHabits(): List<HabitEntity> =
            habits.filter { !it.archived }

        override suspend fun getHabitsWithReminders(): List<HabitEntity> =
            habits.filter { it.hasReminder && it.reminderTime.isNotEmpty() }

        override suspend fun getHabit(id: Long): HabitEntity? =
            habits.firstOrNull { it.id == id }

        override suspend fun findByNameAndEmoji(name: String, emoji: String): HabitEntity? =
            habits.firstOrNull { it.name == name && it.emoji == emoji && !it.archived }

        override suspend fun nextSortOrder(): Int =
            (habits.maxOfOrNull { it.sortOrder } ?: 0) + 1

        override suspend fun insert(habit: HabitEntity): Long {
            val id = (habits.maxOfOrNull { it.id } ?: 0) + 1
            habits.add(habit.copy(id = id))
            return id
        }

        override suspend fun update(habit: HabitEntity) {
            val i = habits.indexOfFirst { it.id == habit.id }
            if (i >= 0) habits[i] = habit
        }

        override suspend fun delete(id: Long) {
            habits.removeAll { it.id == id }
            completions.removeAll { it.habitId == id }
        }

        override suspend fun addCompletion(completion: CompletionEntity) {
            if (completions.none { it.habitId == completion.habitId && it.date == completion.date }) {
                completions.add(completion)
            }
        }

        override suspend fun removeCompletion(habitId: Long, date: String) {
            completions.removeAll { it.habitId == habitId && it.date == date }
        }

        override suspend fun isCompleted(habitId: Long, date: String): Boolean =
            completions.any { it.habitId == habitId && it.date == date }

        override fun observeCompletionsSince(since: String): Flow<List<CompletionEntity>> =
            flowOf(completions.filter { it.date >= since })

        override suspend fun getCompletionsSince(since: String): List<CompletionEntity> =
            completions.filter { it.date >= since }

        override suspend fun totalCompletions(): Int = completions.size

        private fun activeWithCompletions() = habits
            .filter { !it.archived }
            .map { HabitWithCompletions(it, completions.filter { c -> c.habitId == it.id }) }
    }

    private fun mondayMask() = Schedule.maskFor(setOf(Schedule.MONDAY))

    private fun nextWeekday(target: Int): String {
        val today = DateUtils.today()
        for (offset in 0..7) {
            val date = DateUtils.plusDays(today, offset)
            val cal = Calendar.getInstance().apply { time = DateUtils.parse(date)!! }
            if (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY == target) return date
        }
        return today
    }

    @Test
    fun `scheduleMask survives a read from the database`() = runBlocking {
        val mask = Schedule.maskFor(setOf(Schedule.MONDAY, Schedule.WEDNESDAY, Schedule.FRIDAY))
        val dao = FakeDao(
            mutableListOf(
                HabitEntity(id = 1, name = "Run", emoji = "🏃", creationDate = DateUtils.today(), scheduleMask = mask)
            )
        )
        val habits = HabitRepository(dao).getActiveHabits()
        assertEquals(1, habits.size)
        assertEquals(mask, habits[0].scheduleMask)
    }

    @Test
    fun `updateHabit persists an edited schedule`() = runBlocking {
        val dao = FakeDao(
            mutableListOf(
                HabitEntity(id = 1, name = "Run", emoji = "🏃", creationDate = DateUtils.today())
            )
        )
        val repo = HabitRepository(dao)
        val habit = repo.getActiveHabits().first()
        habit.scheduleMask = mondayMask()
        repo.updateHabit(habit)
        assertEquals(mondayMask(), repo.getActiveHabits().first().scheduleMask)
    }

    @Test
    fun `habitsScheduledOn respects the weekday mask`() = runBlocking {
        val monday = nextWeekday(Schedule.MONDAY)
        val tuesday = DateUtils.plusDays(monday, 1)
        val dao = FakeDao(
            mutableListOf(
                HabitEntity(
                    id = 1, name = "Monday only", emoji = "📅",
                    creationDate = monday, scheduleMask = mondayMask()
                )
            )
        )
        val repo = HabitRepository(dao)
        assertEquals(1, repo.habitsScheduledOn(monday).size)
        assertTrue(repo.habitsScheduledOn(tuesday).isEmpty())
    }

    @Test
    fun `dayStatsBetween does not count off-schedule habits in the total`() = runBlocking {
        val monday = nextWeekday(Schedule.MONDAY)
        val tuesday = DateUtils.plusDays(monday, 1)
        val dao = FakeDao(
            mutableListOf(
                HabitEntity(
                    id = 1, name = "Monday only", emoji = "📅",
                    creationDate = monday, scheduleMask = mondayMask()
                )
            )
        )
        val repo = HabitRepository(dao)
        val stats = repo.dayStatsBetween(monday, tuesday)
        assertEquals(2, stats.size)
        assertEquals(1, stats.first { it.date == monday }.totalTasks)
        assertEquals(0, stats.first { it.date == tuesday }.totalTasks)
    }

    @Test
    fun `statsSince counts only scheduled days as possible`() = runBlocking {
        val monday = nextWeekday(Schedule.MONDAY)
        val tuesday = DateUtils.plusDays(monday, 1)
        val dao = FakeDao(
            mutableListOf(
                HabitEntity(
                    id = 1, name = "Monday only", emoji = "📅",
                    creationDate = monday, scheduleMask = mondayMask()
                )
            )
        )
        val repo = HabitRepository(dao)
        // Window covers Monday..today, but only Mondays are possible.
        val mondays = generateSequence(monday) { DateUtils.plusDays(it, 1) }
            .takeWhile { it <= DateUtils.today() }
            .count { Schedule.isScheduledOn(mondayMask(), it) }
        val stats = repo.statsSince(monday)
        assertEquals(1, stats.size)
        assertEquals(mondays, stats[0].totalPossible)
        assertEquals(0, stats[0].totalDone)
    }
}
