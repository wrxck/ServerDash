package com.serverdash.app.presentation.screens.server

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.serverdash.app.core.theme.StatusGreen
import com.serverdash.app.core.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallTab(
    state: ServerUiState,
    onEvent: (ServerEvent) -> Unit,
) {
    if (state.isLoading && !state.firewallLoaded) {
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
        // Status card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.ufwState.isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Shield,
                            null,
                            tint = if (state.ufwState.isActive) StatusGreen else StatusRed,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "UFW: ${state.ufwState.status}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.weight(1f))
                        if (state.ufwState.isActive) {
                            FilledTonalButton(
                                onClick = { onEvent(ServerEvent.RequestAction(ServerAction.DisableFirewall)) },
                            ) { Text("Disable") }
                        } else {
                            Button(
                                onClick = { onEvent(ServerEvent.RequestAction(ServerAction.EnableFirewall)) },
                            ) { Text("Enable") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("In: ${state.ufwState.defaultIncoming}", style = MaterialTheme.typography.bodySmall)
                        Text("Out: ${state.ufwState.defaultOutgoing}", style = MaterialTheme.typography.bodySmall)
                        Text("Log: ${state.ufwState.logging}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Default policy controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val newPolicy = if (state.ufwState.defaultIncoming == "deny") "allow" else "deny"
                        onEvent(ServerEvent.RequestAction(ServerAction.SetDefaultPolicy("incoming", newPolicy)))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Default In: ${state.ufwState.defaultIncoming}") }
                OutlinedButton(
                    onClick = {
                        val newPolicy = if (state.ufwState.defaultOutgoing == "allow") "deny" else "allow"
                        onEvent(ServerEvent.RequestAction(ServerAction.SetDefaultPolicy("outgoing", newPolicy)))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Default Out: ${state.ufwState.defaultOutgoing}") }
            }
        }

        // Add rule section
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Add Rule", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.newRulePort,
                            onValueChange = { onEvent(ServerEvent.UpdateNewRulePort(it)) },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        // Protocol selector
                        var protoExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = protoExpanded,
                            onExpandedChange = { protoExpanded = it },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = state.newRuleProtocol,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Proto") },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                singleLine = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(protoExpanded) },
                            )
                            ExposedDropdownMenu(expanded = protoExpanded, onDismissRequest = { protoExpanded = false }) {
                                listOf("tcp", "udp", "any").forEach { proto ->
                                    DropdownMenuItem(
                                        text = { Text(proto) },
                                        onClick = {
                                            onEvent(ServerEvent.UpdateNewRuleProtocol(proto))
                                            protoExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Action selector
                        var actionExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = actionExpanded,
                            onExpandedChange = { actionExpanded = it },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = state.newRuleAction,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Action") },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                singleLine = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(actionExpanded) },
                            )
                            ExposedDropdownMenu(expanded = actionExpanded, onDismissRequest = { actionExpanded = false }) {
                                listOf("allow", "deny", "reject", "limit").forEach { action ->
                                    DropdownMenuItem(
                                        text = { Text(action) },
                                        onClick = {
                                            onEvent(ServerEvent.UpdateNewRuleAction(action))
                                            actionExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.newRuleFrom,
                            onValueChange = { onEvent(ServerEvent.UpdateNewRuleFrom(it)) },
                            label = { Text("From") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (state.newRulePort.isNotBlank()) {
                                onEvent(ServerEvent.RequestAction(ServerAction.AddUfwRule(state.newRulePort)))
                            }
                        },
                        enabled = state.newRulePort.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Rule")
                    }
                }
            }
        }

        // Rules list
        if (state.ufwRules.isNotEmpty()) {
            item {
                Text(
                    "Active Rules",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.ufwRules, key = { it.number }) { rule ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "#${rule.number}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.to, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${rule.action} from ${rule.from}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onEvent(ServerEvent.RequestAction(ServerAction.DeleteUfwRule(rule.number))) },
                        ) {
                            Icon(Icons.Default.Delete, "Delete rule", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Reset button
        item {
            OutlinedButton(
                onClick = { onEvent(ServerEvent.RequestAction(ServerAction.ResetFirewall)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset Firewall")
            }
        }

        // iptables viewer toggle
        item {
            TextButton(
                onClick = { onEvent(ServerEvent.ToggleIptablesView) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.showIptables) "Hide iptables" else "View iptables (read-only)")
            }
        }

        // iptables chains
        if (state.showIptables) {
            items(state.iptablesChains, key = { it.name }) { chain ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${chain.name} (policy: ${chain.policy})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (chain.rules.isEmpty()) {
                            Text(
                                "No rules",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        chain.rules.forEach { rule ->
                            Text(
                                "${rule.num}: ${rule.target} ${rule.protocol} ${rule.source} -> ${rule.destination} ${rule.extra}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}
