package com.anish.momentum.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.anish.momentum.models.Habit
import java.util.*

object ReminderUtils {

    /** "HH:mm" storage format → "h:mm a" display format ("14:30" → "2:30 PM").
     *  Returns the input unchanged if it does not parse, so bad data never
     *  blanks the UI. */
    fun formatTo12Hour(hhMm: String): String {
        val parts = hhMm.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) return hhMm
        val suffix = if (hour < 12) "AM" else "PM"
        val hour12 = when (hour % 12) {
            0 -> 12
            else -> hour % 12
        }
        return "$hour12:${minute.toString().padStart(2, '0')} $suffix"
    }

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
        val triggerAt = calendar.timeInMillis
        val pending = buildPendingIntent(context, habit)
        // setRepeating is inexact and paused by Doze, so reminders arrived
        // hours late or never. Fire exact once, the receiver re-arms tomorrow.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pending
                )
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
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
