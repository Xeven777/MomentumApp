package com.anish.momentum.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anish.momentum.ai.AiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by
        preferencesDataStore(name = "momentum_settings")

/**
 * Lightweight user settings. The AI API key deliberately does NOT live here — it goes in
 * [com.anish.momentum.ai.ApiKeyStore], backed by the Android Keystore.
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val userName: Flow<String> = store.data.map { it[KEY_NAME] ?: "" }
    val aiButtonEnabled: Flow<Boolean> = store.data.map { it[KEY_AI_BUTTON] ?: true }

    /** 0 = "all habits that existed that day". */
    val dailyGoal: Flow<Int> = store.data.map { it[KEY_DAILY_GOAL] ?: 0 }

    val aiConfig: Flow<AiConfig> =
            store.data.map { prefs ->
                val storedModel = prefs[KEY_MODEL]
                AiConfig(
                        baseUrl = prefs[KEY_BASE_URL] ?: AiConfig.DEFAULT_BASE_URL,
                        model =
                                if (storedModel == null || storedModel == LEGACY_DEFAULT_MODEL) {
                                    AiConfig.DEFAULT_MODEL
                                } else {
                                    storedModel
                                },
                        systemPrompt = prefs[KEY_SYSTEM_PROMPT] ?: AiConfig.DEFAULT_SYSTEM_PROMPT,
                        temperature = prefs[KEY_TEMPERATURE] ?: AiConfig.DEFAULT_TEMPERATURE
                )
            }

    suspend fun currentAiConfig(): AiConfig = aiConfig.first()

    suspend fun currentDailyGoal(): Int = dailyGoal.first()

    suspend fun setUserName(name: String) {
        store.edit { it[KEY_NAME] = name }
    }

    suspend fun setAiButtonEnabled(enabled: Boolean) {
        store.edit { it[KEY_AI_BUTTON] = enabled }
    }

    suspend fun setDailyGoal(goal: Int) {
        store.edit { it[KEY_DAILY_GOAL] = goal.coerceAtLeast(0) }
    }

    suspend fun setAiBaseUrl(url: String) {
        store.edit { it[KEY_BASE_URL] = url.trim() }
    }

    suspend fun setAiModel(model: String) {
        store.edit { it[KEY_MODEL] = model.trim() }
    }

    suspend fun setAiSystemPrompt(prompt: String) {
        store.edit { it[KEY_SYSTEM_PROMPT] = prompt }
    }

    suspend fun setAiTemperature(value: Double) {
        store.edit { it[KEY_TEMPERATURE] = value.coerceIn(0.0, 2.0) }
    }

    /** One-shot restore of the shipped defaults. */
    suspend fun resetAiConfig() {
        store.edit {
            it.remove(KEY_BASE_URL)
            it.remove(KEY_MODEL)
            it.remove(KEY_SYSTEM_PROMPT)
            it.remove(KEY_TEMPERATURE)
        }
    }

    companion object {
        private const val LEGACY_DEFAULT_MODEL = "google/gemma-4-31b-it:free"
        private val KEY_NAME = stringPreferencesKey("user_name")
        private val KEY_AI_BUTTON = booleanPreferencesKey("ai_button_enabled")
        private val KEY_DAILY_GOAL = intPreferencesKey("daily_goal")
        private val KEY_BASE_URL = stringPreferencesKey("ai_base_url")
        private val KEY_MODEL = stringPreferencesKey("ai_model")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("ai_system_prompt")
        private val KEY_TEMPERATURE = doublePreferencesKey("ai_temperature")
    }
}
