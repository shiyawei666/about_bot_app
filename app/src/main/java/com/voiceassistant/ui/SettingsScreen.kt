package com.voiceassistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.voiceassistant.utils.PreferenceManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentApiKey: String,
    currentBaseUrl: String,
    currentModel: String,
    currentTemperature: Double,
    currentMaxTokens: Int,
    isWakeWordEnabled: Boolean,
    currentWakeWords: String,
    onSave: (apiKey: String, baseUrl: String, model: String, temperature: Double, maxTokens: Int, wakeWordEnabled: Boolean, wakeWords: String) -> Unit,
    onBack: () -> Unit
) {
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var baseUrl by remember { mutableStateOf(currentBaseUrl) }
    var model by remember { mutableStateOf(currentModel) }
    var temperature by remember { mutableStateOf(currentTemperature.toFloat()) }
    var maxTokens by remember { mutableStateOf(currentMaxTokens.toString()) }
    var wakeWordEnabled by remember { mutableStateOf(isWakeWordEnabled) }
    var wakeWords by remember { mutableStateOf(currentWakeWords) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 语音唤醒设置卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "语音唤醒设置",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 语音唤醒开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启用语音唤醒",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "开启后可通过唤醒词激活助手",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wakeWordEnabled,
                            onCheckedChange = { wakeWordEnabled = it }
                        )
                    }

                    if (wakeWordEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = wakeWords,
                            onValueChange = { wakeWords = it },
                            label = { Text("唤醒词") },
                            placeholder = { Text("你好助手,小助手,嗨助手") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("多个唤醒词用逗号分隔") }
                        )
                    }
                }
            }

            Divider()

            // LLM 设置
            Text(
                text = "大模型设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API 地址") },
                placeholder = { Text("http://localhost:8000/v1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key (可选)") },
                placeholder = { Text("sk-xxx") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型名称") },
                placeholder = { Text("default") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "Temperature: ${String.format("%.1f", temperature)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0f..2f,
                steps = 20,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it.filter { c -> c.isDigit() } },
                label = { Text("最大 Token 数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onSave(
                        apiKey,
                        baseUrl,
                        model,
                        temperature.toDouble(),
                        maxTokens.toIntOrNull() ?: 2048,
                        wakeWordEnabled,
                        wakeWords
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存设置")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "使用说明",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = """
                            1. 语音唤醒: 开启后，说唤醒词即可激活助手进行对话
                            2. 唤醒词: 支持自定义多个唤醒词，用逗号分隔
                            3. API地址: 大模型服务的API地址，需要支持OpenAI兼容格式
                            4. API Key: 如果服务需要认证，填写对应的密钥
                            5. 模型名称: 使用的模型标识
                            6. Temperature: 控制回答的随机性，值越大越随机
                            7. 最大Token: 控制回答的最大长度
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
