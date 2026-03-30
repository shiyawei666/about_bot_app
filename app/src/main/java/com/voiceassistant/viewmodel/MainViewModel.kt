package com.voiceassistant.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voiceassistant.core.llm.ChatMessage
import com.voiceassistant.core.llm.LLMConfig
import com.voiceassistant.core.llm.LLMService
import com.voiceassistant.core.llm.OpenAICompatibleProvider
import com.voiceassistant.core.voice.TextToSpeechManager
import com.voiceassistant.core.voice.TTSState
import com.voiceassistant.core.voice.VoiceRecognitionManager
import com.voiceassistant.core.voice.VoiceRecognitionState
import com.voiceassistant.utils.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Message(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false
)

sealed class UIState {
    object Idle : UIState()
    object Listening : UIState()
    object Processing : UIState()
    object Speaking : UIState()
    data class Error(val message: String) : UIState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferenceManager = PreferenceManager(application)
    
    private val llmConfig = LLMConfig(
        apiKey = preferenceManager.getApiKey(),
        baseUrl = preferenceManager.getBaseUrl(),
        model = preferenceManager.getModel(),
        temperature = preferenceManager.getTemperature(),
        maxTokens = preferenceManager.getMaxTokens()
    )
    
    private val llmProvider = OpenAICompatibleProvider(llmConfig)
    private val llmService = LLMService(llmProvider)
    
    private val voiceRecognitionManager = VoiceRecognitionManager(application)
    private val ttsManager = TextToSpeechManager(application)
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _uiState = MutableStateFlow<UIState>(UIState.Idle)
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()
    
    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private var currentLlmJob: Job? = null
    private var fullResponse = StringBuilder()
    
    init {
        voiceRecognitionManager.initialize()
        ttsManager.initialize()
        
        viewModelScope.launch {
            voiceRecognitionManager.state.collect { state ->
                when (state) {
                    is VoiceRecognitionState.Listening -> {
                        _uiState.value = UIState.Listening
                        _isRecording.value = true
                    }
                    is VoiceRecognitionState.Processing -> {
                    }
                    is VoiceRecognitionState.Result -> {
                        _isRecording.value = false
                        if (state.text.isNotEmpty()) {
                            processUserInput(state.text)
                        } else {
                            _uiState.value = UIState.Idle
                        }
                    }
                    is VoiceRecognitionState.Error -> {
                        _uiState.value = UIState.Error(state.message)
                        _isRecording.value = false
                    }
                    is VoiceRecognitionState.Idle -> {
                        if (_uiState.value == UIState.Listening) {
                            _uiState.value = UIState.Idle
                        }
                    }
                }
            }
        }
        
        viewModelScope.launch {
            ttsManager.state.collect { state ->
                when (state) {
                    is TTSState.Speaking -> {
                        _uiState.value = UIState.Speaking
                    }
                    is TTSState.Idle, is TTSState.Completed -> {
                        if (_uiState.value == UIState.Speaking) {
                            _uiState.value = UIState.Idle
                        }
                    }
                    is TTSState.Error -> {
                        _uiState.value = UIState.Error(state.message)
                    }
                }
            }
        }
    }
    
    fun startListening() {
        if (_uiState.value == UIState.Speaking) {
            ttsManager.stop()
        }
        _uiState.value = UIState.Listening
        voiceRecognitionManager.startListening()
    }
    
    fun stopListening() {
        voiceRecognitionManager.stopListening()
        _isRecording.value = false
    }
    
    fun sendMessage(text: String) {
        if (text.isNotEmpty()) {
            processUserInput(text)
        }
    }
    
    private fun processUserInput(text: String) {
        _uiState.value = UIState.Processing
        
        _messages.value = _messages.value + Message(
            content = text,
            isUser = true
        )
        
        fullResponse.clear()
        _currentResponse.value = ""
        
        currentLlmJob = viewModelScope.launch {
            try {
                llmService.sendMessage(text).collect { chunk ->
                    fullResponse.append(chunk)
                    _currentResponse.value = fullResponse.toString()
                }
                
                val response = fullResponse.toString()
                if (response.isNotEmpty()) {
                    llmService.addAssistantMessage(response)
                    
                    _messages.value = _messages.value + Message(
                        content = response,
                        isUser = false
                    )
                    
                    speakResponse(response)
                } else {
                    _uiState.value = UIState.Idle
                }
            } catch (e: Exception) {
                _uiState.value = UIState.Error(e.message ?: "发生错误")
            }
        }
    }
    
    private fun speakResponse(text: String) {
        _uiState.value = UIState.Speaking
        ttsManager.speak(text) {
            _uiState.value = UIState.Idle
        }
    }
    
    fun stopSpeaking() {
        ttsManager.stop()
        _uiState.value = UIState.Idle
    }
    
    fun cancelCurrentRequest() {
        currentLlmJob?.cancel()
        currentLlmJob = null
        llmProvider.cancel()
        _uiState.value = UIState.Idle
        _currentResponse.value = ""
    }
    
    fun clearHistory() {
        llmService.clearHistory()
        _messages.value = emptyList()
    }
    
    override fun onCleared() {
        super.onCleared()
        voiceRecognitionManager.destroy()
        ttsManager.destroy()
        currentLlmJob?.cancel()
        llmProvider.cancel()
    }
}
