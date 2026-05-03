package com.feelvision.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feelvision.ui.theme.FVColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    viewModel: SplashViewModel = hiltViewModel(),
    onReady: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    val statusText = state.statusMessage

    LaunchedEffect(Unit) {
        viewModel.initialize()
        alpha.animateTo(1f, tween(500))
    }

    LaunchedEffect(state.progressTarget) {
        progress.animateTo(state.progressTarget, tween(800))
    }

    LaunchedEffect(state.initComplete) {
        if (state.initComplete) {
            delay(500)
            onReady()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FVColors.SkyBlue),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
        ) {
            // Logo ring
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(FVColors.Surface)
                    .border(3.dp, FVColors.DeepBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("👓", style = MaterialTheme.typography.headlineLarge)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("feelvision", style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold, color = FVColors.DarkNavy))
                Text("smart glasses for blind", style = MaterialTheme.typography.bodyMedium.copy(color = FVColors.DeepBlue))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.width(200.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = FVColors.LeafGreen,
                    trackColor = FVColors.Surface
                )
                Text(statusText, style = MaterialTheme.typography.labelSmall.copy(color = FVColors.DeepBlue))
            }
        }
    }
}