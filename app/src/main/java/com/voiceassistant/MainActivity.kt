package com.voiceassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voiceassistant.ui.MainScreen
import com.voiceassistant.ui.SettingsScreen
import com.voiceassistant.ui.theme.VoiceAssistantTheme
import com.voiceassistant.utils.PreferenceManager
import com.voiceassistant.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            VoiceAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoiceAssistantApp()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )

        val needRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needRequest.isNotEmpty()) {
            permissionLauncher.launch(needRequest.toTypedArray())
        }
    }
}

@Composable
fun VoiceAssistantApp() {
    val viewModel: MainViewModel = viewModel()
    val preferenceManager = PreferenceManager(androidx.compose.ui.platform.LocalContext.current)

    var showSettings by remember { mutableStateOf(false) }

    // 收集状态
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val currentResponse by viewModel.currentResponse.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isWakeWordEnabled by viewModel.isWakeWordEnabled.collectAsState()

    if (showSettings) {
        SettingsScreen(
            currentApiKey = preferenceManager.getApiKey(),
            currentBaseUrl = preferenceManager.getBaseUrl(),
            currentModel = preferenceManager.getModel(),
            currentTemperature = preferenceManager.getTemperature(),
            currentMaxTokens = preferenceManager.getMaxTokens(),
            isWakeWordEnabled = isWakeWordEnabled,
            currentWakeWords = preferenceManager.getWakeWords(),
            onSave = { apiKey, baseUrl, model, temperature, maxTokens, wakeWordEnabled, wakeWords ->
                preferenceManager.setApiKey(apiKey)
                preferenceManager.setBaseUrl(baseUrl)
                preferenceManager.setModel(model)
                preferenceManager.setTemperature(temperature)
                preferenceManager.setMaxTokens(maxTokens)
                preferenceManager.setWakeWords(wakeWords)
                viewModel.setWakeWordEnabled(wakeWordEnabled)
                showSettings = false
            },
            onBack = { showSettings = false }
        )
    } else {
        MainScreen(
            uiState = uiState,
            messages = messages,
            currentResponse = currentResponse,
            isRecording = isRecording,
            isWakeWordEnabled = isWakeWordEnabled,
            onMicClick = { viewModel.startListening() },
            onStopClick = {
                viewModel.stopListening()
                viewModel.stopSpeaking()
                viewModel.cancelCurrentRequest()
            },
            onClearClick = { viewModel.clearHistory() },
            onSettingsClick = { showSettings = true },
            onToggleWakeWord = {
                viewModel.setWakeWordEnabled(!isWakeWordEnabled)
            }
        )
    }
}
