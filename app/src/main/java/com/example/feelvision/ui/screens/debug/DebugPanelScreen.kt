    package com.feelvision.ui.screens.debug

    import androidx.camera.core.Preview
    import androidx.camera.view.PreviewView
    import androidx.compose.foundation.*
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
    import androidx.compose.ui.platform.LocalLifecycleOwner
    import androidx.compose.ui.text.font.FontFamily
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.compose.ui.viewinterop.AndroidView
    import androidx.hilt.navigation.compose.hiltViewModel
    import androidx.lifecycle.compose.collectAsStateWithLifecycle
    import com.feelvision.domain.model.AppMode
    import com.feelvision.domain.model.PhysicalButton
    import com.feelvision.hardware.PhoneCameraSource
    import com.feelvision.ui.components.modeColor
    import com.feelvision.ui.theme.FVColors

    @Composable
    fun DebugPanelScreen(
        viewModel: DebugViewModel = hiltViewModel(),
        cameraSource: PhoneCameraSource,
        onBack: () -> Unit
    ) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val logState = rememberLazyListState()
        val lifecycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(state.logs.size) {
            if (state.logs.isNotEmpty()) logState.animateScrollToItem(state.logs.size - 1)
        }

        val preview = remember { Preview.Builder().build() }
        LaunchedEffect(Unit) { cameraSource.bindCamera(lifecycleOwner, preview) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FVColors.DebugBg)
                .statusBarsPadding()
        ) {
            // ── Top bar ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FVColors.DebugSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = FVColors.DebugAccent)
                }
                Spacer(Modifier.width(8.dp))

                // Gemma status dot
                val dotColor = when {
                    state.isInferring -> Color(0xFF00AAFF)    // blue  = running
                    state.gemmaReady  -> FVColors.DebugAccent // green = ready
                    else              -> FVColors.DebugError  // red   = not ready
                }
                Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(6.dp))

                Text(
                    "Debug Panel",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = FVColors.DebugAccent, fontFamily = FontFamily.Monospace
                    )
                )
                Spacer(Modifier.weight(1f))

                // Gemma status label
                Text(
                    when {
                        state.isInferring -> "Inferring..."
                        state.gemmaReady  -> "Ready"
                        else              -> "Not ready"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = dotColor, fontFamily = FontFamily.Monospace
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    state.connectionLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FVColors.DebugText.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace
                    )
                )
                Spacer(Modifier.width(12.dp))

                // Clear logs only — no reinit button
                IconButton(onClick = { viewModel.clearLogs() }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.DeleteSweep, "Clear",
                        tint = FVColors.DebugAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Inference progress bar
            if (state.isInferring) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF00AAFF),
                    trackColor = FVColors.DebugSurface
                )
            }

            // ── Camera preview ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            preview.setSurfaceProvider(pv.surfaceProvider)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Mode badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FVColors.DebugSurface.copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "MODE ${state.currentMode.id} · ${state.currentMode.shortLabel}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = modeColor(state.currentMode),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Capture button — disabled while inferring
                IconButton(
                    onClick = { viewModel.captureNow() },
                    enabled = !state.isInferring && state.gemmaReady,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isInferring || !state.gemmaReady)
                                FVColors.DebugAccent.copy(alpha = 0.05f)
                            else
                                FVColors.DebugAccent.copy(alpha = 0.15f)
                        )
                ) {
                    if (state.isInferring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF00AAFF)
                        )
                    } else {
                        Icon(Icons.Default.Camera, "Capture", tint = FVColors.DebugAccent)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Mode trigger grid ────────────────────────────────────
                DebugSectionLabel("TRIGGER MODE")
                LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(96.dp)
                ) {
                    AppMode.entries.forEach { mode ->
                        item {
                            val active = mode == state.currentMode
                            val color  = modeColor(mode)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) color.copy(0.2f) else FVColors.DebugSurface)
                                    .border(
                                        1.dp,
                                        if (active) color else FVColors.DebugAccent.copy(0.15f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.triggerMode(mode) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        mode.id.toString(),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = if (active) color else FVColors.DebugText,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        mode.shortLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = FVColors.DebugAccent.copy(0.6f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Button simulation ────────────────────────────────────
                DebugSectionLabel("SIMULATE BUTTONS")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugButtonGroup(
                        "A", "Capture",
                        onShort = { viewModel.simulateButton(PhysicalButton.A) },
                        modifier = Modifier.weight(1f)
                    )
                    DebugButtonGroup(
                        "B", "Mode",
                        onShort = { viewModel.simulateButton(PhysicalButton.B) },
                        onLong  = { viewModel.simulateButton(PhysicalButton.B, "long") },
                        modifier = Modifier.weight(1f)
                    )
                    DebugButtonGroup(
                        "C", "System",
                        onShort  = { viewModel.simulateButton(PhysicalButton.C) },
                        onDouble = { viewModel.simulateButton(PhysicalButton.C, "double") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── Serial log ───────────────────────────────────────────
                DebugSectionLabel("SERIAL LOG  (${state.logs.size})")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF010409))
                        .padding(8.dp)
                ) {
                    LazyColumn(state = logState) {
                        items(state.logs) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = when {
                                        line.startsWith("[ERR]") -> FVColors.DebugError
                                        line.startsWith("[OK]")  -> FVColors.DebugAccent
                                        line.startsWith("[CMD]") -> FVColors.DebugCmd
                                        line.startsWith("[BTN]") -> Color(0xFFFFAA00)
                                        else -> FVColors.DebugText.copy(alpha = 0.75f)
                                    }
                                ),
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DebugSectionLabel(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = FVColors.DebugAccent.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
        )
    }

    @Composable
    private fun DebugButtonGroup(
        label: String,
        role: String,
        onShort: () -> Unit,
        onLong: (() -> Unit)?   = null,
        onDouble: (() -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "BTN $label · $role",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = FVColors.DebugText.copy(0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            )
            OutlinedButton(
                onClick = onShort,
                modifier = Modifier.fillMaxWidth().height(34.dp),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, FVColors.DebugAccent.copy(0.4f)),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FVColors.DebugAccent)
            ) {
                Text("SHORT", style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace))
            }
            if (onLong != null) {
                OutlinedButton(
                    onClick = onLong,
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, FVColors.DebugCmd.copy(0.4f)),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FVColors.DebugCmd)
                ) {
                    Text("LONG", style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace))
                }
            }
            if (onDouble != null) {
                OutlinedButton(
                    onClick = onDouble,
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFAA00).copy(0.4f)),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAA00))
                ) {
                    Text("DOUBLE", style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace))
                }
            }
        }
    }