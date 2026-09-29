package com.anish.momentum

import com.anish.momentum.ai.AiClient
import com.anish.momentum.ai.AiConfig
import com.anish.momentum.ai.AiException
import com.anish.momentum.ai.ChatApi
import com.anish.momentum.ai.ChatRequest
import com.anish.momentum.ai.ChatResponse
import com.anish.momentum.ai.ModelsResponse
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AiClientTest {

    @Test
    fun `provider error retries once then suggests another model`() = runBlocking {
        var chatCalls = 0
        val api = StubChatApi {
            chatCalls++
            FakeCall(Response.error<ChatResponse>(502, errorBody("Provider returned error")))
        }
        val client = AiClient(AiConfig(model = "qwen/qwen3.8-27b:free"), "key", api)
        try {
            client.complete("Give me habits")
            fail("expected AiException")
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("try again"))
        }
        assertEquals(2, chatCalls)
    }

    @Test
    fun `rejected key does not retry`() = runBlocking {
        var chatCalls = 0
        val api = StubChatApi {
            chatCalls++
            FakeCall(Response.error<ChatResponse>(401, errorBody("Invalid API key")))
        }
        val client = AiClient(AiConfig(model = "qwen/qwen3.8-27b:free"), "key", api)
        try {
            client.complete("Give me habits")
            fail("expected AiException")
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("Invalid API key"))
        }
        assertEquals(1, chatCalls)
    }

    private fun errorBody(message: String): ResponseBody =
        ResponseBody.create(null, """{"error":{"message":"$message"}}""")

    private class StubChatApi(
        private val chatHandler: () -> Call<ChatResponse>
    ) : ChatApi {
        override fun chat(authHeader: String, request: ChatRequest): Call<ChatResponse> =
            chatHandler()

        override fun models(authHeader: String): Call<ModelsResponse> =
            FakeCall(Response.success(ModelsResponse(emptyList())))
    }

    private class FakeCall<T>(private val response: Response<T>) : Call<T> {
        override fun enqueue(callback: Callback<T>) {
            callback.onResponse(this, response)
        }

        override fun isExecuted() = false
        override fun clone(): Call<T> = this
        override fun isCanceled() = false
        override fun cancel() {}
        override fun execute(): Response<T> = response
        override fun request(): Request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .build()

        override fun timeout(): Timeout = Timeout.NONE
    }
}
