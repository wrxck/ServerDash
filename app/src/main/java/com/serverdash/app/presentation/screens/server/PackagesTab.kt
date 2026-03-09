package com.serverdash.app.presentation.screens.server

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PackagesTab(
    state: ServerUiState,
    onEvent: (ServerEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = state.packageSearchQuery,
            onValueChange = { onEvent(ServerEvent.UpdatePackageSearch(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search packages...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.packageSearchQuery.isNotBlank()) {
                    IconButton(onClick = { onEvent(ServerEvent.ClearSearch) }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onEvent(ServerEvent.SearchPackages) }),
        )

        // Quick actions row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { onEvent(ServerEvent.RequestAction(ServerAction.AptUpdate)) },
                enabled = !state.aptOperationRunning,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Update")
            }
            FilledTonalButton(
                onClick = { onEvent(ServerEvent.RequestAction(ServerAction.AptUpgrade)) },
                enabled = !state.aptOperationRunning,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.SystemUpdateAlt, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Upgrade")
            }
        }

        // Apt operation output
        if (state.aptOperationRunning || state.aptOutputLines.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onEvent(ServerEvent.ToggleAptOutput) },
                    ) {
                        if (state.aptOperationRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Operation in progress...", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Operation complete", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(
                            if (state.aptOutputExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            "Toggle output",
                        )
                    }
                    AnimatedVisibility(visible = state.aptOutputExpanded) {
                        Text(
                            state.aptOutputLines.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        // Upgradeable packages badge
        if (state.upgradeablePackages.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${state.upgradeablePackages.size} packages upgradeable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        // Content
        if (state.isLoading && !state.packagesLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.isSearchingPackages) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.searchResults.isNotEmpty()) {
            // Search results
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        "${state.searchResults.size} results",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.searchResults, key = { it.name }) { pkg ->
                    PackageItem(
                        pkg = pkg,
                        onInstall = { onEvent(ServerEvent.RequestAction(ServerAction.InstallPackage(pkg.name))) },
                        onRemove = { onEvent(ServerEvent.RequestAction(ServerAction.RemovePackage(pkg.name))) },
                        aptRunning = state.aptOperationRunning,
                    )
                }
            }
        } else {
            // Installed packages
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        "${state.installedPackages.size} installed packages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.installedPackages, key = { it.name }) { pkg ->
                    PackageItem(
                        pkg = pkg,
                        onInstall = {},
                        onRemove = { onEvent(ServerEvent.RequestAction(ServerAction.RemovePackage(pkg.name))) },
                        aptRunning = state.aptOperationRunning,
                    )
                }
            }
        }
    }
}

@Composable
private fun PackageItem(
    pkg: AptPackage,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    aptRunning: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pkg.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pkg.description.isNotBlank()) {
                    Text(
                        pkg.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (pkg.upgradeVersion != null) {
                    Text(
                        "${pkg.version} -> ${pkg.upgradeVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (pkg.isInstalled) {
                IconButton(onClick = onRemove, enabled = !aptRunning) {
                    Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = onInstall, enabled = !aptRunning) {
                    Icon(Icons.Default.Download, "Install", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
