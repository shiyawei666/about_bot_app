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
    
    if (showSettings) {
        SettingsScreen(
            currentApiKey = preferenceManager.getApiKey(),
            currentBaseUrl = preferenceManager.getBaseUrl(),
            currentModel = preferenceManager.getModel(),
            currentTemperature = preferenceManager.getTemperature(),
            currentMaxTokens = preferenceManager.getMaxTokens(),
            onSave = { apiKey, baseUrl, model, temperature, maxTokens ->
                preferenceManager.setApiKey(apiKey)
                preferenceManager.setBaseUrl(baseUrl)
                preferenceManager.setModel(model)
                preferenceManager.setTemperature(temperature)
                preferenceManager.setMaxTokens(maxTokens)
                showSettings = false
            },
            onBack = { showSettings = false }
        )
    } else {
        MainScreen(
            uiState = viewModel.uiState.value,
            messages = viewModel.messages.value,
            currentResponse = viewModel.currentResponse.value,
            isRecording = viewModel.isRecording.value,
            onMicClick = { viewModel.startListening() },
            onStopClick = { 
                viewModel.stopListening()
                viewModel.stopSpeaking()
                viewModel.cancelCurrentRequest()
            },
            onClearClick = { viewModel.clearHistory() },
            onSettingsClick = { showSettings = true }
        )
    }
}
