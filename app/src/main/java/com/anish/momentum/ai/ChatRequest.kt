package com.anish.momentum.ai

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<Message>,
    @SerializedName("temperature") val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int? = null
)

data class Message(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)
