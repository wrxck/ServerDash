package com.serverdash.ide.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.serverdash.ide.EditorViewModel
import com.serverdash.ide.MonacoBridge
import com.serverdash.ide.MonacoCommands
import com.serverdash.ide.MonacoEditorView

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    initialPath: String = "/",
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val bridge = remember { MonacoBridge() }
    val cursorPos by bridge.cursorPosition.collectAsState()
    val isEditorReady by bridge.isReady.collectAsState()
    var commands by remember { mutableStateOf<MonacoCommands?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialPath) { viewModel.navigateTo(initialPath) }
    LaunchedEffect(Unit) { bridge.onSaveRequested = { viewModel.saveCurrentFile() } }
    LaunchedEffect(Unit) {
        bridge.currentContent.collect { content ->
            if (content.isNotEmpty()) viewModel.updateContent(content)
        }
    }
    LaunchedEffect(state.activeFileIndex, isEditorReady) {
        if (isEditorReady) {
            state.activeFile?.let { file ->
                commands?.setContent(file.content, file.language)
                bridge.resetDirty()
            }
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.openFiles.isNotEmpty()) {
                TabBar(
                    files = state.openFiles,
                    activeIndex = state.activeFileIndex,
                    onSelect = { viewModel.selectFile(it) },
                    onClose = { viewModel.closeFile(it) },
                )
            }
            Row(modifier = Modifier.weight(1f)) {
                AnimatedVisibility(visible = state.isFileTreeVisible) {
                    Row {
                        FileTreePanel(
                            currentPath = state.currentPath,
                            files = state.directoryContents,
                            onFileClick = { file ->
                                if (file.isDirectory) viewModel.navigateTo(file.path)
                                else viewModel.openFile(file)
                            },
                            onNavigateUp = {
                                val parent = state.currentPath
                                    .substringBeforeLast('/').ifEmpty { "/" }
                                viewModel.navigateTo(parent)
                            },
                        )
                        VerticalDivider()
                    }
                }
                if (state.activeFile != null) {
                    MonacoEditorView(
                        bridge = bridge,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        config = state.config,
                        onWebViewReady = { commands = MonacoCommands(it) },
                    )
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Select a file to edit",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.activeFile?.let { file ->
                StatusBar(
                    line = cursorPos.first,
                    column = cursorPos.second,
                    language = file.language,
                    isDirty = file.isDirty,
                )
            }
        }
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
