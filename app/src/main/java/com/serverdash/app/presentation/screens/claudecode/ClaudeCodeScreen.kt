package com.serverdash.app.presentation.screens.claudecode

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.core.util.MarkdownEditorView
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeCodeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToClaudeTerminal: () -> Unit = {},
    viewModel: ClaudeCodeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val tabs = listOf("Overview", "MCP Servers", "Settings", "CLAUDE.md", "Projects")

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
                title = {
                    Column {
                        Text("Claude Code")
                        if (state.claudeVersion.isNotBlank() && state.isDetected) {
                            Text(
                                state.claudeVersion,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.isDetected) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToClaudeTerminal,
                    icon = { Icon(Icons.Default.Terminal, "Terminal") },
                    text = { Text("Terminal") }
                )
            }
        }
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
                if (state.claudeCodeUsers.size > 1) {
                    UserSelector(state, viewModel)
                }

                ScrollableTabRow(selectedTabIndex = state.selectedTab, edgePadding = 0.dp) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = state.selectedTab == index,
                            onClick = { viewModel.onEvent(ClaudeCodeEvent.SelectTab(index)) },
                            text = { Text(title) }
                        )
                    }
                }

                // Detail views overlay the tab content
                val detail = state.activeDetail
                if (detail != null) {
                    when (detail) {
                        DetailView.STORAGE -> StorageDetailView(state, viewModel)
                        DetailView.PROJECTS -> ProjectsDetailView(state, viewModel)
                        DetailView.SESSIONS -> SessionsDetailView(state, viewModel)
                        DetailView.PLANS -> PlansDetailView(state, viewModel)
                        DetailView.PLUGINS -> PluginsDetailView(state, viewModel)
                        DetailView.HOOKS -> HooksDetailView(state, viewModel)
                        DetailView.SKILLS -> SkillsDetailView(state, viewModel)
                    }
                } else {
                    when (state.selectedTab) {
                        0 -> OverviewTab(state, viewModel)
                        1 -> McpServersTab(state, viewModel)
                        2 -> SettingsTab(state, viewModel)
                        3 -> ClaudeMdTab(state, viewModel)
                        4 -> ProjectsTab(state, viewModel)
                    }
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
            MarkdownEditorView(
                content = state.claudeMdContent,
                onContentChange = { viewModel.onEvent(ClaudeCodeEvent.UpdateClaudeMd(it)) },
                modifier = Modifier.fillMaxSize(),
                readOnly = !state.editingClaudeMd
            )
        }
    }
}

@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    )
}

@Composable
private fun OverviewSkeleton() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Storage card skeleton
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    ShimmerBox(Modifier.size(80.dp, 16.dp))
                    Spacer(Modifier.height(12.dp))
                    ShimmerBox(Modifier.size(120.dp, 28.dp))
                }
            }
        }
        // Stat cards skeleton
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ShimmerBox(Modifier.size(24.dp))
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(Modifier.size(40.dp, 24.dp))
                        Spacer(Modifier.height(4.dp))
                        ShimmerBox(Modifier.size(60.dp, 12.dp))
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ShimmerBox(Modifier.size(24.dp))
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(Modifier.size(40.dp, 24.dp))
                        Spacer(Modifier.height(4.dp))
                        ShimmerBox(Modifier.size(60.dp, 12.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ShimmerBox(Modifier.size(24.dp))
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(Modifier.size(40.dp, 24.dp))
                        Spacer(Modifier.height(4.dp))
                        ShimmerBox(Modifier.size(60.dp, 12.dp))
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ShimmerBox(Modifier.size(24.dp))
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(Modifier.size(40.dp, 24.dp))
                        Spacer(Modifier.height(4.dp))
                        ShimmerBox(Modifier.size(60.dp, 12.dp))
                    }
                }
            }
        }
        // Plugin/skill/hook card skeletons
        repeat(3) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        ShimmerBox(Modifier.size(120.dp, 16.dp))
                        Spacer(Modifier.height(12.dp))
                        ShimmerBox(Modifier.fillMaxWidth().height(12.dp))
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.7f).height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    if (state.isLoadingOverview) {
        OverviewSkeleton()
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Disk usage card - clickable
        item {
            Card(
                Modifier.fillMaxWidth().clickable {
                    viewModel.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.STORAGE))
                }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Storage", style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.diskUsage.isNotBlank()) state.diskUsage else "...", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(4.dp))
                        Text("total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Quick stats grid - all clickable
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewStatCard("Projects", "${state.projectCount}", Icons.Default.Folder, Modifier.weight(1f).clickable {
                    viewModel.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.PROJECTS))
                })
                OverviewStatCard("Sessions", "${state.sessionCount}", Icons.Default.Forum, Modifier.weight(1f).clickable {
                    viewModel.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.SESSIONS))
                })
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewStatCard("Plans", "${state.planCount}", Icons.Default.Map, Modifier.weight(1f).clickable {
                    viewModel.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.PLANS))
                })
                OverviewStatCard("Plugins", "${state.installedPlugins.size}", Icons.Default.Extension, Modifier.weight(1f).clickable {
                    viewModel.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.PLUGINS))
                })
            }
        }

        // Installed plugins list - clickable to detail
        if (state.installedPlugins.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth().clickable {
                        viewModel.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.PLUGINS))
                    }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Installed Plugins", style = MaterialTheme.typography.titleMedium)
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        state.installedPlugins.take(3).forEach { plugin ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Extension, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(plugin, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (state.installedPlugins.size > 3) {
                            Text(
                                "+${state.installedPlugins.size - 3} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Custom skills - clickable to CRUD
        item {
            Card(
                Modifier.fillMaxWidth().clickable {
                    viewModel.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.SKILLS))
                }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Custom Skills", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${state.customSkills.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.customSkills.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        state.customSkills.take(3).forEach { skill ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(8.dp))
                                Text(skill, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (state.customSkills.size > 3) {
                            Text(
                                "+${state.customSkills.size - 3} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else {
                        Text("Tap to create", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        // Hooks - clickable to CRUD
        item {
            Card(
                Modifier.fillMaxWidth().clickable {
                    viewModel.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.HOOKS))
                }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Hook Scripts", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${state.hookFiles.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.hookFiles.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        state.hookFiles.take(3).forEach { hook ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Code, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(hook, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                            }
                        }
                        if (state.hookFiles.size > 3) {
                            Text(
                                "+${state.hookFiles.size - 3} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else {
                        Text("Tap to create", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        // Usage stats
        if (state.usageStats.isNotBlank()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Usage Stats", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val statsText = try {
                            val statsObj = Json.parseToJsonElement(state.usageStats).jsonObject
                            buildString {
                                statsObj.entries.forEach { (k, v) ->
                                    appendLine("$k: ${v.jsonPrimitive.content}")
                                }
                            }.trimEnd()
                        } catch (e: Exception) { state.usageStats }
                        Text(statsText, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun OverviewStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProjectsTab(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    // Memory viewer dialog
    if (state.selectedProjectMemory != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ClaudeCodeEvent.DismissProjectMemory) },
            title = { Text(state.selectedProjectName ?: "Memory") },
            text = {
                Text(
                    state.selectedProjectMemory,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.DismissProjectMemory) }) { Text("Close") }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (state.isLoadingProjects) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else if (state.projects.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No projects found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.projects.size) { index ->
                    val project = state.projects[index]
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(project.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${project.sessionCount} session${if (project.sessionCount != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (project.hasMemory) {
                                IconButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.ViewProjectMemory(project)) }) {
                                    Icon(Icons.Default.Psychology, "View Memory", tint = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
