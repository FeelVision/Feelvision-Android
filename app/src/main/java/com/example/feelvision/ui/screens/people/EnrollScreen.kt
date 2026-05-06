package com.feelvision.ui.screens.people

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feelvision.hardware.PhoneCameraSource
import com.feelvision.ui.theme.FVColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollScreen(
    personId     : Long,
    viewModel    : PeopleViewModel = hiltViewModel(),
    cameraSource : PhoneCameraSource,
    onBack       : () -> Unit
) {
    val state          = viewModel.enrollState.collectAsStateWithLifecycle().value
    val lifecycleOwner = LocalLifecycleOwner.current
    val preview        = remember { Preview.Builder().build() }
    val isNewPerson    = personId == -1L

    // Bind camera
    LaunchedEffect(Unit) {
        cameraSource.bindCamera(lifecycleOwner, preview)
    }

    // Load person data if editing
    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    // Navigate back on success
    LaunchedEffect(state.savedSuccess) {
        if (state.savedSuccess) {
            viewModel.resetEnrollState()
            onBack()
        }
    }

    // Clean up voice enrollment when screen is disposed/navigated away
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelVoiceEnrollment()
        }
    }

    Scaffold(
        containerColor = FVColors.SkyBlue,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isNewPerson) "Add Person" else "Edit Person",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FVColors.DarkNavy, fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelVoiceEnrollment()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = FVColors.DeepBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = FVColors.SkyBlue
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Camera preview ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
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

                // Photo count badge
                if (state.capturedCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FVColors.DarkNavy.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${state.capturedCount} photos",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White
                            )
                        )
                    }
                }

                // Hint text at bottom of preview
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.hint,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White
                        )
                    )
                }
            }

            // ── Voice Enrollment Section ──────────────────────────────
            if (!state.isVoiceActive) {
                OutlinedButton(
                    onClick = { viewModel.startVoiceEnrollment() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FVColors.DeepBlue
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, FVColors.DeepBlue)
                ) {
                    Icon(Icons.Default.SettingsVoice, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Voice Enrollment", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.7f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = FVColors.DarkNavy
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(pulseScale)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(FVColors.LeafGreen.copy(alpha = pulseAlpha))
                            )
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(FVColors.LeafGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "VOICE ASSISTANCE ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FVColors.SkyBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                state.voiceStage,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }

                        IconButton(
                            onClick = { viewModel.cancelVoiceEnrollment() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0xFFE57373).copy(alpha = 0.15f))
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                "Cancel",
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ── Capture button ────────────────────────────────────────
            Button(
                onClick = { viewModel.captureEnrollPhoto() },
                enabled = !state.isVoiceActive,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FVColors.DeepBlue,
                    contentColor   = Color.White
                )
            ) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Capture Photo", style = MaterialTheme.typography.labelLarge)
            }

            // ── Progress dots ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { index ->
                    val filled = index < state.capturedCount
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (filled) 12.dp else 8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (filled) FVColors.LeafGreen
                                else FVColors.DarkNavy.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            // ── Form fields ───────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = FVColors.Surface),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value         = state.name,
                        onValueChange = viewModel::updateName,
                        label         = { Text("Name *") },
                        placeholder   = { Text("e.g. John Smith") },
                        leadingIcon   = {
                            Icon(Icons.Default.Person, null, tint = FVColors.DeepBlue)
                        },
                        isError       = state.error?.contains("name", ignoreCase = true) == true,
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        colors        = enrollFieldColors(),
                        enabled       = !state.isVoiceActive
                    )

                    OutlinedTextField(
                        value         = state.relation,
                        onValueChange = viewModel::updateRelation,
                        label         = { Text("Relation") },
                        placeholder   = { Text("e.g. Friend, Family, Colleague") },
                        leadingIcon   = {
                            Icon(Icons.Default.People, null, tint = FVColors.DeepBlue)
                        },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        colors        = enrollFieldColors(),
                        enabled       = !state.isVoiceActive
                    )

                    OutlinedTextField(
                        value         = state.notes,
                        onValueChange = viewModel::updateNotes,
                        label         = { Text("Notes") },
                        placeholder   = { Text("Optional additional info") },
                        leadingIcon   = {
                            Icon(Icons.Default.Notes, null, tint = FVColors.DeepBlue)
                        },
                        modifier      = Modifier.fillMaxWidth(),
                        maxLines      = 3,
                        colors        = enrollFieldColors(),
                        enabled       = !state.isVoiceActive
                    )
                }
            }

            // ── Error message ─────────────────────────────────────────
            if (state.error != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE57373).copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    Icon(
                        Icons.Default.ErrorOutline, null,
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.error,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFFE57373)
                        )
                    )
                }
            }

            // ── Save button ───────────────────────────────────────────
            Button(
                onClick  = { viewModel.saveEnrolledPerson() },
                enabled  = state.name.isNotBlank() && !state.isSaving && !state.isVoiceActive,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = FVColors.LeafGreen,
                    contentColor           = Color.White,
                    disabledContainerColor = FVColors.LeafGreen.copy(alpha = 0.4f)
                )
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = Color.White
                    )
                } else {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Person", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun enrollFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = FVColors.DeepBlue,
    unfocusedBorderColor = FVColors.DarkNavy.copy(alpha = 0.2f),
    focusedLabelColor    = FVColors.DeepBlue,
    cursorColor          = FVColors.DeepBlue
)