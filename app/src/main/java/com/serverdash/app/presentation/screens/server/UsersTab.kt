package com.serverdash.app.presentation.screens.server

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UsersTab(
    state: ServerUiState,
    onEvent: (ServerEvent) -> Unit,
) {
    if (state.isLoading && !state.usersLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Add user section
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Add User", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.newUsername,
                            onValueChange = { onEvent(ServerEvent.UpdateNewUsername(it)) },
                            label = { Text("Username") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                if (state.newUsername.isNotBlank()) {
                                    onEvent(ServerEvent.RequestAction(ServerAction.AddUser(state.newUsername)))
                                }
                            },
                            enabled = state.newUsername.isNotBlank(),
                        ) {
                            Icon(Icons.Default.PersonAdd, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
            }
        }

        item {
            Text(
                "${state.users.size} users",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // User list
        items(state.users, key = { it.username }) { user ->
            val isExpanded = state.expandedUser == user.username

            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    onEvent(ServerEvent.ToggleExpandUser(user.username))
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (user.hasSudo) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                            null,
                            tint = if (user.hasSudo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user.username, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                if (user.hasSudo) {
                                    Spacer(Modifier.width(4.dp))
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("sudo", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(20.dp),
                                    )
                                }
                            }
                            Text(
                                "UID: ${user.uid} | ${user.shell}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            "Expand",
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text("Home: ${user.homeDir}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Groups: ${user.groups.joinToString(", ").ifBlank { "none" }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (user.uid != 0) {
                                    if (user.hasSudo) {
                                        OutlinedButton(
                                            onClick = {
                                                onEvent(
                                                    ServerEvent.RequestAction(
                                                        ServerAction.ToggleSudo(user.username, grant = false),
                                                    ),
                                                )
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error,
                                            ),
                                        ) { Text("Revoke sudo") }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                onEvent(
                                                    ServerEvent.RequestAction(
                                                        ServerAction.ToggleSudo(user.username, grant = true),
                                                    ),
                                                )
                                            },
                                        ) { Text("Grant sudo") }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onEvent(ServerEvent.RequestAction(ServerAction.DeleteUser(user.username)))
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                    ) {
                                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
