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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            render(context, AppWidgetManager.getInstance(context), widgetIds(context))
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = loadState(appContext)
                for (id in ids) {
                    val options = manager.getAppWidgetOptions(id)
                    val wide = isWide(options)
                    val views = RemoteViews(appContext.packageName, R.layout.widget_momentum)
                    bind(appContext, views, state, wide)
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

    private fun isWide(options: Bundle?): Boolean {
        if (options == null) return false
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return minWidth >= WIDE_MIN_WIDTH_DP || minHeight >= WIDE_MIN_HEIGHT_DP
    }

    private fun bind(
        context: Context,
        views: RemoteViews,
        state: WidgetState,
        wide: Boolean
    ) {
        val density = context.resources.displayMetrics.density
        val gone = android.view.View.GONE
        val visible = android.view.View.VISIBLE

        // One hierarchy serves both sizes: the grid and the extra line only
        // appear when the widget is large enough to show them.
        views.setViewVisibility(R.id.widget_heatmap, if (wide) visible else gone)
        views.setViewVisibility(R.id.widget_best, if (wide) visible else gone)

        val ringSize = 84.dpToPx(density).toInt()
        views.setImageViewBitmap(
            R.id.widget_ring,
            WidgetArtwork.ring(
                progress = state.todayProgress,
                streak = state.streak,
                sizePx = ringSize,
                centerTextSizePx = ringSize * 0.34f,
                captionTextSizePx = ringSize * 0.10f
            )
        )

        val todayLabel = if (state.todayTotal == 0) "No habits yet"
        else "Today ${state.todayDone}/${state.todayTotal}"
        views.setTextViewText(R.id.widget_today, todayLabel)

        val goalLabel = if (state.goal <= 0) "All habits" else "Goal ${state.goal}"
        views.setTextViewText(R.id.widget_goal, goalLabel)

        views.setTextViewText(
            R.id.widget_best,
            "Best ${state.bestStreak} · ${state.totalCompletions} done"
        )

        if (wide) {
            val heatmapSize = (state.heatWeeks * 16).dpToPx(density).toInt()
            views.setImageViewBitmap(
                R.id.widget_heatmap,
                WidgetArtwork.heatmap(
                    weeks = state.heatWeeks,
                    values = state.ratios,
                    present = state.present,
                    sizePx = heatmapSize
                )
            )
        }

        // Tap the body to open the app, the + to tick the next habit.
        views.setOnClickPendingIntent(
            R.id.widget_body,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_tick,
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, WidgetActionReceiver::class.java).apply {
                    action = ACTION_TICK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    companion object {
        private const val TAG = "StreakWidget"
        const val ACTION_REFRESH = "com.anish.momentum.action.WIDGET_REFRESH"
        const val ACTION_TICK = "com.anish.momentum.action.WIDGET_TICK"

        private const val WIDE_MIN_WIDTH_DP = 250
        private const val WIDE_MIN_HEIGHT_DP = 110

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
