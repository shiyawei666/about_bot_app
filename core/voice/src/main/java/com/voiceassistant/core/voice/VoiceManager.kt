package com.voiceassistant.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

sealed class VoiceRecognitionState {
    object Idle : VoiceRecognitionState()
    object Listening : VoiceRecognitionState()
    data class Processing(val partialText: String) : VoiceRecognitionState()
    data class Result(val text: String) : VoiceRecognitionState()
    data class Error(val message: String) : VoiceRecognitionState()
}

class VoiceRecognitionManager(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow<VoiceRecognitionState>(VoiceRecognitionState.Idle)
    val state: StateFlow<VoiceRecognitionState> = _state.asStateFlow()
    
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceRecognitionState.Listening
        }
        
        override fun onBeginningOfSpeech() {
            _state.value = VoiceRecognitionState.Listening
        }
        
        override fun onRmsChanged(rmsdB: Float) {}
        
        override fun onBufferReceived(buffer: ByteArray?) {}
        
        override fun onEndOfSpeech() {
            if (_state.value is VoiceRecognitionState.Listening) {
                _state.value = VoiceRecognitionState.Processing("")
            }
        }
        
        override fun onError(error: Int) {
            val errorMessage = getErrorMessage(error)
            _state.value = VoiceRecognitionState.Error(errorMessage)
        }
        
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            _state.value = VoiceRecognitionState.Result(text)
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            _state.value = VoiceRecognitionState.Processing(text)
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
        if (speechRecognizer == null) {
            _state.value = VoiceRecognitionState.Error("语音识别服务不可用")
            return
        }
        
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _state.value = VoiceRecognitionState.Error("启动语音识别失败: ${e.message}")
        }
    }
    
    fun stopListening() {
        speechRecognizer?.stopListening()
    }
    
    fun cancel() {
        speechRecognizer?.cancel()
        _state.value = VoiceRecognitionState.Idle
    }
    
    fun destroy() {
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
            SpeechRecognizer.ERROR_NO_MATCH -> "无法识别语音"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙碌"
            SpeechRecognizer.ERROR_SERVER -> "服务器错误"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音"
            else -> "未知错误: $errorCode"
        }
    }
}

sealed class TTSState {
    object Idle : TTSState()
    object Speaking : TTSState()
    object Completed : TTSState()
    data class Error(val message: String) : TTSState()
}

class TextToSpeechManager(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private val _state = MutableStateFlow<TTSState>(TTSState.Idle)
    val state: StateFlow<TTSState> = _state.asStateFlow()
    
    private var onSpeakComplete: (() -> Unit)? = null
    
    fun getAvailableTtsEngines(): List<String> {
        val pm = context.packageManager
        val intent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        return resolveInfoList.map { it.activityInfo.packageName }
    }
    
    fun initialize(onReady: () -> Unit = {}) {
        // 初始化TTS引擎
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        // 获取所有可用的TTS引擎
                        val availableEngines = getAvailableTtsEngines()
                        
                        // 过滤掉Google TTS
                        val nonGoogleEngines = availableEngines.filter { it != "com.google.android.tts" }
                        
                        // 优先使用非Google TTS引擎
                        if (nonGoogleEngines.isNotEmpty()) {
                            val selectedEngine = nonGoogleEngines.first()
                            val result = tts?.setEngineByPackageName(selectedEngine)
                            if (result != TextToSpeech.SUCCESS) {
                                _state.value = TTSState.Error("无法使用所选TTS引擎: $selectedEngine")
                                return@TextToSpeech
                            }
                        }
                        
                        // 设置语言为中文
                        val langResult = tts?.setLanguage(Locale.CHINESE)
                        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            _state.value = TTSState.Error("不支持中文语言")
                            return@TextToSpeech
                        }
                        
                        // 设置监听器
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _state.value = TTSState.Speaking
                            }
                            
                            override fun onDone(utteranceId: String?) {
                                _state.value = TTSState.Completed
                                onSpeakComplete?.invoke()
                                _state.value = TTSState.Idle
                            }
                            
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                _state.value = TTSState.Error("TTS播放错误")
                            }
                            
                            override fun onError(utteranceId: String?, errorCode: Int) {
                                _state.value = TTSState.Error("TTS播放错误: $errorCode")
                            }
                        })
                        
                        onReady()
                    } catch (e: Exception) {
                        _state.value = TTSState.Error("TTS初始化过程出错: ${e.message}")
                    }
                } else {
                    _state.value = TTSState.Error("TTS初始化失败")
                }
            }
        } catch (e: Exception) {
            _state.value = TTSState.Error("创建TTS实例失败: ${e.message}")
        }
    }
    
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        onSpeakComplete = onComplete
        tts?.let {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utterance_${System.currentTimeMillis()}")
                putString(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1.0")
                putString(TextToSpeech.Engine.KEY_PARAM_STREAM, "3")
            }
            it.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utterance_${System.currentTimeMillis()}")
        }
    }
    
    fun speakStream(text: String, onComplete: (() -> Unit)? = null) {
        onSpeakComplete = onComplete
        tts?.let {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utterance_${System.currentTimeMillis()}")
                putString(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1.0")
                putString(TextToSpeech.Engine.KEY_PARAM_STREAM, "3")
            }
            it.speak(text, TextToSpeech.QUEUE_ADD, params, "utterance_${System.currentTimeMillis()}")
        }
    }
    
    fun stop() {
        tts?.stop()
        _state.value = TTSState.Idle
    }
    
    fun pause() {
        tts?.playSilentUtterance(100, TextToSpeech.QUEUE_ADD, null)
    }
    
    fun resume() {
    }
    
    fun isSpeaking(): Boolean = tts?.isSpeaking ?: false
    
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }
    
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }
    
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
