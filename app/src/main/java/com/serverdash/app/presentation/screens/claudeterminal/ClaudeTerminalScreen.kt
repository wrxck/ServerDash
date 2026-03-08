package com.serverdash.app.presentation.screens.claudeterminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
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
    isImmersive: Boolean = false,
    initialPrompt: String? = null,
    onShowOverlay: (() -> Unit)? = null,
    viewModel: ClaudeTerminalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when output changes
    val outputLines = remember(state.terminalOutput) {
        state.terminalOutput.lines()
    }
    LaunchedEffect(outputLines.size) {
        if (outputLines.isNotEmpty()) {
            listState.animateScrollToItem(outputLines.size - 1)
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

    // Project picker dialog
    if (state.showProjectPicker) {
        ProjectPickerDialog(
            projects = state.availableProjects,
            onDismiss = { viewModel.onEvent(ClaudeTerminalEvent.DismissProjectPicker) },
            onSelect = { path -> viewModel.onEvent(ClaudeTerminalEvent.NewSession(projectPath = path)) }
        )
    }

    // Session list dialog
    if (state.showSessionList) {
        SessionListDialog(
            sessions = state.tmuxSessions,
            isLoading = state.isLoadingSessions,
            onDismiss = { viewModel.onEvent(ClaudeTerminalEvent.DismissSessionList) },
            onAttach = { name -> viewModel.onEvent(ClaudeTerminalEvent.AttachSession(name)) },
            onKill = { name -> viewModel.onEvent(ClaudeTerminalEvent.KillSession(name)) },
            onRefresh = { viewModel.onEvent(ClaudeTerminalEvent.RefreshSessions) }
        )
    }

    @Composable
    fun TerminalContent(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF1A1B26))
        ) {
            if (!state.isConnected && !state.isConnecting) {
                // Not connected - show start screen
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            null,
                            Modifier.size(64.dp),
                            tint = Color(0xFF7AA2F7)
                        )
                        Text(
                            "Claude Code",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFFC0CAF5)
                        )
                        Text(
                            "Persistent tmux sessions on your server",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF565F89)
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { viewModel.onEvent(ClaudeTerminalEvent.ShowProjectPicker) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF292E42),
                                contentColor = Color(0xFF7AA2F7)
                            )
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("New Session")
                        }
                        if (state.tmuxSessions.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = { viewModel.onEvent(ClaudeTerminalEvent.ShowSessionList) },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF292E42),
                                    contentColor = Color(0xFF9ECE6A)
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Resume Session (${state.tmuxSessions.size})")
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.onEvent(ClaudeTerminalEvent.NewSession(projectPath = "")) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF565F89)
                            )
                        ) {
                            Text("Start without project")
                        }
                    }
                }
            } else {
                // Terminal output area
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        state = listState
                    ) {
                        items(outputLines) { line ->
                            TerminalLine(line)
                        }
                    }

                    if (state.isConnecting) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                            color = Color(0xFF7AA2F7)
                        )
                    }
                }

                // Input area
                TerminalInputBar(
                    onSendInput = { text ->
                        viewModel.onEvent(ClaudeTerminalEvent.SendInput(text))
                        viewModel.onEvent(ClaudeTerminalEvent.SendSpecialKey(SpecialKey.ENTER))
                    },
                    onSpecialKey = { key ->
                        viewModel.onEvent(ClaudeTerminalEvent.SendSpecialKey(key))
                    },
                    isConnected = state.isConnected
                )
            }
        }
    }

    if (isImmersive) {
        Box(Modifier.fillMaxSize()) {
            TerminalContent()
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Claude Code")
                            if (state.isConnected && state.currentSessionName.isNotBlank()) {
                                Text(
                                    state.currentSessionName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (state.projectPath.isNotBlank()) {
                                Text(
                                    state.projectPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                        if (state.isConnected) {
                            // Ctrl+C
                            IconButton(onClick = {
                                viewModel.onEvent(ClaudeTerminalEvent.SendSpecialKey(SpecialKey.CTRL_C))
                            }) {
                                Text("^C", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            // Detach (session stays alive)
                            IconButton(onClick = {
                                viewModel.onEvent(ClaudeTerminalEvent.Detach)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.CallSplit, "Detach")
                            }
                        } else {
                            // Session list
                            IconButton(onClick = {
                                viewModel.onEvent(ClaudeTerminalEvent.ShowSessionList)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.List, "Sessions")
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            TerminalContent(modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun TerminalLine(line: String) {
    val styledText = remember(line) { AnsiParser.parse(line) }
    if (styledText.text.isNotBlank()) {
        Text(
            styledText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun TerminalInputBar(
    onSendInput: (String) -> Unit,
    onSpecialKey: (SpecialKey) -> Unit,
    isConnected: Boolean
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF16161E))
    ) {
        // Quick key bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QuickKey("Tab") { onSpecialKey(SpecialKey.TAB) }
            QuickKey("Esc") { onSpecialKey(SpecialKey.ESCAPE) }
            QuickKey("^C") { onSpecialKey(SpecialKey.CTRL_C) }
            QuickKey("^D") { onSpecialKey(SpecialKey.CTRL_D) }
            QuickKey("^Z") { onSpecialKey(SpecialKey.CTRL_Z) }
            QuickKey("\u2191") { onSpecialKey(SpecialKey.ARROW_UP) }
            QuickKey("\u2193") { onSpecialKey(SpecialKey.ARROW_DOWN) }
            QuickKey("\u2190") { onSpecialKey(SpecialKey.ARROW_LEFT) }
            QuickKey("\u2192") { onSpecialKey(SpecialKey.ARROW_RIGHT) }
        }

        // Text input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                ">",
                color = Color(0xFF7AA2F7),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 6.dp)
            )
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            onSendInput(inputText)
                            inputText = ""
                            true
                        } else false
                    },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFFC0CAF5)
                ),
                cursorBrush = SolidColor(Color(0xFF7AA2F7)),
                singleLine = true,
                enabled = isConnected,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onSendInput(inputText)
                    inputText = ""
                }),
                decorationBox = { innerTextField ->
                    Box {
                        if (inputText.isEmpty()) {
                            Text(
                                if (isConnected) "Type here..." else "Not connected",
                                color = Color(0xFF565F89),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            IconButton(
                onClick = {
                    onSendInput(inputText)
                    inputText = ""
                },
                enabled = isConnected,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    "Send",
                    tint = if (isConnected) Color(0xFF7AA2F7) else Color(0xFF565F89),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickKey(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF292E42),
        contentColor = Color(0xFF7AA2F7),
        modifier = Modifier.height(28.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ProjectPickerDialog(
    projects: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var customPath by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Claude Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customPath,
                    onValueChange = { customPath = it },
                    label = { Text("Project path") },
                    placeholder = { Text("/home/user/project") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                )
                if (projects.isNotEmpty()) {
                    Text(
                        "Or select:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Card(Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                        LazyColumn(contentPadding = PaddingValues(4.dp)) {
                            items(projects) { path ->
                                Text(
                                    path,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(path) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                onClick = { onSelect(customPath) },
                enabled = customPath.isNotBlank()
            ) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SessionListDialog(
    sessions: List<TmuxSession>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAttach: (String) -> Unit,
    onKill: (String) -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sessions", modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Refresh", modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (sessions.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No active sessions",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sessions) { session ->
                        SessionCard(
                            session = session,
                            onAttach = { onAttach(session.name) },
                            onKill = { onKill(session.name) }
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
private fun SessionCard(
    session: TmuxSession,
    onAttach: () -> Unit,
    onKill: () -> Unit
) {
    var showKillConfirm by remember { mutableStateOf(false) }

    if (showKillConfirm) {
        AlertDialog(
            onDismissRequest = { showKillConfirm = false },
            title = { Text("Kill Session") },
            text = { Text("Kill '${session.name}'? Claude will stop running.") },
            confirmButton = {
                TextButton(onClick = { showKillConfirm = false; onKill() }) {
                    Text("Kill", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKillConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.name.removePrefix("sd-claude-"),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace
                )
                if (session.created.isNotBlank()) {
                    Text(
                        session.created,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (session.size.isNotBlank()) {
                    Text(
                        session.size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            FilledTonalButton(
                onClick = onAttach,
                modifier = Modifier.padding(start = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Attach", fontSize = 12.sp)
            }
            IconButton(
                onClick = { showKillConfirm = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    "Kill",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
