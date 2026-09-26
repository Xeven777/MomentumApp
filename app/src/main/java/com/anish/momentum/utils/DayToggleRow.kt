package com.anish.momentum.utils

import android.view.View
import android.widget.TextView
import com.anish.momentum.data.Schedule

/**
 * The seven weekday circles in the habit add/edit dialog. All seven selected
 * means "every day", which is stored as [Schedule.EVERY_DAY] so existing habits
 * keep their meaning.
 */
class DayToggleRow(
    private val labels: List<TextView>,
    private val summary: TextView
) {

    private val selected = Schedule.ALL_DAYS.toMutableSet()

    init {
        require(labels.size == ArtworkDays) { "expected 7 weekday labels, got ${labels.size}" }
        labels.forEachIndexed { index, view ->
            view.setOnClickListener { toggle(index) }
        }
        render()
    }

    /** @param mask a [Schedule] mask; [Schedule.EVERY_DAY] selects every day. */
    fun setMask(mask: Int) {
        selected.clear()
        selected.addAll(Schedule.daysOfWeek(mask))
        render()
    }

    fun mask(): Int = if (selected.size == ArtworkDays) Schedule.EVERY_DAY else Schedule.maskFor(selected)

    private fun toggle(index: Int) {
        if (selected.contains(index)) selected.remove(index) else selected.add(index)
        // Never allow an empty selection: a habit has to happen on some day.
        if (selected.isEmpty()) {
            selected.add(index)
        }
        render()
    }

    private fun render() {
        labels.forEachIndexed { index, view ->
            val isOn = selected.contains(index)
            view.isSelected = isOn
            (view as? View)?.let { it.alpha = if (isOn) 1f else 0.75f }
        }
        summary.text = Schedule.describe(mask())
    }

    private companion object {
        const val ArtworkDays = 7
    }
}
