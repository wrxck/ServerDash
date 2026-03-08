package com.serverdash.app.presentation.screens.guardian

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
fun GuardianScreen(
    onNavigateBack: () -> Unit,
    viewModel: GuardianViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Guardian")
                        if (state.guardianVersion.isNotBlank()) {
                            Text(state.guardianVersion, style = MaterialTheme.typography.labelSmall,
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
                    IconButton(onClick = { viewModel.onEvent(GuardianEvent.Refresh) }) {
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
                        OutlinedButton(onClick = { viewModel.onEvent(GuardianEvent.Refresh) }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Status card
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Shield, null,
                                            tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Status", style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(state.guardianStatus, fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp, lineHeight = 18.sp)
                                }
                            }
                        }

                        // Monitored processes
                        if (state.processes.isNotEmpty()) {
                            item {
                                Text("Monitored Processes", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            items(state.processes, key = { it.name }) { process ->
                                ProcessCard(process)
                            }
                        }

                        // Recent events
                        if (state.recentEvents.isNotEmpty()) {
                            item {
                                Text("Recent Events", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        state.recentEvents.forEach { event ->
                                            Text(event, fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp, lineHeight = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessCard(process: GuardianProcess) {
    val statusColor = when (process.status.lowercase()) {
        "running", "active", "online" -> MaterialTheme.colorScheme.primary
        "stopped", "dead", "offline" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Memory, null, tint = statusColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(process.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("PID: ${process.pid}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (process.cpu != "-") {
                Column(horizontalAlignment = Alignment.End) {
                    Text("CPU: ${process.cpu}", style = MaterialTheme.typography.labelSmall)
                    Text("MEM: ${process.memory}", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(8.dp))
            }
            AssistChip(
                onClick = {},
                label = { Text(process.status, style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(labelColor = statusColor)
            )
        }
    }
}
