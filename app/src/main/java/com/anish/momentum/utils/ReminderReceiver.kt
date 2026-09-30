package com.anish.momentum.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.anish.momentum.MainActivity
import com.anish.momentum.R
import com.anish.momentum.data.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shows a habit reminder. The alarm fires exact once and re-arms tomorrow,
 * so a habit scheduled for Mon/Wed/Fri is checked here and stays quiet
 * on the days it is not due.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
        val habitName = intent.getStringExtra("habitName") ?: "Habit Reminder"
        val habitEmoji = intent.getStringExtra("habitEmoji") ?: "✒️"

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val habit = ServiceLocator.habits
                    .getActiveHabits()
                    .firstOrNull { it.id == habitId }

                if (habit != null) {
                    // Exact alarms fire once, so re-arm tomorrow before anything else.
                    try {
                        ReminderUtils.scheduleHabitReminder(appContext, habit)
                    } catch (e: Exception) {
                        Log.e("ReminderReceiver", "Could not re-arm reminder", e)
                    }
                    if (!habit.hasReminder) return@launch
                    if (!Schedule.isScheduledOn(habit.scheduleMask, DateUtils.today())) {
                        Log.d("ReminderReceiver", "Skipping '$habitName', not scheduled today")
                        return@launch
                    }
                }
                notify(appContext, habitId, habitEmoji, habitName)
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Could not show reminder", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, habitId: String, habitEmoji: String, habitName: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = CHANNEL_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Habit Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("ReminderReceiver", "Alarm triggered for: $habitName")

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("$habitEmoji $habitName")
            .setContentText(nudgeFor(habitId))
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudgeFor(habitId)))
            .setSmallIcon(R.drawable.fire)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(habitId.hashCode(), notification)
    }

    private fun nudgeFor(habitId: String): String {
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        val index = Math.floorMod(day + habitId.hashCode(), NUDGES.size)
        return NUDGES[index]
    }

    companion object {
        const val CHANNEL_ID = "habit_channel"
        const val EXTRA_HABIT_ID = "habitId"

        private val NUDGES = listOf(
            "A small session still counts today.",
            "Go slow if you need to. Still show up.",
            "Today is a good day to keep this going.",
            "You kept this up before. Do one round now.",
            "No rush. Start with the first minute.",
            "This counts even when it feels small.",
            "One honest effort is enough today.",
            "Your future self will thank you for today."
        )
    }
}
