package com.anish.momentum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.anish.momentum.data.BadgeProgress
import com.anish.momentum.data.Badges
import com.anish.momentum.data.DayStat
import com.anish.momentum.data.HabitRepository
import com.anish.momentum.data.Schedule
import com.anish.momentum.data.StreakCalculator
import com.anish.momentum.databinding.ActivityStatsBinding
import com.anish.momentum.databinding.ItemBadgeBinding
import com.anish.momentum.databinding.ItemHabitStatBinding
import com.anish.momentum.ui.Artwork
import com.anish.momentum.ui.BarChartView
import com.anish.momentum.utils.DateUtils
import com.anish.momentum.utils.ServiceLocator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Everything the app knows about your consistency: headline streaks, a
 * six-month contribution grid, an eight-week trend, per-habit rates and badges.
 */
class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    private val repository: HabitRepository get() = ServiceLocator.habits
    private val settings get() = ServiceLocator.settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.close.setOnClickListener { finish() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settings.dailyGoal.collect { goal ->
                    render(goal)
                }
            }
        }
    }

    private suspend fun render(goal: Int) {
        val weeks = HEATMAP_WEEKS
        val since = DateUtils.daysAgo(weeks * Artwork.DAYS_PER_WEEK)
        val stats = repository.dayStatsSince(since)

        renderHeadline(stats, goal)
        renderHeatmap(stats)
        renderWeeklyTrend(stats)
        renderPerHabit(stats)
        renderBadges(stats, goal)
    }

    private fun renderHeadline(stats: List<DayStat>, goal: Int) {
        binding.statCurrentStreak.text = StreakCalculator.current(stats, goal).toString()
        binding.statBestStreak.text = StreakCalculator.best(stats, goal).toString()
        binding.statTotal.text = stats.sumOf { it.finishedTasks }.toString()
    }

    /** Reuses the widget's grid logic so both views stay identical. */
    private fun renderHeatmap(stats: List<DayStat>) {
        val state = com.anish.momentum.widgets.WidgetStateFactory.build(
            stats = stats,
            goal = 0,
            heatWeeks = HEATMAP_WEEKS
        )
        binding.yearHeatmap.setData(HEATMAP_WEEKS, state.ratios, state.present)
    }

    private fun renderWeeklyTrend(stats: List<DayStat>) {
        val bars = mutableListOf<BarChartView.Bar>()
        val byDate = stats.associateBy { it.date }

        // Oldest week first so the chart reads left to right.
        for (week in WEEKS_SHOWN - 1 downTo 0) {
            var done = 0
            for (day in 0 until Artwork.DAYS_PER_WEEK) {
                val date = DateUtils.daysAgo(week * Artwork.DAYS_PER_WEEK + (Artwork.DAYS_PER_WEEK - 1 - day))
                byDate[date]?.let { done += it.finishedTasks }
            }
            val label = SimpleDateFormat("d MMM", Locale.getDefault()).format(
                DateUtils.parse(DateUtils.daysAgo(week * Artwork.DAYS_PER_WEEK + 6)) ?: System.currentTimeMillis()
            )
            bars.add(BarChartView.Bar(label = "W${WEEKS_SHOWN - week}", value = done.toFloat(), highlight = week == 0))
        }
        binding.weeklyChart.setBars(bars)
    }

    private suspend fun renderPerHabit(stats: List<DayStat>) {
        val since = DateUtils.daysAgo(PER_HABIT_DAYS)
        val completionsByHabit = repository.statsSince(since)
        val habits = repository.getActiveHabits()
        val nameById = habits.associate { it.id.toLongOrNull() to it }

        binding.habitStatsContainer.removeAllViews()
        if (habits.isEmpty()) {
            binding.noHabitsText.visibility = View.VISIBLE
            return
        }
        binding.noHabitsText.visibility = View.GONE

        for (habit in habits) {
            val id = habit.id.toLongOrNull() ?: continue
            val stat = completionsByHabit.firstOrNull { it.habitId == id } ?: continue
            val totalPossible = habitPossibleDays(habit.creationDate, since, habit.scheduleMask)
            val percent = if (totalPossible == 0) 0f
            else (stat.totalDone.toFloat() / totalPossible * 100f).coerceAtMost(100f)

            val row = ItemHabitStatBinding.inflate(
                LayoutInflater.from(this), binding.habitStatsContainer, false
            )
            row.habitRing.set(progress = percent / 100f, valueText = "${percent.toInt()}%", caption = "")
            row.habitStatName.text = "${habit.emoji} ${habit.name}"
            row.habitStatDetail.text = buildString {
                append("${stat.totalDone} of $totalPossible days")
                if (!Schedule.isEveryDay(habit.scheduleMask)) {
                    append(" · ${Schedule.describe(habit.scheduleMask)}")
                }
            }
            binding.habitStatsContainer.addView(row.root)
        }
    }

    /** How many days the habit was actually scheduled for within the window. */
    private fun habitPossibleDays(creationDate: String, since: String, scheduleMask: Int): Int {
        var count = 0
        var date = maxOf(creationDate, since)
        val limit = DateUtils.today()
        while (date <= limit) {
            if (Schedule.isScheduledOn(scheduleMask, date)) count++
            date = DateUtils.plusDays(date, 1)
        }
        return count
    }

    private fun renderBadges(stats: List<DayStat>, goal: Int) {
        val bestStreak = StreakCalculator.best(stats, goal)
        val progresses = Badges.evaluate(bestStreak, stats, goal)

        binding.badgeContainer.removeAllViews()
        progresses.forEach { progress ->
            val row = ItemBadgeBinding.inflate(
                LayoutInflater.from(this), binding.badgeContainer, false
            )
            row.badgeEmoji.text = badgeEmoji(progress)
            row.badgeTitle.text = progress.badge.title
            row.badgeTitle.alpha = if (progress.unlocked) 1f else 0.6f
            row.badgeSubtitle.text = if (progress.unlocked) {
                "${progress.badge.subtitle} · unlocked"
            } else {
                "${progress.badge.subtitle} · ${progress.current}/${progress.badge.target}"
            }
            row.badgeProgress.progress = (progress.progress * 100).toInt()
            binding.badgeContainer.addView(row.root)
        }
    }

    private fun badgeEmoji(progress: BadgeProgress): String = when {
        progress.badge.id == "perfect_week" -> "\u2728"
        progress.unlocked -> "\uD83C\uDFC5"
        else -> "\uD83D\uDD12"
    }

    private companion object {
        const val HEATMAP_WEEKS = 26
        const val WEEKS_SHOWN = 8
        const val PER_HABIT_DAYS = 30
    }
}
