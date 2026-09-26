package com.anish.momentum.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Transaction
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sort_order ASC, id ASC")
    fun observeActiveHabits(): Flow<List<HabitWithCompletions>>

    @Transaction
    @Query("SELECT * FROM habits ORDER BY sort_order ASC, id ASC")
    fun observeAllHabits(): Flow<List<HabitWithCompletions>>

    @Query("SELECT * FROM habits WHERE archived = 0")
    suspend fun getActiveHabits(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE has_reminder = 1 AND reminder_time != ''")
    suspend fun getHabitsWithReminders(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabit(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE name = :name AND emoji = :emoji AND archived = 0 LIMIT 1")
    suspend fun findByNameAndEmoji(name: String, emoji: String): HabitEntity?

    @Query("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM habits")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCompletion(completion: CompletionEntity)

    @Query("DELETE FROM completions WHERE habit_id = :habitId AND date = :date")
    suspend fun removeCompletion(habitId: Long, date: String)

    @Query("SELECT EXISTS(SELECT 1 FROM completions WHERE habit_id = :habitId AND date = :date)")
    suspend fun isCompleted(habitId: Long, date: String): Boolean

    @Query("SELECT * FROM completions WHERE date >= :since ORDER BY date ASC")
    fun observeCompletionsSince(since: String): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE date >= :since ORDER BY date ASC")
    suspend fun getCompletionsSince(since: String): List<CompletionEntity>

    @Query("SELECT COUNT(*) FROM completions")
    suspend fun totalCompletions(): Int
}
