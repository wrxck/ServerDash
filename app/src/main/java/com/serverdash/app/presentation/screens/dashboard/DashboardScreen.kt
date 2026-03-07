package com.serverdash.app.presentation.screens.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.core.theme.*
import com.serverdash.app.core.util.*
import com.serverdash.app.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val landscape = isLandscape()
    val columns = if (landscape) 4 else 2

    LaunchedEffect(Unit) {
        viewModel.navigateToDetail.collect { service ->
            onNavigateToDetail(service.name, service.type.name)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ServerDash") },
                actions = {
                    IconButton(onClick = onNavigateToTerminal) {
                        Icon(Icons.Default.Terminal, "Terminal")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Connection status bar
            ConnectionStatusBar(state.connectionState)

            // Alert banner
            if (state.activeAlerts.isNotEmpty()) {
                AlertBanner(
                    alertCount = state.activeAlerts.size,
                    onDismiss = { viewModel.onEvent(DashboardEvent.AcknowledgeAlerts) }
                )
            }

            // Metrics summary
            state.metrics?.let { MetricsSummaryRow(it) }

            // Service grid
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.onEvent(DashboardEvent.Refresh) },
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.services.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No services found", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.services, key = { it.id }) { service ->
                            ServiceCard(
                                service = service,
                                onClick = { viewModel.onEvent(DashboardEvent.NavigateToDetail(service)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBar(connectionState: ConnectionState) {
    val color by animateColorAsState(
        when {
            connectionState.isConnected -> StatusGreen
            connectionState.isConnecting -> StatusYellow
            connectionState.error != null -> StatusRed
            else -> StatusGray
        }, label = "status"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when {
                connectionState.isConnected -> Icons.Default.Wifi
                connectionState.isConnecting -> Icons.Default.Sync
                else -> Icons.Default.WifiOff
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                connectionState.isConnected -> "Connected"
                connectionState.isConnecting -> "Connecting..."
                connectionState.error != null -> connectionState.error!!
                else -> "Disconnected"
            },
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
fun AlertBanner(alertCount: Int, onDismiss: () -> Unit) {
    Surface(
        color = StatusRed.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = StatusRed)
            Spacer(Modifier.width(8.dp))
            Text(
                "$alertCount active alert${if (alertCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = StatusRed,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
fun MetricsSummaryRow(metrics: SystemMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        MetricChip("CPU", "%.0f%%".format(metrics.cpuUsage))
        MetricChip("MEM", "%.0f%%".format(metrics.memoryUsagePercent))
        MetricChip("DISK", "%.0f%%".format(metrics.diskUsagePercent))
        MetricChip("UP", formatUptime(metrics.uptimeSeconds))
    }
}

@Composable
fun MetricChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ServiceCard(service: Service, onClick: () -> Unit) {
    val statusColor = when (service.status) {
        ServiceStatus.RUNNING -> StatusGreen
        ServiceStatus.FAILED -> StatusRed
        ServiceStatus.STOPPED -> StatusYellow
        ServiceStatus.UNKNOWN -> StatusGray
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    service.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${service.type.name} - ${service.status.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (service.isPinned) {
                Icon(Icons.Default.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
