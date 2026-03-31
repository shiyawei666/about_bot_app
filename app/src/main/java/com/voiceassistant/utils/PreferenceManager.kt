package com.voiceassistant.utils

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""
    
    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }
    
    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    
    fun setBaseUrl(baseUrl: String) {
        prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
    }
    
    fun getModel(): String = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    
    fun setModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model).apply()
    }
    
    fun getTemperature(): Double = prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE).toDouble()
    
    fun setTemperature(temperature: Double) {
        prefs.edit().putFloat(KEY_TEMPERATURE, temperature.toFloat()).apply()
    }
    
    fun getMaxTokens(): Int = prefs.getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS)
    
    fun setMaxTokens(maxTokens: Int) {
        prefs.edit().putInt(KEY_MAX_TOKENS, maxTokens).apply()
    }
    
    fun getSpeechRate(): Float = prefs.getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE)

    fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat(KEY_SPEECH_RATE, rate).apply()
    }

    // 新增：语音唤醒设置
    fun isWakeWordEnabled(): Boolean = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, DEFAULT_WAKE_WORD_ENABLED)

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply()
    }

    fun getWakeWords(): String = prefs.getString(KEY_WAKE_WORDS, DEFAULT_WAKE_WORDS) ?: DEFAULT_WAKE_WORDS

    fun setWakeWords(wakeWords: String) {
        prefs.edit().putString(KEY_WAKE_WORDS, wakeWords).apply()
    }

    companion object {
        private const val PREFS_NAME = "voice_assistant_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_WAKE_WORDS = "wake_words"

        private const val DEFAULT_BASE_URL = "http://localhost:8000/v1"
        private const val DEFAULT_MODEL = "default"
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_MAX_TOKENS = 2048
        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val DEFAULT_WAKE_WORD_ENABLED = true
        private const val DEFAULT_WAKE_WORDS = "你好助手,小助手,嗨助手"
    }
}
