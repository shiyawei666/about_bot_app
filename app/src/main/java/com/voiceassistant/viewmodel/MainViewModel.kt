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
import com.voiceassistant.core.voice.WakeWordDetector
import com.voiceassistant.core.voice.WakeWordState
import com.voiceassistant.utils.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    object WakeWordListening : UIState() // 新增：监听唤醒词状态
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

    // 新增：语音唤醒检测器
    private val wakeWordDetector = WakeWordDetector(
        context = application,
        wakeWords = listOf("你好助手", "小助手", "嗨助手", "hello assistant", "hi assistant"),
        onWakeWordDetected = { onWakeWordDetected() }
    )

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow<UIState>(UIState.Idle)
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // 新增：语音唤醒开关
    private val _isWakeWordEnabled = MutableStateFlow(preferenceManager.isWakeWordEnabled())
    val isWakeWordEnabled: StateFlow<Boolean> = _isWakeWordEnabled.asStateFlow()

    private var currentLlmJob: Job? = null
    private var fullResponse = StringBuilder()

    init {
        voiceRecognitionManager.initialize()
        ttsManager.initialize()
        wakeWordDetector.initialize()

        // 如果启用了语音唤醒，开始监听
        if (_isWakeWordEnabled.value) {
            startWakeWordListening()
        }

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
                            // 如果没有识别到内容，回到唤醒监听状态
                            if (_isWakeWordEnabled.value) {
                                startWakeWordListening()
                            } else {
                                _uiState.value = UIState.Idle
                            }
                        }
                    }
                    is VoiceRecognitionState.Error -> {
                        _uiState.value = UIState.Error(state.message)
                        _isRecording.value = false
                        // 出错后回到唤醒监听
                        if (_isWakeWordEnabled.value) {
                            startWakeWordListening()
                        }
                    }
                    is VoiceRecognitionState.Idle -> {
                        if (_uiState.value == UIState.Listening) {
                            if (_isWakeWordEnabled.value) {
                                startWakeWordListening()
                            } else {
                                _uiState.value = UIState.Idle
                            }
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
                            // 播放完成后回到唤醒监听状态
                            if (_isWakeWordEnabled.value) {
                                startWakeWordListening()
                            } else {
                                _uiState.value = UIState.Idle
                            }
                        }
                    }
                    is TTSState.Error -> {
                        _uiState.value = UIState.Error(state.message)
                        if (_isWakeWordEnabled.value) {
                            startWakeWordListening()
                        }
                    }
                }
            }
        }

        // 监听唤醒词检测器状态
        viewModelScope.launch {
            wakeWordDetector.state.collect { state ->
                when (state) {
                    is WakeWordState.Listening -> {
                        if (_uiState.value != UIState.Listening && 
                            _uiState.value != UIState.Speaking &&
                            _uiState.value != UIState.Processing) {
                            _uiState.value = UIState.WakeWordListening
                        }
                    }
                    is WakeWordState.WakeWordDetected -> {
                        // 播放提示音或语音提示
                        ttsManager.speak("我在听，请说")
                        // 短暂延迟后开始语音识别
                        viewModelScope.launch {
                            delay(800)
                            startVoiceRecognition()
                        }
                    }
                    is WakeWordState.Error -> {
                        // 错误时尝试重新启动
                    }
                    else -> {}
                }
            }
        }
    }

    // 新增：语音唤醒回调
    private fun onWakeWordDetected() {
        // 停止唤醒监听，开始语音识别
        wakeWordDetector.pauseListening()
    }

    // 新增：开始语音唤醒监听
    fun startWakeWordListening() {
        if (_isWakeWordEnabled.value) {
            wakeWordDetector.startListening()
        }
    }

    // 新增：停止语音唤醒监听
    fun stopWakeWordListening() {
        wakeWordDetector.stopListening()
    }

    // 新增：设置语音唤醒开关
    fun setWakeWordEnabled(enabled: Boolean) {
        _isWakeWordEnabled.value = enabled
        preferenceManager.setWakeWordEnabled(enabled)
        if (enabled) {
            startWakeWordListening()
        } else {
            stopWakeWordListening()
            if (_uiState.value == UIState.WakeWordListening) {
                _uiState.value = UIState.Idle
            }
        }
    }

    // 新增：开始语音识别（用于唤醒后的交互）
    private fun startVoiceRecognition() {
        if (_uiState.value == UIState.Speaking) {
            ttsManager.stop()
        }
        _uiState.value = UIState.Listening
        voiceRecognitionManager.startListening()
    }

    // 原有的手动开始监听方法
    fun startListening() {
        // 如果正在唤醒监听，先停止
        if (_uiState.value == UIState.WakeWordListening) {
            wakeWordDetector.pauseListening()
        }
        startVoiceRecognition()
    }

    fun stopListening() {
        voiceRecognitionManager.stopListening()
        _isRecording.value = false
        // 停止后回到唤醒监听
        if (_isWakeWordEnabled.value) {
            startWakeWordListening()
        }
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
                    if (_isWakeWordEnabled.value) {
                        startWakeWordListening()
                    } else {
                        _uiState.value = UIState.Idle
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UIState.Error(e.message ?: "发生错误")
                if (_isWakeWordEnabled.value) {
                    startWakeWordListening()
                }
            }
        }
    }

    private fun speakResponse(text: String) {
        _uiState.value = UIState.Speaking
        ttsManager.speak(text) {
            // 播放完成后自动回到唤醒监听状态
            if (_isWakeWordEnabled.value) {
                startWakeWordListening()
            } else {
                _uiState.value = UIState.Idle
            }
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
        if (_isWakeWordEnabled.value) {
            startWakeWordListening()
        } else {
            _uiState.value = UIState.Idle
        }
    }

    fun cancelCurrentRequest() {
        currentLlmJob?.cancel()
        currentLlmJob = null
        llmProvider.cancel()
        if (_isWakeWordEnabled.value) {
            startWakeWordListening()
        } else {
            _uiState.value = UIState.Idle
        }
        _currentResponse.value = ""
    }

    fun clearHistory() {
        llmService.clearHistory()
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        wakeWordDetector.destroy()
        voiceRecognitionManager.destroy()
        ttsManager.destroy()
        currentLlmJob?.cancel()
        llmProvider.cancel()
    }
}
