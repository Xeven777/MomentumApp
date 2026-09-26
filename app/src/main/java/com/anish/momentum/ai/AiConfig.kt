package com.anish.momentum.ai

/**
 * User-owned endpoint configuration. Nothing here is baked into the build:
 * the user brings their own key and can point the app at any
 * OpenAI-compatible provider.
 */
data class AiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Double = DEFAULT_TEMPERATURE
) {
    /**
     * Retrofit requires a base URL ending in "/", and throws otherwise. Users
     * type URLs without it constantly, so normalise instead of failing.
     */
    val normalizedBaseUrl: String
        get() = baseUrl.trim().let { if (it.endsWith("/")) it else "$it/" }

    val isUsable: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
        const val DEFAULT_MODEL = "mistralai/mistral-small"

        const val DEFAULT_SYSTEM_PROMPT =
            "You are a helpful assistant that suggests useful, real-life daily habits.\n" +
                "Output should be **only a list of 5 habits**, each on a new line,\n" +
                "in the format: emoji + concise habit name (e.g. \uD83E\uDED8 Meditation).\n" +
                "Do NOT include numbering, titles, or any extra text.\n" +
                "These habits will be shown directly in a list in a mobile app."

        const val DEFAULT_TEMPERATURE = 0.7

        /** OpenRouter asks apps to identify themselves in these headers. */
        const val REFERER = "https://github.com/Xeven777/Momentum"
        const val APP_TITLE = "Momentum"
    }
}
