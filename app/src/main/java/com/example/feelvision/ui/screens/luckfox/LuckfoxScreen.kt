package com.feelvision.ui.screens.luckfox

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feelvision.luckfox.LuckfoxTcpServer
import com.feelvision.ui.theme.FVColors
import com.feelvision.ui.components.modeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuckfoxScreen(
    viewModel: LuckfoxViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val serverStatus by viewModel.serverStatus.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val currentImage by viewModel.currentImage.collectAsState()
    val imageTimestamp by viewModel.imageTimestamp.collectAsState()
    val imageSize by viewModel.imageSize.collectAsState()
    val currentResponse by viewModel.currentResponse.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val ttsState by viewModel.ttsState.collectAsState()
    val isListeningForPrompt by viewModel.isListeningForPrompt.collectAsState()
    val gemmaReady by viewModel.gemmaReady.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()

    val isRunning = serverStatus.state == LuckfoxTcpServer.ServerState.LISTENING || 
                    serverStatus.state == LuckfoxTcpServer.ServerState.CONNECTED
    
    val isConnecting = serverStatus.state == LuckfoxTcpServer.ServerState.STARTING

    Scaffold(
        containerColor = FVColors.SkyBlue,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Luckfox Connection", 
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FVColors.DarkNavy, 
                            fontWeight = FontWeight.Bold
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = FVColors.DeepBlue)
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(FVColors.Surface.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        // Gemma Status (AI ready indicator)
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (gemmaReady) FVColors.LeafGreen else FVColors.DeepBlue.copy(alpha = 0.3f))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "AI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (gemmaReady) FVColors.DarkNavy else FVColors.DarkNavy.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        
                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = when (serverStatus.state) {
                                        LuckfoxTcpServer.ServerState.CONNECTED -> FVColors.LeafGreen
                                        LuckfoxTcpServer.ServerState.LISTENING -> Color(0xFFFFB300) // Deep Amber
                                        LuckfoxTcpServer.ServerState.ERROR -> Color.Red
                                        else -> Color.Gray
                                    },
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (serverStatus.state) {
                                LuckfoxTcpServer.ServerState.CONNECTED -> "Connected"
                                LuckfoxTcpServer.ServerState.LISTENING -> "Listening"
                                LuckfoxTcpServer.ServerState.STARTING -> "Starting..."
                                LuckfoxTcpServer.ServerState.ERROR -> "Error"
                                else -> "Stopped"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FVColors.DarkNavy,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = FVColors.SkyBlue
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Server Network Addresses & Info Bar
            Surface(
                color = FVColors.DeepBlue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Received Images: ${serverStatus.imageCount}", 
                        style = MaterialTheme.typography.bodySmall.copy(color = FVColors.White, fontWeight = FontWeight.Medium)
                    )

                    if (isProcessing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = FVColors.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Streaming Gemma...", 
                                style = MaterialTheme.typography.bodySmall.copy(color = FVColors.White, fontWeight = FontWeight.Medium)
                            )
                        }
                    }

                    if (serverStatus.listenAddresses.isNotEmpty()) {
                        Text(
                            serverStatus.listenAddresses.first().split(" ").first(),
                            style = MaterialTheme.typography.bodySmall.copy(color = FVColors.White, fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ActiveModeCard(mode = currentMode)
                }

                item {
                    ImageDisplayFrame(
                        bitmap = currentImage,
                        timestamp = imageTimestamp,
                        imageSize = imageSize
                    )
                }

                item {
                    AiResponseBox(
                        response = currentResponse,
                        isSpeaking = isSpeaking,
                        modifier = Modifier.heightIn(min = 180.dp)
                    )
                }
            }

            // Bottom Actions Control Panel
            Surface(
                color = FVColors.Surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = if (isRunning) viewModel::stopServer else viewModel::startServer,
                        enabled = !isConnecting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFD32F2F) else FVColors.DeepBlue
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRunning) "Stop Server" else "Start Server", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}



@Composable
fun ActiveModeCard(
    mode: com.feelvision.domain.model.AppMode,
    modifier: Modifier = Modifier
) {
    val modeColor = modeColor(mode)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FVColors.Surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(modeColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Active Strategy Mode",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FVColors.DarkNavy.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = FVColors.DarkNavy,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = FVColors.DarkNavy.copy(alpha = 0.6f),
                letterSpacing = 1.2.sp
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = FVColors.DarkNavy.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun ImageDisplayFrame(
    bitmap: Bitmap?,
    timestamp: String?,
    imageSize: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("LIVE FRAME")
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.4f))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Frame counter badge / timestamp
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(12.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${imageSize / 1024} KB", style = MaterialTheme.typography.labelSmall.copy(color = Color.White))
                    
                    timestamp?.let {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(12.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall.copy(color = Color.White))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CastConnected,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = FVColors.DarkNavy.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Waiting for Luckfox connection...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FVColors.DarkNavy.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun AiResponseBox(
    response: String,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("AI RESPONSE")
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.4f))
        ) {
            if (response.isBlank()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = FVColors.DarkNavy.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Response will appear here after\na frame is captured and processed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FVColors.DarkNavy.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // Active Response State
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    if (isSpeaking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Speaking",
                                modifier = Modifier.size(20.dp),
                                tint = FVColors.LeafGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        response,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            color = FVColors.DarkNavy
                        )
                    )
                }
            }
        }
    }
}
