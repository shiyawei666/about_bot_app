package com.voiceassistant.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class WakeWordState {
    object Idle : WakeWordState()
    object Listening : WakeWordState()
    data class WakeWordDetected(val wakeWord: String) : WakeWordState()
    data class Error(val message: String) : WakeWordState()
}

class WakeWordDetector(
    private val context: Context,
    private val wakeWords: List<String> = listOf("你好助手", "小助手", "嗨助手", "hello assistant"),
    private val onWakeWordDetected: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow<WakeWordState>(WakeWordState.Idle)
    val state: StateFlow<WakeWordState> = _state.asStateFlow()

    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = WakeWordState.Listening
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val errorMessage = getErrorMessage(error)
            _state.value = WakeWordState.Error(errorMessage)
            // 出错后短暂延迟重新开始监听
            if (isRunning) {
                scope.launch {
                    try {
                        delay(500)
                        if (isRunning) {
                            restartListening()
                        }
                    } catch (e: CancellationException) {
                        // 协程被取消，忽略
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""

            // 检查是否包含唤醒词
            val detectedWakeWord = wakeWords.find { wakeWord ->
                text.contains(wakeWord.lowercase(Locale.getDefault()))
            }

            if (detectedWakeWord != null) {
                _state.value = WakeWordState.WakeWordDetected(detectedWakeWord)
                onWakeWordDetected()
            }

            // 继续监听
            if (isRunning) {
                scope.launch {
                    try {
                        delay(300)
                        if (isRunning) {
                            restartListening()
                        }
                    } catch (e: CancellationException) {
                        // 协程被取消，忽略
                    }
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""

            // 实时检查部分结果
            val detectedWakeWord = wakeWords.find { wakeWord ->
                text.contains(wakeWord.lowercase(Locale.getDefault()))
            }

            if (detectedWakeWord != null) {
                speechRecognizer?.stopListening()
                _state.value = WakeWordState.WakeWordDetected(detectedWakeWord)
                onWakeWordDetected()

                if (isRunning) {
                    scope.launch {
                        try {
                            delay(500)
                            if (isRunning) {
                                restartListening()
                            }
                        } catch (e: CancellationException) {
                            // 协程被取消，忽略
                        }
                    }
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun initialize() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }
        }
    }

    fun startListening() {
        isRunning = true
        restartListening()
    }

    fun stopListening() {
        isRunning = false
        speechRecognizer?.stopListening()
        _state.value = WakeWordState.Idle
    }

    fun pauseListening() {
        isRunning = false
        speechRecognizer?.stopListening()
    }

    fun resumeListening() {
        if (!isRunning) {
            isRunning = true
            restartListening()
        }
    }

    private fun restartListening() {
        if (!isRunning) return

        try {
            if (speechRecognizer == null) {
                _state.value = WakeWordState.Error("语音识别服务不可用")
                return
            }
            
            speechRecognizer?.cancel()

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                // 设置较短的超时时间，以便快速响应
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _state.value = WakeWordState.Error("启动监听失败: ${e.message}")
        }
    }

    fun destroy() {
        isRunning = false
        scope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
            SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
            SpeechRecognizer.ERROR_NETWORK -> "网络错误"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
            SpeechRecognizer.ERROR_NO_MATCH -> "未检测到语音"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙碌"
            SpeechRecognizer.ERROR_SERVER -> "服务器错误"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
            else -> "未知错误: $errorCode"
        }
    }
}
