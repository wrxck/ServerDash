package com.serverdash.app.presentation.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.core.privacy.redact
import com.serverdash.app.core.theme.*
import com.serverdash.app.core.util.*
import com.serverdash.app.data.encryption.EncryptionManager
import com.serverdash.app.domain.model.*
import com.serverdash.app.presentation.screens.security.EncryptionPromptDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToClaudeCode: () -> Unit = {},
    onNavigateToFleet: () -> Unit = {},
    onNavigateToGuardian: () -> Unit = {},
    onNavigateToGit: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onDebugWithClaude: (String, String) -> Unit = { _, _ -> },
    encryptionManager: EncryptionManager? = null,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val landscape = isLandscape()
    val configuration = LocalConfiguration.current
    val isCompactScreen = configuration.screenWidthDp < 600
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val columns = when {
        preferences.gridColumns > 0 -> preferences.gridColumns
        landscape -> 4
        else -> 2
    }

    val displayServices = remember(state.filteredServices, preferences) {
        var result = state.filteredServices.toList()
        // Hide unknown
        if (preferences.hideUnknownServices) {
            result = result.filter { it.status != ServiceStatus.UNKNOWN }
        }
        // Sort
        result = when (preferences.serviceSortOrder) {
            ServiceSortOrder.PINNED_FIRST -> result.sortedWith(compareByDescending<Service> { it.isPinned }.thenBy { it.displayName.lowercase() })
            ServiceSortOrder.NAME -> result.sortedBy { it.displayName.lowercase() }
            ServiceSortOrder.STATUS -> result.sortedBy { it.status.ordinal }
            ServiceSortOrder.TYPE -> result.sortedWith(compareBy<Service> { it.type }.thenBy { it.displayName.lowercase() })
        }
        // Max limit
        if (preferences.maxServicesDisplayed > 0) {
            result = result.take(preferences.maxServicesDisplayed)
        }
        result
    }

    // Encryption prompt for existing users
    var showEncryptionPrompt by remember {
        mutableStateOf(encryptionManager?.shouldShowEncryptionPrompt == true)
    }

    if (showEncryptionPrompt && encryptionManager != null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val biometricAvailable = remember {
            androidx.biometric.BiometricManager.from(context).canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        }
        EncryptionPromptDialog(
            biometricAvailable = biometricAvailable,
            encryptionManager = encryptionManager,
            onDismissAfterSuccess = { showEncryptionPrompt = false },
            onRemindLater = {
                encryptionManager.dismissEncryptionPrompt()
                showEncryptionPrompt = false
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToDetail.collect { service ->
            onNavigateToDetail(service.name, service.type.name)
        }
    }

    val claudeCodeAvailable = state.detectedPlugins["claude-code"] == true &&
        !preferences.disabledPlugins.contains("claude-code")

    val scaffoldContent: @Composable () -> Unit = {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSearchVisible) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.onEvent(DashboardEvent.UpdateSearch(it)) },
                            placeholder = { Text("Search services...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("ServerDash")
                    }
                },
                navigationIcon = {
                    if (state.isSearchVisible) {
                        IconButton(onClick = { viewModel.onEvent(DashboardEvent.ToggleSearchVisibility) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                        }
                    } else if (isCompactScreen) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    }
                },
                actions = {
                    if (!state.isSearchVisible) {
                        IconButton(onClick = { viewModel.onEvent(DashboardEvent.ToggleSearchVisibility) }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                    }
                    if (state.isFiltered && !state.isSearchVisible) {
                        IconButton(onClick = { viewModel.onEvent(DashboardEvent.ClearFilters) }) {
                            Icon(Icons.Default.FilterListOff, "Clear filters")
                        }
                    }
                    if (!isCompactScreen) {
                        if (claudeCodeAvailable) {
                            IconButton(onClick = onNavigateToClaudeCode) {
                                Icon(Icons.Default.SmartToy, "Claude Code")
                            }
                        }
                        IconButton(onClick = onNavigateToGit) {
                            Icon(Icons.Default.Code, "Git")
                        }
                        IconButton(onClick = onNavigateToTerminal) {
                            Icon(Icons.Default.Terminal, "Terminal")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                        if (preferences.appLockEnabled) {
                            IconButton(onClick = { viewModel.onEvent(DashboardEvent.LockApp) }) {
                                Icon(Icons.Default.Lock, "Lock", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (state.fleetAvailable) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (state.showNonFleetServices) "Hide system services"
                                             else "Show system services")
                                    },
                                    onClick = {
                                        viewModel.onEvent(DashboardEvent.ToggleShowNonFleetServices)
                                        showMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = { showMenu = false; onNavigateToAbout() },
                                leadingIcon = { Icon(Icons.Default.Info, null) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // connection status bar
            ConnectionStatusBar(state.connectionState)

            // detected plugin chips (with skeleton while loading)
            if (state.isDetectingPlugins) {
                val transition = rememberInfiniteTransition(label = "pluginShimmer")
                val alpha by transition.animateFloat(
                    initialValue = 0.15f,
                    targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pluginShimmerAlpha"
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(width = 80.dp, height = 32.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }
            } else if (state.detectedPlugins.any { it.value }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.detectedPlugins.filter { it.value }.forEach { (id, _) ->
                        AssistChip(
                            onClick = {
                                when (id) {
                                    "claude-code" -> onNavigateToClaudeCode()
                                    "fleet" -> onNavigateToFleet()
                                    "guardian" -> onNavigateToGuardian()
                                    else -> onNavigateToSettings()
                                }
                            },
                            label = { Text(id.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // fleet info banner (only after plugin detection has completed)
            if (!state.fleetAvailable && state.services.isNotEmpty() && state.detectedPlugins.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Install Fleet CLI for enhanced service management",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // alert banner
            if (state.activeAlerts.isNotEmpty()) {
                AlertBanner(
                    alertCount = state.activeAlerts.size,
                    onDismiss = { viewModel.onEvent(DashboardEvent.AcknowledgeAlerts) }
                )
            }

            // Metrics cards with sparkline charts (clickable for detail)
            if (state.activeMetricDetail != null) {
                MetricDetailOverlay(state, viewModel)
            } else {

            state.metrics?.let { MetricsCardsRow(state, viewModel) }

            // Tabs
            if (state.availableTabs.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = state.selectedTab,
                    edgePadding = 8.dp,
                    divider = {}
                ) {
                    state.availableTabs.forEachIndexed { index, tab ->
                        val count = if (index == 0) state.services.size
                            else state.services.count { it.effectiveGroup == tab }
                        Tab(
                            selected = state.selectedTab == index,
                            onClick = { viewModel.onEvent(DashboardEvent.SelectTab(index)) },
                            text = { Text("$tab ($count)") }
                        )
                    }
                }
            }

            // Filter chips
            FilterChipRow(state, viewModel)

            // Service count when filtered
            if (state.isFiltered) {
                Text(
                    "${displayServices.size} of ${state.services.size} services",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Service grid
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.onEvent(DashboardEvent.Refresh) },
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (displayServices.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (state.isFiltered) "No services match filters" else "No services found",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (state.isFiltered) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.onEvent(DashboardEvent.ClearFilters) }) {
                                    Text("Clear filters")
                                }
                            }
                        }
                    }
                } else {
                    when (preferences.dashboardLayout) {
                        DashboardLayout.LIST -> {
                            LazyColumn(
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayServices, key = { "${it.serverId}_${it.name}" }) { service ->
                                    ServiceCard(
                                        service = service,
                                        compact = preferences.compactCards,
                                        showDescription = preferences.showServiceDescription,
                                        onClick = { viewModel.onEvent(DashboardEvent.NavigateToDetail(service)) },
                                        onDebugWithClaude = onDebugWithClaude
                                    )
                                }
                            }
                        }
                        DashboardLayout.COMPACT -> {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(columns),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalItemSpacing = 4.dp
                            ) {
                                items(displayServices, key = { "${it.serverId}_${it.name}" }) { service ->
                                    ServiceCard(
                                        service = service,
                                        compact = true,
                                        showDescription = false,
                                        onClick = { viewModel.onEvent(DashboardEvent.NavigateToDetail(service)) },
                                        onDebugWithClaude = onDebugWithClaude
                                    )
                                }
                            }
                        }
                        DashboardLayout.GRID -> {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(columns),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalItemSpacing = 8.dp
                            ) {
                                items(displayServices, key = { "${it.serverId}_${it.name}" }) { service ->
                                    ServiceCard(
                                        service = service,
                                        compact = preferences.compactCards,
                                        showDescription = preferences.showServiceDescription,
                                        onClick = { viewModel.onEvent(DashboardEvent.NavigateToDetail(service)) },
                                        onDebugWithClaude = onDebugWithClaude
                                    )
                                }
                            }
                        }
                    }
                }
            }

            } // end of else (no metric detail)
        }
    }
    } // end scaffoldContent

    if (isCompactScreen) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "ServerDash",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    if (claudeCodeAvailable) {
                        DrawerNavItem(Icons.Default.SmartToy, "Claude Code") {
                            scope.launch { drawerState.close() }; onNavigateToClaudeCode()
                        }
                    }
                    DrawerNavItem(Icons.Default.Code, "Git") {
                        scope.launch { drawerState.close() }; onNavigateToGit()
                    }
                    DrawerNavItem(Icons.Default.Terminal, "Terminal") {
                        scope.launch { drawerState.close() }; onNavigateToTerminal()
                    }
                    DrawerNavItem(Icons.Default.Settings, "Settings") {
                        scope.launch { drawerState.close() }; onNavigateToSettings()
                    }
                    DrawerNavItem(Icons.Default.Shield, "Security") {
                        scope.launch { drawerState.close() }; onNavigateToSecurity()
                    }
                    DrawerNavItem(Icons.Default.Info, "About") {
                        scope.launch { drawerState.close() }; onNavigateToAbout()
                    }
                    if (preferences.appLockEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        DrawerNavItem(Icons.Default.Lock, "Lock App", tint = MaterialTheme.colorScheme.error) {
                            scope.launch { drawerState.close() }
                            viewModel.onEvent(DashboardEvent.LockApp)
                        }
                    }
                }
            }
        ) {
            scaffoldContent()
        }
    } else {
        scaffoldContent()
    }
}

@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, null, tint = tint) },
        label = { Text(label, color = tint) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

private val statusEntries = ServiceStatus.entries.toList()
private val typeEntries = ServiceType.entries.toList()

@Composable
fun FilterChipRow(state: DashboardUiState, viewModel: DashboardViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Status filters
        items(statusEntries) { status ->
            val selected = status in state.statusFilters
            FilterChip(
                selected = selected,
                onClick = { viewModel.onEvent(DashboardEvent.ToggleStatusFilter(status)) },
                label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                leadingIcon = if (selected) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null
            )
        }
        // Type filters
        items(typeEntries) { type ->
            val selected = type in state.typeFilters
            FilterChip(
                selected = selected,
                onClick = { viewModel.onEvent(DashboardEvent.ToggleTypeFilter(type)) },
                label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                leadingIcon = if (selected) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null
            )
        }
    }
}

@Composable
fun ConnectionStatusBar(connectionState: ConnectionState) {
    val color by animateColorAsState(
        when {
            connectionState.isConnected -> StatusGreen
            connectionState.isConnecting -> StatusYellow
            connectionState.error != null -> StatusRed
            else -> StatusGray
        }, label = "status"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when {
                connectionState.isConnected -> Icons.Default.Wifi
                connectionState.isConnecting -> Icons.Default.Sync
                else -> Icons.Default.WifiOff
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                connectionState.isConnected -> "Connected"
                connectionState.isConnecting -> "Connecting..."
                connectionState.error != null -> connectionState.error!!
                else -> "Disconnected"
            },
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
fun AlertBanner(alertCount: Int, onDismiss: () -> Unit) {
    Surface(
        color = StatusRed.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = StatusRed)
            Spacer(Modifier.width(8.dp))
            Text(
                "$alertCount active alert${if (alertCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = StatusRed,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
fun MetricsSummaryRow(metrics: SystemMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        MetricChip("CPU", "%.0f%%".format(metrics.cpuUsage))
        MetricChip("MEM", "%.0f%%".format(metrics.memoryUsagePercent))
        MetricChip("DISK", "%.0f%%".format(metrics.diskUsagePercent))
        MetricChip("UP", formatUptime(metrics.uptimeSeconds))
    }
}

@Composable
fun MetricChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ServiceCard(
    service: Service,
    compact: Boolean = false,
    showDescription: Boolean = false,
    onClick: () -> Unit,
    onDebugWithClaude: (String, String) -> Unit = { _, _ -> }
) {
    val statusColor = when (service.status) {
        ServiceStatus.RUNNING -> StatusGreen
        ServiceStatus.FAILED -> StatusRed
        ServiceStatus.STOPPED -> StatusYellow
        ServiceStatus.UNKNOWN -> StatusGray
    }

    val cardPadding = if (compact) 6.dp else 12.dp
    val dotSize = if (compact) 8.dp else 12.dp

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(cardPadding), verticalAlignment = Alignment.CenterVertically) {
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.width(if (compact) 6.dp else 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    redact(service.displayName),
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact) {
                    Text(
                        "${service.type.name} - ${service.status.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showDescription && service.description.isNotBlank()) {
                    Text(
                        redact(service.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (service.status == ServiceStatus.FAILED) {
                Spacer(Modifier.width(4.dp))
                AssistChip(
                    onClick = { onDebugWithClaude(service.name, service.type.name) },
                    label = { Text("Debug", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.SmartToy, null, Modifier.size(14.dp)) },
                    modifier = Modifier.height(24.dp)
                )
            }
            if (service.isPinned) {
                Icon(Icons.Default.PushPin, null, Modifier.size(if (compact) 12.dp else 16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
