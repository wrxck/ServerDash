package com.serverdash.app.presentation.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.core.privacy.LocalPrivacyFilter
import com.serverdash.app.core.privacy.redactWith
import com.serverdash.app.core.util.highlightTerminalOutput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) {
            listState.animateScrollToItem(state.entries.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(TerminalEvent.ClearScreen) }) {
                        Icon(Icons.Default.ClearAll, "Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(Color(0xFF1E1E1E))
        ) {
            // Quick command buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QuickCommandButton("uptime") { viewModel.onEvent(TerminalEvent.QuickCommand("uptime")) }
                QuickCommandButton("df -h") { viewModel.onEvent(TerminalEvent.QuickCommand("df -h")) }
                QuickCommandButton("free -h") { viewModel.onEvent(TerminalEvent.QuickCommand("free -h")) }
                QuickCommandButton("top -bn1") { viewModel.onEvent(TerminalEvent.QuickCommand("top -bn1 | head -20")) }
            }

            // Output area
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                state = listState
            ) {
                items(state.entries) { entry ->
                    Text(
                        "$ ${entry.command}",
                        color = Color(0xFF4CAF50),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    val privacyFilter = LocalPrivacyFilter.current
                    val filteredOutput = remember(entry.output) { redactWith(privacyFilter, entry.output) }
                    if (entry.exitCode != 0) {
                        Text(
                            filteredOutput,
                            color = Color(0xFFEF5350),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    } else {
                        val highlighted = remember(filteredOutput) {
                            highlightTerminalOutput(filteredOutput)
                        }
                        Text(
                            highlighted,
                            color = Color(0xFFE0E0E0),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
                if (state.isExecuting) {
                    item {
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF4CAF50)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Executing...", color = Color(0xFF9E9E9E), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Input area
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$", color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(end = 4.dp))
                OutlinedTextField(
                    value = state.currentCommand,
                    onValueChange = { viewModel.onEvent(TerminalEvent.UpdateCommand(it)) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.onEvent(TerminalEvent.Execute) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color(0xFF424242),
                        cursorColor = Color(0xFF4CAF50)
                    ),
                    placeholder = { Text("Enter command...", color = Color(0xFF616161)) }
                )
                IconButton(
                    onClick = { viewModel.onEvent(TerminalEvent.Execute) },
                    enabled = !state.isExecuting && state.currentCommand.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Execute", tint = Color(0xFF4CAF50))
                }
            }
        }
    }
}

@Composable
private fun QuickCommandButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(28.dp)
    ) {
        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
