package com.anish.momentum

import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.anish.momentum.data.HabitRepository
import com.anish.momentum.data.Schedule
import com.anish.momentum.data.SettingsStore
import com.anish.momentum.models.DateTaskStatus
import com.anish.momentum.models.Habit
import com.anish.momentum.utils.CalendarAdapter
import com.anish.momentum.utils.DayToggleRow
import com.anish.momentum.utils.DateUtils
import com.anish.momentum.utils.HabitAdapter
import com.anish.momentum.ui.MonthCalendarView
import com.anish.momentum.utils.ReminderUtils
import com.anish.momentum.utils.ServiceLocator
import com.anish.momentum.utils.Vibration
import com.anish.momentum.widgets.StreakWidget
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var titleTxt: TextView
    private lateinit var wishTxt: TextView
    private lateinit var finishedTasks: TextView
    private lateinit var totalTasks: TextView
    private lateinit var streakDay: TextView
    private lateinit var streakBg: LinearLayout
    private lateinit var lottieFire: LottieAnimationView
    private lateinit var settings: ImageView
    private lateinit var addHabitBtn: CardView
    private lateinit var aiBtn: CardView
    private lateinit var noTasksText: TextView
    private lateinit var monthCalendarCard: View
    private lateinit var monthCalendar: MonthCalendarView

    private val allHabits = mutableListOf<Habit>()
    private val filteredHabits = mutableListOf<Habit>()
    private lateinit var habitAdapter: HabitAdapter
    private var selectedDateString: String? = null
    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var calendarRecycler: RecyclerView

    private val repository: HabitRepository get() = ServiceLocator.habits
    private val settingsStore: SettingsStore get() = ServiceLocator.settings

    private var dailyGoal = 0
    private var namePromptShown = false

    private val PREFS_NAME = "ai_settings_prefs"
    private val STREAK_KEY = "current_streak"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        titleTxt = findViewById(R.id.title_txt)
        wishTxt = findViewById(R.id.wish_txt)
        finishedTasks = findViewById(R.id.finished_tasks)
        totalTasks = findViewById(R.id.total_tasks)
        streakDay = findViewById(R.id.streak_day)
        streakBg = findViewById(R.id.streak_bg)
        aiBtn = findViewById(R.id.ai_btn)
        addHabitBtn = findViewById(R.id.add_habit_btn)
        settings = findViewById(R.id.settings)
        lottieFire = findViewById(R.id.lottie_fire)
        noTasksText = findViewById(R.id.no_tasks_text)
        monthCalendarCard = findViewById(R.id.month_calendar_card)
        monthCalendar = findViewById(R.id.month_calendar)

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        wishTxt.text = when (hour) {
            in 5..11 -> "Good morning ☀️"
            in 12..15 -> "Good afternoon 🌤️"
            in 16..20 -> "Good evening 🌆"
            else -> "Its late, get some rest 🌝"
        }

        aiBtn.setOnClickListener {
            Vibration.vibrate(this, 100)
            startActivity(Intent(this, AiActivity::class.java))
        }

        addHabitBtn.setOnClickListener {
            Vibration.vibrate(this, 100)
            showAddHabitDialog()
        }

        settings.setOnClickListener {
            Vibration.vibrate(this, 50)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        calendarRecycler = findViewById(R.id.calendar_recycler)
        calendarAdapter = CalendarAdapter(emptyList(), { selectedDate ->
            val selectedDateStr = DateUtils.format(selectedDate.date)
            calendarAdapter.updateSelectedDate(selectedDateStr)
            onCalendarDateSelected(selectedDate)
            Vibration.vibrate(this, 50)
        }, selectedDateString)
        calendarRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        calendarRecycler.adapter = calendarAdapter

        habitAdapter = HabitAdapter(
            filteredHabits,
            onToggle = { habit -> onHabitToggled(habit) },
            onLongPress = { habit -> showEditDeleteDialog(habit) }
        )
        val habitRecycler = findViewById<RecyclerView>(R.id.habit_recycler)
        habitRecycler.layoutManager = LinearLayoutManager(this)
        habitRecycler.adapter = habitAdapter

        // Tapping the day strip opens the full month calendar.
        calendarRecycler.setOnClickListener {
            Vibration.vibrate(this, 50)
            toggleMonthCalendar()
        }
        monthCalendar.onDateSelected = { date ->
            Vibration.vibrate(this, 50)
            selectedDateString = date
            monthCalendar.setSelectedDate(date)
            calendarAdapter.updateSelectedDate(date)
            refreshSelectedDay()
        }

        // The streak card is the entry point to the stats screen.
        findViewById<View>(R.id.streak_card).setOnClickListener {
            Vibration.vibrate(this, 50)
            startActivity(Intent(this, StatsActivity::class.java))
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
                    settingsStore.userName.collect { name ->
                        if (name.isBlank()) {
                            if (!namePromptShown) {
                                namePromptShown = true
                                showNamePrompt()
                            }
                        } else {
                            animateTitleSequence(titleTxt, name)
                        }
                    }
                }

                launch {
                    settingsStore.aiButtonEnabled.collect { enabled ->
                        aiBtn.visibility = if (enabled) View.VISIBLE else View.GONE
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

    private fun showNamePrompt() {
        val input = EditText(this)
        input.hint = "Your name"
        input.setPadding(40, 40, 40, 40)
        AlertDialog.Builder(this)
            .setTitle("Welcome to Momentum")
            .setMessage("What should we call you?")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch { settingsStore.setUserName(name) }
                } else {
                    namePromptShown = false
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                namePromptShown = false
            }
            .show()
    }

    // --------------------------------------------------------------- calendar

    private fun toggleMonthCalendar() {
        val showing = monthCalendarCard.visibility == View.VISIBLE
        monthCalendarCard.visibility = if (showing) View.GONE else View.VISIBLE
        if (showing) return
        selectedDateString?.let { monthCalendar.showMonthContaining(it) }
        monthCalendar.setSelectedDate(selectedDateString)
        monthCalendar.onMonthChanged = { refreshMonthCalendar() }
        refreshMonthCalendar()
    }

    private fun refreshMonthCalendar() {
        if (monthCalendarCard.visibility != View.VISIBLE) return
        lifecycleScope.launch {
            val monthStart = monthCalendar.displayedMonthStart()
            // A little padding either side so the leading/trailing blanks of the
            // grid are filled in too.
            val stats = repository.dayStatsBetween(
                DateUtils.plusDays(monthStart, -7),
                DateUtils.plusDays(monthStart, 45)
            )
            monthCalendar.setData(stats)
        }
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
            if (data.isNotEmpty()) calendarRecycler.scrollToPosition(data.size - 1)
            updateStreak()
        }
    }

    private fun onCalendarDateSelected(selectedDate: DateTaskStatus) {
        val selectedDateStr = DateUtils.format(selectedDate.date)
        selectedDateString = selectedDateStr
        calendarAdapter.updateSelectedDate(selectedDateStr)
        monthCalendar.setSelectedDate(selectedDateStr)
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

        finishedTasks.text = filtered.count { it.isDone }.toString()
        totalTasks.text = filtered.size.toString()
        noTasksText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateStreak() {
        lifecycleScope.launch {
            val streak = repository.currentStreak(dailyGoal)
            streakDay.text = streak.toString()
            saveStreakToPrefs(streak)
            updateWidget()

            if (streak == 0) {
                lottieFire.pauseAnimation()
            } else {
                streakBg.setBackgroundResource(R.color.streak_yellow)
                lottieFire.resumeAnimation()
            }
        }
    }

    private fun animateTitleSequence(titleTxt: TextView, name: String) {
        if (titleTxt.text.toString().contains(name.uppercase())) return
        titleTxt.animate()
            .alpha(0f)
            .setDuration(500)
            .withEndAction {
                titleTxt.text = "HELLO ${name.uppercase()}"
                titleTxt.animate()
                    .alpha(1f)
                    .setDuration(800)
                    .setStartDelay(200)
                    .withEndAction {
                        titleTxt.animate()
                            .alpha(0f)
                            .setDuration(600)
                            .setStartDelay(400)
                            .withEndAction {
                                titleTxt.text = "MOMENTUM"
                                titleTxt.animate().alpha(1f).setDuration(800).start()
                            }
                            .start()
                    }
                    .start()
            }
            .start()
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
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_habit, null)
        val etEmoji = dialogView.findViewById<EditText>(R.id.et_emoji)
        val etHabitName = dialogView.findViewById<EditText>(R.id.et_habit_name)
        val switchReminder = dialogView.findViewById<MaterialSwitch>(R.id.switch_reminder)
        val timePicker = dialogView.findViewById<CardView>(R.id.reminder_time)
        val timePickerTxt = dialogView.findViewById<TextView>(R.id.time_picker_txt)
        val dayToggles = DayToggleRow(dayToggleViews(dialogView), dialogView.findViewById(R.id.schedule_label))
        var pickedTime: String? = null

        switchReminder.setOnCheckedChangeListener { _, isChecked ->
            timePicker.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        timePicker.setOnClickListener {
            Vibration.vibrate(this, 50)
            val cal = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, selectedHour, selectedMinute ->
                    pickedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                    timePickerTxt.text = pickedTime
                },
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
            ).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Add Habit")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etHabitName.text.toString().trim()
                val emoji = etEmoji.text.toString().trim().takeIf { it.isNotEmpty() } ?: "✅"
                val hasReminder = switchReminder.isChecked
                val time = pickedTime ?: ""
                val scheduleMask = dayToggles.mask()

                if (name.isEmpty()) {
                    Toast.makeText(this, "Habit name can't be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val habit = repository.addHabit(name, emoji, hasReminder, time, scheduleMask)
                    ReminderUtils.scheduleHabitReminder(this@MainActivity, habit)
                    Toast.makeText(this@MainActivity, "Habit added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dayToggleViews(dialogView: View): List<TextView> =
        (0..6).map { dialogView.findViewById<TextView>(dayToggleIds[it]) }

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
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_habit, null)
        val etHabitName = dialogView.findViewById<EditText>(R.id.et_habit_name)
        val etEmoji = dialogView.findViewById<EditText>(R.id.et_emoji)
        val switchReminder = dialogView.findViewById<MaterialSwitch>(R.id.switch_reminder)
        val timePicker = dialogView.findViewById<CardView>(R.id.reminder_time)
        val timePickerTxt = dialogView.findViewById<TextView>(R.id.time_picker_txt)

        val dayToggles = DayToggleRow(dayToggleViews(dialogView), dialogView.findViewById(R.id.schedule_label))
        dayToggles.setMask(habit.scheduleMask)

        etEmoji.setText(habit.emoji)
        etHabitName.setText(habit.name)
        switchReminder.isChecked = habit.hasReminder
        var pickedTime: String? = habit.reminderTime
        timePicker.visibility = if (habit.hasReminder) View.VISIBLE else View.GONE
        timePickerTxt.text = habit.reminderTime

        switchReminder.setOnCheckedChangeListener { _, isChecked ->
            timePicker.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        timePicker.setOnClickListener {
            Vibration.vibrate(this, 50)
            val cal = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, selectedHour, selectedMinute ->
                    pickedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                    timePickerTxt.text = pickedTime
                },
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
            ).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Habit")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val newName = etHabitName.text.toString().trim()
                val newEmoji = etEmoji.text.toString().trim().takeIf { it.isNotEmpty() } ?: "✅"
                val newReminder = switchReminder.isChecked
                val newTime = pickedTime ?: ""

                habit.scheduleMask = dayToggles.mask()

                if (newName.isEmpty()) {
                    Toast.makeText(this, "Habit name can't be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                habit.name = newName
                habit.emoji = newEmoji
                habit.hasReminder = newReminder
                habit.reminderTime = if (newReminder) newTime else ""

                lifecycleScope.launch {
                    repository.updateHabit(habit)
                    ReminderUtils.scheduleHabitReminder(this@MainActivity, habit)
                    Toast.makeText(this@MainActivity, "Habit updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- widget

    private fun saveStreakToPrefs(streak: Int) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putInt(STREAK_KEY, streak) }
    }

    private fun updateWidget() {
        StreakWidget.refresh(this)
    }

    companion object {
        private const val CALENDAR_WINDOW_DAYS = 10
        private val dayToggleIds = intArrayOf(
            R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3,
            R.id.day_4, R.id.day_5, R.id.day_6
        )
    }
}
