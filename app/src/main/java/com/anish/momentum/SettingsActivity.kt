package com.anish.momentum

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.anish.momentum.ai.AiConfig
import com.anish.momentum.ai.AiProvider
import com.anish.momentum.data.SettingsStore
import com.anish.momentum.utils.ServiceLocator
import com.anish.momentum.utils.Vibration
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var editName: CardView
    private lateinit var github: CardView
    private lateinit var projects: CardView
    private lateinit var aiSwitch: MaterialSwitch
    private lateinit var apiKeyValue: TextView
    private lateinit var baseUrlValue: TextView
    private lateinit var modelValue: TextView
    private lateinit var systemPromptValue: TextView
    private lateinit var temperatureValue: TextView
    private lateinit var temperatureSlider: Slider
    private lateinit var dailyGoalValue: TextView
    private lateinit var dailyGoalSlider: Slider
    private lateinit var testConnectionTxt: TextView

    private val settings: SettingsStore get() = ServiceLocator.settings
    private val ai by lazy { AiProvider(this) }

    /** Guards the sliders' programmatic writes from echoing back as user edits. */
    private var updatingTemperature = false
    private var updatingGoal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        editName = findViewById(R.id.edit_name)
        github = findViewById(R.id.github)
        projects = findViewById(R.id.projects)
        aiSwitch = findViewById(R.id.ai_switch)
        apiKeyValue = findViewById(R.id.api_key_value)
        baseUrlValue = findViewById(R.id.base_url_value)
        modelValue = findViewById(R.id.model_value)
        systemPromptValue = findViewById(R.id.system_prompt_value)
        temperatureValue = findViewById(R.id.temperature_value)
        temperatureSlider = findViewById(R.id.temperature_slider)
        dailyGoalValue = findViewById(R.id.daily_goal_value)
        dailyGoalSlider = findViewById(R.id.daily_goal_slider)
        testConnectionTxt = findViewById(R.id.test_connection_txt)

        editName.setOnClickListener {
            Vibration.vibrate(this, 50)
            showEditNameDialog()
        }

        github.setOnClickListener {
            Vibration.vibrate(this, 50)
            openUrl("https://github.com/Xeven777/Momentum")
        }

        projects.setOnClickListener {
            Vibration.vibrate(this, 50)
            openUrl("https://anish7.me/projects")
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settings.aiButtonEnabled.collect { enabled ->
                        if (aiSwitch.isChecked != enabled) aiSwitch.isChecked = enabled
                    }
                }
                launch {
                    settings.aiConfig.collect { config -> renderAiConfig(config) }
                }
                launch {
                    settings.dailyGoal.collect { goal -> renderDailyGoal(goal) }
                }
            }
        }

        aiSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { settings.setAiButtonEnabled(isChecked) }
        }

        findViewById<CardView>(R.id.api_key_card).setOnClickListener {
            Vibration.vibrate(this, 50)
            showApiKeyDialog()
        }
        findViewById<CardView>(R.id.base_url_card).setOnClickListener {
            Vibration.vibrate(this, 50)
            showTextDialog(
                title = "Base URL",
                hint = "https://openrouter.ai/api/v1",
                initial = currentBaseUrl,
                multiline = false
            ) { value -> lifecycleScope.launch { settings.setAiBaseUrl(value) } }
        }
        findViewById<CardView>(R.id.model_card).setOnClickListener {
            Vibration.vibrate(this, 50)
            showModelDialog()
        }
        findViewById<CardView>(R.id.system_prompt_card).setOnClickListener {
            Vibration.vibrate(this, 50)
            showTextDialog(
                title = "System prompt",
                hint = "Describe the format you want the AI to return",
                initial = currentSystemPrompt,
                multiline = true
            ) { value -> lifecycleScope.launch { settings.setAiSystemPrompt(value) } }
        }

        dailyGoalSlider.addOnChangeListener { _, value, fromUser ->
            renderDailyGoal(value.toInt())
            if (fromUser && !updatingGoal) {
                lifecycleScope.launch { settings.setDailyGoal(value.toInt()) }
            }
        }

        temperatureSlider.addOnChangeListener { _, value, fromUser ->
            temperatureValue.text = String.format("%.1f", value)
            if (fromUser && !updatingTemperature) {
                lifecycleScope.launch { settings.setAiTemperature(value.toDouble()) }
            }
        }

        findViewById<CardView>(R.id.test_connection_card).setOnClickListener {
            Vibration.vibrate(this, 50)
            testConnection()
        }

        findViewById<CardView>(R.id.reset_ai_card).setOnClickListener {
            Vibration.vibrate(this, 50)
            confirmResetAi()
        }

        refreshApiKeyLabel()
    }

    // ------------------------------------------------------------- rendering

    private var currentBaseUrl = AiConfig.DEFAULT_BASE_URL
    private var currentSystemPrompt = AiConfig.DEFAULT_SYSTEM_PROMPT

    private fun renderAiConfig(config: AiConfig) {
        currentBaseUrl = config.baseUrl
        currentSystemPrompt = config.systemPrompt
        baseUrlValue.text = config.normalizedBaseUrl
        modelValue.text = config.model
        systemPromptValue.text = config.systemPrompt
        temperatureValue.text = String.format("%.1f", config.temperature)
        if (kotlin.math.abs(temperatureSlider.value - config.temperature) > 0.001f) {
            updatingTemperature = true
            temperatureSlider.value = config.temperature.toFloat()
            updatingTemperature = false
        }
    }

    private fun renderDailyGoal(goal: Int) {
        dailyGoalValue.text = if (goal <= 0) "All habits that day" else "$goal habit${if (goal == 1) "" else "s"}"
        if (dailyGoalSlider.value.toInt() != goal) {
            updatingGoal = true
            dailyGoalSlider.value = goal.toFloat()
            updatingGoal = false
        }
    }

    private fun refreshApiKeyLabel() {
        lifecycleScope.launch {
            val masked = ai.maskedKey()
            apiKeyValue.text = if (masked.isBlank()) "Not set — tap to add" else masked
        }
    }

    // ---------------------------------------------------------------- dialogs

    private fun showEditNameDialog() {
        val input = EditText(this)
        input.hint = "Enter new name"
        AlertDialog.Builder(this)
            .setTitle("Edit Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch { settings.setUserName(newName) }
                Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showApiKeyDialog() {
        val input = EditText(this)
        input.hint = "Paste your API key"
        input.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.setPadding(40, 40, 40, 40)

        AlertDialog.Builder(this)
            .setTitle("API key")
            .setMessage(
                "Stored encrypted on this device only. Works with OpenRouter or any " +
                    "OpenAI-compatible provider."
            )
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().trim()
                lifecycleScope.launch {
                    ai.setKey(value)
                    refreshApiKeyLabel()
                }
                Toast.makeText(
                    this,
                    if (value.isBlank()) "Key cleared" else "Key saved",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNeutralButton("Paste") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                val pasted = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                input.setText(pasted)
                Toast.makeText(this, "Pasted — tap Save to confirm", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showModelDialog() {
        val input = EditText(this)
        input.hint = "e.g. mistralai/mistral-small"
        input.setPadding(40, 40, 40, 40)

        AlertDialog.Builder(this)
            .setTitle("Model")
            .setMessage(
                "OpenRouter model id, or whatever your provider calls it. " +
                    "Free options include google/gemini-2.0-flash-exp:free and " +
                    "deepseek/deepseek-chat-v3-0324:free"
            )
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    lifecycleScope.launch { settings.setAiModel(value) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTextDialog(
        title: String,
        hint: String,
        initial: String,
        multiline: Boolean,
        onSave: (String) -> Unit
    ) {
        val input = EditText(this)
        input.hint = hint
        input.setText(initial)
        if (multiline) {
            input.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            input.minLines = 4
            input.setPadding(40, 40, 40, 40)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Save") { _, _ -> onSave(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmResetAi() {
        AlertDialog.Builder(this)
            .setTitle("Reset AI settings?")
            .setMessage("Base URL, model, system prompt and temperature return to defaults. Your API key is kept.")
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch {
                    settings.resetAiConfig()
                    Toast.makeText(this@SettingsActivity, "AI settings reset", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------ connection

    private fun testConnection() {
        testConnectionTxt.text = "Testing…"
        lifecycleScope.launch {
            val client = ai.client()
            if (client == null) {
                testConnectionTxt.text = "Test connection"
                Toast.makeText(
                    this@SettingsActivity,
                    "Add an API key first",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val result = client.testConnection()
            testConnectionTxt.text = "Test connection"
            result.onSuccess { count ->
                Toast.makeText(
                    this@SettingsActivity,
                    "Connected — $count models available",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@SettingsActivity,
                    error.message ?: "Connection failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No browser found to open link", Toast.LENGTH_SHORT).show()
        }
    }
}
