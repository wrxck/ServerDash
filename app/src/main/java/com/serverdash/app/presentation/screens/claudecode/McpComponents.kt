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

@Composable
internal fun McpServersTab(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    Box(Modifier.fillMaxSize()) {
        if (state.isLoadingMcp) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.mcpServers.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No MCP servers configured", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(state.mcpServers) { server ->
                    McpServerCard(
                        server = server,
                        onEdit = { viewModel.onEvent(ClaudeCodeEvent.EditMcpServer(server)) },
                        onDelete = { viewModel.onEvent(ClaudeCodeEvent.DeleteMcpServer(server)) }
                    )
                }
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

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Hub, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleSmall)
                    Text(server.command, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (server.source.isNotBlank()) {
                        Text(server.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
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
                OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text("Command") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = args, onValueChange = { args = it }, label = { Text("Arguments (one per line)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = env, onValueChange = { env = it }, label = { Text("Environment (KEY=VALUE, one per line)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
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
