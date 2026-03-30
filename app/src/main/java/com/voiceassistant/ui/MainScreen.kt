package com.voiceassistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceassistant.viewmodel.Message
import com.voiceassistant.viewmodel.UIState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: UIState,
    messages: List<Message>,
    currentResponse: String,
    isRecording: Boolean,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
    onClearClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "语音助手",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                    IconButton(onClick = onClearClick) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "清除历史"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MessageList(
                messages = messages,
                currentResponse = currentResponse,
                isStreaming = uiState == UIState.Processing,
                modifier = Modifier.weight(1f)
            )
            
            StatusIndicator(
                uiState = uiState,
                modifier = Modifier.padding(16.dp)
            )
            
            MicButton(
                isRecording = isRecording,
                isProcessing = uiState == UIState.Processing || uiState == UIState.Speaking,
                scale = if (isRecording) scale else 1f,
                onClick = {
                    when {
                        isRecording -> onStopClick()
                        uiState == UIState.Speaking -> onStopClick()
                        uiState == UIState.Processing -> onStopClick()
                        else -> onMicClick()
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
fun MessageList(
    messages: List<Message>,
    currentResponse: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (isStreaming && currentResponse.isNotEmpty()) {
            item {
                MessageBubble(
                    message = Message(
                        content = currentResponse,
                        isUser = false,
                        isStreaming = true
                    )
                )
            }
        }
        
        items(messages.reversed()) { message ->
            MessageBubble(message = message)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun MessageBubble(
    message: Message
) {
    val backgroundColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    
    val contentColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor,
                contentColor = contentColor
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = if (message.isUser) "我" else "助手",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "正在输入...",
                        fontSize = 12.sp,
                        color = contentColor.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(
    uiState: UIState,
    modifier: Modifier = Modifier
) {
    val (text, color, icon) = when (uiState) {
        is UIState.Idle -> Triple("点击麦克风开始对话", Color.Gray, Icons.Default.Mic)
        is UIState.Listening -> Triple("正在聆听...", Color(0xFF4CAF50), Icons.Default.Mic)
        is UIState.Processing -> Triple("正在思考...", Color(0xFF2196F3), Icons.Default.Psychology)
        is UIState.Speaking -> Triple("正在播放...", Color(0xFFFF9800), Icons.Default.VolumeUp)
        is UIState.Error -> Triple(uiState.message, Color.Red, Icons.Default.Error)
    }
    
    val animatedColor by animateColorAsState(
        targetValue = color,
        label = "statusColor"
    )
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = animatedColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = animatedColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MicButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonColor = when {
        isRecording -> Color(0xFFE53935)
        isProcessing -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary
    }
    
    val icon: ImageVector = when {
        isRecording -> Icons.Default.Stop
        isProcessing -> Icons.Default.Stop
        else -> Icons.Default.Mic
    }
    
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(80.dp)
            .scale(scale),
        containerColor = buttonColor,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 12.dp
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (isRecording) "停止" else "开始录音",
            modifier = Modifier.size(32.dp)
        )
    }
}
