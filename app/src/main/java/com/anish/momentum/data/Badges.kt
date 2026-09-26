package com.anish.momentum.data

enum class BadgeKind { STREAK, PERFECT_WEEK }

/**
 * A milestone. [target] is the streak length (or number of perfect weeks) needed
 * to unlock it, and it is measured against the *best* streak so unlocking is
 * permanent rather than something you can lose.
 */
data class Badge(
    val id: String,
    val title: String,
    val subtitle: String,
    val target: Int,
    val kind: BadgeKind
)

data class BadgeProgress(
    val badge: Badge,
    val current: Int,
    val unlocked: Boolean
) {
    val progress: Float
        get() = if (badge.target <= 0) 1f
        else (current.toFloat() / badge.target).coerceIn(0f, 1f)
}

object Badges {

    val ALL: List<Badge> = listOf(
        Badge("streak_3", "Warming up", "3 day streak", 3, BadgeKind.STREAK),
        Badge("streak_7", "Week warrior", "7 day streak", 7, BadgeKind.STREAK),
        Badge("streak_30", "Month of momentum", "30 day streak", 30, BadgeKind.STREAK),
        Badge("streak_100", "Century", "100 day streak", 100, BadgeKind.STREAK),
        Badge("perfect_week", "Perfect week", "7 perfect days in a row", 7, BadgeKind.PERFECT_WEEK)
    )

    /**
     * Longest run of days that fully met the goal. Tracked separately from the
     * streak, which a daily goal can also satisfy partially.
     */
    fun longestPerfectRun(days: List<DayStat>, goal: Int): Int {
        var best = 0
        var run = 0
        for (day in days) {
            if (StreakCalculator.isComplete(day, goal)) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }

    fun evaluate(
        bestStreak: Int,
        days: List<DayStat>,
        goal: Int
    ): List<BadgeProgress> {
        val perfectRun = longestPerfectRun(days, goal)
        return ALL.map { badge ->
            val current = when (badge.kind) {
                BadgeKind.STREAK -> bestStreak
                BadgeKind.PERFECT_WEEK -> perfectRun
            }
            BadgeProgress(
                badge = badge,
                current = current.coerceAtMost(badge.target),
                unlocked = current >= badge.target
            )
        }
    }
}
