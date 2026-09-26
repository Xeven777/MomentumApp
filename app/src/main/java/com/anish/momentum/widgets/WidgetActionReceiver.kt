package com.anish.momentum.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anish.momentum.utils.DateUtils
import com.anish.momentum.utils.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the widget's "+" button: ticks the first habit that is still pending
 * for today, without opening the app. If everything is already done it leaves
 * the list alone rather than silently un-ticking something.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StreakWidget.ACTION_TICK) return

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = DateUtils.today()
                val repository = ServiceLocator.habits
                val next = repository.habitsScheduledOn(today)
                    .firstOrNull { !it.completionDates.contains(today) }

                if (next != null) {
                    repository.setCompleted(next, today, true)
                    Log.d(TAG, "Ticked '${next.name}' from the widget")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Widget tick failed", e)
            } finally {
                pending.finish()
                StreakWidget.refresh(appContext)
            }
        }
    }

    private companion object {
        const val TAG = "WidgetActionReceiver"
    }
}
