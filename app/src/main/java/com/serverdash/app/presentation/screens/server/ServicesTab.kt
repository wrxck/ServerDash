package com.serverdash.app.presentation.screens.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.serverdash.app.core.theme.StatusGreen
import com.serverdash.app.core.theme.StatusRed
import com.serverdash.app.core.theme.StatusYellow

@Composable
fun ServicesTab(
    state: ServerUiState,
    onEvent: (ServerEvent) -> Unit,
) {
    // Service logs dialog
    if (state.serviceLogsUnit != null) {
        AlertDialog(
            onDismissRequest = { onEvent(ServerEvent.DismissServiceLogs) },
            confirmButton = {
                TextButton(onClick = { onEvent(ServerEvent.DismissServiceLogs) }) { Text("Close") }
            },
            title = { Text("Logs: ${state.serviceLogsUnit}") },
            text = {
                if (state.isLoadingServiceLogs) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            state.serviceLogs.joinToString("\n").ifBlank { "No logs available" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            },
        )
    }

    if (state.isLoading && !state.servicesLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = state.serviceSearchQuery,
            onValueChange = { onEvent(ServerEvent.UpdateServiceSearch(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Filter services...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.serviceSearchQuery.isNotBlank()) {
                    IconButton(onClick = { onEvent(ServerEvent.UpdateServiceSearch("")) }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {}),
        )

        Text(
            "${state.filteredServices.size} services",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.filteredServices, key = { it.unit }) { service ->
                ServiceItem(service = service, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun ServiceItem(
    service: SystemctlService,
    onEvent: (ServerEvent) -> Unit,
) {
    val statusColor = when (service.active) {
        "active" -> StatusGreen
        "failed" -> StatusRed
        "inactive" -> StatusYellow
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status dot
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = statusColor,
                ) {}
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        service.unit,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${service.active}/${service.sub} | ${if (service.isEnabled) "enabled" else "disabled"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (service.description.isNotBlank()) {
                        Text(
                            service.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (service.active == "active") {
                    SmallActionButton("Stop", Icons.Default.Stop) {
                        onEvent(ServerEvent.RequestAction(ServerAction.ServiceControl(service.unit, "stop")))
                    }
                    SmallActionButton("Restart", Icons.Default.RestartAlt) {
                        onEvent(ServerEvent.RequestAction(ServerAction.ServiceControl(service.unit, "restart")))
                    }
                } else {
                    SmallActionButton("Start", Icons.Default.PlayArrow) {
                        onEvent(ServerEvent.RequestAction(ServerAction.ServiceControl(service.unit, "start")))
                    }
                }

                if (service.isEnabled) {
                    SmallActionButton("Disable", Icons.Default.Block) {
                        onEvent(ServerEvent.RequestAction(ServerAction.ServiceEnable(service.unit, enable = false)))
                    }
                } else {
                    SmallActionButton("Enable", Icons.Default.CheckCircle) {
                        onEvent(ServerEvent.RequestAction(ServerAction.ServiceEnable(service.unit, enable = true)))
                    }
                }

                SmallActionButton("Logs", Icons.AutoMirrored.Filled.Article) {
                    onEvent(ServerEvent.ViewServiceLogs(service.unit))
                }
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(28.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
