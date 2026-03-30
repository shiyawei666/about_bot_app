package com.voiceassistant.core.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

sealed class LLMState {
    object Idle : LLMState()
    object Connecting : LLMState()
    data class Streaming(val text: String) : LLMState()
    data class Completed(val fullText: String) : LLMState()
    data class Error(val message: String) : LLMState()
}

interface LLMProvider {
    suspend fun chat(messages: List<ChatMessage>): Flow<String>
    fun cancel()
}

class LLMConfig(
    val apiKey: String = "",
    val baseUrl: String = "http://localhost:8000/v1",
    val model: String = "default",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val timeoutSeconds: Long = 60
)

class OpenAICompatibleProvider(
    private val config: LLMConfig
) : LLMProvider {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .build()
    
    private var currentEventSource: EventSource? = null
    
    override suspend fun chat(messages: List<ChatMessage>): Flow<String> = callbackFlow {
        val request = ChatRequest(
            model = config.model,
            messages = messages,
            stream = true,
            temperature = config.temperature,
            maxTokens = config.maxTokens
        )
        
        val requestBody = json.encodeToString(request)
            .toRequestBody("application/json".toMediaType())
        
        val httpRequest = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .header("Content-Type", "application/json")
            .apply {
                if (config.apiKey.isNotEmpty()) {
                    header("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .post(requestBody)
            .build()
        
        val eventSourceFactory = EventSources.createFactory(client)
        val fullResponse = StringBuilder()
        
        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data == "[DONE]") {
                    close()
                    return
                }
                
                try {
                    val response = json.decodeFromString<ChatResponse>(data)
                    val content = response.choices?.firstOrNull()?.delta?.content
                    if (!content.isNullOrEmpty()) {
                        fullResponse.append(content)
                        trySend(content)
                    }
                } catch (e: Exception) {
                }
            }
            
            override fun onClosed(eventSource: EventSource) {
                close()
            }
            
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                val errorMsg = t?.message ?: response?.message ?: "Unknown error"
                close(Exception(errorMsg))
            }
        }
        
        currentEventSource = eventSourceFactory.newEventSource(httpRequest, listener)
        
        awaitClose {
            currentEventSource?.cancel()
            currentEventSource = null
        }
    }.flowOn(Dispatchers.IO)
    
    override fun cancel() {
        currentEventSource?.cancel()
        currentEventSource = null
    }
}

class LLMService(
    private val provider: LLMProvider
) {
    private val conversationHistory = mutableListOf<ChatMessage>()
    private val systemPrompt = """
        你是一个智能语音助手，请用简洁、自然的语言回答用户的问题。
        回答时请注意：
        1. 回答要简洁明了，适合语音播报
        2. 避免使用复杂的格式和特殊符号
        3. 如果需要列举，请使用"第一、第二、第三"这样的表达方式
        4. 语气要友好自然，像在和朋友聊天
    """.trimIndent()
    
    init {
        conversationHistory.add(ChatMessage("system", systemPrompt))
    }
    
    suspend fun sendMessage(userMessage: String): Flow<String> {
        conversationHistory.add(ChatMessage("user", userMessage))
        return provider.chat(conversationHistory.toList())
    }
    
    fun addAssistantMessage(message: String) {
        conversationHistory.add(ChatMessage("assistant", message))
    }
    
    fun clearHistory() {
        conversationHistory.clear()
        conversationHistory.add(ChatMessage("system", systemPrompt))
    }
    
    fun getHistory(): List<ChatMessage> = conversationHistory.toList()
}
