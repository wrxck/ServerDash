package com.serverdash.app.presentation.screens.claudecode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*

@Composable
internal fun DetailHeader(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
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

// ── Storage Detail ─────────────────────────────────────────────────

@Composable
internal fun StorageDetailView(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    Column(Modifier.fillMaxSize()) {
        DetailHeader("Storage Breakdown", onBack = { viewModel.onEvent(ClaudeCodeEvent.CloseDetail) })

        if (state.isLoadingDetail) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.storageBreakdown) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (item.category) {
                                    "Total" -> Icons.Default.Storage
                                    "Projects & Sessions" -> Icons.Default.Folder
                                    "Plans" -> Icons.Default.Map
                                    "Hook Scripts" -> Icons.Default.Code
                                    "Custom Skills" -> Icons.Default.AutoAwesome
                                    "Plugins" -> Icons.Default.Extension
                                    "Usage Stats" -> Icons.Default.BarChart
                                    else -> Icons.Default.Description
                                },
                                null,
                                Modifier.size(24.dp),
                                tint = if (item.category == "Total") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.category, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    item.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                item.size,
                                style = if (item.category == "Total") MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Sessions Detail ────────────────────────────────────────────────

private val jsonFormatter = Json { prettyPrint = true; ignoreUnknownKeys = true }

@Composable
private fun syntaxHighlight(jsonStr: String): AnnotatedString {
    val keyColor = MaterialTheme.colorScheme.primary
    val stringColor = Color(0xFF66BB6A)
    val numberColor = Color(0xFFF0B866)
    val boolNullColor = Color(0xFFCBB2F0)
    val braceColor = MaterialTheme.colorScheme.onSurfaceVariant

    return remember(jsonStr) {
        buildAnnotatedString {
            var i = 0
            val s = jsonStr
            while (i < s.length) {
                when {
                    s[i] == '"' -> {
                        val end = s.indexOf('"', i + 1).let { if (it < 0) s.length else it + 1 }
                        val str = s.substring(i, end)
                        // Check if this is a key (followed by :)
                        val afterStr = s.substring(end).trimStart()
                        val color = if (afterStr.startsWith(":")) keyColor else stringColor
                        withStyle(SpanStyle(color = color)) { append(str) }
                        i = end
                    }
                    s[i].isDigit() || (s[i] == '-' && i + 1 < s.length && s[i + 1].isDigit()) -> {
                        val start = i
                        while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == '-' || s[i] == 'e' || s[i] == 'E' || s[i] == '+')) i++
                        withStyle(SpanStyle(color = numberColor)) { append(s.substring(start, i)) }
                    }
                    s.startsWith("true", i) -> { withStyle(SpanStyle(color = boolNullColor)) { append("true") }; i += 4 }
                    s.startsWith("false", i) -> { withStyle(SpanStyle(color = boolNullColor)) { append("false") }; i += 5 }
                    s.startsWith("null", i) -> { withStyle(SpanStyle(color = boolNullColor)) { append("null") }; i += 4 }
                    s[i] in "{}[]" -> { withStyle(SpanStyle(color = braceColor, fontWeight = FontWeight.Bold)) { append(s[i].toString()) }; i++ }
                    else -> { append(s[i].toString()); i++ }
                }
            }
        }
    }
}

@Composable
private fun getRoleFromLine(line: String): String? {
    return remember(line) {
        try {
            val obj = Json.parseToJsonElement(line).jsonObject
            obj["role"]?.jsonPrimitive?.content
                ?: obj["type"]?.jsonPrimitive?.content
        } catch (e: Exception) { null }
    }
}

@Composable
private fun roleColor(role: String?): Color {
    return when (role) {
        "user" -> Color(0xFF5CCFE6)
        "assistant" -> Color(0xFF66BB6A)
        "system" -> Color(0xFFF0B866)
        "tool_use", "tool_result", "tool" -> Color(0xFFCBB2F0)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionsDetailView(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    // Full session JSONL viewer
    if (state.selectedSessionName != null) {
        SessionJsonlViewer(state, viewModel)
        return
    }

    Column(Modifier.fillMaxSize()) {
        DetailHeader(
            "Sessions (${state.sessionsList.size})",
            onBack = { viewModel.onEvent(ClaudeCodeEvent.CloseDetail) }
        )

        if (state.isLoadingDetail) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.sessionsList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No sessions found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.sessionsList) { session ->
                    var showDeleteConfirm by remember { mutableStateOf(false) }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Delete Session") },
                            text = { Text("Remove this session transcript?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteConfirm = false
                                    viewModel.onEvent(ClaudeCodeEvent.DeleteSession(session))
                                }) { Text("Delete") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }

                    Card(
                        Modifier.fillMaxWidth().clickable {
                            viewModel.onEvent(ClaudeCodeEvent.ViewSession(session))
                        }
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Forum, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.projectDisplay,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${session.filename} - ${session.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                                if (session.modified.isNotBlank()) {
                                    Text(
                                        session.modified,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, "Delete", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionJsonlViewer(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    val roles = listOf(null, "user", "assistant", "system", "tool")
    val roleLabels = listOf("All", "User", "Assistant", "System", "Tool")

    // Filter and search the lines
    val filteredLines = remember(state.sessionLines, state.sessionSearchQuery, state.sessionFilterRole) {
        state.sessionLines.filter { line ->
            // Role filter
            val passRole = if (state.sessionFilterRole == null) true
            else {
                try {
                    val obj = Json.parseToJsonElement(line).jsonObject
                    val role = obj["role"]?.jsonPrimitive?.content
                        ?: obj["type"]?.jsonPrimitive?.content ?: ""
                    when (state.sessionFilterRole) {
                        "tool" -> role.startsWith("tool")
                        else -> role == state.sessionFilterRole
                    }
                } catch (e: Exception) { false }
            }
            // Search filter
            val passSearch = if (state.sessionSearchQuery.isBlank()) true
            else line.contains(state.sessionSearchQuery, ignoreCase = true)
            passRole && passSearch
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        DetailHeader(
            state.selectedSessionName ?: "Session",
            onBack = { viewModel.onEvent(ClaudeCodeEvent.DismissSessionContent) }
        ) {
            IconButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.ToggleSessionPrettyPrint) }) {
                Icon(
                    if (state.sessionPrettyPrint) Icons.Default.Code else Icons.Default.DataObject,
                    if (state.sessionPrettyPrint) "Raw" else "Pretty",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.isLoadingSession) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        // Search bar
        OutlinedTextField(
            value = state.sessionSearchQuery,
            onValueChange = { viewModel.onEvent(ClaudeCodeEvent.UpdateSessionSearch(it)) },
            placeholder = { Text("Search JSONL...") },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
            trailingIcon = {
                if (state.sessionSearchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.UpdateSessionSearch("")) }) {
                        Icon(Icons.Default.Clear, "Clear", Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )

        // Role filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            roles.forEachIndexed { index, role ->
                FilterChip(
                    selected = state.sessionFilterRole == role,
                    onClick = { viewModel.onEvent(ClaudeCodeEvent.FilterSessionRole(role)) },
                    label = { Text(roleLabels[index], style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (state.sessionFilterRole == role) {
                        { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                    } else null
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "${filteredLines.size} / ${state.sessionLines.size} entries",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }

        HorizontalDivider()

        // JSONL entries
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(filteredLines) { index, line ->
                JsonlEntryCard(
                    line = line,
                    index = index,
                    prettyPrint = state.sessionPrettyPrint,
                    searchQuery = state.sessionSearchQuery
                )
            }
        }
    }
}

@Composable
private fun JsonlEntryCard(
    line: String,
    index: Int,
    prettyPrint: Boolean,
    searchQuery: String
) {
    val role = getRoleFromLine(line)
    val color = roleColor(role)
    var expanded by remember { mutableStateOf(false) }

    val displayText = remember(line, prettyPrint) {
        if (prettyPrint) {
            try {
                val element = Json.parseToJsonElement(line)
                jsonFormatter.encodeToString(JsonElement.serializer(), element)
            } catch (e: Exception) { line }
        } else line
    }

    Card(
        Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(8.dp)) {
            // Role badge + index
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "#${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (role != null) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(color.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                role,
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "Toggle",
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            if (expanded) {
                // Full syntax-highlighted content
                val highlighted = syntaxHighlight(displayText)
                Text(
                    highlighted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            } else {
                // Preview: first line or truncated
                val preview = remember(line) {
                    try {
                        val obj = Json.parseToJsonElement(line).jsonObject
                        val content = obj["content"]
                        when {
                            content is JsonArray -> {
                                content.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content?.take(120)
                                    ?: content.toString().take(120)
                            }
                            content is JsonPrimitive -> content.content.take(120)
                            else -> {
                                val msg = obj["message"]?.jsonObject?.get("content")
                                msg?.toString()?.take(120) ?: line.take(120)
                            }
                        }
                    } catch (e: Exception) { line.take(120) }
                }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ── Plans Detail ───────────────────────────────────────────────────

@Composable
internal fun PlansDetailView(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    if (state.selectedPlanContent != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ClaudeCodeEvent.DismissPlanContent) },
            title = { Text(state.selectedPlanName ?: "Plan") },
            text = {
                Text(
                    state.selectedPlanContent,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.DismissPlanContent) }) { Text("Close") }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        DetailHeader(
            "Plans (${state.plansList.size})",
            onBack = { viewModel.onEvent(ClaudeCodeEvent.CloseDetail) }
        )

        if (state.isLoadingDetail) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.plansList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No plans found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.plansList) { plan ->
                    var showDeleteConfirm by remember { mutableStateOf(false) }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Delete Plan") },
                            text = { Text("Remove '${plan.name}'?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteConfirm = false
                                    viewModel.onEvent(ClaudeCodeEvent.DeletePlan(plan))
                                }) { Text("Delete") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }

                    Card(
                        Modifier.fillMaxWidth().clickable {
                            viewModel.onEvent(ClaudeCodeEvent.ViewPlan(plan))
                        }
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Map, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(plan.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${plan.path} (${plan.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, "Delete", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Plugins Detail ─────────────────────────────────────────────────

@Composable
internal fun PluginsDetailView(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    Column(Modifier.fillMaxSize()) {
        DetailHeader(
            "Installed Plugins (${state.installedPlugins.size})",
            onBack = { viewModel.onEvent(ClaudeCodeEvent.CloseDetail) }
        )

        if (state.installedPlugins.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Extension, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No plugins installed", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Install plugins via Claude Code CLI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.installedPlugins) { plugin ->
                    var showConfirm by remember { mutableStateOf(false) }

                    if (showConfirm) {
                        AlertDialog(
                            onDismissRequest = { showConfirm = false },
                            title = { Text("Uninstall Plugin") },
                            text = { Text("Remove '$plugin' from installed plugins?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showConfirm = false
                                    viewModel.onEvent(ClaudeCodeEvent.UninstallPlugin(plugin))
                                }) { Text("Uninstall") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Extension, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(plugin, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showConfirm = true }) {
                                Icon(Icons.Default.Delete, "Uninstall", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Hooks Detail (CRUD) ────────────────────────────────────────────

@Composable
internal fun HooksDetailView(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    // Edit/Add dialog
    if (state.editingHook != null) {
        ScriptEditorDialog(
            title = if (state.isAddingHook) "New Hook Script" else "Edit Hook",
            filename = state.editingHook.filename,
            content = state.editingHook.content,
            onDismiss = { viewModel.onEvent(ClaudeCodeEvent.DismissHookDialog) },
            onSave = { name, content ->
                viewModel.onEvent(ClaudeCodeEvent.SaveHook(
                    originalName = if (state.isAddingHook) null else state.editingHook.filename,
                    hook = HookScript(filename = name, content = content)
                ))
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        DetailHeader(
            "Hook Scripts (${state.hookScripts.size})",
            onBack = { viewModel.onEvent(ClaudeCodeEvent.CloseDetail) }
        ) {
            IconButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.ShowAddHook) }) {
                Icon(Icons.Default.Add, "Add Hook")
            }
        }

        if (state.isLoadingDetail) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.hookScripts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Code, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No hook scripts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.ShowAddHook) }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Create Hook")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.hookScripts) { hook ->
                    var showDeleteConfirm by remember { mutableStateOf(false) }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Delete Hook") },
                            text = { Text("Remove '${hook.filename}'?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteConfirm = false
                                    viewModel.onEvent(ClaudeCodeEvent.DeleteHook(hook))
                                }) { Text("Delete") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp).clickable {
                                viewModel.onEvent(ClaudeCodeEvent.EditHook(hook))
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Code, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                hook.filename,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.EditHook(hook)) }) {
                                Icon(Icons.Default.Edit, "Edit", Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, "Delete", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Skills Detail (CRUD) ───────────────────────────────────────────

@Composable
internal fun SkillsDetailView(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
    if (state.editingSkill != null) {
        ScriptEditorDialog(
            title = if (state.isAddingSkill) "New Custom Skill" else "Edit Skill",
            filename = state.editingSkill.filename,
            content = state.editingSkill.content,
            onDismiss = { viewModel.onEvent(ClaudeCodeEvent.DismissSkillDialog) },
            onSave = { name, content ->
                viewModel.onEvent(ClaudeCodeEvent.SaveSkill(
                    originalName = if (state.isAddingSkill) null else state.editingSkill.filename,
                    skill = SkillScript(filename = name, content = content)
                ))
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        DetailHeader(
            "Custom Skills (${state.skillScripts.size})",
            onBack = { viewModel.onEvent(ClaudeCodeEvent.CloseDetail) }
        ) {
            IconButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.ShowAddSkill) }) {
                Icon(Icons.Default.Add, "Add Skill")
            }
        }

        if (state.isLoadingDetail) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.skillScripts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No custom skills", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.ShowAddSkill) }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Create Skill")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.skillScripts) { skill ->
                    var showDeleteConfirm by remember { mutableStateOf(false) }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Delete Skill") },
                            text = { Text("Remove '${skill.filename}'?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteConfirm = false
                                    viewModel.onEvent(ClaudeCodeEvent.DeleteSkill(skill))
                                }) { Text("Delete") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp).clickable {
                                viewModel.onEvent(ClaudeCodeEvent.EditSkill(skill))
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                skill.filename,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.onEvent(ClaudeCodeEvent.EditSkill(skill)) }) {
                                Icon(Icons.Default.Edit, "Edit", Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, "Delete", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Shared Script Editor Dialog ────────────────────────────────────

@Composable
private fun ScriptEditorDialog(
    title: String,
    filename: String,
    content: String,
    onDismiss: () -> Unit,
    onSave: (name: String, content: String) -> Unit
) {
    var editName by remember { mutableStateOf(filename) }
    var editContent by remember { mutableStateOf(content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                OutlinedTextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    label = { Text("Content") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(editName.trim(), editContent) },
                enabled = editName.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Projects Detail (reuses existing tab but with detail header) ───

@Composable
internal fun ProjectsDetailView(state: ClaudeCodeUiState, viewModel: ClaudeCodeViewModel) {
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

    Column(Modifier.fillMaxSize()) {
        DetailHeader(
            "Projects (${state.projects.size})",
            onBack = { viewModel.onEvent(ClaudeCodeEvent.CloseDetail) }
        )

        if (state.isLoadingProjects) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.projects.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No projects found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.projects) { project ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            if (project.hasMemory) viewModel.onEvent(ClaudeCodeEvent.ViewProjectMemory(project))
                        }
                    ) {
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
