package com.feelvision.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun FeelVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = FVColors.SkyBlue,
            surface = FVColors.Surface,
            primary = FVColors.DeepBlue,
            secondary = FVColors.LeafGreen,
            onBackground = FVColors.DarkNavy,
            onSurface = FVColors.DarkNavy,
            onPrimary = FVColors.White,
            onSecondary = FVColors.White
        ),
        content = content
    )
}