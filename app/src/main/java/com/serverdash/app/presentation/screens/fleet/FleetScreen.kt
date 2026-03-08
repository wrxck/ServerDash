package com.serverdash.app.presentation.screens.fleet

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetScreen(
    onNavigateBack: () -> Unit,
    viewModel: FleetViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Fleet")
                        if (state.fleetVersion.isNotBlank()) {
                            Text(state.fleetVersion, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(FleetEvent.Refresh) }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.onEvent(FleetEvent.Refresh) }) {
                            Text("Retry")
                        }
                    }
                }
                state.apps.isEmpty() -> {
                    Text("No Fleet apps found", Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.apps, key = { it.name }) { app ->
                            FleetAppCard(app, onAction = { action ->
                                viewModel.onEvent(FleetEvent.RunCommand(app.name, action))
                            })
                        }
                    }
                }
            }
        }
    }

    // Command output dialog
    state.commandOutput?.let { output ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(FleetEvent.DismissOutput) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(FleetEvent.DismissOutput) }) {
                    Text("Close")
                }
            },
            title = { Text("Output") },
            text = {
                Text(output, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    modifier = Modifier.heightIn(max = 400.dp))
            }
        )
    }
}

@Composable
private fun FleetAppCard(app: FleetApp, onAction: (String) -> Unit) {
    val statusColor = when (app.status) {
        "running" -> MaterialTheme.colorScheme.primary
        "active" -> MaterialTheme.colorScheme.primary
        "failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RocketLaunch, null, tint = statusColor)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(app.type, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(app.status) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = statusColor
                    )
                )
            }

            if (app.domains.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                app.domains.forEach { domain ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(domain, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (app.containers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                app.containers.forEach { container ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ViewInAr, null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(container.name, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        Text(container.state, style = MaterialTheme.typography.labelSmall,
                            color = if (container.state == "running") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            app.port?.let { port ->
                Spacer(Modifier.height(4.dp))
                Text("Port: $port", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (app.status == "running" || app.status == "active") {
                    OutlinedButton(onClick = { onAction("restart") }, modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restart", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { onAction("stop") }, modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    OutlinedButton(onClick = { onAction("start") }, modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Start", style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedButton(onClick = { onAction("logs") }, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Default.Terminal, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Logs", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
