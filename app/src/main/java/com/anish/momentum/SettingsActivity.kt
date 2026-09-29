package com.anish.momentum

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.anish.momentum.ai.AiConfig
import com.anish.momentum.ai.AiProvider
import com.anish.momentum.data.SettingsStore
import com.anish.momentum.databinding.ActivitySettingsBinding
import com.anish.momentum.utils.ServiceLocator
import com.anish.momentum.utils.Vibration
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val settings: SettingsStore get() = ServiceLocator.settings
    private val ai by lazy { AiProvider(this) }

    /** Guards the sliders' programmatic writes from echoing back as user edits. */
    private var updatingTemperature = false
    private var updatingGoal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.editName.setOnClickListener {
            Vibration.vibrate(this, 50)
            showEditNameDialog()
        }

        binding.github.setOnClickListener {
            Vibration.vibrate(this, 50)
            openUrl("https://github.com/Xeven777/Momentum")
        }

        binding.projects.setOnClickListener {
            Vibration.vibrate(this, 50)
            openUrl("https://anish7.me/projects")
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settings.aiButtonEnabled.collect { enabled ->
                        if (binding.aiSwitch.isChecked != enabled) binding.aiSwitch.isChecked = enabled
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

        binding.aiSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { settings.setAiButtonEnabled(isChecked) }
        }

        binding.apiKeyCard.setOnClickListener {
            Vibration.vibrate(this, 50)
            showApiKeyDialog()
        }
        binding.baseUrlCard.setOnClickListener {
            Vibration.vibrate(this, 50)
            showTextDialog(
                title = "Base URL",
                hint = "https://openrouter.ai/api/v1",
                initial = currentBaseUrl,
                multiline = false
            ) { value -> lifecycleScope.launch { settings.setAiBaseUrl(value) } }
        }
        binding.modelCard.setOnClickListener {
            Vibration.vibrate(this, 50)
            showModelDialog()
        }
        binding.systemPromptCard.setOnClickListener {
            Vibration.vibrate(this, 50)
            showTextDialog(
                title = "System prompt",
                hint = "Describe the format you want the AI to return",
                initial = currentSystemPrompt,
                multiline = true
            ) { value -> lifecycleScope.launch { settings.setAiSystemPrompt(value) } }
        }

        binding.dailyGoalSlider.addOnChangeListener { _, value, fromUser ->
            renderDailyGoal(value.toInt())
            if (fromUser && !updatingGoal) {
                lifecycleScope.launch { settings.setDailyGoal(value.toInt()) }
            }
        }

        binding.temperatureSlider.addOnChangeListener { _, value, fromUser ->
            binding.temperatureValue.text = String.format("%.1f", value)
            if (fromUser && !updatingTemperature) {
                lifecycleScope.launch { settings.setAiTemperature(value.toDouble()) }
            }
        }

        binding.testConnectionCard.setOnClickListener {
            Vibration.vibrate(this, 50)
            testConnection()
        }

        binding.resetAiCard.setOnClickListener {
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
        binding.baseUrlValue.text = config.normalizedBaseUrl
        binding.modelValue.text = config.model
        binding.systemPromptValue.text = config.systemPrompt
        binding.temperatureValue.text = String.format("%.1f", config.temperature)
        if (kotlin.math.abs(binding.temperatureSlider.value - config.temperature) > 0.001f) {
            updatingTemperature = true
            binding.temperatureSlider.value = config.temperature.toFloat()
            updatingTemperature = false
        }
    }

    private fun renderDailyGoal(goal: Int) {
        binding.dailyGoalValue.text = if (goal <= 0) "All habits that day" else "$goal habit${if (goal == 1) "" else "s"}"
        if (binding.dailyGoalSlider.value.toInt() != goal) {
            updatingGoal = true
            binding.dailyGoalSlider.value = goal.toFloat()
            updatingGoal = false
        }
    }

    private fun refreshApiKeyLabel() {
        lifecycleScope.launch {
            val masked = ai.maskedKey()
            binding.apiKeyValue.text = if (masked.isBlank()) "Not set — tap to add" else masked
        }
    }

    // ---------------------------------------------------------------- dialogs

    private fun showEditNameDialog() {
        val input = EditText(this)
        input.hint = "Enter new name"
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Name")
            .setView(input)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .show()
        // Validate on click without auto-dismissing, so a blank name does not
        // throw away the dialog.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = input.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch { settings.setUserName(newName) }
            Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
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
        binding.testConnectionTxt.text = "Testing…"
        lifecycleScope.launch {
            val client = ai.client()
            if (client == null) {
                binding.testConnectionTxt.text = "Test connection"
                Toast.makeText(
                    this@SettingsActivity,
                    "Add an API key first",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val result = client.testConnection()
            binding.testConnectionTxt.text = "Test connection"
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
