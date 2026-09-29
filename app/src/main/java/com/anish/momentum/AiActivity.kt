package com.anish.momentum

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView as CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.anish.momentum.ai.AiProvider
import com.anish.momentum.ai.HabitReplyParser
import com.anish.momentum.utils.AiHabit
import com.anish.momentum.utils.AiHabitAdapter
import com.anish.momentum.utils.ServiceLocator
import com.anish.momentum.utils.Vibration
import com.anish.momentum.widgets.StreakWidget
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class AiActivity : AppCompatActivity() {

    private lateinit var questionsCard: CardView
    private lateinit var aiReplyRv: RecyclerView
    private lateinit var replyLayout: LinearLayout
    private lateinit var addToHabits: CardView
    private lateinit var clearBtn: CardView
    private lateinit var loading: LottieAnimationView

    private var currentAiHabits: List<AiHabit> = emptyList()
    private val addedHabitKeys = mutableSetOf<String>()
    private var aiAdapter: AiHabitAdapter? = null

    private val PREFS_NAME = "ai_habits_prefs"
    private val HABITS_KEY = "ai_habits_list"
    private val ADDED_KEY = "added_ai_habits"
    private val gson = Gson()

    private lateinit var freePromptLayout: LinearLayout
    private lateinit var freePromptInput: TextInputEditText

    private val ai by lazy { AiProvider(this) }
    private val repository get() = ServiceLocator.habits

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ai)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        questionsCard = findViewById(R.id.questions_card)
        questionsCard.setOnClickListener {
            Vibration.vibrate(this, 50)
            startActivity(Intent(this, QuestionsActivity::class.java))
            finish()
        }

        replyLayout = findViewById(R.id.reply_layout)
        aiReplyRv = findViewById(R.id.ai_reply_rv)
        addToHabits = findViewById(R.id.add_to_habits_card)
        clearBtn = findViewById(R.id.clear_btn)
        loading = findViewById(R.id.loading)
        freePromptLayout = findViewById(R.id.free_prompt_layout)
        freePromptInput = findViewById(R.id.free_prompt_input)

        findViewById<CardView>(R.id.generate_btn).setOnClickListener {
            Vibration.vibrate(this, 50)
            val prompt = freePromptInput.text?.toString()?.trim().orEmpty()
            if (prompt.isEmpty()) {
                Toast.makeText(this, "Describe what you want first", Toast.LENGTH_SHORT).show()
            } else {
                getAiHabits(prompt)
            }
        }

        addedHabitKeys.clear()
        addedHabitKeys.addAll(loadAddedHabitKeysFromPrefs())

        val savedHabits = loadHabitsFromPrefs()
        if (savedHabits.isNotEmpty()) {
            currentAiHabits = savedHabits
            showReplyLayout()
            aiReplyRv.layoutManager = LinearLayoutManager(this)
            aiAdapter = buildAdapter(savedHabits)
            aiReplyRv.adapter = aiAdapter
        } else {
            showQuestionsCard()
        }

        clearBtn.setOnClickListener {
            crossfadeViews(replyLayout, questionsCard)
            freePromptLayout.visibility = View.VISIBLE
            clearHabitsFromPrefs()
        }

        addToHabits.setOnClickListener {
            if (currentAiHabits.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Add all to habits?")
                    .setMessage("Are you sure you want to add all these habits to your list?")
                    .setPositiveButton("Yes") { _, _ -> addAllAiHabits(currentAiHabits) }
                    .setNegativeButton("No", null)
                    .show()
            }
        }

        if (intent.hasExtra("result")) {
            questionsCard.visibility = View.GONE
            freePromptLayout.visibility = View.GONE
            getAiHabits(intent.getStringExtra("result").orEmpty())
        }
    }

    // ------------------------------------------------------------------- UI

    private fun crossfadeViews(hideView: View, showView: View) {
        hideView.animate().alpha(0f).setDuration(200).withEndAction {
            hideView.visibility = View.GONE
            showView.alpha = 0f
            showView.visibility = View.VISIBLE
            showView.animate().alpha(1f).setDuration(200).start()
        }.start()
    }

    private fun showReplyLayout() {
        replyLayout.visibility = View.VISIBLE
        replyLayout.alpha = 1f
        questionsCard.visibility = View.GONE
        freePromptLayout.visibility = View.GONE
    }

    private fun showQuestionsCard() {
        questionsCard.visibility = View.VISIBLE
        questionsCard.alpha = 1f
        replyLayout.visibility = View.GONE
        freePromptLayout.visibility = View.VISIBLE
    }

    private fun buildAdapter(habits: List<AiHabit>) = AiHabitAdapter(
        habits,
        { aiHabit ->
            if (addedHabitKeys.contains(keyOf(aiHabit))) {
                Toast.makeText(this, "Already added", Toast.LENGTH_SHORT).show()
            } else {
                showAddHabitDialog(aiHabit)
            }
        },
        addedHabitKeys
    )

    private fun keyOf(habit: AiHabit) = habit.emoji + "|" + habit.name

    // --------------------------------------------------------------- storage

    private fun saveHabitsToPrefs(habits: List<AiHabit>) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putString(HABITS_KEY, gson.toJson(habits)).apply()
    }

    private fun loadHabitsFromPrefs(): List<AiHabit> {
        val json = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(HABITS_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<AiHabit>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveAddedHabitKeysToPrefs() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putStringSet(ADDED_KEY, addedHabitKeys).apply()
    }

    private fun loadAddedHabitKeysFromPrefs(): Set<String> =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getStringSet(ADDED_KEY, emptySet()) ?: emptySet()

    private fun clearHabitsFromPrefs() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(HABITS_KEY)
            .remove(ADDED_KEY)
            .apply()
        addedHabitKeys.clear()
    }

    // -------------------------------------------------------------------- AI

    private fun getAiHabits(prompt: String) {
        if (prompt.isBlank()) {
            showQuestionsCard()
            return
        }

        loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val client = ai.client()
            if (client == null) {
                loading.visibility = View.GONE
                promptForApiKey()
                return@launch
            }

            val reply = try {
                client.complete(prompt.trimIndent())
            } catch (e: Exception) {
                loading.visibility = View.GONE
                Log.e("AiActivity", "AI request failed", e)
                Toast.makeText(
                    this@AiActivity,
                    e.message ?: "Something went wrong",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            loading.visibility = View.GONE
            Log.d("AiActivity", "AI Suggestion: $reply")

            val habits = HabitReplyParser.parse(reply)
            if (habits.isEmpty()) {
                Toast.makeText(
                    this@AiActivity,
                    "Couldn't read the AI response. Try rephrasing, or adjust the system prompt in Settings.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            currentAiHabits = habits
            aiReplyRv.layoutManager = LinearLayoutManager(this@AiActivity)
            aiAdapter = buildAdapter(habits)
            aiReplyRv.adapter = aiAdapter
            saveHabitsToPrefs(habits)
            showReplyLayout()
        }
    }

    private fun promptForApiKey() {
        AlertDialog.Builder(this)
            .setTitle("API key needed")
            .setMessage("Add your OpenAI-compatible API key in Settings to use the AI assistant.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    // ------------------------------------------------------------- add habit

    private fun showAddHabitDialog(aiHabit: AiHabit) {
        AlertDialog.Builder(this)
            .setTitle("Add to habits?")
            .setMessage("Do you want to add '${aiHabit.emoji} ${aiHabit.name}' to your habits?")
            .setPositiveButton("Yes") { _, _ -> addAiHabit(aiHabit) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun markAdded(aiHabit: AiHabit) {
        StreakWidget.refresh(this)
        com.anish.momentum.widgets.StreakMiniWidget.refresh(this)
        addedHabitKeys.add(keyOf(aiHabit))
        saveAddedHabitKeysToPrefs()
        aiAdapter?.notifyDataSetChanged()
    }

    private fun addAiHabit(aiHabit: AiHabit) {
        lifecycleScope.launch {
            val added = repository.addHabitIfAbsent(aiHabit.name, aiHabit.emoji)
            markAdded(aiHabit)
            Toast.makeText(
                this@AiActivity,
                if (added != null) "Habit added" else "Habit already exists",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun addAllAiHabits(aiHabits: List<AiHabit>) {
        lifecycleScope.launch {
            var addedCount = 0
            aiHabits.forEach { aiHabit ->
                if (repository.addHabitIfAbsent(aiHabit.name, aiHabit.emoji) != null) addedCount++
                markAdded(aiHabit)
            }
            Toast.makeText(
                this@AiActivity,
                if (addedCount == 0) "All habits already exist"
                else "$addedCount habit${if (addedCount == 1) "" else "s"} added",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
