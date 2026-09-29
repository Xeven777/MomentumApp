package com.anish.momentum.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.anish.momentum.models.Habit
import java.util.*

object ReminderUtils {

    private fun buildPendingIntent(context: Context, habit: Habit): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("habitName", habit.name)
            putExtra("habitEmoji", habit.emoji)
            putExtra(ReminderReceiver.EXTRA_HABIT_ID, habit.id)
        }
        return PendingIntent.getBroadcast(
            context,
            habit.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleHabitReminder(context: Context, habit: Habit) {
        if (!habit.hasReminder || habit.reminderTime.isEmpty()) {
            cancelHabitReminder(context, habit)
            return
        }

        val timeParts = habit.reminderTime.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: return
        if (hour !in 0..23 || minute !in 0..59) return

        val now = Calendar.getInstance()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            buildPendingIntent(context, habit)
        )
    }

    fun cancelHabitReminder(context: Context, habit: Habit) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val existing = PendingIntent.getBroadcast(
            context,
            habit.id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }
}
