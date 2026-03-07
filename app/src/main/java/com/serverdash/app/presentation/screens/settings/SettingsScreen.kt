package com.serverdash.app.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onDisconnected: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Disconnect confirmation
    if (state.showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(SettingsEvent.DismissDisconnect) },
            title = { Text("Disconnect") },
            text = { Text("Are you sure you want to disconnect from the server?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(SettingsEvent.ConfirmDisconnect)
                    onDisconnected()
                }) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(SettingsEvent.DismissDisconnect) }) { Text("Cancel") }
            }
        )
    }

    // Reset confirmation
    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(SettingsEvent.DismissReset) },
            title = { Text("Reset App") },
            text = { Text("This will delete all settings and server configuration. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(SettingsEvent.ConfirmReset)
                    onDisconnected()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(SettingsEvent.DismissReset) }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Server section
            item {
                Text("Server", style = MaterialTheme.typography.titleMedium)
                state.serverConfig?.let { config ->
                    ListItem(
                        headlineContent = { Text(config.label.ifBlank { config.host }) },
                        supportingContent = { Text("${config.username}@${config.host}:${config.port}") },
                        leadingContent = { Icon(Icons.Default.Dns, null) }
                    )
                }
            }

            // Display section
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Display", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.preferences.themeMode == mode,
                            onClick = { viewModel.onEvent(SettingsEvent.UpdateThemeMode(mode)) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
            item {
                SwitchSetting(
                    title = "Keep Screen On",
                    checked = state.preferences.keepScreenOn,
                    onChanged = { viewModel.onEvent(SettingsEvent.UpdateKeepScreenOn(it)) }
                )
            }
            item {
                SwitchSetting(
                    title = "Pixel Shift (OLED)",
                    checked = state.preferences.pixelShiftEnabled,
                    onChanged = { viewModel.onEvent(SettingsEvent.UpdatePixelShift(it)) }
                )
            }
            item {
                Text("Brightness Override", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = if (state.preferences.brightnessOverride < 0) 0.5f else state.preferences.brightnessOverride,
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateBrightness(it)) },
                    valueRange = 0.01f..1f
                )
            }

            // Monitoring section
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Monitoring", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Text("Polling Interval: ${state.preferences.pollingIntervalSeconds}s", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = state.preferences.pollingIntervalSeconds.toFloat(),
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdatePollingInterval(it.toInt())) },
                    valueRange = 5f..60f,
                    steps = 10
                )
            }
            item {
                Text("Background Check: ${state.preferences.backgroundCheckIntervalMinutes}min", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = state.preferences.backgroundCheckIntervalMinutes.toFloat(),
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateBgCheckInterval(it.toInt())) },
                    valueRange = 15f..120f,
                    steps = 6
                )
            }

            // Kiosk section
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Kiosk Mode", style = MaterialTheme.typography.titleMedium)
            }
            item {
                SwitchSetting(
                    title = "Enable Kiosk Mode",
                    checked = state.preferences.kioskMode,
                    onChanged = { viewModel.onEvent(SettingsEvent.UpdateKioskMode(it)) }
                )
            }
            item {
                SwitchSetting(
                    title = "Auto-start on Boot",
                    checked = state.preferences.autoStartOnBoot,
                    onChanged = { viewModel.onEvent(SettingsEvent.UpdateAutoStart(it)) }
                )
            }

            // Danger zone
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Danger Zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.onEvent(SettingsEvent.Disconnect) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Disconnect") }
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.onEvent(SettingsEvent.ResetApp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset App") }
            }
        }
    }
}

@Composable
private fun SwitchSetting(title: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}
