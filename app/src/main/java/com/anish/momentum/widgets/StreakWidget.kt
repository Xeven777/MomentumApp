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
 * Home screen widget. Renders a small layout (streak ring + today's progress)
 * and, when there is room, a GitHub-style contribution grid.
 *
 * There is no `updatePeriodMillis`: the widget is refreshed from the app
 * whenever habits change, which is both cheaper and more accurate than waking
 * the CPU every hour to re-read data that has not moved.
 */
class StreakWidget : AppWidgetProvider() {

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
                    val views = RemoteViews(appContext.packageName, R.layout.widget_momentum)
                    bind(appContext, views, state, id)
                    manager.updateAppWidget(id, views)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not refresh widget", e)
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

        val ringSize = 104.dpToPx(density).toInt()
        views.setImageViewBitmap(
            R.id.widget_ring,
            WidgetArtwork.ring(
                progress = state.todayProgress,
                streak = state.streak,
                sizePx = ringSize,
                centerTextSizePx = ringSize * 0.34f,
                captionTextSizePx = ringSize * 0.07f,
                caption = context.getString(R.string.widget_day_streak)
            )
        )

        val todayLabel = if (state.todayTotal == 0) {
            context.getString(R.string.widget_no_habits_yet)
        } else {
            context.getString(R.string.widget_today, state.todayDone, state.todayTotal)
        }
        views.setTextViewText(R.id.widget_today, todayLabel)

        val goalLabel = if (state.goal <= 0) {
            context.getString(R.string.widget_goal_all_habits)
        } else {
            context.getString(R.string.widget_goal_target, state.goal)
        }
        views.setTextViewText(R.id.widget_goal, goalLabel)

        views.setTextViewText(
            R.id.widget_best,
            context.resources.getQuantityString(
                R.plurals.widget_best_summary,
                state.totalCompletions,
                state.bestStreak,
                state.totalCompletions
            )
        )

        // Always render the grid: it is the main reason the widget exists, and
        // hiding it left a big empty black block. A high-res bitmap keeps cells
        // crisp on every density; the ImageView scales it down to fit.
        val heatmapWidth = 360.dpToPx(density).toInt()
        views.setImageViewBitmap(
            R.id.widget_heatmap,
            WidgetArtwork.heatmap(
                weeks = state.heatWeeks,
                values = state.ratios,
                present = state.present,
                widthPx = heatmapWidth
            )
        )

        // Tap anywhere on the widget to open the app.
        views.setOnClickPendingIntent(
            R.id.widget_body,
            PendingIntent.getActivity(
                context,
                BODY_REQUEST_CODE + appWidgetId,
                Intent(context, MainActivity::class.java).apply {
                    data = android.net.Uri.parse("momentum://widget/open/main/$appWidgetId")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    companion object {
        private const val TAG = "StreakWidget"

        // Unique base so this provider's PendingIntent can never collide with
        // the mini widget's.
        private const val BODY_REQUEST_CODE = 1000

        /** Asks every placed widget to redraw. Cheap and safe to call often. */
        fun refresh(context: Context) {
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, StreakWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }

        fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, StreakWidget::class.java))

        private fun Int.dpToPx(density: Float): Float = this * density
    }
}
