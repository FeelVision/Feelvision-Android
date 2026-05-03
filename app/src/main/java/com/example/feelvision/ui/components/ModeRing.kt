// ui/components/ModeRing.kt
package com.feelvision.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feelvision.domain.model.AppMode
import com.feelvision.ui.theme.FVColors

fun modeColor(mode: AppMode): Color = when (mode) {
    AppMode.Default -> FVColors.ModeDefault
    AppMode.OCR -> FVColors.ModeOCR
    AppMode.Navigate -> FVColors.ModeNavigate
    AppMode.Face -> FVColors.ModeFace
    AppMode.Currency -> FVColors.ModeCurrency
    AppMode.Edu -> FVColors.ModeEdu
    AppMode.Narrate -> FVColors.ModeNarrate
}

@Composable
fun ModeRing(mode: AppMode, modifier: Modifier = Modifier) {
    val color by animateColorAsState(modeColor(mode), label = "ringColor")
    val sweep by animateFloatAsState(300f, tween(500), label = "sweep")

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val r = (size.minDimension - stroke) / 2f
            val topLeft = Offset((size.width - r * 2) / 2, (size.height - r * 2) / 2)
            val arcSize = Size(r * 2, r * 2)

            // Background ring
            drawArc(color.copy(alpha = 0.12f), -90f, 360f, false,
                topLeft, arcSize, style = Stroke(stroke * 2.5f))
            // Active ring
            drawArc(color, -90f, sweep, false,
                topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                mode.id.toString(),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold, color = color, fontSize = 48.sp)
            )
            Text(
                mode.shortLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = FVColors.DarkNavy.copy(alpha = 0.55f), letterSpacing = 2.sp)
            )
        }
    }
}