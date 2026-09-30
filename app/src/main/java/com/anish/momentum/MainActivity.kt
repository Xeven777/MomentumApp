package com.anish.momentum

import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.anish.momentum.data.HabitRepository
import com.anish.momentum.data.Schedule
import com.anish.momentum.data.SettingsStore
import com.anish.momentum.databinding.ActivityMainBinding
import com.anish.momentum.databinding.DialogAddHabitBinding
import com.anish.momentum.models.DateTaskStatus
import com.anish.momentum.models.Habit
import com.anish.momentum.utils.CalendarAdapter
import com.anish.momentum.utils.DayToggleRow
import com.anish.momentum.utils.DateUtils
import com.anish.momentum.utils.HabitAdapter
import com.anish.momentum.utils.ReminderUtils
import com.anish.momentum.utils.ServiceLocator
import com.anish.momentum.ui.Quotes
import com.anish.momentum.utils.Vibration
import com.anish.momentum.widgets.StreakWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val allHabits = mutableListOf<Habit>()
    private val filteredHabits = mutableListOf<Habit>()
    private lateinit var habitAdapter: HabitAdapter
    private var selectedDateString: String? = null
    private lateinit var calendarAdapter: CalendarAdapter

    private val repository: HabitRepository get() = ServiceLocator.habits
    private val settingsStore: SettingsStore get() = ServiceLocator.settings

    private var dailyGoal = 0
    private var lastAnimatedName: String? = null

    /** Time-of-day emoji, shown beside the name in the header. */
    private var greetingEmoji = "☀️"
    private var calendarInitialised = false
    private var widgetRefreshJob: Job? = null

    private val PREFS_NAME = "ai_settings_prefs"
    private val STREAK_KEY = "current_streak"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001
                )
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (emoji, greeting) = when (hour) {
            in 5..11 -> "☀️" to getString(R.string.greeting_morning)
            in 12..17 -> "🌤️" to getString(R.string.greeting_afternoon)
            in 18..20 -> "🌆" to getString(R.string.greeting_evening)
            else -> "🌝" to getString(R.string.greeting_late)
        }
        greetingEmoji = emoji
        binding.wishTxt.text = if (hour in 5..20) {
            "$greeting! ${getString(R.string.greeting_tagline)}"
        } else {
            greeting
        }

        binding.quoteText.text = Quotes.forToday()

        // The quote card is optional; the toggle lives in Settings.
        lifecycleScope.launch {
            settingsStore.quoteEnabled.collect { show ->
                binding.quoteCard.visibility = if (show) View.VISIBLE else View.GONE
            }
        }

        binding.aiBtn.setOnClickListener {
            Vibration.vibrate(this, 100)
            startActivity(Intent(this, AiActivity::class.java))
        }

        binding.addHabitBtn.setOnClickListener {
            Vibration.vibrate(this, 100)
            showAddHabitDialog()
        }

        binding.settings.setOnClickListener {
            Vibration.vibrate(this, 50)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        calendarAdapter = CalendarAdapter(emptyList(), { selectedDate ->
            val selectedDateStr = DateUtils.format(selectedDate.date)
            calendarAdapter.updateSelectedDate(selectedDateStr)
            onCalendarDateSelected(selectedDate)
            Vibration.vibrate(this, 50)
        }, selectedDateString)
        binding.calendarRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.calendarRecycler.adapter = calendarAdapter

        habitAdapter = HabitAdapter(
            filteredHabits,
            onToggle = { habit -> onHabitToggled(habit) },
            onLongPress = { habit -> showEditDeleteDialog(habit) }
        )
        binding.habitRecycler.layoutManager = LinearLayoutManager(this)
        binding.habitRecycler.adapter = habitAdapter

        // The expander below the day strip opens the full month calendar;
// tapping the strip's empty space does the same.
        binding.monthCalendarToggle.setOnClickListener {
            Vibration.vibrate(this, 50)
            toggleMonthCalendar()
        }
        binding.calendarRecycler.setOnClickListener {
            Vibration.vibrate(this, 50)
            toggleMonthCalendar()
        }
        binding.monthPrev.setOnClickListener {
            Vibration.vibrate(this, 50)
            binding.monthCalendar.changeMonth(-1)
        }
        binding.monthNext.setOnClickListener {
            Vibration.vibrate(this, 50)
            binding.monthCalendar.changeMonth(1)
        }
        binding.monthCalendar.onDateSelected = { date ->
            Vibration.vibrate(this, 50)
            selectedDateString = date
            binding.monthCalendar.setSelectedDate(date)
            calendarAdapter.updateSelectedDate(date)
            refreshSelectedDay()
        }

        // The streak card is the entry point to the stats screen.
        binding.streakCard.setOnClickListener {
            Vibration.vibrate(this, 50)
            startActivity(Intent(this, StatsActivity::class.java))
        }

        // The habits card is the entry point to the add-habit dialog.
        binding.habitCard.setOnClickListener {
            Vibration.vibrate(this, 50)
            showAddHabitDialog()
        }

        observeState()

        onCalendarDateSelected(
            DateTaskStatus(Calendar.getInstance().time, 0, 0)
        )
    }

    // ------------------------------------------------------------- observers

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    repository.observeHabits().collect { habits ->
                        allHabits.clear()
                        allHabits.addAll(habits.sortedBy { it.isDone })
                        habitAdapter.notifyDataSetChanged()
                        refreshCalendar()
                        refreshSelectedDay()
                        updateStreak()
                        refreshMonthCalendar()
                    }
                }

                launch {
                    // The name is collected during onboarding and edited in
                    // Settings, so here it only ever drives the greeting.
                    settingsStore.userName.collect { name ->
                        if (name.isNotBlank()) showGreeting(binding.titleTxt, name)
                    }
                }

                launch {
                    settingsStore.aiButtonEnabled.collect { enabled ->
                        binding.aiBtn.visibility = if (enabled) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    settingsStore.dailyGoal.collect { goal ->
                        if (dailyGoal != goal) {
                            dailyGoal = goal
                            updateStreak()
                        }
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------- calendar

    private fun toggleMonthCalendar() {
        val card = binding.monthCalendarCard
        val toggle = binding.monthCalendarToggle
        val showing = card.visibility == View.VISIBLE
        // Chevron points down when closed, up when open; 200ms matches the card fade.
        toggle.animate().cancel()
        toggle.animate().rotation(if (showing) 90f else 270f).setDuration(200).start()
        toggle.contentDescription = getString(
            if (showing) R.string.show_month_calendar_description
            else R.string.hide_month_calendar_description
        )
        if (showing) {
            card.animate().cancel()
            card.animate().alpha(0f).translationY(-8f * resources.displayMetrics.density)
                .setDuration(200).withEndAction {
                    card.visibility = View.GONE
                    card.alpha = 1f
                    card.translationY = 0f
                }.start()
            return
        }
        selectedDateString?.let { binding.monthCalendar.showMonthContaining(it) }
        binding.monthCalendar.setSelectedDate(selectedDateString)
        binding.monthCalendar.onMonthChanged = { refreshMonthCalendar() }
        card.alpha = 0f
        card.translationY = -8f * resources.displayMetrics.density
        card.visibility = View.VISIBLE
        card.animate().alpha(1f).translationY(0f).setDuration(200).start()
        refreshMonthCalendar()
    }

    private fun refreshMonthCalendar() {
        if (binding.monthCalendarCard.visibility != View.VISIBLE) return
        updateMonthHeader()
        lifecycleScope.launch {
            val monthStart = binding.monthCalendar.displayedMonthStart()
            // A little padding either side so the leading/trailing blanks of the
            // grid are filled in too.
            val stats = repository.dayStatsBetween(
                DateUtils.plusDays(monthStart, -7),
                DateUtils.plusDays(monthStart, 45)
            )
            binding.monthCalendar.setData(stats)
        }
    }

    /** Month title plus › dimming; called on open and on every month change. */
    private fun updateMonthHeader() {
        binding.monthTitle.text = binding.monthCalendar.currentMonthLabel()
        val forward = binding.monthCalendar.canGoNext()
        binding.monthNext.isEnabled = forward
        binding.monthNext.alpha = if (forward) 1f else 0.3f
    }

    private fun refreshCalendar() {
        lifecycleScope.launch {
            val stats = repository.dayStatsSince(DateUtils.daysAgo(CALENDAR_WINDOW_DAYS))
            val data = stats.mapNotNull { stat ->
                DateUtils.parse(stat.date)?.let {
                    DateTaskStatus(it, stat.finishedTasks, stat.totalTasks)
                }
            }
            calendarAdapter.updateData(data)
            selectedDateString?.let { calendarAdapter.updateSelectedDate(it) }
            if (data.isNotEmpty() && !calendarInitialised) {
                calendarInitialised = true
                binding.calendarRecycler.scrollToPosition(data.size - 1)
            }
        }
    }

    private fun onCalendarDateSelected(selectedDate: DateTaskStatus) {
        val selectedDateStr = DateUtils.format(selectedDate.date)
        selectedDateString = selectedDateStr
        calendarAdapter.updateSelectedDate(selectedDateStr)
        binding.monthCalendar.setSelectedDate(selectedDateStr)
        refreshSelectedDay()
    }

    private fun refreshSelectedDay() {
        val selectedDateStr = selectedDateString ?: DateUtils.today()
        val filtered = allHabits.filter { habit ->
            (habit.creationDate.isNullOrEmpty() || habit.creationDate <= selectedDateStr) &&
                Schedule.isScheduledOn(habit.scheduleMask, selectedDateStr)
        }.map { habit ->
            habit.copy(isDone = habit.completionDates.contains(selectedDateStr))
        }

        filteredHabits.clear()
        filteredHabits.addAll(filtered.sortedBy { it.isDone })
        habitAdapter.notifyDataSetChanged()

        val finishedCount = filtered.count { it.isDone }
        val totalCount = filtered.size
        binding.finishedTasks.text = finishedCount.toString()
        binding.totalTasks.text = totalCount.toString()
        binding.tasksCount.text = String.format(Locale.getDefault(), "%d", totalCount)

        val percent = if (totalCount == 0) 0 else finishedCount * 100 / totalCount
        binding.todayProgress.setProgressCompat(percent, true)
        binding.todayPercent.text = getString(R.string.percent_format, percent)

        binding.noTasksText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateStreak() {
        lifecycleScope.launch {
            val streak = repository.currentStreak(dailyGoal)
            binding.streakDay.text = streak.toString()
            binding.streakCaption.text = getString(
                if (streak == 0) R.string.streak_start else R.string.streak_keep
            )
            saveStreakToPrefs(streak)
            updateWidget()

            if (streak == 0) {
                binding.lottieFire.pauseAnimation()
            } else {
                binding.streakBg.setBackgroundResource(R.color.streak_yellow)
                binding.lottieFire.resumeAnimation()
            }
        }
    }

    /** The header is a permanent warm greeting; it only updates when the name changes. */
    private fun showGreeting(view: TextView, name: String) {
        if (name == lastAnimatedName) return
        lastAnimatedName = name
        view.alpha = 1f
        val trimmed = name.trim()
        val display = if (trimmed.isEmpty()) {
            trimmed
        } else {
            trimmed[0].uppercaseChar() + trimmed.substring(1)
        }
        view.text = getString(R.string.hello_name, display, greetingEmoji)
    }

    // ----------------------------------------------------------------- habits

    private fun onHabitToggled(habit: Habit) {
        Vibration.vibrate(this, 50)
        val dateStr = selectedDateString ?: DateUtils.today()
        val todayStr = DateUtils.today()

        if (dateStr != todayStr) {
            Toast.makeText(this, "Can't mark it done for this day", Toast.LENGTH_SHORT).show()
            return
        }

        if (habit.completionDates.contains(dateStr)) {
            AlertDialog.Builder(this)
                .setTitle("Undo Habit")
                .setMessage("Are you sure you want to undo this habit for today?")
                .setPositiveButton("Yes") { _, _ ->
                    lifecycleScope.launch {
                        repository.setCompleted(habit, dateStr, false)
                    }
                }
                .setNegativeButton("No", null)
                .show()
        } else {
            lifecycleScope.launch {
                repository.setCompleted(habit, dateStr, true)
            }
        }
    }

    private fun showAddHabitDialog() {
        val dialogBinding = DialogAddHabitBinding.inflate(layoutInflater)
        val dayToggles = DayToggleRow(
            listOf(
                dialogBinding.day0, dialogBinding.day1, dialogBinding.day2,
                dialogBinding.day3, dialogBinding.day4, dialogBinding.day5,
                dialogBinding.day6
            ),
            dialogBinding.scheduleLabel
        )
        var pickedTime: String? = null

        dialogBinding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.reminderTime.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        dialogBinding.reminderTime.setOnClickListener {
            Vibration.vibrate(this, 50)
            val cal = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, selectedHour, selectedMinute ->
                    val picked = String.format("%02d:%02d", selectedHour, selectedMinute)
                    pickedTime = picked
                    dialogBinding.timePickerTxt.text =
                        ReminderUtils.formatTo12Hour(picked)
                },
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false
            ).show()
        }

        // The positive button is wired after show() so a blank name keeps the
        // dialog open instead of throwing away everything the user typed.
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Habit")
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.etHabitName.text.toString().trim()
            val emoji = dialogBinding.etEmoji.text.toString().trim().takeIf { it.isNotEmpty() } ?: "✅"
            val hasReminder = dialogBinding.switchReminder.isChecked
            val time = pickedTime ?: ""
            val scheduleMask = dayToggles.mask()

            if (name.isEmpty()) {
                Toast.makeText(this, "Habit name can't be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (hasReminder && time.isEmpty()) {
                Toast.makeText(this, "Pick a reminder time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val habit = repository.addHabit(name, emoji, hasReminder, time, scheduleMask)
                ReminderUtils.scheduleHabitReminder(this@MainActivity, habit)
                Toast.makeText(this@MainActivity, "Habit added", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
    }

    private fun showEditDeleteDialog(habit: Habit) {
        AlertDialog.Builder(this)
            .setTitle("Choose an option")
            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                if (which == 0) showEditHabitDialog(habit) else confirmDeleteHabit(habit)
            }
            .show()
    }

    private fun confirmDeleteHabit(habit: Habit) {
        AlertDialog.Builder(this)
            .setTitle("Delete Habit?")
            .setMessage("Deleting this habit also deletes all its past records")
            .setPositiveButton("Yes") { _, _ ->
                ReminderUtils.cancelHabitReminder(this, habit)
                lifecycleScope.launch {
                    repository.deleteHabit(habit)
                    Toast.makeText(this@MainActivity, "Habit deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditHabitDialog(habit: Habit) {
        val dialogBinding = DialogAddHabitBinding.inflate(layoutInflater)

        val dayToggles = DayToggleRow(
            listOf(
                dialogBinding.day0, dialogBinding.day1, dialogBinding.day2,
                dialogBinding.day3, dialogBinding.day4, dialogBinding.day5,
                dialogBinding.day6
            ),
            dialogBinding.scheduleLabel
        )
        dayToggles.setMask(habit.scheduleMask)

        dialogBinding.etEmoji.setText(habit.emoji)
        dialogBinding.etHabitName.setText(habit.name)
        dialogBinding.switchReminder.isChecked = habit.hasReminder
        var pickedTime: String? = habit.reminderTime
        dialogBinding.reminderTime.visibility = if (habit.hasReminder) View.VISIBLE else View.GONE
        dialogBinding.timePickerTxt.text = ReminderUtils.formatTo12Hour(habit.reminderTime)

        dialogBinding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.reminderTime.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        dialogBinding.reminderTime.setOnClickListener {
            Vibration.vibrate(this, 50)
            val cal = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, selectedHour, selectedMinute ->
                    val picked = String.format("%02d:%02d", selectedHour, selectedMinute)
                    pickedTime = picked
                    dialogBinding.timePickerTxt.text =
                        ReminderUtils.formatTo12Hour(picked)
                },
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false
            ).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Habit")
            .setView(dialogBinding.root)
            .setPositiveButton("Update", null)
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = dialogBinding.etHabitName.text.toString().trim()
            val newEmoji = dialogBinding.etEmoji.text.toString().trim().takeIf { it.isNotEmpty() } ?: "✅"
            val newReminder = dialogBinding.switchReminder.isChecked
            val newTime = pickedTime ?: ""

            if (newName.isEmpty()) {
                Toast.makeText(this, "Habit name can't be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newReminder && newTime.isEmpty()) {
                Toast.makeText(this, "Pick a reminder time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            habit.scheduleMask = dayToggles.mask()
            habit.name = newName
            habit.emoji = newEmoji
            habit.hasReminder = newReminder
            habit.reminderTime = if (newReminder) newTime else ""

            lifecycleScope.launch {
                repository.updateHabit(habit)
                ReminderUtils.scheduleHabitReminder(this@MainActivity, habit)
                Toast.makeText(this@MainActivity, "Habit updated", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
    }

    // ---------------------------------------------------------------- widget

    private fun saveStreakToPrefs(streak: Int) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putInt(STREAK_KEY, streak) }
    }

    private fun updateWidget() {
        // Habit toggles fire several emissions in a burst; coalesce them so
        // the widget (which re-queries the DB per refresh) redraws once.
        widgetRefreshJob?.cancel()
        widgetRefreshJob = lifecycleScope.launch {
            delay(500)
            StreakWidget.refresh(this@MainActivity)
            com.anish.momentum.widgets.StreakMiniWidget.refresh(this@MainActivity)
        }
    }

    companion object {
        private const val CALENDAR_WINDOW_DAYS = 10
    }
}
