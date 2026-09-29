package com.anish.momentum.ui

import java.util.Calendar

/**
 * Lines shown on the home screen's quote card.
 *
 * Deliberately unattributed: these are written for the app rather than quoted
 * from named people, so there is nothing to misattribute. The pick is stable
 * for a whole calendar day, so the card does not reshuffle every time the
 * activity is recreated.
 */
object Quotes {

    val LINES = listOf(
        "Discipline today builds the life you want tomorrow.",
        "Small repetitions, quietly repeated, are the whole trick.",
        "You don't need motivation. You need a smaller first step.",
        "Miss once and it's an accident. Miss twice and it's a new habit.",
        "The streak isn't the point. Showing up is.",
        "Progress hides in the days that feel unremarkable.",
        "Start before you feel ready — ready rarely arrives.",
        "Consistency is just a decision you keep making."
    )

    /** The line for today, rotating through [LINES] one per calendar day. */
    fun forToday(): String {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return LINES[dayOfYear % LINES.size]
    }
}
