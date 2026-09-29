package com.anish.momentum.utils

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.anish.momentum.databinding.ItemHabitBinding
import com.anish.momentum.models.Habit

class HabitAdapter(
    private val habits: MutableList<Habit>,
    private val onToggle: (Habit) -> Unit,
    private val onLongPress: (Habit) -> Unit
) : RecyclerView.Adapter<HabitAdapter.HabitViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HabitViewHolder {
        val binding = ItemHabitBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HabitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        val habit = habits[position]
        with(holder.binding) {
            habitEmoji.text = habit.emoji
            habitName.text = habit.name

            if (habit.isDone) {
                habitName.paintFlags = habitName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                habitItemLayout.alpha = 0.4f
            } else {
                habitName.paintFlags = habitName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                habitItemLayout.alpha = 1.0f
            }

            habitItemLayout.setOnClickListener { onToggle(habit) }
            habitItemLayout.setOnLongClickListener {
                onLongPress(habit)
                true
            }
        }
    }

    override fun getItemCount(): Int = habits.size

    class HabitViewHolder(val binding: ItemHabitBinding) :
        RecyclerView.ViewHolder(binding.root)
}
