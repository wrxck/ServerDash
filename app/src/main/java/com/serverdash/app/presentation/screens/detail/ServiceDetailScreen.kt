package com.serverdash.app.presentation.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.core.theme.*
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.usecase.ServiceAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServiceDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val tabs = listOf("Overview", "Logs", "Config")

    // Confirmation dialog
    state.showConfirmDialog?.let { action ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ServiceDetailEvent.DismissConfirmDialog) },
            title = { Text("Confirm ${action.name}") },
            text = { Text("Are you sure you want to ${action.command} ${state.service?.displayName}?") },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(ServiceDetailEvent.ConfirmAction(action)) }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(ServiceDetailEvent.DismissConfirmDialog) }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.service?.displayName ?: "Service Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Status and controls
            state.service?.let { service ->
                ServiceControlRow(
                    service = service,
                    isControlling = state.isControlling,
                    onAction = { viewModel.onEvent(ServiceDetailEvent.ControlService(it)) }
                )
            }

            // Tabs
            TabRow(selectedTabIndex = state.selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.onEvent(ServiceDetailEvent.SelectTab(index)) },
                        text = { Text(title) }
                    )
                }
            }

            // Tab content
            when (state.selectedTab) {
                0 -> OverviewTab(state)
                1 -> LogsTab(state, viewModel::onEvent)
                2 -> ConfigTab(state, viewModel::onEvent)
            }
        }
    }
}

@Composable
private fun ServiceControlRow(
    service: Service,
    isControlling: Boolean,
    onAction: (ServiceAction) -> Unit
) {
    val statusColor = when (service.status) {
        ServiceStatus.RUNNING -> StatusGreen
        ServiceStatus.FAILED -> StatusRed
        ServiceStatus.STOPPED -> StatusYellow
        ServiceStatus.UNKNOWN -> StatusGray
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(service.status.name, style = MaterialTheme.typography.titleMedium, color = statusColor)
                Spacer(Modifier.width(8.dp))
                Text("(${service.subState})", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(service.type.name, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onAction(ServiceAction.START) },
                    enabled = !isControlling && service.status != ServiceStatus.RUNNING
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Start")
                }
                FilledTonalButton(
                    onClick = { onAction(ServiceAction.STOP) },
                    enabled = !isControlling && service.status == ServiceStatus.RUNNING
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }
                FilledTonalButton(
                    onClick = { onAction(ServiceAction.RESTART) },
                    enabled = !isControlling
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restart")
                }
                if (isControlling) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(state: ServiceDetailUiState) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            state.service?.let { service ->
                Text("Service Information", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InfoRow("Name", service.name)
                InfoRow("Type", service.type.name)
                InfoRow("Status", service.status.name)
                InfoRow("Sub-state", service.subState)
                if (service.description.isNotBlank()) {
                    InfoRow("Description", service.description)
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            state.controlResult?.let {
                Text("Last Action: $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LogsTab(state: ServiceDetailUiState, onEvent: (ServiceDetailEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(8.dp)) {
            Button(onClick = { onEvent(ServiceDetailEvent.RefreshLogs) }, enabled = !state.isLoadingLogs) {
                Text("Refresh Logs")
            }
        }
        if (state.isLoadingLogs) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(state.logs) { log ->
                    Text(
                        log.message,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigTab(state: ServiceDetailUiState, onEvent: (ServiceDetailEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.configPath,
            onValueChange = {},
            label = { Text("Config file path") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { onEvent(ServiceDetailEvent.LoadConfig(state.configPath)) }) {
                    Icon(Icons.Default.FileOpen, "Load")
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        if (state.isLoadingConfig) {
            CircularProgressIndicator()
        } else {
            OutlinedTextField(
                value = state.configContent,
                onValueChange = { onEvent(ServiceDetailEvent.UpdateConfig(it)) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onEvent(ServiceDetailEvent.SaveConfig) },
                enabled = !state.isSavingConfig && state.configPath.isNotBlank()
            ) {
                Text(if (state.isSavingConfig) "Saving..." else "Save Config")
            }
        }
    }
}
