package com.feelvision.ui.screens.people

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feelvision.hardware.PhoneCameraSource
import com.feelvision.ui.theme.FVColors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EnrollScreen(
    viewModel: PeopleViewModel = hiltViewModel(),
    cameraSource: PhoneCameraSource,
    onBack: () -> Unit
) {
    val state by viewModel.enrollState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val preview = remember { Preview.Builder().build() }
    LaunchedEffect(Unit) { cameraSource.bindCamera(lifecycleOwner, preview) }

    Scaffold(
        containerColor = FVColors.SkyBlue,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Add Person", style = MaterialTheme.typography.titleMedium.copy(
                    color = FVColors.DarkNavy, fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = FVColors.DeepBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = FVColors.SkyBlue)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Face capture preview area
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(4.dp, FVColors.DeepBlue.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            preview.setSurfaceProvider(pv.surfaceProvider)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (state.capturedCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(FVColors.LeafGreen.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${state.capturedCount}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 64.sp
                            )
                        )
                    }
                }
            }

            // Photo count indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) { i ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < state.capturedCount) FVColors.LeafGreen
                                else FVColors.Surface
                            )
                    )
                }
            }

            Text(
                text = state.hint,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = FVColors.DarkNavy.copy(alpha = 0.7f)
                ),
                textAlign = TextAlign.Center
            )

            // Name field
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FVColors.LeafGreen,
                    unfocusedBorderColor = FVColors.DeepBlue.copy(alpha = 0.3f),
                    focusedLabelColor = FVColors.LeafGreen,
                    cursorColor = FVColors.LeafGreen
                )
            )

            // Relation field
            OutlinedTextField(
                value = state.relation,
                onValueChange = { viewModel.updateRelation(it) },
                label = { Text("Relation (e.g. Family, Friend)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FVColors.LeafGreen,
                    unfocusedBorderColor = FVColors.DeepBlue.copy(alpha = 0.3f),
                    focusedLabelColor = FVColors.LeafGreen,
                    cursorColor = FVColors.LeafGreen
                )
            )

            Button(
                onClick = { viewModel.captureEnrollPhoto() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FVColors.LeafGreen)
            ) {
                Icon(Icons.Default.CameraAlt, null, tint = FVColors.White)
                Spacer(Modifier.width(8.dp))
                Text("Capture Photo ${state.capturedCount + 1}/5",
                    style = MaterialTheme.typography.labelLarge.copy(color = FVColors.White))
            }

            if (state.capturedCount >= 3) {
                OutlinedButton(
                    onClick = { viewModel.saveEnrolledPerson(); onBack() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, FVColors.DeepBlue)
                ) {
                    Text("Save Person", color = FVColors.DeepBlue,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}