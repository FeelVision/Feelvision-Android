package com.feelvision.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feelvision.ui.theme.FVColors

@Composable
fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            color = FVColors.DeepBlue,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
    )
}