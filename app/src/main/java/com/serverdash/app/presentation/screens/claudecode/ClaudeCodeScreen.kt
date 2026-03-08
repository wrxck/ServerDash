package com.serverdash.app.presentation.screens.claudecode

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
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeCodeScreen(
    onNavigateBack: () -> Unit,
    viewModel: ClaudeCodeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val tabs = listOf("MCP Servers", "Settings", "CLAUDE.md")

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(ClaudeCodeEvent.DismissError)
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(ClaudeCodeEvent.DismissSuccess)
        }
    }

    // MCP Server edit dialog
    if (state.editingMcpServer != null) {
        McpServerDialog(
            server = state.editingMcpServer!!,
            isNew = state.isAddingMcpServer,
            onDismiss = { viewModel.onEvent(ClaudeCodeEvent.DismissMcpDialog) },
            onSave = { updated ->
                viewModel.onEvent(ClaudeCodeEvent.SaveMcpServer(
                    original = if (state.isAddingMcpServer) null else state.editingMcpServer,
                    updated = updated
                ))
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claude Code") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.Refresh) }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (!state.isDetected) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CodeOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text("Claude Code not detected", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Install Claude Code on the server to manage it here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Version info
                ListItem(
                    headlineContent = { Text("Claude Code") },
                    supportingContent = { Text(state.claudeVersion) },
                    leadingContent = { Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary) }
                )

                // User selector (only shown when multiple Claude Code users detected)
                if (state.claudeCodeUsers.size > 1) {
                    UserSelector(state, viewModel)
                }

                // Tabs
                TabRow(selectedTabIndex = state.selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = state.selectedTab == index,
                            onClick = { viewModel.onEvent(ClaudeCodeEvent.SelectTab(index)) },
                            text = { Text(title) }
                        )
                    }
                }

                // Tab content
                when (state.selectedTab) {
                    0 -> McpServersTab(state, viewModel)
                    1 -> SettingsTab(state, viewModel)
                    2 -> ClaudeMdTab(state, viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserSelector(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = state.selectedUser?.let { "${it.username} (${it.homeDirectory})" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("User") },
            leadingIcon = {
                Icon(
                    if (state.selectedUser?.uid == 0) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                    null
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.claudeCodeUsers.forEach { user ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(user.username, style = MaterialTheme.typography.bodyLarge)
                            Text(user.homeDirectory, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        viewModel.onEvent(ClaudeCodeEvent.SelectUser(user))
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            if (user.uid == 0) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                            null
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun McpServersTab(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
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
private fun McpServerDialog(server: McpServer, isNew: Boolean, onDismiss: () -> Unit, onSave: (McpServer) -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    Column(Modifier.fillMaxSize()) {
        if (state.isLoadingSettings) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Toggle + action buttons row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.width(160.dp)) {
                    SegmentedButton(
                        selected = state.settingsViewMode == SettingsViewMode.UI,
                        onClick = { if (state.settingsViewMode != SettingsViewMode.UI) viewModel.onEvent(ClaudeCodeEvent.ToggleSettingsViewMode) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("UI") }
                    SegmentedButton(
                        selected = state.settingsViewMode == SettingsViewMode.JSON,
                        onClick = { if (state.settingsViewMode != SettingsViewMode.JSON) viewModel.onEvent(ClaudeCodeEvent.ToggleSettingsViewMode) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("JSON") }
                }

                if (state.settingsViewMode == SettingsViewMode.JSON) {
                    Row {
                        if (state.editingSettings) {
                            TextButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.CancelEditSettings) }) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.onEvent(ClaudeCodeEvent.SaveSettings) },
                                enabled = !state.isSavingSettings
                            ) {
                                if (state.isSavingSettings) CircularProgressIndicator(Modifier.size(16.dp))
                                else Text("Save")
                            }
                        } else {
                            OutlinedButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.StartEditSettings) }) {
                                Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit")
                            }
                        }
                    }
                }
            }

            when (state.settingsViewMode) {
                SettingsViewMode.UI -> SettingsUiView(state, viewModel)
                SettingsViewMode.JSON -> {
                    OutlinedTextField(
                        value = state.settingsJson,
                        onValueChange = { viewModel.onEvent(ClaudeCodeEvent.UpdateSettingsJson(it)) },
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        readOnly = !state.editingSettings
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsUiView(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    val jsonFormatter = remember { Json { prettyPrint = true; ignoreUnknownKeys = true } }

    val settingsObj = remember(state.settingsJson) {
        try { Json.parseToJsonElement(state.settingsJson).jsonObject } catch (e: Exception) { null }
    }

    if (settingsObj == null) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("Could not parse settings", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.ToggleSettingsViewMode) }) {
                    Text("Switch to JSON editor")
                }
            }
        }
        return
    }

    fun updateAndSave(transform: (MutableMap<String, JsonElement>) -> Unit) {
        val current = try { Json.parseToJsonElement(state.settingsJson).jsonObject } catch (e: Exception) { return }
        val mutable = current.toMutableMap()
        transform(mutable)
        val newJson = jsonFormatter.encodeToString(JsonObject.serializer(), JsonObject(mutable))
        viewModel.onEvent(ClaudeCodeEvent.UpdateSettingsJson(newJson))
        viewModel.onEvent(ClaudeCodeEvent.AutoSaveSettings)
    }

    // Parse fields
    val permissions = try { settingsObj["permissions"]?.jsonObject } catch (e: Exception) { null }
    val allowPerms = try { permissions?.get("allow")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList() } catch (e: Exception) { emptyList() }
    val denyPerms = try { permissions?.get("deny")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList() } catch (e: Exception) { emptyList() }
    val model = try { settingsObj["model"]?.jsonPrimitive?.content ?: "" } catch (e: Exception) { "" }
    val envVars = try {
        settingsObj["env"]?.jsonObject?.entries?.associate { (k, v) -> k to v.jsonPrimitive.content } ?: emptyMap()
    } catch (e: Exception) { emptyMap() }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Model
        item {
            var editModel by remember(model) { mutableStateOf(model) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Model", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editModel,
                        onValueChange = { editModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Default (not set)") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        trailingIcon = {
                            if (editModel != model) {
                                IconButton(onClick = {
                                    updateAndSave { root ->
                                        if (editModel.isBlank()) root.remove("model")
                                        else root["model"] = JsonPrimitive(editModel)
                                    }
                                }) {
                                    Icon(Icons.Default.Check, "Apply", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    )
                }
            }
        }

        // Allowed Permissions
        item {
            PermissionsCard(
                title = "Allowed Permissions",
                icon = Icons.Default.CheckCircle,
                permissions = allowPerms,
                onAdd = { pattern ->
                    updateAndSave { root ->
                        val permsObj = try { root["permissions"]?.jsonObject?.toMutableMap() ?: mutableMapOf() } catch (e: Exception) { mutableMapOf() }
                        val current = try { permsObj["allow"]?.jsonArray?.map { it.jsonPrimitive.content }?.toMutableList() ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
                        current.add(pattern)
                        permsObj["allow"] = JsonArray(current.map { JsonPrimitive(it) })
                        root["permissions"] = JsonObject(permsObj)
                    }
                },
                onRemove = { pattern ->
                    updateAndSave { root ->
                        val permsObj = try { root["permissions"]?.jsonObject?.toMutableMap() ?: mutableMapOf() } catch (e: Exception) { mutableMapOf() }
                        val current = try { permsObj["allow"]?.jsonArray?.map { it.jsonPrimitive.content }?.toMutableList() ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
                        current.remove(pattern)
                        permsObj["allow"] = JsonArray(current.map { JsonPrimitive(it) })
                        root["permissions"] = JsonObject(permsObj)
                    }
                }
            )
        }

        // Denied Permissions
        item {
            PermissionsCard(
                title = "Denied Permissions",
                icon = Icons.Default.Block,
                permissions = denyPerms,
                onAdd = { pattern ->
                    updateAndSave { root ->
                        val permsObj = try { root["permissions"]?.jsonObject?.toMutableMap() ?: mutableMapOf() } catch (e: Exception) { mutableMapOf() }
                        val current = try { permsObj["deny"]?.jsonArray?.map { it.jsonPrimitive.content }?.toMutableList() ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
                        current.add(pattern)
                        permsObj["deny"] = JsonArray(current.map { JsonPrimitive(it) })
                        root["permissions"] = JsonObject(permsObj)
                    }
                },
                onRemove = { pattern ->
                    updateAndSave { root ->
                        val permsObj = try { root["permissions"]?.jsonObject?.toMutableMap() ?: mutableMapOf() } catch (e: Exception) { mutableMapOf() }
                        val current = try { permsObj["deny"]?.jsonArray?.map { it.jsonPrimitive.content }?.toMutableList() ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
                        current.remove(pattern)
                        permsObj["deny"] = JsonArray(current.map { JsonPrimitive(it) })
                        root["permissions"] = JsonObject(permsObj)
                    }
                }
            )
        }

        // Environment Variables
        item {
            EnvVarsCard(
                envVars = envVars,
                onAdd = { key, value ->
                    updateAndSave { root ->
                        val currentEnv = try { root["env"]?.jsonObject?.toMutableMap() ?: mutableMapOf() } catch (e: Exception) { mutableMapOf() }
                        currentEnv[key] = JsonPrimitive(value)
                        root["env"] = JsonObject(currentEnv)
                    }
                },
                onRemove = { key ->
                    updateAndSave { root ->
                        val currentEnv = try { root["env"]?.jsonObject?.toMutableMap() ?: mutableMapOf() } catch (e: Exception) { mutableMapOf() }
                        currentEnv.remove(key)
                        if (currentEnv.isEmpty()) root.remove("env")
                        else root["env"] = JsonObject(currentEnv)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    permissions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var showAddField by remember { mutableStateOf(false) }
    var newPermission by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
                IconButton(onClick = { showAddField = !showAddField; newPermission = "" }) {
                    Icon(if (showAddField) Icons.Default.Close else Icons.Default.Add, "Add")
                }
            }

            if (showAddField) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newPermission,
                        onValueChange = { newPermission = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("e.g. Bash(git:*)") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (newPermission.isNotBlank()) {
                                onAdd(newPermission.trim())
                                newPermission = ""
                                showAddField = false
                            }
                        },
                        enabled = newPermission.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, "Add", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (permissions.isEmpty()) {
                Text(
                    "None configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    permissions.forEach { perm ->
                        InputChip(
                            selected = false,
                            onClick = { onRemove(perm) },
                            label = { Text(perm, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1) },
                            trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvVarsCard(
    envVars: Map<String, String>,
    onAdd: (String, String) -> Unit,
    onRemove: (String) -> Unit
) {
    var showAddFields by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Environment Variables", style = MaterialTheme.typography.titleSmall)
                }
                IconButton(onClick = { showAddFields = !showAddFields; newKey = ""; newValue = "" }) {
                    Icon(if (showAddFields) Icons.Default.Close else Icons.Default.Add, "Add")
                }
            }

            if (showAddFields) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("KEY") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("value") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (newKey.isNotBlank()) {
                                onAdd(newKey.trim(), newValue.trim())
                                newKey = ""
                                newValue = ""
                                showAddFields = false
                            }
                        },
                        enabled = newKey.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, "Add", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (envVars.isEmpty()) {
                Text(
                    "None configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                envVars.entries.forEachIndexed { index, (key, value) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(key, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemove(key) }) {
                            Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (index < envVars.size - 1) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ClaudeMdTab(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    Column(Modifier.fillMaxSize()) {
        if (state.isLoadingClaudeMd) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                if (state.editingClaudeMd) {
                    TextButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.CancelEditClaudeMd) }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.onEvent(ClaudeCodeEvent.SaveClaudeMd) },
                        enabled = !state.isSavingClaudeMd
                    ) {
                        if (state.isSavingClaudeMd) CircularProgressIndicator(Modifier.size(16.dp))
                        else Text("Save")
                    }
                } else {
                    OutlinedButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.StartEditClaudeMd) }) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                }
            }
            OutlinedTextField(
                value = state.claudeMdContent,
                onValueChange = { viewModel.onEvent(ClaudeCodeEvent.UpdateClaudeMd(it)) },
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                readOnly = !state.editingClaudeMd,
                placeholder = { Text("# CLAUDE.md\n\nAdd project instructions here...") }
            )
        }
    }
}
