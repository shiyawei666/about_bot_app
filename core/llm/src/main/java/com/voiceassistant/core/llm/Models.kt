package com.voiceassistant.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String = "default",
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 2048
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val error: ErrorResponse? = null
)

@Serializable
data class Choice(
    val index: Int = 0,
    val delta: Delta? = null,
    val message: Message? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ErrorResponse(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
