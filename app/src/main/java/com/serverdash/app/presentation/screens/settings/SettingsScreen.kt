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
import com.serverdash.app.domain.model.*

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
            // ── Server ──
            item {
                SectionHeader("Server")
                state.serverConfig?.let { config ->
                    ListItem(
                        headlineContent = { Text(config.label.ifBlank { config.host }) },
                        supportingContent = { Text("${config.username}@${config.host}:${config.port}") },
                        leadingContent = { Icon(Icons.Default.Dns, null) }
                    )
                }
            }

            // ── Display ──
            item { SectionDivider(); SectionHeader("Display") }
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
                SwitchSetting("Keep Screen On", state.preferences.keepScreenOn) {
                    viewModel.onEvent(SettingsEvent.UpdateKeepScreenOn(it))
                }
            }
            item {
                SwitchSetting("Pixel Shift (OLED)", state.preferences.pixelShiftEnabled) {
                    viewModel.onEvent(SettingsEvent.UpdatePixelShift(it))
                }
            }
            item {
                SliderSetting(
                    title = "Brightness Override",
                    value = if (state.preferences.brightnessOverride < 0) 0.5f else state.preferences.brightnessOverride,
                    range = 0.01f..1f,
                    formatValue = { "%.0f%%".format(it * 100) },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateBrightness(it)) }
                )
            }

            // ── Dashboard Layout ──
            item { SectionDivider(); SectionHeader("Dashboard Layout") }
            item {
                Text("Layout Style", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardLayout.entries.forEach { layout ->
                        FilterChip(
                            selected = state.preferences.dashboardLayout == layout,
                            onClick = { viewModel.onEvent(SettingsEvent.UpdateDashboardLayout(layout)) },
                            label = { Text(layout.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
            item {
                SliderSetting(
                    title = "Grid Columns",
                    value = state.preferences.gridColumns.toFloat(),
                    range = 0f..6f,
                    steps = 5,
                    formatValue = { if (it.toInt() == 0) "Auto" else "${it.toInt()}" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateGridColumns(it.toInt())) }
                )
            }
            item {
                Text("Sort Order", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServiceSortOrder.entries.forEach { order ->
                        FilterChip(
                            selected = state.preferences.serviceSortOrder == order,
                            onClick = { viewModel.onEvent(SettingsEvent.UpdateServiceSortOrder(order)) },
                            label = {
                                Text(order.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                            }
                        )
                    }
                }
            }
            item {
                SwitchSetting("Show Service Description", state.preferences.showServiceDescription) {
                    viewModel.onEvent(SettingsEvent.UpdateShowServiceDescription(it))
                }
            }
            item {
                SwitchSetting("Compact Cards", state.preferences.compactCards) {
                    viewModel.onEvent(SettingsEvent.UpdateCompactCards(it))
                }
            }
            item {
                SwitchSetting("Hide Unknown Services", state.preferences.hideUnknownServices) {
                    viewModel.onEvent(SettingsEvent.UpdateHideUnknownServices(it))
                }
            }
            item {
                SliderSetting(
                    title = "Max Services Displayed",
                    value = state.preferences.maxServicesDisplayed.toFloat(),
                    range = 0f..200f,
                    steps = 19,
                    formatValue = { if (it.toInt() == 0) "Unlimited" else "${it.toInt()}" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateMaxServicesDisplayed(it.toInt())) }
                )
            }

            // ── Metrics ──
            item { SectionDivider(); SectionHeader("Metrics") }
            item {
                Text("Metrics Display", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricsDisplayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.preferences.metricsDisplayMode == mode,
                            onClick = { viewModel.onEvent(SettingsEvent.UpdateMetricsDisplayMode(mode)) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
            item {
                SwitchSetting("Show Load Average", state.preferences.showLoadAverage) {
                    viewModel.onEvent(SettingsEvent.UpdateShowLoadAverage(it))
                }
            }
            item {
                SliderSetting(
                    title = "CPU Warning Threshold",
                    value = state.preferences.cpuWarningThreshold,
                    range = 50f..100f,
                    steps = 9,
                    formatValue = { "${it.toInt()}%" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateCpuWarning(it)) }
                )
            }
            item {
                SliderSetting(
                    title = "Memory Warning Threshold",
                    value = state.preferences.memoryWarningThreshold,
                    range = 50f..100f,
                    steps = 9,
                    formatValue = { "${it.toInt()}%" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateMemoryWarning(it)) }
                )
            }
            item {
                SliderSetting(
                    title = "Disk Warning Threshold",
                    value = state.preferences.diskWarningThreshold,
                    range = 50f..100f,
                    steps = 9,
                    formatValue = { "${it.toInt()}%" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateDiskWarning(it)) }
                )
            }

            // ── Monitoring ──
            item { SectionDivider(); SectionHeader("Monitoring") }
            item {
                SliderSetting(
                    title = "Polling Interval",
                    value = state.preferences.pollingIntervalSeconds.toFloat(),
                    range = 5f..60f,
                    steps = 10,
                    formatValue = { "${it.toInt()}s" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdatePollingInterval(it.toInt())) }
                )
            }
            item {
                SliderSetting(
                    title = "Background Check Interval",
                    value = state.preferences.backgroundCheckIntervalMinutes.toFloat(),
                    range = 5f..120f,
                    steps = 22,
                    formatValue = { "${it.toInt()} min" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateBgCheckInterval(it.toInt())) }
                )
            }
            item {
                SliderSetting(
                    title = "Metrics Retention",
                    value = state.preferences.metricsRetentionHours.toFloat(),
                    range = 1f..168f,
                    steps = 23,
                    formatValue = { val h = it.toInt(); if (h < 24) "${h}h" else "${h / 24}d ${h % 24}h" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateMetricsRetention(it.toInt())) }
                )
            }

            // ── Notifications ──
            item { SectionDivider(); SectionHeader("Notifications") }
            item {
                SwitchSetting("Notifications Enabled", state.preferences.notificationsEnabled) {
                    viewModel.onEvent(SettingsEvent.UpdateNotificationsEnabled(it))
                }
            }
            if (state.preferences.notificationsEnabled) {
                item {
                    SwitchSetting("Notify on Service Down", state.preferences.notifyOnServiceDown) {
                        viewModel.onEvent(SettingsEvent.UpdateNotifyServiceDown(it))
                    }
                }
                item {
                    SwitchSetting("Notify on High CPU", state.preferences.notifyOnHighCpu) {
                        viewModel.onEvent(SettingsEvent.UpdateNotifyHighCpu(it))
                    }
                }
                item {
                    SwitchSetting("Notify on High Memory", state.preferences.notifyOnHighMemory) {
                        viewModel.onEvent(SettingsEvent.UpdateNotifyHighMemory(it))
                    }
                }
                item {
                    SwitchSetting("Notify on High Disk", state.preferences.notifyOnHighDisk) {
                        viewModel.onEvent(SettingsEvent.UpdateNotifyHighDisk(it))
                    }
                }
                item {
                    SwitchSetting("Notification Sound", state.preferences.notificationSound) {
                        viewModel.onEvent(SettingsEvent.UpdateNotificationSound(it))
                    }
                }
                item {
                    SwitchSetting("Notification Vibrate", state.preferences.notificationVibrate) {
                        viewModel.onEvent(SettingsEvent.UpdateNotificationVibrate(it))
                    }
                }
            }

            // ── Logs ──
            item { SectionDivider(); SectionHeader("Logs") }
            item {
                Text("Font Size", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogFontSize.entries.forEach { size ->
                        FilterChip(
                            selected = state.preferences.logFontSize == size,
                            onClick = { viewModel.onEvent(SettingsEvent.UpdateLogFontSize(size)) },
                            label = { Text(size.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
            item {
                SliderSetting(
                    title = "Log Lines to Fetch",
                    value = state.preferences.logLineCount.toFloat(),
                    range = 25f..500f,
                    steps = 18,
                    formatValue = { "${it.toInt()}" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateLogLineCount(it.toInt())) }
                )
            }
            item {
                SwitchSetting("Wrap Long Lines", state.preferences.logWrapLines) {
                    viewModel.onEvent(SettingsEvent.UpdateLogWrapLines(it))
                }
            }
            item {
                SwitchSetting("Auto-Refresh Logs", state.preferences.logAutoRefresh) {
                    viewModel.onEvent(SettingsEvent.UpdateLogAutoRefresh(it))
                }
            }
            if (state.preferences.logAutoRefresh) {
                item {
                    SliderSetting(
                        title = "Auto-Refresh Interval",
                        value = state.preferences.logAutoRefreshSeconds.toFloat(),
                        range = 2f..30f,
                        steps = 13,
                        formatValue = { "${it.toInt()}s" },
                        onValueChange = { viewModel.onEvent(SettingsEvent.UpdateLogAutoRefreshSeconds(it.toInt())) }
                    )
                }
            }

            // ── Terminal ──
            item { SectionDivider(); SectionHeader("Terminal") }
            item {
                SliderSetting(
                    title = "Terminal Font Size",
                    value = state.preferences.terminalFontSize.toFloat(),
                    range = 10f..24f,
                    steps = 13,
                    formatValue = { "${it.toInt()}sp" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateTerminalFontSize(it.toInt())) }
                )
            }
            item {
                SliderSetting(
                    title = "Terminal History Limit",
                    value = state.preferences.terminalMaxHistory.toFloat(),
                    range = 100f..2000f,
                    steps = 18,
                    formatValue = { "${it.toInt()}" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateTerminalMaxHistory(it.toInt())) }
                )
            }
            item {
                SwitchSetting("Show Timestamps", state.preferences.terminalShowTimestamps) {
                    viewModel.onEvent(SettingsEvent.UpdateTerminalShowTimestamps(it))
                }
            }

            // ── Connection ──
            item { SectionDivider(); SectionHeader("Connection") }
            item {
                SliderSetting(
                    title = "Connection Timeout",
                    value = state.preferences.connectionTimeoutSeconds.toFloat(),
                    range = 10f..120f,
                    steps = 10,
                    formatValue = { "${it.toInt()}s" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateConnectionTimeout(it.toInt())) }
                )
            }
            item {
                SwitchSetting("Auto Reconnect", state.preferences.autoReconnect) {
                    viewModel.onEvent(SettingsEvent.UpdateAutoReconnect(it))
                }
            }
            if (state.preferences.autoReconnect) {
                item {
                    SliderSetting(
                        title = "Reconnect Delay",
                        value = state.preferences.autoReconnectDelaySeconds.toFloat(),
                        range = 1f..30f,
                        steps = 28,
                        formatValue = { "${it.toInt()}s" },
                        onValueChange = { viewModel.onEvent(SettingsEvent.UpdateReconnectDelay(it.toInt())) }
                    )
                }
                item {
                    SliderSetting(
                        title = "Max Reconnect Attempts",
                        value = state.preferences.maxReconnectAttempts.toFloat(),
                        range = 1f..10f,
                        steps = 8,
                        formatValue = { "${it.toInt()}" },
                        onValueChange = { viewModel.onEvent(SettingsEvent.UpdateMaxReconnectAttempts(it.toInt())) }
                    )
                }
            }

            // ── Kiosk Mode ──
            item { SectionDivider(); SectionHeader("Kiosk Mode") }
            item {
                SwitchSetting("Enable Kiosk Mode", state.preferences.kioskMode) {
                    viewModel.onEvent(SettingsEvent.UpdateKioskMode(it))
                }
            }
            item {
                SwitchSetting("Auto-start on Boot", state.preferences.autoStartOnBoot) {
                    viewModel.onEvent(SettingsEvent.UpdateAutoStart(it))
                }
            }

            // ── Danger Zone ──
            item { SectionDivider(); SectionHeader("Danger Zone", isError = true) }
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

            // Bottom spacing
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, isError: Boolean = false) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
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

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    formatValue: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(formatValue(value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
