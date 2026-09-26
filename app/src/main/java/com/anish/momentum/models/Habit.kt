package com.anish.momentum.models

import com.anish.momentum.data.Schedule

data class Habit(
    var id: String = "",
    var name: String = "",
    var emoji: String = "",
    var isDone: Boolean = false,
    var hasReminder: Boolean = false,
    var reminderTime: String = "",
    var creationDate: String = "",
    var scheduleMask: Int = Schedule.EVERY_DAY,
    var completionDates: MutableList<String> = mutableListOf()

)