package com.serverdash.app.presentation.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.serverdash.app.core.theme.StatusGreen
import com.serverdash.app.core.theme.StatusRed
import com.serverdash.app.core.theme.StatusYellow
import com.serverdash.app.core.util.formatUptime
import com.serverdash.app.domain.model.SystemMetrics

// ── Sparkline Chart ────────────────────────────────────────────────

@Composable
fun SparklineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = lineColor.copy(alpha = 0.15f),
    maxValue: Float = 100f
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val step = w / (values.size - 1).coerceAtLeast(1)
        val points = values.mapIndexed { i, v ->
            Offset(i * step, h - (v / maxValue.coerceAtLeast(0.01f)) * h)
        }

        // Fill area
        val fillPath = Path().apply {
            moveTo(0f, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, h)
            close()
        }
        drawPath(fillPath, fillColor)

        // Line
        for (i in 0 until points.size - 1) {
            drawLine(lineColor, points[i], points[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

// ── Clickable Metrics Row with Sparklines ──────────────────────────

@Composable
fun MetricsCardsRow(
    state: DashboardUiState,
    viewModel: DashboardViewModel
) {
    val metrics = state.metrics ?: return
    val history = state.metricsHistory

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCardWithChart(
            label = "CPU",
            value = "%.0f%%".format(metrics.cpuUsage),
            chartValues = history.takeLast(30).map { it.cpuUsage },
            chartColor = when {
                metrics.cpuUsage > 80 -> StatusRed
                metrics.cpuUsage > 50 -> StatusYellow
                else -> StatusGreen
            },
            onClick = { viewModel.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.CPU)) },
            modifier = Modifier.weight(1f)
        )
        MetricCardWithChart(
            label = "MEM",
            value = "%.0f%%".format(metrics.memoryUsagePercent),
            chartValues = history.takeLast(30).map { it.memoryUsagePercent },
            chartColor = when {
                metrics.memoryUsagePercent > 80 -> StatusRed
                metrics.memoryUsagePercent > 50 -> StatusYellow
                else -> MaterialTheme.colorScheme.primary
            },
            onClick = { viewModel.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.MEMORY)) },
            modifier = Modifier.weight(1f)
        )
        MetricCardWithChart(
            label = "DISK",
            value = "%.0f%%".format(metrics.diskUsagePercent),
            chartValues = history.takeLast(30).map { it.diskUsagePercent },
            chartColor = when {
                metrics.diskUsagePercent > 80 -> StatusRed
                metrics.diskUsagePercent > 50 -> StatusYellow
                else -> MaterialTheme.colorScheme.tertiary
            },
            onClick = { viewModel.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.DISK)) },
            modifier = Modifier.weight(1f)
        )
        // Processes button
        Card(
            modifier = Modifier.weight(1f).clickable {
                viewModel.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.PROCESSES))
            }
        ) {
            Column(
                Modifier.padding(8.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("UP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatUptime(metrics.uptimeSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Icon(Icons.Default.Terminal, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text("htop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MetricCardWithChart(
    label: String,
    value: String,
    chartValues: List<Float>,
    chartColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (chartValues.size >= 2) {
                SparklineChart(
                    values = chartValues,
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    lineColor = chartColor,
                    fillColor = chartColor.copy(alpha = 0.15f)
                )
            } else {
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

// ── Metric Detail Views ────────────────────────────────────────────

@Composable
fun MetricDetailOverlay(state: DashboardUiState, viewModel: DashboardViewModel) {
    val detail = state.activeMetricDetail ?: return

    Column(Modifier.fillMaxSize()) {
        when (detail) {
            MetricDetailType.CPU -> CpuDetailView(state, viewModel)
            MetricDetailType.MEMORY -> MemoryDetailView(state, viewModel)
            MetricDetailType.DISK -> DiskDetailView(state, viewModel)
            MetricDetailType.PROCESSES -> ProcessListView(state, viewModel)
        }
    }
}

@Composable
private fun MetricDetailHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        actions()
    }
    HorizontalDivider()
}

@Composable
private fun CpuDetailView(state: DashboardUiState, viewModel: DashboardViewModel) {
    val metrics = state.metrics
    val history = state.metricsHistory

    MetricDetailHeader("CPU Usage", onBack = { viewModel.onEvent(DashboardEvent.CloseMetricDetail) }) {
        IconButton(onClick = { viewModel.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.PROCESSES)) }) {
            Icon(Icons.Default.Terminal, "Processes")
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.1f".format(metrics?.cpuUsage ?: 0f),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("History (last ${history.size} samples)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    SparklineChart(
                        values = history.map { it.cpuUsage },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        lineColor = StatusGreen
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Load Average", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        LoadAvgItem("1 min", metrics?.loadAvg1 ?: 0f)
                        LoadAvgItem("5 min", metrics?.loadAvg5 ?: 0f)
                        LoadAvgItem("15 min", metrics?.loadAvg15 ?: 0f)
                    }
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = { viewModel.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.PROCESSES)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Terminal, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("View Processes")
            }
        }
    }
}

@Composable
private fun LoadAvgItem(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("%.2f".format(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MemoryDetailView(state: DashboardUiState, viewModel: DashboardViewModel) {
    val metrics = state.metrics
    val history = state.metricsHistory

    MetricDetailHeader("Memory Usage", onBack = { viewModel.onEvent(DashboardEvent.CloseMetricDetail) })

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.1f".format(metrics?.memoryUsagePercent ?: 0f),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (metrics?.memoryUsagePercent ?: 0f) / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Used: ${formatBytesHuman(metrics?.memoryUsed ?: 0)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Total: ${formatBytesHuman(metrics?.memoryTotal ?: 0)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("History", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    SparklineChart(
                        values = history.map { it.memoryUsagePercent },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        lineColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DiskDetailView(state: DashboardUiState, viewModel: DashboardViewModel) {
    val metrics = state.metrics
    val history = state.metricsHistory

    MetricDetailHeader("Disk Usage", onBack = { viewModel.onEvent(DashboardEvent.CloseMetricDetail) })

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Root Partition (/)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.1f".format(metrics?.diskUsagePercent ?: 0f),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (metrics?.diskUsagePercent ?: 0f) / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Used: ${formatBytesHuman(metrics?.diskUsed ?: 0)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Total: ${formatBytesHuman(metrics?.diskTotal ?: 0)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("History", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    SparklineChart(
                        values = history.map { it.diskUsagePercent },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        lineColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

// ── htop-style Process List ────────────────────────────────────────

@Composable
private fun ProcessListView(state: DashboardUiState, viewModel: DashboardViewModel) {
    MetricDetailHeader(
        "Processes (${state.processList.size})",
        onBack = { viewModel.onEvent(DashboardEvent.CloseMetricDetail) }
    ) {
        IconButton(onClick = { viewModel.onEvent(DashboardEvent.RefreshProcesses) }) {
            Icon(Icons.Default.Refresh, "Refresh")
        }
    }

    // Sort buttons
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SortChip("CPU", "cpu", state.processSortBy, viewModel)
        SortChip("MEM", "mem", state.processSortBy, viewModel)
        SortChip("PID", "pid", state.processSortBy, viewModel)
    }

    // Process header
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("PID", Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("USER", Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("CPU%", Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("MEM%", Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("RSS", Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("CMD", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

    if (state.isLoadingProcesses) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(state.processList, key = { it.pid }) { process ->
                ProcessRow(process, viewModel)
            }
        }
    }
}

@Composable
private fun SortChip(label: String, sortKey: String, current: String, viewModel: DashboardViewModel) {
    FilterChip(
        selected = current == sortKey,
        onClick = { viewModel.onEvent(DashboardEvent.SortProcesses(sortKey)) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = if (current == sortKey) {
            { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(14.dp)) }
        } else null,
        modifier = Modifier.height(28.dp)
    )
}

@Composable
private fun ProcessRow(process: ProcessInfo, viewModel: DashboardViewModel) {
    var showKillConfirm by remember { mutableStateOf(false) }

    if (showKillConfirm) {
        AlertDialog(
            onDismissRequest = { showKillConfirm = false },
            title = { Text("Kill Process") },
            text = { Text("Send SIGTERM to PID ${process.pid} (${process.command})?") },
            confirmButton = {
                TextButton(onClick = {
                    showKillConfirm = false
                    viewModel.onEvent(DashboardEvent.KillProcess(process.pid))
                }) { Text("Kill") }
            },
            dismissButton = {
                TextButton(onClick = { showKillConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val cpuColor = when {
        process.cpuPercent > 50 -> Color(0xFFEF5350)
        process.cpuPercent > 10 -> Color(0xFFFFA726)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val memColor = when {
        process.memPercent > 20 -> Color(0xFFEF5350)
        process.memPercent > 5 -> Color(0xFFFFA726)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { showKillConfirm = true }
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${process.pid}",
            Modifier.width(52.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            process.user,
            Modifier.width(64.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "%.1f".format(process.cpuPercent),
            Modifier.width(44.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (process.cpuPercent > 10) FontWeight.Bold else FontWeight.Normal,
            color = cpuColor
        )
        Text(
            "%.1f".format(process.memPercent),
            Modifier.width(44.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (process.memPercent > 5) FontWeight.Bold else FontWeight.Normal,
            color = memColor
        )
        Text(
            process.rss,
            Modifier.width(52.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            process.command,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────

private fun formatBytesHuman(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
