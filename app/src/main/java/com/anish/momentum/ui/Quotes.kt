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
        "Consistency is just a decision you keep making.",
        "Motivation starts it. Systems finish it.",
        "You are what you repeat.",
        "Don't break the chain — especially today.",
        "Win the morning, win the day.",
        "A habit done badly still beats a habit skipped.",
        "Future you is watching. Give them something good.",
        "Small wins compound into unrecognizable change.",
        "Show up tired. Show up busy. Just show up.",
        "Done is a habit too. Practice it daily.",
        "The best time was a year ago. The second best is today.",
        "Make it obvious. Make it easy. Make it daily.",
        "Rest is part of the streak. Quitting isn't.",
        "One day, or day one — you choose every morning.",
        "Boring consistency beats exciting intensity.",
        "Your habits are votes for who you become.",
        "Action kills doubt faster than thinking ever will."
    )

    /** The line for today, rotating through [LINES] one per calendar day. */
    fun forToday(): String {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return LINES[dayOfYear % LINES.size]
    }
}
