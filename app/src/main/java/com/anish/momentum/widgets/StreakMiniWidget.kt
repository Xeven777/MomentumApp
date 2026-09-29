package com.anish.momentum.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.anish.momentum.MainActivity
import com.anish.momentum.R
import com.anish.momentum.ui.Artwork
import com.anish.momentum.utils.DateUtils
import com.anish.momentum.utils.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Compact 2x2 home screen widget: just the streak ring. Tap anywhere to open
 * the app. Refreshed from the app whenever habits change, same as [StreakWidget].
 */
class StreakMiniWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        render(context, manager, intArrayOf(appWidgetId))
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = loadState(appContext)
                for (id in ids) {
                    val views = RemoteViews(appContext.packageName, R.layout.widget_momentum_mini)
                    bind(appContext, views, state, id)
                    manager.updateAppWidget(id, views)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not refresh mini widget", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun loadState(context: Context): WidgetState {
        val goal = ServiceLocator.settings.currentDailyGoal()
        val since = DateUtils.daysAgo(WidgetState.DEFAULT_WEEKS * Artwork.DAYS_PER_WEEK)
        val stats = ServiceLocator.habits.dayStatsSince(since)
        return WidgetStateFactory.build(stats, goal)
    }

    private fun bind(
        context: Context,
        views: RemoteViews,
        state: WidgetState,
        appWidgetId: Int,
    ) {
        val density = context.resources.displayMetrics.density

        val ringSize = 84.dpToPx(density).toInt()
        views.setImageViewBitmap(
            R.id.widget_mini_ring,
            WidgetArtwork.ring(
                progress = state.todayProgress,
                streak = state.streak,
                sizePx = ringSize,
                centerTextSizePx = ringSize * 0.34f,
                captionTextSizePx = ringSize * 0.105f,
                caption = context.getString(R.string.widget_day_streak)
            )
        )

        views.setOnClickPendingIntent(
            R.id.widget_mini_body,
            PendingIntent.getActivity(
                context,
                BODY_REQUEST_CODE + appWidgetId,
                Intent(context, MainActivity::class.java).apply {
                    data = android.net.Uri.parse("momentum://widget/open/mini/$appWidgetId")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    companion object {
        private const val TAG = "StreakMiniWidget"

        // Kept clear of StreakWidget's bases (1000/2000).
        private const val BODY_REQUEST_CODE = 3000

        /** Asks every placed mini widget to redraw. Cheap and safe to call often. */
        fun refresh(context: Context) {
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, StreakMiniWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }

        fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, StreakMiniWidget::class.java))

        private fun Int.dpToPx(density: Float): Float = this * density
    }
}
