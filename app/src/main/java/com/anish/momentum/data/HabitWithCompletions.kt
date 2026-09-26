package com.anish.momentum.data

import androidx.room.Embedded
import androidx.room.Relation

/** A habit plus its completion dates, assembled by Room in a single query. */
data class HabitWithCompletions(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habit_id")
    val completions: List<CompletionEntity>
)
