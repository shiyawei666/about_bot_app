# VoiceAssistant - Android 语音助手

一个基于 Android 的智能语音交互应用，支持语音识别、大模型对话和语音播报功能。

## 功能特性

- 🎤 **语音识别**: 使用 Android 原生 SpeechRecognizer 进行实时语音识别
- 🤖 **大模型对接**: 支持 OpenAI 兼容格式的 API，支持流式响应
- 🔊 **语音播报**: 使用 Android TTS 将回复转为语音
- 💬 **对话历史**: 保持上下文记忆，支持多轮对话
- ⚙️ **可配置**: 支持自定义 API 地址、模型、温度等参数

## 项目结构

```
app/
├── src/main/java/com/voiceassistant/
│   ├── MainActivity.kt           # 主Activity
│   ├── ui/
│   │   ├── MainScreen.kt         # 主界面
│   │   ├── SettingsScreen.kt     # 设置界面
│   │   └── theme/Theme.kt        # 主题配置
│   ├── viewmodel/
│   │   └── MainViewModel.kt      # 业务逻辑
│   └── utils/
│       └── PreferenceManager.kt  # 偏好设置管理
core/
├── voice/                        # 语音模块
│   └── src/main/java/.../VoiceManager.kt
└── llm/                          # 大模型模块
    └── src/main/java/.../
        ├── Models.kt             # 数据模型
        └── LLMService.kt         # LLM服务
```

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM
- **网络**: OkHttp + SSE (Server-Sent Events)
- **序列化**: kotlinx.serialization
- **异步**: Kotlin Coroutines + Flow

## 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.4

### 构建运行

1. 克隆项目并打开 Android Studio
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器
4. 点击 Run 按钮运行应用

### 配置大模型服务

应用需要对接一个支持 OpenAI 兼容格式的大模型 API。你可以：

1. **使用本地模型服务**:
   - 使用 vLLM、Ollama 等部署本地模型
   - 默认地址: `http://localhost:8000/v1`

2. **使用云服务**:
   - 百度文心一言、阿里通义千问等都提供 OpenAI 兼容接口
   - 在设置页面配置 API 地址和密钥

## 使用说明

1. **开始对话**: 点击底部的麦克风按钮开始语音输入
2. **停止录音**: 录音过程中点击按钮停止
3. **打断播放**: 播放过程中点击按钮停止语音播报
4. **清除历史**: 点击右上角清除按钮清空对话记录
5. **设置**: 点击右上角设置按钮配置 API 参数

## API 配置说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| API 地址 | 大模型服务的 API 地址 | http://localhost:8000/v1 |
| API Key | 认证密钥（可选） | 空 |
| 模型名称 | 使用的模型标识 | default |
| Temperature | 回答随机性 (0-2) | 0.7 |
| 最大 Token | 回答最大长度 | 2048 |

## 权限说明

- `RECORD_AUDIO`: 语音识别必需
- `INTERNET`: 网络请求必需
- `ACCESS_NETWORK_STATE`: 网络状态检测

## 扩展开发

### 添加新的 LLM 提供商

实现 `LLMProvider` 接口:

```kotlin
class CustomLLMProvider : LLMProvider {
    override suspend fun chat(messages: List<ChatMessage>): Flow<String> {
        // 实现你的逻辑
    }
    
    override fun cancel() {
        // 取消请求
    }
}
```

### 自定义语音识别

修改 `VoiceRecognitionManager` 类，可以集成第三方语音识别服务如:
- 百度语音识别
- 讯飞语音识别
- 阿里云语音识别

## 注意事项

1. 首次运行需要授予录音权限
2. 确保设备已安装 Google 语音服务（用于语音识别）
3. 确保 API 服务可访问（本地服务需要同一网络或端口转发）

## License

MIT License
