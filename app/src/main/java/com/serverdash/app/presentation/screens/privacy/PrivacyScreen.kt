package com.serverdash.app.presentation.screens.privacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val prefs = state.preferences
    val streamingEnabled = prefs.streamingModeEnabled

    val topBarColor by animateColorAsState(
        targetValue = if (streamingEnabled) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surface,
        label = "topBarColor"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Streaming Mode")
                        if (streamingEnabled) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    " LIVE ",
                                    color = MaterialTheme.colorScheme.onError,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Master Toggle ──
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (streamingEnabled)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Privacy Filter",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (streamingEnabled) "Active -- sensitive data is being redacted"
                                else "Disabled -- all data shown as-is",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = streamingEnabled,
                            onCheckedChange = { viewModel.onEvent(PrivacyEvent.ToggleStreamingMode(it)) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }

            // ── Filter Categories ──
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Filter Categories")
                Text(
                    "Choose which types of sensitive information to redact",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                FilterToggle(
                    icon = Icons.Default.Language,
                    title = "IP Addresses",
                    description = "IPv4 and IPv6 addresses",
                    checked = prefs.privacyFilterIps,
                    enabled = streamingEnabled,
                    onChanged = { viewModel.onEvent(PrivacyEvent.ToggleFilterIps(it)) }
                )
            }
            item {
                FilterToggle(
                    icon = Icons.Default.Pin,
                    title = "Port Numbers",
                    description = "Ports in context like :8080 or port 443",
                    checked = prefs.privacyFilterPorts,
                    enabled = streamingEnabled,
                    onChanged = { viewModel.onEvent(PrivacyEvent.ToggleFilterPorts(it)) }
                )
            }
            item {
                FilterToggle(
                    icon = Icons.Default.Email,
                    title = "Email Addresses",
                    description = "user@domain patterns",
                    checked = prefs.privacyFilterEmails,
                    enabled = streamingEnabled,
                    onChanged = { viewModel.onEvent(PrivacyEvent.ToggleFilterEmails(it)) }
                )
            }
            item {
                FilterToggle(
                    icon = Icons.Default.Dns,
                    title = "Hostnames / Domains",
                    description = "server.example.com patterns",
                    checked = prefs.privacyFilterHostnames,
                    enabled = streamingEnabled,
                    onChanged = { viewModel.onEvent(PrivacyEvent.ToggleFilterHostnames(it)) }
                )
            }
            item {
                FilterToggle(
                    icon = Icons.Default.Folder,
                    title = "File Paths",
                    description = "Usernames in /home/ and /Users/ paths",
                    checked = prefs.privacyFilterPaths,
                    enabled = streamingEnabled,
                    onChanged = { viewModel.onEvent(PrivacyEvent.ToggleFilterPaths(it)) }
                )
            }
            item {
                FilterToggle(
                    icon = Icons.Default.Terminal,
                    title = "SSH Connections",
                    description = "user@host connection strings",
                    checked = prefs.privacyFilterSsh,
                    enabled = streamingEnabled,
                    onChanged = { viewModel.onEvent(PrivacyEvent.ToggleFilterSsh(it)) }
                )
            }
            item {
                FilterToggle(
                    icon = Icons.Default.Key,
                    title = "API Keys / Tokens",
                    description = "Long hex and base64 strings",
                    checked = prefs.privacyFilterTokens,
                    enabled = streamingEnabled,
                    onChanged = { viewModel.onEvent(PrivacyEvent.ToggleFilterTokens(it)) }
                )
            }
            item {
                FilterToggle(
                    icon = Icons.Default.Password,
                    title = "Passwords",
                    description = "password=, passwd:, secret= patterns",
                    checked = prefs.privacyFilterPasswords,
                    enabled = streamingEnabled,
                    onChanged = { viewModel.onEvent(PrivacyEvent.ToggleFilterPasswords(it)) }
                )
            }
            item {
                FilterToggle(
                    icon = Icons.Default.MiscellaneousServices,
                    title = "Service Names",
                    description = "User-configured service name list",
                    checked = prefs.privacyFilterServiceNames,
                    enabled = streamingEnabled,
                    onChanged = { viewModel.onEvent(PrivacyEvent.ToggleFilterServiceNames(it)) }
                )
            }

            // ── Service Names Management ──
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                SectionHeader("Redacted Service Names")
                Text(
                    "Service names that will be replaced with [SERVICE]",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.newServiceName,
                        onValueChange = { viewModel.onEvent(PrivacyEvent.UpdateNewServiceName(it)) },
                        label = { Text("Service name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = streamingEnabled,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.onEvent(PrivacyEvent.AddServiceName) }
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { viewModel.onEvent(PrivacyEvent.AddServiceName) },
                        enabled = streamingEnabled && state.newServiceName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, "Add")
                    }
                }
            }

            if (prefs.privacyRedactedServiceNames.isNotEmpty()) {
                items(prefs.privacyRedactedServiceNames.toList()) { name ->
                    ChipItem(
                        text = name,
                        enabled = streamingEnabled,
                        onRemove = { viewModel.onEvent(PrivacyEvent.RemoveServiceName(name)) }
                    )
                }
            }

            // ── Custom Patterns ──
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                SectionHeader("Custom Regex Patterns")
                Text(
                    "Add custom regular expressions to match and redact",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.newPattern,
                        onValueChange = { viewModel.onEvent(PrivacyEvent.UpdateNewPattern(it)) },
                        label = { Text("Regex pattern") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = streamingEnabled,
                        isError = state.patternError != null,
                        supportingText = state.patternError?.let { error -> { Text(error) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.onEvent(PrivacyEvent.AddPattern) }
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { viewModel.onEvent(PrivacyEvent.AddPattern) },
                        enabled = streamingEnabled && state.newPattern.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, "Add")
                    }
                }
            }

            if (prefs.privacyCustomPatterns.isNotEmpty()) {
                items(prefs.privacyCustomPatterns.toList()) { pattern ->
                    ChipItem(
                        text = pattern,
                        enabled = streamingEnabled,
                        onRemove = { viewModel.onEvent(PrivacyEvent.RemovePattern(pattern)) },
                        isMono = true
                    )
                }
            }

            // ── Replacement Text ──
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                SectionHeader("Replacement Text")
                Text(
                    "Default text used for general redaction",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prefs.privacyReplacementText,
                    onValueChange = { viewModel.onEvent(PrivacyEvent.UpdateReplacementText(it)) },
                    label = { Text("Replacement text") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = streamingEnabled
                )
            }

            // ── Preview / Test ──
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Test & Preview")
                    TextButton(
                        onClick = { viewModel.onEvent(PrivacyEvent.LoadSampleText) },
                        enabled = streamingEnabled
                    ) {
                        Text("Load Sample")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.testInput,
                    onValueChange = { viewModel.onEvent(PrivacyEvent.UpdateTestInput(it)) },
                    label = { Text("Paste text to test") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    enabled = streamingEnabled,
                    maxLines = 10
                )
            }

            if (state.testInput.isNotEmpty()) {
                item {
                    Text(
                        "Filtered Output:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            state.testOutput,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bottom spacing
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun FilterToggle(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled && checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChanged,
            enabled = enabled
        )
    }
}

@Composable
private fun ChipItem(
    text: String,
    enabled: Boolean,
    onRemove: () -> Unit,
    isMono: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                style = if (isMono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close, "Remove",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
