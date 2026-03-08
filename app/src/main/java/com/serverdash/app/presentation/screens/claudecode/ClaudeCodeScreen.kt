package com.serverdash.app.presentation.screens.claudecode

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
                ListItem(
                    headlineContent = { Text("Claude Code") },
                    supportingContent = { Text(state.claudeVersion) },
                    leadingContent = { Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary) }
                )

                if (state.claudeCodeUsers.size > 1) {
                    UserSelector(state, viewModel)
                }

                TabRow(selectedTabIndex = state.selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = state.selectedTab == index,
                            onClick = { viewModel.onEvent(ClaudeCodeEvent.SelectTab(index)) },
                            text = { Text(title) }
                        )
                    }
                }

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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    if (state.showDiffDialog) {
        DiffDialog(
            diffs = state.settingsDiff,
            onConfirm = { viewModel.onEvent(ClaudeCodeEvent.ConfirmSaveSettings) },
            onDismiss = { viewModel.onEvent(ClaudeCodeEvent.DismissDiffDialog) }
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (state.isLoadingSettings) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
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

@OptIn(ExperimentalMaterial3Api::class)
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

    fun updateKey(path: String, value: JsonElement?) {
        val current = try { Json.parseToJsonElement(state.settingsJson).jsonObject } catch (e: Exception) { return }
        val mutable = current.toMutableMap()
        val parts = path.split(".")
        if (parts.size == 1) {
            if (value == null) mutable.remove(parts[0]) else mutable[parts[0]] = value
        } else if (parts.size == 2) {
            val parentObj = (mutable[parts[0]] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            if (value == null) parentObj.remove(parts[1]) else parentObj[parts[1]] = value
            mutable[parts[0]] = JsonObject(parentObj)
        } else if (parts.size == 3) {
            val p1 = (mutable[parts[0]] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            val p2 = (p1[parts[1]] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            if (value == null) p2.remove(parts[2]) else p2[parts[2]] = value
            p1[parts[1]] = JsonObject(p2)
            mutable[parts[0]] = JsonObject(p1)
        }
        val newJson = jsonFormatter.encodeToString(JsonObject.serializer(), JsonObject(mutable))
        viewModel.onEvent(ClaudeCodeEvent.UpdateSettingsJson(newJson))
    }

    val onUpdate: (String, JsonElement?) -> Unit = { path, value -> updateKey(path, value) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = { viewModel.onEvent(ClaudeCodeEvent.RequestSaveSettings) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Review & Save")
            }
        }

        item { SettingsSectionHeader("Core") }
        item { StringSetting("Model", "model", settingsObj, onUpdate, placeholder = "default (not set)") }
        item { CcSegmentedSetting("Effort Level", "effortLevel", settingsObj, onUpdate, options = listOf("low", "medium", "high")) }
        item { CcSwitchSetting("Fast Mode", "fastMode", settingsObj, onUpdate) }
        item { CcSwitchSetting("Auto Memory", "autoMemoryEnabled", settingsObj, onUpdate, default = true) }
        item { IntSliderSetting("Cleanup Period (days)", "cleanupPeriodDays", settingsObj, onUpdate, range = 0..90, default = 0) }

        item { SettingsSectionHeader("Permissions") }
        item { CcSegmentedSetting("Default Mode", "permissions.defaultMode", settingsObj, onUpdate, options = listOf("default", "acceptEdits", "plan", "bypassPermissions")) }
        item { StringListSetting("Allow", "permissions.allow", settingsObj, onUpdate, placeholder = "e.g. Bash(git:*)") }
        item { StringListSetting("Ask", "permissions.ask", settingsObj, onUpdate, placeholder = "e.g. WebFetch") }
        item { StringListSetting("Deny", "permissions.deny", settingsObj, onUpdate, placeholder = "e.g. Bash(rm:*)") }
        item { StringListSetting("Additional Directories", "permissions.additionalDirectories", settingsObj, onUpdate, placeholder = "/path/to/dir") }

        item { SettingsSectionHeader("Hooks") }
        item { HooksViewer(settingsObj) }

        item { SettingsSectionHeader("Sandbox") }
        item { CcSwitchSetting("Enabled", "sandbox.enabled", settingsObj, onUpdate) }
        item { CcSwitchSetting("Auto-allow Bash if Sandboxed", "sandbox.autoAllowBashIfSandboxed", settingsObj, onUpdate, default = true) }
        item { StringListSetting("Allowed Domains", "sandbox.allowedDomains", settingsObj, onUpdate, placeholder = "example.com") }
        item { StringListSetting("Filesystem Allow Write", "sandbox.filesystem.allowWrite", settingsObj, onUpdate, placeholder = "/path") }
        item { StringListSetting("Filesystem Deny Write", "sandbox.filesystem.denyWrite", settingsObj, onUpdate, placeholder = "/path") }

        item { SettingsSectionHeader("Plugins") }
        item { PluginsMapSetting(settingsObj, onUpdate) }

        item { SettingsSectionHeader("UI / Display") }
        item { CcSegmentedSetting("Theme", "theme", settingsObj, onUpdate, options = listOf("dark", "light", "light-daltonized", "dark-daltonized")) }
        item { StringSetting("Language", "language", settingsObj, onUpdate, placeholder = "e.g. en") }
        item { StringSetting("Output Style", "outputStyle", settingsObj, onUpdate) }
        item { CcSwitchSetting("Verbose", "verbose", settingsObj, onUpdate) }
        item { CcSwitchSetting("Show Turn Duration", "showTurnDuration", settingsObj, onUpdate, default = true) }
        item { CcSwitchSetting("Terminal Progress Bar", "terminalProgressBarEnabled", settingsObj, onUpdate, default = true) }
        item { CcSwitchSetting("Spinner Tips", "spinnerTipsEnabled", settingsObj, onUpdate, default = true) }

        item { SettingsSectionHeader("Git / Attribution") }
        item { StringSetting("Commit Attribution", "attribution.commit", settingsObj, onUpdate) }
        item { StringSetting("PR Attribution", "attribution.pr", settingsObj, onUpdate) }
        item { CcSwitchSetting("Include Git Instructions", "includeGitInstructions", settingsObj, onUpdate, default = true) }

        item { SettingsSectionHeader("Environment") }
        item { CcEnvVarsCard(settingsObj, onUpdate) }

        item { SettingsSectionHeader("Advanced") }
        item { StringSetting("API Key Helper", "apiKeyHelper", settingsObj, onUpdate) }
        item { CcSegmentedSetting("Auto-updates Channel", "autoUpdatesChannel", settingsObj, onUpdate, options = listOf("stable", "latest")) }
        item { CcSwitchSetting("Skip WebFetch Preflight", "skipWebFetchPreflight", settingsObj, onUpdate) }

        item { Spacer(Modifier.height(16.dp)) }
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
