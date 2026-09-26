package com.anish.momentum.ai

import com.google.gson.annotations.SerializedName

data class ChatResponse(
    @SerializedName("choices") val choices: List<Choice>? = null
)

data class Choice(
    @SerializedName("message") val message: Message? = null
)

/** OpenAI-style error envelope, also used by most compatible providers. */
data class ErrorResponse(
    @SerializedName("error") val error: ErrorBody? = null
)

data class ErrorBody(
    @SerializedName("message") val message: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("type") val type: String? = null
)

data class ModelsResponse(
    @SerializedName("data") val data: List<ModelInfo>? = null
)

data class ModelInfo(
    @SerializedName("id") val id: String? = null
)
