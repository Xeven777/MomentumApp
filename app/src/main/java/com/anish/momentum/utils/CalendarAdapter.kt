package com.anish.momentum.utils

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.anish.momentum.R
import com.anish.momentum.databinding.ItemCalendarDateBinding
import com.anish.momentum.databinding.ItemCalendarDateSelectedBinding
import com.anish.momentum.models.DateTaskStatus
import com.anish.momentum.utils.DateUtils.format
import java.text.SimpleDateFormat
import java.util.Locale

class CalendarAdapter(
    private var dates: List<DateTaskStatus>,
    private val onDateClick: (DateTaskStatus) -> Unit,
    private var selectedDateString: String? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val dayNumberFormat = SimpleDateFormat("dd", Locale.getDefault())

    fun updateData(newData: List<DateTaskStatus>) {
        dates = newData
        notifyDataSetChanged()
    }

    fun updateSelectedDate(selected: String?) {
        selectedDateString = selected
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (format(dates[position].date) == selectedDateString) VIEW_TYPE_SELECTED else VIEW_TYPE_NORMAL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SELECTED) {
            SelectedHolder(ItemCalendarDateSelectedBinding.inflate(inflater, parent, false))
        } else {
            NormalHolder(ItemCalendarDateBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = dates[position]
        val day = dayFormat.format(item.date)
        val number = dayNumberFormat.format(item.date)
        val isComplete = item.totalTasks > 0 && item.finishedTasks == item.totalTasks
        val dot = if (isComplete) R.drawable.dot_green else R.drawable.dot_grey

        when (holder) {
            is SelectedHolder -> with(holder.binding) {
                tvDay.text = day
                tvDate.text = number
                statusDot.setBackgroundResource(dot)
                root.setOnClickListener { onDateClick(item) }
            }
            is NormalHolder -> with(holder.binding) {
                tvDay.text = day
                tvDate.text = number
                statusDot.setBackgroundResource(dot)
                root.setOnClickListener { onDateClick(item) }
            }
        }
    }

    override fun getItemCount(): Int = dates.size

    private class NormalHolder(val binding: ItemCalendarDateBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class SelectedHolder(val binding: ItemCalendarDateSelectedBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val VIEW_TYPE_NORMAL = 0
        private const val VIEW_TYPE_SELECTED = 1
    }
}
