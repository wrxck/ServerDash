package com.serverdash.app.presentation.screens.claudeterminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeTerminalScreen(
    onNavigateBack: () -> Unit,
    viewModel: ClaudeTerminalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val activeSession = state.sessions.find { it.id == state.activeSessionId }
    val listState = rememberLazyListState()

    // Auto-scroll when new messages arrive
    LaunchedEffect(activeSession?.messages?.size) {
        val msgCount = activeSession?.messages?.size ?: 0
        if (msgCount > 0) {
            listState.animateScrollToItem(msgCount - 1)
        }
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(ClaudeTerminalEvent.DismissError)
        }
    }

    // New Session Dialog
    if (state.showNewSessionDialog) {
        NewSessionDialog(
            availableProjects = state.availableProjects,
            onDismiss = { viewModel.onEvent(ClaudeTerminalEvent.DismissNewSessionDialog) },
            onCreate = { name, path ->
                viewModel.onEvent(ClaudeTerminalEvent.CreateSession(name, path))
            }
        )
    }

    // Session Manager Overlay
    if (state.showSessionManager) {
        SessionManagerOverlay(
            sessions = state.sessions,
            activeSessionId = state.activeSessionId,
            onSelect = { viewModel.onEvent(ClaudeTerminalEvent.SwitchSession(it)) },
            onDelete = { viewModel.onEvent(ClaudeTerminalEvent.DeleteSession(it)) },
            onRename = { id, name -> viewModel.onEvent(ClaudeTerminalEvent.RenameSession(id, name)) },
            onDismiss = { viewModel.onEvent(ClaudeTerminalEvent.ToggleSessionManager) },
            onNewSession = { viewModel.onEvent(ClaudeTerminalEvent.ShowNewSessionDialog) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claude Terminal") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(ClaudeTerminalEvent.ToggleSessionManager) }) {
                        Icon(Icons.Default.GridView, "Session Manager")
                    }
                    IconButton(onClick = { viewModel.onEvent(ClaudeTerminalEvent.ShowNewSessionDialog) }) {
                        Icon(Icons.Default.Add, "New Session")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Session tab bar
            if (state.sessions.isNotEmpty()) {
                SessionTabBar(
                    sessions = state.sessions,
                    activeSessionId = state.activeSessionId,
                    onSelect = { viewModel.onEvent(ClaudeTerminalEvent.SwitchSession(it)) },
                    onNewSession = { viewModel.onEvent(ClaudeTerminalEvent.ShowNewSessionDialog) }
                )
                HorizontalDivider()
            }

            if (activeSession == null) {
                // No sessions - show empty state
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Terminal,
                            null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No active sessions",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Create a new session to start chatting with Claude",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = {
                            viewModel.onEvent(ClaudeTerminalEvent.ShowNewSessionDialog)
                        }) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("New Session")
                        }
                    }
                }
            } else {
                // Chat message area
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeSession.messages) { message ->
                        MessageBubble(message)
                    }
                    if (state.isProcessing) {
                        item {
                            ProcessingIndicator()
                        }
                    }
                }

                // Quick prompt suggestions
                QuickPromptBar(onSelect = { prompt ->
                    viewModel.onEvent(ClaudeTerminalEvent.SendMessage(prompt))
                })

                HorizontalDivider()

                // Input area
                MessageInputBar(
                    inputText = state.inputText,
                    isProcessing = state.isProcessing,
                    onInputChange = { viewModel.onEvent(ClaudeTerminalEvent.UpdateInput(it)) },
                    onSend = { viewModel.onEvent(ClaudeTerminalEvent.SendMessage(state.inputText)) }
                )
            }
        }
    }
}

@Composable
private fun SessionTabBar(
    sessions: List<ClaudeSession>,
    activeSessionId: String?,
    onSelect: (String) -> Unit,
    onNewSession: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sessions.forEach { session ->
            val isActive = session.id == activeSessionId
            FilterChip(
                selected = isActive,
                onClick = { onSelect(session.id) },
                label = {
                    Text(
                        session.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                    )
                }
            )
        }
        IconButton(
            onClick = onNewSession,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Add, "New Session", Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MessageBubble(message: ClaudeMessage) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"
    val isTool = message.role == "tool"

    val roleColor = when (message.role) {
        "user" -> Color(0xFF5CCFE6)
        "assistant" -> Color(0xFF66BB6A)
        "system" -> Color(0xFFF0B866)
        "tool" -> Color(0xFFCBB2F0)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Role badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(
                start = if (isUser) 48.dp else 0.dp,
                end = if (isUser) 0.dp else 48.dp
            )
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(roleColor)
            )
            Text(
                message.role.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = roleColor,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(2.dp))

        // Message content
        val bubbleColor = when {
            isUser -> MaterialTheme.colorScheme.primary
            isSystem -> MaterialTheme.colorScheme.surfaceVariant
            isTool -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
        val textColor = when {
            isUser -> MaterialTheme.colorScheme.onPrimary
            isSystem -> MaterialTheme.colorScheme.onSurfaceVariant
            isTool -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSecondaryContainer
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp).let {
                if (isUser) it.padding(start = 48.dp) else it.padding(end = 48.dp)
            }
        ) {
            Text(
                text = message.content,
                style = if (isSystem || isTool) {
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = textColor,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun ProcessingIndicator() {
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF66BB6A)
        )
        Text(
            "Claude is thinking...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickPromptBar(onSelect: (String) -> Unit) {
    val prompts = listOf(
        "Explain this project",
        "List files",
        "Find bugs",
        "Summarize changes",
        "Suggest improvements"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        prompts.forEach { prompt ->
            SuggestionChip(
                onClick = { onSelect(prompt) },
                label = { Text(prompt, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    inputText: String,
    isProcessing: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .imePadding(),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask Claude...") },
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            shape = RoundedCornerShape(24.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onSend,
            enabled = !isProcessing && inputText.isNotBlank(),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send")
        }
    }
}

@Composable
private fun NewSessionDialog(
    availableProjects: List<String>,
    onDismiss: () -> Unit,
    onCreate: (name: String, projectPath: String) -> Unit
) {
    var sessionName by remember { mutableStateOf("") }
    var projectPath by remember { mutableStateOf("") }
    var showProjectPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Claude Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = { sessionName = it },
                    label = { Text("Session Name") },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = projectPath,
                    onValueChange = { projectPath = it },
                    label = { Text("Project Path") },
                    placeholder = { Text("/home/user/project") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (availableProjects.isNotEmpty()) {
                            IconButton(onClick = { showProjectPicker = !showProjectPicker }) {
                                Icon(
                                    if (showProjectPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    "Pick project"
                                )
                            }
                        }
                    }
                )
                if (showProjectPicker && availableProjects.isNotEmpty()) {
                    Card(
                        Modifier.fillMaxWidth().heightIn(max = 200.dp)
                    ) {
                        LazyColumn(contentPadding = PaddingValues(4.dp)) {
                            items(availableProjects) { path ->
                                Text(
                                    path,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            projectPath = path
                                            showProjectPicker = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(sessionName, projectPath) },
                enabled = projectPath.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SessionManagerOverlay(
    sessions: List<ClaudeSession>,
    activeSessionId: String?,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onNewSession: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sessions")
                IconButton(onClick = onNewSession) {
                    Icon(Icons.Default.Add, "New Session")
                }
            }
        },
        text = {
            if (sessions.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No sessions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(sessions) { session ->
                        SessionManagerCard(
                            session = session,
                            isActive = session.id == activeSessionId,
                            onSelect = { onSelect(session.id) },
                            onDelete = { onDelete(session.id) },
                            onRename = { newName -> onRename(session.id, newName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun SessionManagerCard(
    session: ClaudeSession,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var showRenameField by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(session.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Session") },
            text = { Text("Delete '${session.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        Modifier.fillMaxWidth().clickable { onSelect() },
        colors = if (isActive) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showRenameField) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardActions = KeyboardActions(onDone = {
                            onRename(renameText)
                            showRenameField = false
                        })
                    )
                    IconButton(onClick = {
                        onRename(renameText)
                        showRenameField = false
                    }) {
                        Icon(Icons.Default.Check, "Save", Modifier.size(18.dp))
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            session.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            session.projectPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${session.messages.size} messages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        renameText = session.name
                        showRenameField = true
                    }) {
                        Icon(Icons.Default.Edit, "Rename", Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, "Delete", Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
