package com.serverdash.app.presentation.screens.claudecode

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.serverdash.app.core.util.CodeEditorField

@Composable
internal fun McpServersTab(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    // ~/.mcp.json migration dialog
    if (state.showMcpMigrationDialog) {
        McpMigrationDialog(
            user = state.mcpMigrationUser,
            servers = state.mcpMigrationServers,
            isMigrating = state.isMigrating,
            onConfirm = { viewModel.onEvent(ClaudeCodeEvent.ConfirmMcpMigration) },
            onDismiss = { viewModel.onEvent(ClaudeCodeEvent.DismissMcpMigration) }
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show progress bar at top while scanning
            if (state.isLoadingMcp) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.mcpScanProgress.ifBlank { "Scanning for MCP servers…" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Show empty state only when NOT loading and no servers found
            if (!state.isLoadingMcp && state.mcpServers.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No MCP servers configured", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.mcpDebugInfo.isNotBlank()) {
                            var showDebug by remember { mutableStateOf(false) }
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { showDebug = !showDebug }) {
                                Icon(
                                    if (showDebug) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null,
                                    Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Debug Info", style = MaterialTheme.typography.labelSmall)
                            }
                            if (showDebug) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    state.mcpDebugInfo,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Show servers as they appear (even while still scanning)
            items(state.mcpServers) { server ->
                McpServerCard(
                    server = server,
                    onEdit = { viewModel.onEvent(ClaudeCodeEvent.EditMcpServer(server)) },
                    onDelete = { viewModel.onEvent(ClaudeCodeEvent.DeleteMcpServer(server)) }
                )
            }
        }

        FloatingActionButton(
            onClick = { viewModel.onEvent(ClaudeCodeEvent.ShowAddMcpServer) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "Add MCP Server")
        }
    }
}

@Composable
private fun McpServerCard(server: McpServer, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete MCP Server") },
            text = { Text("Remove '${server.name}' from Claude Code configuration?") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = if (!server.enabled) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (server.type == "stdio") Icons.Default.Hub else Icons.Default.Cloud,
                    null,
                    tint = if (server.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(server.name, style = MaterialTheme.typography.titleSmall)
                        if (!server.enabled) {
                            Spacer(Modifier.width(8.dp))
                            Text("disabled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Show command for stdio, url for http/sse
                    val detail = when (server.type) {
                        "http", "sse" -> server.url
                        else -> server.command
                    }
                    if (detail.isNotBlank()) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        if (server.type != "stdio") {
                            Text(server.type.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            if (server.source.isNotBlank()) Spacer(Modifier.width(8.dp))
                        }
                        if (server.source.isNotBlank()) {
                            Text(server.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
            }
            if (server.args.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Args: ${server.args.joinToString(" ")}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (server.env.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Env: ${server.env.entries.joinToString(", ") { "${it.key}=${it.value}" }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (server.headers.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Headers: ${server.headers.size} configured", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun McpServerDialog(server: McpServer, isNew: Boolean, onDismiss: () -> Unit, onSave: (McpServer) -> Unit) {
    var name by remember { mutableStateOf(server.name) }
    var command by remember { mutableStateOf(server.command) }
    var args by remember { mutableStateOf(server.args.joinToString("\n")) }
    var env by remember { mutableStateOf(server.env.entries.joinToString("\n") { "${it.key}=${it.value}" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add MCP Server" else "Edit MCP Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text("Command") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                CodeEditorField(
                    content = args,
                    onContentChange = { args = it },
                    language = "sh",
                    label = "Arguments (one per line)",
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    showLineNumbers = false
                )
                CodeEditorField(
                    content = env,
                    onContentChange = { env = it },
                    language = "sh",
                    label = "Environment (KEY=VALUE, one per line)",
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    showLineNumbers = false
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(McpServer(
                        name = name.trim(),
                        command = command.trim(),
                        args = args.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        env = env.lines().map { it.trim() }.filter { it.contains("=") }.associate {
                            val (k, v) = it.split("=", limit = 2)
                            k.trim() to v.trim()
                        }
                    ))
                },
                enabled = name.isNotBlank() && command.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun McpMigrationDialog(
    user: String,
    servers: List<McpServer>,
    isMigrating: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isMigrating) onDismiss() },
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text("Non-standard MCP Config Found") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Found ${servers.size} MCP server(s) in ~/$user/.mcp.json",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Per the official Claude Code documentation, MCP servers should be configured in ~/.claude.json, not ~/.mcp.json in the home directory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Docs: code.claude.com/docs/en/mcp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Servers to migrate:",
                    style = MaterialTheme.typography.labelMedium
                )
                servers.forEach { server ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Hub, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(server.name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "This will merge servers into ~/.claude.json and remove ~/.mcp.json.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isMigrating
            ) {
                if (isMigrating) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Migrate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isMigrating) { Text("Ignore") }
        }
    )
}
