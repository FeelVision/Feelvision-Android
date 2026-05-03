package com.feelvision.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feelvision.ui.theme.FVColors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = FVColors.SkyBlue,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Settings", style = MaterialTheme.typography.titleMedium.copy(
                        color = FVColors.DarkNavy, fontWeight = FontWeight.Bold
                    ))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = FVColors.DeepBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = FVColors.SkyBlue
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SettingsSectionHeader("Voice & Language") }

            item {
                SettingsCard {
                    LanguagePicker(
                        selected = state.language,
                        onSelect = { viewModel.setLanguage(it) }
                    )
                }
            }

            item {
                SettingsCard {
                    SettingsSliderRow(
                        label = "Speech Rate",
                        value = state.speechRate,
                        range = 0.5f..2.0f,
                        onValueChange = { viewModel.setSpeechRate(it) },
                        displayValue = "${state.speechRate}x"
                    )
                }
            }

            item { SettingsSectionHeader("Feedback") }

            item {
                SettingsCard {
                    SettingsToggleRow(
                        label = "Haptic Feedback",
                        subtitle = "Vibrate on mode change",
                        checked = state.hapticEnabled,
                        onCheckedChange = { viewModel.setHaptic(it) }
                    )
                    HorizontalDivider(color = FVColors.SkyBlue)
                    SettingsToggleRow(
                        label = "Announce Mode on Switch",
                        subtitle = "TTS speaks mode name",
                        checked = state.announceModeSwitch,
                        onCheckedChange = { viewModel.setAnnounceMode(it) }
                    )
                }
            }

            item { SettingsSectionHeader("Device") }

            item {
                SettingsCard {
                    SettingsActionRow(
                        label = "Luckfox Pairing",
                        subtitle = if (state.devicePaired) "Connected: ${state.deviceName}"
                                   else "No device paired",
                        icon = Icons.Default.Bluetooth,
                        iconTint = if (state.devicePaired) FVColors.LeafGreen
                                   else FVColors.DeepBlue,
                        onClick = { viewModel.openPairing() }
                    )
                }
            }

            item { SettingsSectionHeader("Developer") }

            item {
                SettingsCard {
                    SettingsToggleRow(
                        label = "Debug Mode",
                        subtitle = "Show button-based debug panel",
                        checked = state.debugEnabled,
                        onCheckedChange = { viewModel.setDebug(it) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LanguagePicker(selected: String, onSelect: (String) -> Unit) {
    val languages = listOf("English", "Telugu", "Hindi", "Tamil", "Kannada", "Malayalam")
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Language, null, tint = FVColors.DeepBlue)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Language", style = MaterialTheme.typography.bodyMedium.copy(
                color = FVColors.DarkNavy
            ))
            Text(selected, style = MaterialTheme.typography.labelSmall.copy(
                color = FVColors.DeepBlue
            ))
        }
        Icon(Icons.Default.ChevronRight, null, tint = FVColors.DeepBlue)

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang) },
                    onClick = { onSelect(lang); expanded = false },
                    leadingIcon = if (lang == selected) {{ Icon(Icons.Default.Check, null, tint = FVColors.LeafGreen) }} else null
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FVColors.Surface)
    ) {
        content()
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            color = FVColors.DeepBlue,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = FVColors.DarkNavy))
            Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(color = FVColors.DarkNavy.copy(alpha = 0.6f)))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = FVColors.White,
                checkedTrackColor = FVColors.LeafGreen,
                uncheckedTrackColor = FVColors.SkyBlue
            )
        )
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    displayValue: String
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, null, tint = FVColors.DeepBlue)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = FVColors.DarkNavy), modifier = Modifier.weight(1f))
            Text(displayValue, style = MaterialTheme.typography.labelMedium.copy(color = FVColors.LeafGreen, fontWeight = FontWeight.Bold))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = FVColors.LeafGreen,
                activeTrackColor = FVColors.LeafGreen,
                inactiveTrackColor = FVColors.SkyBlue
            )
        )
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = FVColors.DarkNavy))
            Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(color = FVColors.DarkNavy.copy(alpha = 0.6f)))
        }
        Icon(Icons.Default.ChevronRight, null, tint = FVColors.DeepBlue)
    }
}