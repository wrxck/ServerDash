package com.serverdash.app.presentation.screens.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.core.privacy.redact
import com.serverdash.app.core.theme.*
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.usecase.GetServiceLogsUseCase
import com.serverdash.app.domain.usecase.ServiceAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailScreen(
    onNavigateBack: () -> Unit,
    onDebugWithClaude: ((String, String) -> Unit)? = null,
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
                    onAction = { viewModel.onEvent(ServiceDetailEvent.ControlService(it)) },
                    onDebugWithClaude = onDebugWithClaude
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
    onAction: (ServiceAction) -> Unit,
    onDebugWithClaude: ((String, String) -> Unit)? = null
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
                if (service.status == ServiceStatus.FAILED && onDebugWithClaude != null) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { onDebugWithClaude(service.name, service.type.name) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Icon(Icons.Default.SmartToy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Debug with Claude")
                    }
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
    var searchQuery by remember { mutableStateOf("") }
    var filterLevel by remember { mutableStateOf<String?>(null) }
    var jsonPrettyPrint by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val filteredLogs = remember(state.logs, searchQuery, filterLevel) {
        state.logs.filter { log ->
            val matchesSearch = searchQuery.isBlank() || log.message.contains(searchQuery, ignoreCase = true)
            val matchesLevel = filterLevel == null || detectLogLevel(log.message) == filterLevel
            matchesSearch && matchesLevel
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            placeholder = { Text("Search logs...") },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )

        // Filter chips and controls
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onEvent(ServiceDetailEvent.RefreshLogs) },
                enabled = !state.isLoadingLogs,
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Refresh")
            }

            // Log scope dropdown (only for systemd)
            if (state.service?.type == ServiceType.SYSTEMD) {
                var scopeExpanded by remember { mutableStateOf(false) }
                val scopeLabels = mapOf(
                    GetServiceLogsUseCase.LogScope.SERVICE_ONLY to "Service",
                    GetServiceLogsUseCase.LogScope.ALL_SYSTEM to "All System",
                    GetServiceLogsUseCase.LogScope.KERNEL to "Kernel",
                    GetServiceLogsUseCase.LogScope.USER_UNIT to "User Unit"
                )
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { scopeExpanded = true },
                        label = { Text(scopeLabels[state.logScope] ?: "Service", fontSize = 11.sp) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) }
                    )
                    DropdownMenu(expanded = scopeExpanded, onDismissRequest = { scopeExpanded = false }) {
                        scopeLabels.forEach { (scope, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onEvent(ServiceDetailEvent.ChangeLogScope(scope))
                                    scopeExpanded = false
                                },
                                leadingIcon = if (scope == state.logScope) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            FilterChip(
                selected = filterLevel == null,
                onClick = { filterLevel = null },
                label = { Text("All", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filterLevel == "ERROR",
                onClick = { filterLevel = if (filterLevel == "ERROR") null else "ERROR" },
                label = { Text("Error", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x33EF5350))
            )
            FilterChip(
                selected = filterLevel == "WARN",
                onClick = { filterLevel = if (filterLevel == "WARN") null else "WARN" },
                label = { Text("Warn", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x33FF9800))
            )
            FilterChip(
                selected = filterLevel == "INFO",
                onClick = { filterLevel = if (filterLevel == "INFO") null else "INFO" },
                label = { Text("Info", fontSize = 11.sp) }
            )

            Spacer(Modifier.width(8.dp))

            FilterChip(
                selected = jsonPrettyPrint,
                onClick = { jsonPrettyPrint = !jsonPrettyPrint },
                label = { Text("JSON", fontSize = 11.sp) },
                leadingIcon = if (jsonPrettyPrint) {
                    { Icon(Icons.Default.DataObject, null, Modifier.size(14.dp)) }
                } else null
            )

            Spacer(Modifier.width(8.dp))

            Text(
                "${filteredLogs.size}/${state.logs.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(4.dp))

        if (state.isLoadingLogs) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredLogs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.logs.isEmpty()) "No logs available" else "No logs match filter",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                state = listState
            ) {
                items(filteredLogs.size) { index ->
                    LogLine(log = filteredLogs[index], searchQuery = searchQuery, jsonPrettyPrint = jsonPrettyPrint)
                }
            }
        }
    }
}

@Composable
private fun LogLine(log: ServiceLog, searchQuery: String, jsonPrettyPrint: Boolean) {
    val message = redact(log.message)
    val logLevel = detectLogLevel(message)

    // Check if message contains JSON
    val jsonContent = if (jsonPrettyPrint) extractJson(message) else null

    Column(modifier = Modifier.padding(vertical = 1.dp)) {
        if (log.timestamp.isNotBlank()) {
            Text(
                log.timestamp,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF78909C)
            )
        }
        if (jsonContent != null) {
            // Render non-JSON prefix if any
            val jsonStart = message.indexOf(jsonContent.first)
            if (jsonStart > 0) {
                Text(
                    highlightSearch(colorizeLogLine(message.substring(0, jsonStart), logLevel), searchQuery),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            // Render pretty-printed JSON
            Text(
                highlightSearch(colorizeJson(jsonContent.second), searchQuery),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            Text(
                highlightSearch(colorizeLogLine(message, logLevel), searchQuery),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun detectLogLevel(message: String): String {
    val upper = message.uppercase()
    return when {
        upper.contains("ERROR") || upper.contains("FATAL") || upper.contains("CRIT") || upper.contains("EMERG") -> "ERROR"
        upper.contains("WARN") || upper.contains("WARNING") -> "WARN"
        upper.contains("DEBUG") || upper.contains("TRACE") -> "DEBUG"
        else -> "INFO"
    }
}

private fun colorizeLogLine(text: String, level: String): AnnotatedString = buildAnnotatedString {
    val color = when (level) {
        "ERROR" -> Color(0xFFEF5350)
        "WARN" -> Color(0xFFFF9800)
        "DEBUG" -> Color(0xFF78909C)
        else -> Color.Unspecified
    }
    if (color != Color.Unspecified) {
        withStyle(SpanStyle(color = color)) { append(text) }
    } else {
        append(text)
    }
}

private fun extractJson(message: String): Pair<String, String>? {
    // Find first { or [ that could start JSON
    val braceIdx = message.indexOf('{')
    val bracketIdx = message.indexOf('[')
    val startIdx = when {
        braceIdx >= 0 && bracketIdx >= 0 -> minOf(braceIdx, bracketIdx)
        braceIdx >= 0 -> braceIdx
        bracketIdx >= 0 -> bracketIdx
        else -> return null
    }
    val jsonCandidate = message.substring(startIdx)
    return try {
        val element = Json.parseToJsonElement(jsonCandidate)
        // Only pretty-print objects/arrays, not primitives
        if (element is JsonObject || element is JsonArray) {
            jsonCandidate to prettyPrintJson(element)
        } else null
    } catch (_: Exception) {
        null
    }
}

private fun prettyPrintJson(element: JsonElement, indent: Int = 0): String {
    val pad = "  ".repeat(indent)
    val padInner = "  ".repeat(indent + 1)
    return when (element) {
        is JsonObject -> {
            if (element.isEmpty()) "{}"
            else element.entries.joinToString(",\n", "{\n", "\n$pad}") { (key, value) ->
                "$padInner\"$key\": ${prettyPrintJson(value, indent + 1)}"
            }
        }
        is JsonArray -> {
            if (element.isEmpty()) "[]"
            else element.joinToString(",\n", "[\n", "\n$pad]") { "$padInner${prettyPrintJson(it, indent + 1)}" }
        }
        is JsonPrimitive -> element.toString()
        is JsonNull -> "null"
    }
}

private fun colorizeJson(json: String): AnnotatedString = buildAnnotatedString {
    val keyColor = Color(0xFF82B1FF)
    val stringColor = Color(0xFFC3E88D)
    val numberColor = Color(0xFFF78C6C)
    val boolNullColor = Color(0xFFFF5370)
    val braceColor = Color(0xFF89DDFF)

    var i = 0
    while (i < json.length) {
        when {
            json[i] == '"' -> {
                // Find end of string
                val end = findStringEnd(json, i)
                val str = json.substring(i, end + 1)
                // Check if it's a key (followed by :)
                val afterStr = json.substring(end + 1).trimStart()
                val color = if (afterStr.startsWith(":")) keyColor else stringColor
                withStyle(SpanStyle(color = color)) { append(str) }
                i = end + 1
            }
            json[i].isDigit() || (json[i] == '-' && i + 1 < json.length && json[i + 1].isDigit()) -> {
                val start = i
                while (i < json.length && (json[i].isDigit() || json[i] == '.' || json[i] == '-' || json[i] == 'e' || json[i] == 'E' || json[i] == '+')) i++
                withStyle(SpanStyle(color = numberColor)) { append(json.substring(start, i)) }
            }
            json.startsWith("true", i) -> {
                withStyle(SpanStyle(color = boolNullColor)) { append("true") }; i += 4
            }
            json.startsWith("false", i) -> {
                withStyle(SpanStyle(color = boolNullColor)) { append("false") }; i += 5
            }
            json.startsWith("null", i) -> {
                withStyle(SpanStyle(color = boolNullColor)) { append("null") }; i += 4
            }
            json[i] in "{}[]" -> {
                withStyle(SpanStyle(color = braceColor, fontWeight = FontWeight.Bold)) { append(json[i].toString()) }; i++
            }
            else -> { append(json[i].toString()); i++ }
        }
    }
}

private fun findStringEnd(json: String, startQuote: Int): Int {
    var i = startQuote + 1
    while (i < json.length) {
        if (json[i] == '\\') { i += 2; continue }
        if (json[i] == '"') return i
        i++
    }
    return json.length - 1
}

private fun highlightSearch(text: AnnotatedString, query: String): AnnotatedString {
    if (query.isBlank()) return text
    return buildAnnotatedString {
        append(text)
        val plainText = text.text
        var searchFrom = 0
        val lowerPlain = plainText.lowercase()
        val lowerQuery = query.lowercase()
        while (searchFrom < plainText.length) {
            val idx = lowerPlain.indexOf(lowerQuery, searchFrom)
            if (idx < 0) break
            addStyle(SpanStyle(background = Color(0x66FFEB3B), fontWeight = FontWeight.Bold), idx, idx + query.length)
            searchFrom = idx + query.length
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
