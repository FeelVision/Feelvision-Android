package com.feelvision.ui.screens.main

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feelvision.domain.model.AppMode
import com.feelvision.domain.model.ModeResult
import com.feelvision.hardware.PhoneCameraSource
import com.feelvision.ui.components.modeColor
import com.feelvision.ui.theme.FVColors
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    cameraSource: PhoneCameraSource,
    onSettings: () -> Unit,
    onPeople: () -> Unit,
    onDebug: () -> Unit,
    onLuckfox: () -> Unit,
    showDebugButton: Boolean
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Bind phone camera (even without preview on screen, we need it bound for capture)
    val preview = remember { Preview.Builder().build() }
    LaunchedEffect(Unit) { cameraSource.bindCamera(lifecycleOwner, preview) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FVColors.SkyBlue)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "feelvision",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold, color = FVColors.DarkNavy
                    )
                )

                Spacer(Modifier.weight(1f))

                // Connection indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Gemma Status
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (state.gemmaReady) FVColors.LeafGreen else FVColors.DeepBlue.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "AI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (state.gemmaReady) FVColors.DarkNavy else FVColors.DarkNavy.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    Spacer(Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (state.luckfoxConnected) FVColors.LeafGreen else Color(0xFFE57373))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state.luckfoxConnected) "HW" else "DBG",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FVColors.DarkNavy.copy(alpha = 0.6f)
                        )
                    )
                }

                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onLuckfox, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.CastConnected,
                        "Luckfox",
                        tint = if (state.luckfoxConnected) FVColors.LeafGreen else FVColors.DeepBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (showDebugButton) {
                    IconButton(onClick = { viewModel.onIntent(MainIntent.ScanModel) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Refresh, "Scan Model", tint = FVColors.DeepBlue, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDebug, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.BugReport, "Debug", tint = FVColors.DeepBlue, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onPeople, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.People, "People", tint = FVColors.DeepBlue, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Settings, "Settings", tint = FVColors.DeepBlue, modifier = Modifier.size(20.dp))
                }
            }
            
            // ── Camera Preview (Top Half) ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable(enabled = state.capturedBitmap != null) {
                        viewModel.onIntent(MainIntent.DismissResult)
                    }
            ) {
                if (state.capturedBitmap != null) {
                    Image(
                        bitmap = state.capturedBitmap!!.asImageBitmap(),
                        contentDescription = "Captured Frame",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { pv ->
                                preview.setSurfaceProvider(pv.surfaceProvider)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Active indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(FVColors.LeafGreen)
                )

                // "Live preview" gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (state.capturedBitmap != null) Icons.Default.Image else Icons.Default.Visibility,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.capturedBitmap != null) "Captured (Tap to dismiss)" else "Live preview",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Bottom Section ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Mode Header + Divider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${state.currentMode.name} MODE".uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = FVColors.DarkNavy,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                    Spacer(Modifier.width(16.dp))
                    Divider(
                        modifier = Modifier.weight(1f),
                        color = FVColors.DarkNavy.copy(alpha = 0.2f),
                        thickness = 1.dp
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Small mode circle and status text
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Circular indicator
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .border(1.5.dp, FVColors.DarkNavy.copy(alpha = 0.2f), CircleShape)
                            .background(Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.currentMode.ordinal.toString(),
                                style = MaterialTheme.typography.titleLarge.copy(color = FVColors.DarkNavy, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = state.currentMode.name.take(3).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(color = FVColors.DarkNavy.copy(alpha = 0.6f), fontSize = 10.sp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.width(20.dp))
                    
                    // Status text
                    val isListening = state.isListeningForMode
                    Text(
                        text = if (isListening) "Listening..." else state.statusText,
                        style = MaterialTheme.typography.bodyLarge.copy(color = FVColors.DarkNavy.copy(alpha = 0.7f), lineHeight = 24.sp),
                        modifier = Modifier.weight(1f)
                    )
                }

                AnimatedVisibility(visible = state.isListeningForMode) {
                    com.feelvision.ui.components.WaveformBar(
                        data = emptyList(),
                        active = true,
                        modifier = Modifier.fillMaxWidth().height(32.dp).padding(top = 16.dp)
                    )
                }

                Spacer(Modifier.height(48.dp))
            }
                
            // ── Capture and Mode switcher buttons (bottom) ────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Switch Mode Button
                    OutlinedButton(
                        onClick = { viewModel.onIntent(MainIntent.StartVoiceModeSwitch) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, FVColors.DarkNavy.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FVColors.DarkNavy)
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Switch Mode", style = MaterialTheme.typography.labelLarge)
                    }

                    // Capture Button area
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedVisibility(visible = state.burstProgress != null) {
                            Surface(
                                color = Color(0xFFFFAA00),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    "Capturing ${state.burstProgress ?: ""}",
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        val isBusy = state.isInferring || state.burstProgress != null
                        Button(
                            onClick = { viewModel.onIntent(MainIntent.Capture) },
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp),
                            enabled = !state.isListeningForMode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.burstProgress != null) Color(0xFFFFAA00) else FVColors.DeepBlue,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Capture", modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }



    }
}
