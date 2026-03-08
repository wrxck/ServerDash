package com.serverdash.app.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.core.privacy.redact
import com.serverdash.app.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onDisconnected: () -> Unit,
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
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
                        headlineContent = { Text(redact(config.label.ifBlank { config.host })) },
                        supportingContent = { Text(redact("${config.username}@${config.host}:${config.port}")) },
                        leadingContent = { Icon(Icons.Default.Dns, null) }
                    )
                }
            }

            // ── Security ──
            item {
                SectionDivider()
                SectionHeader("Security")
                ListItem(
                    headlineContent = { Text("Your Data") },
                    supportingContent = { Text("Encryption, stored data, privacy") },
                    leadingContent = { Icon(Icons.Default.Shield, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable { onNavigateToSecurity() }
                )
                ListItem(
                    headlineContent = { Text("Streaming Mode") },
                    supportingContent = { Text("Privacy filters for streaming and screen sharing") },
                    leadingContent = { Icon(Icons.Default.VisibilityOff, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable { onNavigateToPrivacy() }
                )
            }

            // ── App Lock ──
            item {
                SectionDivider()
                SectionHeader("App Lock")
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App Lock", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Require authentication to open",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.preferences.appLockEnabled,
                        onCheckedChange = { viewModel.onEvent(SettingsEvent.UpdateAppLockEnabled(it)) }
                    )
                }
            }
            if (state.preferences.appLockEnabled) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    Column {
                        Text("Lock Timeout", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = state.preferences.appLockTimeout.label,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                LockTimeout.entries.forEach { timeout ->
                                    DropdownMenuItem(
                                        text = { Text(timeout.label) },
                                        onClick = {
                                            viewModel.onEvent(SettingsEvent.UpdateAppLockTimeout(timeout))
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Display ──
            item { SectionDivider(); SectionHeader("Display") }
            item {
                ListItem(
                    headlineContent = { Text("Theme Studio") },
                    supportingContent = { Text("Browse, create and edit themes") },
                    leadingContent = { Icon(Icons.Default.Palette, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable { onNavigateToTheme() }
                )
            }
            item {
                Text("Quick Theme Mode", style = MaterialTheme.typography.bodyMedium)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.preferences.themeMode == mode,
                            onClick = { viewModel.onEvent(SettingsEvent.UpdateThemeMode(mode)) },
                            label = { Text(mode.displayLabel) }
                        )
                    }
                }
            }
            if (state.availableThemes.isNotEmpty()) {
                item {
                    Text("Theme", style = MaterialTheme.typography.bodyMedium)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.availableThemes.forEach { theme ->
                            FilterChip(
                                selected = state.preferences.selectedThemeId == theme.id,
                                onClick = { viewModel.onEvent(SettingsEvent.SelectTheme(theme.id)) },
                                label = { Text(theme.name) }
                            )
                        }
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
            item {
                SliderSetting(
                    title = "Undo Duration",
                    value = state.preferences.undoDurationSeconds.toFloat(),
                    range = 2f..15f,
                    steps = 12,
                    formatValue = { "${it.toInt()}s" },
                    onValueChange = { viewModel.onEvent(SettingsEvent.UpdateUndoDuration(it.toInt())) }
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

            // plugins
            item { SectionDivider(); SectionHeader("Plugins") }
            item {
                Column {
                    viewModel.pluginRegistry.getAll().forEach { plugin ->
                        val detected = viewModel.pluginRegistry.isDetected(plugin.id)
                        val disabled = state.preferences.disabledPlugins.contains(plugin.id)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 0.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    plugin.icon, null, Modifier.size(24.dp),
                                    tint = if (detected) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(plugin.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (detected) plugin.description else "Not detected on server",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = detected && !disabled,
                                onCheckedChange = { viewModel.onEvent(SettingsEvent.TogglePlugin(plugin.id)) },
                                enabled = detected
                            )
                        }
                    }
                }
            }

            // kiosk mode
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

            // ── About & Updates ──
            item { SectionDivider(); SectionHeader("About & Updates") }
            item {
                val uriHandler = LocalUriHandler.current
                val updateState = state.updateState

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("ServerDash", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "v${state.currentVersion}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.Info,
                                null,
                                Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Check for Updates button
                        if (updateState.isChecking) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Checking for updates...", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else if (updateState.latestVersion == null) {
                            OutlinedButton(
                                onClick = { viewModel.onEvent(SettingsEvent.CheckForUpdates) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Update, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Check for Updates")
                            }
                        }

                        // Error state
                        if (updateState.error != null) {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        null,
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        updateState.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Update result
                        if (updateState.latestVersion != null) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))

                            if (updateState.isUpdateAvailable) {
                                Card(
                                    Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.NewReleases,
                                                null,
                                                Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Update Available: ${updateState.latestVersion}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "You are on the latest version (${updateState.latestVersion})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Release notes preview
                            if (!updateState.releaseNotes.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Release Notes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    updateState.releaseNotes.take(500) + if (updateState.releaseNotes.length > 500) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 10,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // APK assets
                            if (updateState.apkAssets.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                updateState.apkAssets.filter { it.name.endsWith(".apk") }.forEach { asset ->
                                    val sizeStr = when {
                                        asset.size >= 1_048_576 -> "%.1f MB".format(asset.size / 1_048_576.0)
                                        asset.size >= 1024 -> "%.0f KB".format(asset.size / 1024.0)
                                        asset.size > 0 -> "${asset.size} B"
                                        else -> ""
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            try { uriHandler.openUri(asset.downloadUrl) } catch (_: Exception) { }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Download ${asset.name}")
                                        if (sizeStr.isNotBlank()) {
                                            Spacer(Modifier.width(4.dp))
                                            Text("($sizeStr)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            // SHA-256 hashes
                            if (!updateState.apkHashes.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("SHA-256", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    updateState.apkHashes,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Re-check button
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { viewModel.onEvent(SettingsEvent.CheckForUpdates) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Check Again")
                            }
                        }
                    }
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
