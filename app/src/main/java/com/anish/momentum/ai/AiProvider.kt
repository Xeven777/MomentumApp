package com.anish.momentum.ai

import android.content.Context
import com.anish.momentum.data.SettingsStore
import com.anish.momentum.utils.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds a ready-to-use [AiClient] from whatever the user has configured, and
 * reports whether there is anything to talk to yet.
 */
class AiProvider(private val context: Context) {

    private val keyStore by lazy { ApiKeyStore(context) }
    private val settings: SettingsStore get() = ServiceLocator.settings

    suspend fun hasKey(): Boolean = withContext(Dispatchers.IO) { keyStore.hasApiKey() }

    suspend fun maskedKey(): String = withContext(Dispatchers.IO) { keyStore.maskedKey() }

    suspend fun setKey(value: String) = withContext(Dispatchers.IO) { keyStore.setApiKey(value) }

    suspend fun clearKey() = withContext(Dispatchers.IO) { keyStore.clear() }

    /** @return null when no key is configured. */
    suspend fun client(): AiClient? {
        val key = withContext(Dispatchers.IO) { keyStore.getApiKey() }
        if (key.isBlank()) return null
        return AiClient(settings.currentAiConfig(), key)
    }
}
