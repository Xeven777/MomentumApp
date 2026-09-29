package com.anish.momentum.ai

import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A failure that already carries a message worth showing to the user. */
class AiException(message: String) : Exception(message)

/**
 * Thin client over any OpenAI-compatible endpoint. Retrofit is built per
 * instance because the base URL is user configuration and can change at any
 * time from Settings.
 */
class AiClient(
    private val config: AiConfig,
    private val apiKey: String,
    private val chatApi: ChatApi? = null
) {

    private val gson = Gson()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("HTTP-Referer", AiConfig.REFERER)
                .header("X-Title", AiConfig.APP_TITLE)
                .build()
            chain.proceed(request)
        }
        .build()

    private val api: ChatApi by lazy {
        chatApi ?: Retrofit.Builder()
            .baseUrl(config.normalizedBaseUrl)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatApi::class.java)
    }

    private fun authHeader() = "Bearer $apiKey"

    private companion object {
        const val RETRY_DELAY_MS = 1500L
    }

    /** Returns the assistant's reply text. Throws [AiException] with a friendly message. */
    suspend fun complete(userPrompt: String): String {
        if (apiKey.isBlank()) {
            throw AiException("No API key configured. Add one in Settings.")
        }
        if (!config.isUsable) {
            throw AiException("AI endpoint is not configured. Check the base URL and model in Settings.")
        }

        val request = ChatRequest(
            model = config.model,
            temperature = config.temperature,
            messages = listOf(
                Message("system", config.systemPrompt),
                Message("user", userPrompt)
            )
        )

        val response = try {
            sendWithRetry(request)
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException(errorMessage(e))
        }

        val reply = response.choices?.firstOrNull()?.message?.content
        if (reply.isNullOrBlank()) {
            throw AiException("The model returned an empty response. Try again.")
        }
        return reply
    }

    /**
     * One quick retry on transient free-tier hiccups (provider 5xx, timeouts).
     * The completion call is read-only, so repeating it is safe.
     */
    private suspend fun sendWithRetry(request: ChatRequest): ChatResponse {
        var lastError: Exception = IOException("Request failed")
        repeat(2) { attempt ->
            try {
                return api.chat(authHeader(), request).await()
            } catch (e: AiException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt == 0 && isTransientProviderError(e)) {
                    delay(RETRY_DELAY_MS)
                } else {
                    throw e
                }
            }
        }
        throw lastError
    }

    private fun isTransientProviderError(e: Exception): Boolean = when (e) {
        is SocketTimeoutException -> true
        is HttpFailure ->
            e.code in 500..599 ||
                e.errorBody?.error?.message?.contains("provider", ignoreCase = true) == true
        else -> false
    }

    /** Validates key + base URL. Returns the number of models the endpoint offers. */
    suspend fun testConnection(): Result<Int> {
        if (apiKey.isBlank()) return Result.failure(AiException("No API key configured."))
        return try {
            val response = api.models(authHeader()).await()
            val count = response.data?.size ?: 0
            if (count == 0) Result.failure(AiException("Connected, but the endpoint listed no models."))
            else Result.success(count)
        } catch (e: AiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AiException(errorMessage(e)))
        }
    }

    /** Maps HTTP failures onto the codes users actually hit. */
    private fun errorMessage(e: Exception): String {
        if (e !is HttpFailure) {
            return when (e) {
                is SocketTimeoutException ->
                    "The request timed out. Try again or pick a faster model."
                is UnknownHostException ->
                    "Could not resolve ${config.normalizedBaseUrl}. Check the base URL and your connection."
                is IOException ->
                    "Could not reach ${config.normalizedBaseUrl}. Check the base URL and your connection."
                else -> "Unexpected error: ${e.message}"
            }
        }
        val serverMessage = e.errorBody?.error?.message
        if (e.code in 500..599 ||
            serverMessage?.contains("provider", ignoreCase = true) == true
        ) {
            return "The free model's server stumbled. Wait a moment and try again, " +
                "or pick another free model in Settings."
        }
        return when (e.code) {
            401 -> serverMessage ?: "API key was rejected. Check the key in Settings."
            402 -> serverMessage ?: "This provider reports no credit left on the account."
            403 -> serverMessage ?: "This key is not allowed to use that model."
            404 -> serverMessage ?: "Endpoint or model not found. Check the base URL and model name."
            429 -> serverMessage ?: "Rate limited. Wait a moment and try again."
            else -> serverMessage ?: "Request failed (HTTP ${e.code})."
        }
    }

    private class HttpFailure(val code: Int, val errorBody: ErrorResponse?) : IOException()

    private suspend fun <T> Call<T>.await(): T = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    cont.resume(body)
                } else {
                    val parsed = try {
                        response.errorBody()?.string()?.let {
                            gson.fromJson(it, ErrorResponse::class.java)
                        }
                    } catch (e: Exception) {
                        null
                    }
                    cont.resumeWithException(HttpFailure(response.code(), parsed))
                }
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                cont.resumeWithException(t as? Exception ?: IOException(t.message))
            }
        })
    }
}
