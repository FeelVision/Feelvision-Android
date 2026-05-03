package com.feelvision.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.feelvision.ui.theme.FVColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun WaveformBar(data: List<Float>, active: Boolean = true, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val offset by infiniteTransition.animateFloat(
        0f, (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "offset"
    )

    Canvas(modifier = modifier) {
        val count = 22
        val barW = size.width / (count * 2f)
        val cy = size.height / 2f

        for (i in 0 until count) {
            val h = if (data.isNotEmpty()) {
                (data.getOrElse(i) { 0f } * size.height * 0.45f).coerceAtLeast(3.dp.toPx())
            } else if (active) {
                abs(sin(offset + i * 0.45f)) * size.height * 0.38f + 3.dp.toPx()
            } else {
                3.dp.toPx()
            }
            val x = i * barW * 2 + barW * 0.1f
            drawRoundRect(
                color = FVColors.DeepBlue.copy(alpha = if (active) 0.6f else 0.25f),
                topLeft = Offset(x, cy - h / 2),
                size = Size(barW * 0.8f, h),
                cornerRadius = CornerRadius(barW / 2)
            )
        }
    }
}