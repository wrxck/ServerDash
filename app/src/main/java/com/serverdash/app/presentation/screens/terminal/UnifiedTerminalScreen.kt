package com.serverdash.app.presentation.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.connectbot.terminal.Terminal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedTerminalScreen(
    onNavigateBack: () -> Unit,
    isImmersive: Boolean = false,
    isClaudeMode: Boolean = false,
    contextType: String = "",
    contextParams: String = "",
    viewModel: UnifiedTerminalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val activeSession = viewModel.getActiveSession()

    val terminalBackground = MaterialTheme.colorScheme.surface
    val terminalForeground = MaterialTheme.colorScheme.onSurface

    LaunchedEffect(terminalForeground, terminalBackground) {
        viewModel.setTerminalColors(
            foreground = terminalForeground,
            background = terminalBackground,
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(UnifiedTerminalEvent.DismissError)
        }
    }

    // auto-start with context if provided
    LaunchedEffect(contextType) {
        if (contextType.isNotBlank() && state.sessions.isEmpty() && !state.isConnecting) {
            viewModel.onEvent(UnifiedTerminalEvent.StartWithContext(contextType, contextParams))
        }
    }

    if (state.showProjectPicker) {
        ProjectPickerDialog(
            projects = state.availableProjects,
            onDismiss = { viewModel.onEvent(UnifiedTerminalEvent.DismissProjectPicker) },
            onSelect = { path ->
                viewModel.onEvent(UnifiedTerminalEvent.NewTmuxSession(projectPath = path))
            },
        )
    }

    if (state.showSessionList) {
        SessionListDialog(
            sessions = state.tmuxSessions,
            isLoading = state.isLoadingSessions,
            onDismiss = { viewModel.onEvent(UnifiedTerminalEvent.DismissSessionList) },
            onAttach = { name -> viewModel.onEvent(UnifiedTerminalEvent.AttachTmuxSession(name)) },
            onKill = { name -> viewModel.onEvent(UnifiedTerminalEvent.KillTmuxSession(name)) },
            onRefresh = { viewModel.onEvent(UnifiedTerminalEvent.RefreshSessions) },
        )
    }

    @Composable
    fun TerminalContent(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(terminalBackground),
        ) {
            if (state.sessions.isEmpty() && !state.isConnecting) {
                StartScreen(
                    isClaudeMode = isClaudeMode,
                    tmuxSessionCount = state.tmuxSessions.size,
                    onNewShell = { viewModel.onEvent(UnifiedTerminalEvent.NewShellSession) },
                    onNewClaude = { viewModel.onEvent(UnifiedTerminalEvent.ShowProjectPicker) },
                    onResumeSessions = { viewModel.onEvent(UnifiedTerminalEvent.ShowSessionList) },
                    onStartWithoutProject = {
                        viewModel.onEvent(UnifiedTerminalEvent.NewTmuxSession(projectPath = ""))
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                // tab bar
                if (state.sessions.isNotEmpty()) {
                    TerminalTabBar(
                        sessions = state.sessions,
                        activeIndex = state.activeSessionIndex,
                        onSelectSession = { viewModel.onEvent(UnifiedTerminalEvent.SelectSession(it)) },
                        onCloseSession = { viewModel.onEvent(UnifiedTerminalEvent.CloseSession(it)) },
                        onAddSession = {
                            if (isClaudeMode) {
                                viewModel.onEvent(UnifiedTerminalEvent.ShowProjectPicker)
                            } else {
                                viewModel.onEvent(UnifiedTerminalEvent.NewShellSession)
                            }
                        },
                    )
                }

                // terminal view
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (activeSession != null) {
                        Terminal(
                            terminalEmulator = activeSession.emulator,
                            modifier = Modifier.fillMaxSize(),
                            typeface = android.graphics.Typeface.MONOSPACE,
                            initialFontSize = 13.sp,
                            backgroundColor = terminalBackground,
                            foregroundColor = terminalForeground,
                            keyboardEnabled = true,
                            showSoftKeyboard = true,
                        )
                    }

                    if (state.isConnecting) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // quick key bar
                if (activeSession != null) {
                    QuickKeyBar(emulator = activeSession.emulator)
                }
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
                            Text(if (isClaudeMode) "Claude Code" else "Terminal")
                            val session = state.sessions.getOrNull(state.activeSessionIndex)
                            if (session != null) {
                                Text(
                                    session.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
                        val session = state.sessions.getOrNull(state.activeSessionIndex)
                        if (session != null && session.isTmux) {
                            IconButton(onClick = {
                                viewModel.onEvent(UnifiedTerminalEvent.DetachSession)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.CallSplit, "Detach")
                            }
                        }
                        if (state.sessions.isEmpty()) {
                            IconButton(onClick = {
                                viewModel.onEvent(UnifiedTerminalEvent.ShowSessionList)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.List, "Sessions")
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            TerminalContent(modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun StartScreen(
    isClaudeMode: Boolean,
    tmuxSessionCount: Int,
    onNewShell: () -> Unit,
    onNewClaude: () -> Unit,
    onResumeSessions: () -> Unit,
    onStartWithoutProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (isClaudeMode) Icons.Default.SmartToy else Icons.Default.Terminal,
                null,
                Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (isClaudeMode) "Claude Code" else "Terminal",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (isClaudeMode) "Persistent tmux sessions on your server"
                else "Interactive SSH shell",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (isClaudeMode) {
                FilledTonalButton(
                    onClick = onNewClaude,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New Session")
                }
                if (tmuxSessionCount > 0) {
                    FilledTonalButton(
                        onClick = onResumeSessions,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.tertiary,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Resume Session ($tmuxSessionCount)")
                    }
                }
                OutlinedButton(
                    onClick = onStartWithoutProject,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text("Start without project")
                }
            } else {
                FilledTonalButton(
                    onClick = onNewShell,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Default.Terminal, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Shell")
                }
            }
        }
    }
}
