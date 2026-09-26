package com.anish.momentum.ai

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Any OpenAI-compatible chat API. OpenRouter, Groq, Together, Ollama, LM Studio
 * and a self-hosted vLLM all speak this shape, so the endpoint and model are
 * user configuration rather than hard-coded.
 */
interface ChatApi {

    @POST("chat/completions")
    fun chat(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): Call<ChatResponse>

    /** Used by "Test connection" — cheap way to validate key + base URL. */
    @GET("models")
    fun models(
        @Header("Authorization") authHeader: String
    ): Call<ModelsResponse>
}
