package com.serverdash.app.presentation.screens.security

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private val GreenGood = Color(0xFF66BB6A)     // 5.1:1 on dark, 4.5:1 on light
private val AmberAttention = Color(0xFFFFA726) // 6.4:1 on dark, 3.3:1 on light (used decoratively)

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    onNavigateBack: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Encryption just enabled - restart dialog
    if (state.encryptionJustEnabled) {
        AlertDialog(
            onDismissRequest = onNavigateBack,
            confirmButton = {
                TextButton(onClick = onNavigateBack) { Text("Restart Now") }
            },
            icon = { Icon(Icons.Default.Shield, null, tint = GreenGood) },
            title = { Text("Encryption Enabled") },
            text = { Text("The app needs to restart to encrypt the database. Your data will be preserved.") }
        )
    }

    // Clear confirmation dialog
    state.showClearConfirmation?.let { category ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(SecurityEvent.DismissClearConfirmation) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(SecurityEvent.ConfirmClear) }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(SecurityEvent.DismissClearConfirmation) }) {
                    Text("Cancel")
                }
            },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Clear ${category.label}?") },
            text = { Text("This will permanently remove ${category.description.lowercase()}. This action cannot be undone.") }
        )
    }

    // Success snackbar
    state.clearSuccess?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2500)
            viewModel.onEvent(SecurityEvent.DismissSuccess)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security & Your Data") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = {
            state.clearSuccess?.let { message ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.onEvent(SecurityEvent.DismissSuccess) }) {
                            Text("OK")
                        }
                    }
                ) { Text(message) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Encryption Status
            EncryptionStatusCard(state, viewModel)

            // 2. Security Checkup
            SecurityCheckupCard(state, viewModel)

            // 3. Issues & Fixes (only if there are issues)
            if (state.issues.isNotEmpty()) {
                IssuesCard(state.issues, viewModel)
            }

            // 4. Your Data
            YourDataCard(state, viewModel)

            // 5. Database Info
            DatabaseInfoCard(state)

            // 6. How It Works
            HowItWorksCard()

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EncryptionStatusCard(state: SecurityUiState, viewModel: SecurityViewModel) {
    val isEncrypted = state.isEncryptionEnabled
    val shieldColor = if (isEncrypted) GreenGood else AmberAttention
    val title = if (isEncrypted) "Data is encrypted" else "Data is not encrypted"
    val subtitle = when {
        isEncrypted && state.isBiometricEnabled -> "Protected with biometric-backed encryption"
        isEncrypted -> "Protected with SQLCipher AES-256 encryption"
        else -> "Enable encryption to protect your credentials and server data"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = shieldColor.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = shieldColor,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = shieldColor
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isEncrypted) {
                Spacer(Modifier.height(16.dp))

                // Error message if encryption failed (no red, use amber)
                state.encryptionError?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AmberAttention.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                null,
                                tint = AmberAttention,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.onEvent(SecurityEvent.DismissError) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (state.biometricAvailable) {
                    Button(
                        onClick = { viewModel.onEvent(SecurityEvent.EnableEncryption(true)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Fingerprint, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Encrypt with Biometrics")
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = { viewModel.onEvent(SecurityEvent.EnableEncryption(false)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.biometricAvailable) "Encrypt without Biometrics"
                        else "Enable Encryption"
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityCheckupCard(state: SecurityUiState, viewModel: SecurityViewModel) {
    var expanded by remember { mutableStateOf(true) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Security Checkup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                val passedCount = state.checkupItems.count { it.passed }
                val totalCount = state.checkupItems.size
                if (totalCount > 0) {
                    Text(
                        "$passedCount/$totalCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (passedCount == totalCount) GreenGood else AmberAttention
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))

                state.checkupItems.forEach { item ->
                    CheckupItemRow(item)
                }

                if (state.checkupItems.any { !it.passed }) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = { viewModel.onEvent(SecurityEvent.FixAll) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fix All")
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckupItemRow(item: CheckupItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.passed) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (item.passed) GreenGood else AmberAttention,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IssuesCard(issues: List<SecurityIssue>, viewModel: SecurityViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AmberAttention.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    null,
                    tint = AmberAttention,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Suggestions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))

            issues.forEach { issue ->
                IssueItemRow(issue, viewModel)
                if (issue != issues.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun IssueItemRow(issue: SecurityIssue, viewModel: SecurityViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            issue.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            issue.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { viewModel.onEvent(issue.fixAction) },
            modifier = Modifier.align(Alignment.End),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.AutoFixHigh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(issue.fixLabel, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun YourDataCard(state: SecurityUiState, viewModel: SecurityViewModel) {
    var expanded by remember { mutableStateOf(true) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Storage,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Your Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            if (expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "View and manage all locally stored data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                // SSH Credentials (read-only info, managed in settings)
                DataCategoryRow(
                    icon = Icons.Default.Key,
                    title = "SSH Credentials",
                    description = "Server host, port, username, and authentication method",
                    detail = "Managed in server settings",
                    clearable = false,
                    onClear = {}
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Sudo Password
                DataCategoryRow(
                    icon = Icons.Default.AdminPanelSettings,
                    title = "Sudo Password",
                    description = "Stored for service management commands",
                    detail = "Managed in server settings",
                    clearable = false,
                    onClear = {}
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Terminal History
                DataCategoryRow(
                    icon = Icons.Default.Terminal,
                    title = "Terminal History",
                    description = "Previously executed commands and their output",
                    detail = "${state.terminalHistoryCount} entries",
                    clearable = state.terminalHistoryCount > 0,
                    onClear = { viewModel.onEvent(SecurityEvent.RequestClear(DataCategory.TERMINAL_HISTORY)) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Service Cache
                DataCategoryRow(
                    icon = Icons.Default.Dns,
                    title = "Service Cache",
                    description = "Cached service discovery and status data",
                    detail = "${state.serviceCacheCount} services",
                    clearable = state.serviceCacheCount > 0,
                    onClear = { viewModel.onEvent(SecurityEvent.RequestClear(DataCategory.SERVICE_CACHE)) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Metrics History
                DataCategoryRow(
                    icon = Icons.Default.Timeline,
                    title = "Metrics History",
                    description = "CPU, memory, and disk usage snapshots",
                    detail = "${state.metricsCount} snapshots",
                    clearable = state.metricsCount > 0,
                    onClear = { viewModel.onEvent(SecurityEvent.RequestClear(DataCategory.METRICS_HISTORY)) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Alert Rules
                DataCategoryRow(
                    icon = Icons.Default.Notifications,
                    title = "Alert Rules",
                    description = "Custom monitoring rules and webhook URLs",
                    detail = "${state.alertRulesCount} rules",
                    clearable = state.alertRulesCount > 0,
                    onClear = { viewModel.onEvent(SecurityEvent.RequestClear(DataCategory.ALERT_RULES)) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // App Preferences (read-only info)
                DataCategoryRow(
                    icon = Icons.Default.Settings,
                    title = "App Preferences",
                    description = "Theme, layout, refresh intervals, and plugin toggles",
                    detail = "Managed in settings",
                    clearable = false,
                    onClear = {}
                )

                // Clear All
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.onEvent(SecurityEvent.RequestClear(DataCategory.ALL_DATA)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear All Cached Data")
                }
            }
        }
    }
}

@Composable
private fun DataCategoryRow(
    icon: ImageVector,
    title: String,
    description: String,
    detail: String,
    clearable: Boolean,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (clearable) {
            FilledTonalButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Clear", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DatabaseInfoCard(state: SecurityUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Storage,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Database Info",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))

            DatabaseInfoRow("Name", state.databaseName)
            DatabaseInfoRow(
                "Encryption",
                if (state.isEncryptionEnabled) "SQLCipher AES-256" else "None"
            )
            DatabaseInfoRow("Size on disk", formatBytes(state.databaseSizeBytes))
        }
    }
}

@Composable
private fun DatabaseInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun HowItWorksCard() {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "How It Works",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                InfoItem("Database encryption uses SQLCipher (AES-256)")
                InfoItem("Encryption key is stored in the Android KeyStore hardware module")
                InfoItem("Preferences are secured with EncryptedSharedPreferences")
                InfoItem("Keys never leave the device's secure element")
                InfoItem("All data stays on your device and is never sent to external servers")
            }
        }
    }
}

@Composable
private fun InfoItem(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(
            Icons.Default.CheckCircle,
            null,
            modifier = Modifier.size(16.dp),
            tint = GreenGood
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
