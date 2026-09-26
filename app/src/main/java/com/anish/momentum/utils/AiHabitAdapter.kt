package com.anish.momentum.utils

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anish.momentum.R
import com.anish.momentum.databinding.ItemHabitBinding

data class AiHabit(val emoji: String, val name: String)

class AiHabitAdapter(
    private val habits: List<AiHabit>,
    private val onItemClick: ((AiHabit) -> Unit)? = null,
    private val addedHabits: Set<String> = emptySet()
) : RecyclerView.Adapter<AiHabitAdapter.HabitViewHolder>() {

    class HabitViewHolder(val binding: ItemHabitBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder =
        HabitViewHolder(
            ItemHabitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        val habit = habits[position]
        with(holder.binding) {
            habitEmoji.text = habit.emoji
            habitName.text = habit.name

            val alreadyAdded = addedHabits.contains(habit.emoji + "|" + habit.name)
            if (alreadyAdded) {
                habitName.paintFlags = habitName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                habitItemLayout.setBackgroundColor(
                    ContextCompat.getColor(root.context, R.color.black)
                )
                val grey = ContextCompat.getColor(root.context, R.color.gray)
                habitName.setTextColor(grey)
                habitEmoji.setTextColor(grey)
            } else {
                habitName.paintFlags = habitName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                habitItemLayout.setBackgroundResource(R.drawable.habit_item_bg)
                val white = ContextCompat.getColor(root.context, R.color.white)
                habitName.setTextColor(white)
                habitEmoji.setTextColor(white)
            }

            root.setOnClickListener { onItemClick?.invoke(habit) }
        }
    }

    override fun getItemCount(): Int = habits.size
}
