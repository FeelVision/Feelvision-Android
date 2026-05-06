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
    val logMessages by viewModel.logMessages.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val ttsState by viewModel.ttsState.collectAsState()
    val isListeningForPrompt by viewModel.isListeningForPrompt.collectAsState()
    val gemmaReady by viewModel.gemmaReady.collectAsState()

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
                    ImageDisplayCard(
                        bitmap = currentImage,
                        timestamp = imageTimestamp,
                        imageSize = imageSize
                    )
                }

                if (currentResponse.isNotEmpty()) {
                    item {
                        ResponseCard(
                            response = currentResponse,
                            isSpeaking = isSpeaking
                        )
                    }
                }

                item {
                    SimulateButtonsCard(
                        viewModel = viewModel,
                        isListening = isListeningForPrompt
                    )
                }

                item {
                    LogPanel(
                        logs = logMessages,
                        modifier = Modifier.height(180.dp)
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
fun SimulateButtonsCard(
    viewModel: LuckfoxViewModel,
    isListening: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FVColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Simulate Physical Buttons",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FVColors.DarkNavy
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Button A
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "BTN A · Quick Inf",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FVColors.DarkNavy.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    OutlinedButton(
                        onClick = { viewModel.simulateButton(com.feelvision.domain.model.PhysicalButton.A) },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, FVColors.DeepBlue.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("SHORT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = FVColors.DeepBlue)
                    }
                }

                // Button B
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "BTN B · Voice Prompt",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FVColors.DarkNavy.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    OutlinedButton(
                        onClick = { viewModel.simulateButton(com.feelvision.domain.model.PhysicalButton.B) },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isListening) Color(0xFFE57373) else FVColors.DeepBlue.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isListening) Color(0xFFE57373).copy(alpha = 0.15f) else Color.Transparent
                        )
                    ) {
                        Text(
                            text = if (isListening) "LISTENING" else "SHORT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isListening) Color(0xFFE57373) else FVColors.DeepBlue
                        )
                    }
                }

                // Button C
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "BTN C · System TTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FVColors.DarkNavy.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.simulateButton(com.feelvision.domain.model.PhysicalButton.C) },
                            modifier = Modifier.weight(1.5f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, FVColors.DeepBlue.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("SIL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = FVColors.DeepBlue, fontSize = 9.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.simulateButton(com.feelvision.domain.model.PhysicalButton.C, "double") },
                            modifier = Modifier.weight(1.5f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, FVColors.DeepBlue.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("REP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = FVColors.DeepBlue, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogPanel(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FVColors.Surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Activity Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FVColors.DarkNavy
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        FVColors.SkyBlue.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                items(logs) { log ->
                    Text(
                        log,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = when {
                            log.contains("error") || log.contains("Error") || log.contains("failed") -> Color(0xFFD32F2F)
                            log.contains("Connected") || log.contains("Finished") -> FVColors.LeafGreen
                            log.contains("Listening") -> Color(0xFFE65100)
                            else -> FVColors.DarkNavy
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ResponseCard(
    response: String,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FVColors.Surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Gemma Real-time Response",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FVColors.DarkNavy
                )
                if (isSpeaking) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Speaking",
                        modifier = Modifier.size(20.dp),
                        tint = FVColors.LeafGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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

@Composable
fun ImageDisplayCard(
    bitmap: Bitmap?,
    timestamp: String?,
    imageSize: Int,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FVColors.Surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Luckfox Live Stream Frame",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FVColors.DarkNavy
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FVColors.SkyBlue.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CastConnected,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = FVColors.DeepBlue.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Waiting for Luckfox Frame Connection...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = FVColors.DeepBlue.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (bitmap != null) {
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(14.dp), tint = FVColors.DeepBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${imageSize / 1024} KB",
                            style = MaterialTheme.typography.bodySmall.copy(color = FVColors.DeepBlue, fontWeight = FontWeight.SemiBold)
                        )
                    }
                    timestamp?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp), tint = FVColors.DeepBlue)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                it, 
                                style = MaterialTheme.typography.bodySmall.copy(color = FVColors.DeepBlue, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}
