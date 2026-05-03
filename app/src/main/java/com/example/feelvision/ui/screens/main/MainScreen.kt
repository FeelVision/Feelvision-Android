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
import com.feelvision.ui.components.MicOrb
import com.feelvision.ui.components.ModeRing
import com.feelvision.ui.components.WaveformBar
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
    showDebugButton: Boolean
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showModeSwitcher by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Bind phone camera (even without preview on screen, we need it bound for capture)
    val preview = remember { Preview.Builder().build() }
    LaunchedEffect(Unit) { cameraSource.bindCamera(lifecycleOwner, preview) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FVColors.SkyBlue)
    ) {
        // ── Top bar ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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

        // ── Center content ───────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Small Camera Preview Rectangle
            Box(
                modifier = Modifier
                    .size(width = 240.dp, height = 180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .border(2.dp, FVColors.DeepBlue.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            preview.setSurfaceProvider(pv.surfaceProvider)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Active indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(FVColors.LeafGreen)
                )
            }

            Spacer(Modifier.height(32.dp))

            ModeRing(mode = state.currentMode, modifier = Modifier.size(160.dp))

            Spacer(Modifier.height(36.dp))

            MicOrb(isListening = state.isListening, modifier = Modifier.size(72.dp))

            Spacer(Modifier.height(16.dp))

            WaveformBar(
                data = emptyList(),
                active = state.isListening,
                modifier = Modifier.width(200.dp).height(32.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                state.statusText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = FVColors.DarkNavy.copy(alpha = 0.65f)
                )
            )
        }

        // ── Capture and Mode switcher buttons (bottom) ────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Capture Button
            Button(
                onClick = { viewModel.onIntent(MainIntent.Capture) },
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
                enabled = !state.isInferring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = modeColor(state.currentMode),
                    contentColor = Color.White,
                    disabledContainerColor = modeColor(state.currentMode).copy(alpha = 0.6f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                if (state.isInferring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Capture",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = { showModeSwitcher = true },
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, FVColors.DeepBlue.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FVColors.DeepBlue)
            ) {
                Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Switch Mode", style = MaterialTheme.typography.labelLarge)
            }
        }

        // ── Mode switcher overlay ────────────────────────────────────────
        AnimatedVisibility(
            visible = showModeSwitcher,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 })
        ) {
            ModeSwitcherOverlay(
                currentMode = state.currentMode,
                onSelect = { mode ->
                    viewModel.onIntent(MainIntent.SwitchMode(mode))
                    showModeSwitcher = false
                },
                onDismiss = { showModeSwitcher = false }
            )
        }

        // ── Captured Result Overlay ───────────────────────────────────────
        AnimatedVisibility(
            visible = state.capturedBitmap != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { viewModel.onIntent(MainIntent.DismissResult) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Analysis Result",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White, fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.height(20.dp))

                    state.capturedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Captured Frame",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    val resultText = when (val res = state.lastResult) {
                        is ModeResult.NarrationText -> res.toString()
                        is ModeResult.Error -> "Error: ${res.message}"
                        else -> if (state.isInferring) "Analyzing image..." else "Processing..."
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.White,
                                lineHeight = 26.sp
                            ),
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                    Text(
                        "Tap anywhere to dismiss",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSwitcherOverlay(
    currentMode: AppMode,
    onSelect: (AppMode) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FVColors.DarkNavy.copy(alpha = 0.93f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            Text("Select Mode",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = FVColors.White, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            Text("Press B to cycle  ·  Tap a row",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = FVColors.White.copy(alpha = 0.4f)))
            Spacer(Modifier.height(28.dp))

            AppMode.entries.forEach { mode ->
                val isActive = mode == currentMode
                val color = modeColor(mode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isActive) color.copy(alpha = 0.18f)
                            else FVColors.White.copy(alpha = 0.05f)
                        )
                        .border(
                            width = if (isActive) 1.dp else 0.dp,
                            color = if (isActive) color else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(mode.id.toString(),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = color, fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(mode.displayName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = FVColors.White,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal),
                        modifier = Modifier.weight(1f))
                    if (isActive) Icon(Icons.Default.Check, null,
                        tint = color, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}