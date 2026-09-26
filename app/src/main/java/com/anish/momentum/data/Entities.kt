package com.anish.momentum.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "emoji") val emoji: String,
    @ColumnInfo(name = "creation_date") val creationDate: String,
    @ColumnInfo(name = "has_reminder") val hasReminder: Boolean = false,
    @ColumnInfo(name = "reminder_time") val reminderTime: String = "",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "archived") val archived: Boolean = false,
    /** 7-bit mask of scheduled weekdays, see [Schedule]. 0 means every day. */
    @ColumnInfo(name = "schedule_mask") val scheduleMask: Int = Schedule.EVERY_DAY
)

/**
 * One row per habit per completed day. Replaces the old inline
 * `completionDates: MutableList<String>` so a single toggle writes one row
 * instead of rewriting the whole habit document.
 */
@Entity(
    tableName = "completions",
    primaryKeys = ["habit_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habit_id"), Index("date")]
)
data class CompletionEntity(
    @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "date") val date: String
)
