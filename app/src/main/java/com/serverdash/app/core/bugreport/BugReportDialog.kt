package com.serverdash.app.core.bugreport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun BugReportDialog(
    bugReportManager: BugReportManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var userNotes by remember { mutableStateOf("") }
    var showLogPreview by remember { mutableStateOf(false) }
    val logs = remember { bugReportManager.collectLogs(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
        icon = { Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Report a Bug") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Send diagnostic logs to the developer. All sensitive data (IPs, passwords, keys) is automatically redacted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = userNotes,
                    onValueChange = { userNotes = it },
                    label = { Text("What happened? (optional)") },
                    placeholder = { Text("Describe the bug or steps to reproduce...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                TextButton(onClick = { showLogPreview = !showLogPreview }) {
                    Text(if (showLogPreview) "Hide Log Preview" else "Preview Logs")
                }

                if (showLogPreview) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                    ) {
                        Text(
                            logs,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())
                        )
                    }
                }

                Text(
                    "Logs are sent via email to matt@matthesketh.pro",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { bugReportManager.sendReport(context, logs, userNotes) }) {
                Text("Send Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
