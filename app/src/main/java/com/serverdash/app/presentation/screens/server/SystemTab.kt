package com.serverdash.app.presentation.screens.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SystemTab(
    state: ServerUiState,
    onEvent: (ServerEvent) -> Unit,
) {
    if (state.isLoading && !state.systemLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val info = state.systemInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // OS Info card
        InfoCard(
            title = "Operating System",
            icon = Icons.Default.Computer,
            items = listOf(
                "Distribution" to info.os,
                "Kernel" to info.kernel,
                "Architecture" to info.arch,
                "Uptime" to info.uptime,
            ),
        )

        // Hostname card (editable)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Hostname", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                if (state.editingHostname != null) {
                    OutlinedTextField(
                        value = state.editingHostname,
                        onValueChange = { onEvent(ServerEvent.UpdateEditingHostname(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Hostname") },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onEvent(ServerEvent.CancelEditHostname) },
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                onEvent(ServerEvent.RequestAction(ServerAction.SetHostname(state.editingHostname)))
                            },
                            enabled = state.editingHostname.isNotBlank(),
                        ) { Text("Save") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(info.hostname, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onEvent(ServerEvent.StartEditHostname(info.hostname)) }) {
                            Icon(Icons.Default.Edit, "Edit hostname")
                        }
                    }
                }
            }
        }

        // Timezone card (editable)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Timezone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                if (state.editingTimezone != null) {
                    OutlinedTextField(
                        value = state.editingTimezone,
                        onValueChange = { onEvent(ServerEvent.UpdateEditingTimezone(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Timezone (e.g. America/New_York)") },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onEvent(ServerEvent.CancelEditTimezone) },
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                onEvent(ServerEvent.RequestAction(ServerAction.SetTimezone(state.editingTimezone)))
                            },
                            enabled = state.editingTimezone.isNotBlank(),
                        ) { Text("Save") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(info.timezone, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onEvent(ServerEvent.StartEditTimezone(info.timezone)) }) {
                            Icon(Icons.Default.Edit, "Edit timezone")
                        }
                    }
                }
            }
        }

        // Locale card
        InfoCard(
            title = "Locale",
            icon = Icons.Default.Language,
            items = listOf("Locale" to info.locale),
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    icon: ImageVector,
    items: List<Pair<String, String>>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            items.forEach { (label, value) ->
                if (value.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(100.dp),
                        )
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
