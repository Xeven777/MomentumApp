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
 * Square hero home screen widget, in the style of a modern Apple activity
 * widget: a ring that lights up the black card around it, the streak inside,
 * today's tally under it, then a title and a best-line. Tap anywhere to open
 * the app. Refreshed from the app whenever habits change, same as
 * [StreakWidget].
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
                    bind(appContext, views, state, id, ringDp(context, manager, id))
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
        ringDp: Int,
    ) {
        val density = context.resources.displayMetrics.density

        val ringSize = ringDp.dpToPx(density).toInt()
        views.setImageViewBitmap(
            R.id.widget_mini_ring,
            WidgetArtwork.ring(
                progress = state.todayProgress,
                streak = state.streak,
                sizePx = ringSize,
                centerTextSizePx = ringSize * NUMBER_RATIO,
                // Unused here: an empty caption paints a bare, centred number.
                captionTextSizePx = 0f,
                caption = "",
                style = WidgetArtwork.MINI
            )
        )

        views.setTextViewText(
            R.id.widget_mini_title,
            if (state.streak > 0) context.getString(R.string.streak_keep)
            else context.getString(R.string.streak_start)
        )
        views.setTextViewText(R.id.widget_mini_subtitle, todayLabel(context, state))

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

    /** The micro-line under the streak number: today's tally, as in the design. */
    private fun todayLabel(context: Context, state: WidgetState): String =
        if (state.todayTotal == 0) context.getString(R.string.widget_no_habits_yet)
        else context.getString(R.string.widget_today, state.todayDone, state.todayTotal)

    companion object {
        private const val TAG = "StreakMiniWidget"

        // Kept clear of StreakWidget's bases (1000/2000).
        private const val BODY_REQUEST_CODE = 3000

        /** Streak number height as a share of the ring's diameter. */
        private const val NUMBER_RATIO = 0.42f

        /**
         * The hero ring takes half the card's short side, like the activity
         * rings in a modern home screen widget. Derived from the size the
         * launcher actually gave us, because a 2x2 cell is anything from
         * ~110dp to ~150dp tall depending on the launcher: a fixed dp size is
         * either clipped on the small ones or lost in space on the big ones.
         * The share stays under half because the two caption lines below it
         * have to fit too, and readable type beats a bigger ring.
         */
        @Suppress("DEPRECATION")
        private fun ringDp(context: Context, manager: AppWidgetManager, appWidgetId: Int): Int {
            val options = manager.getAppWidgetOptions(appWidgetId)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val cardDp = minOf(widthDp, heightDp).takeIf { it > 0 } ?: FALLBACK_CARD_DP
            return (cardDp * RING_SHARE).toInt().coerceIn(MIN_RING_DP, MAX_RING_DP)
        }

        private const val RING_SHARE = 0.52f
        private const val MIN_RING_DP = 46
        private const val MAX_RING_DP = 100
        private const val FALLBACK_CARD_DP = 110

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
