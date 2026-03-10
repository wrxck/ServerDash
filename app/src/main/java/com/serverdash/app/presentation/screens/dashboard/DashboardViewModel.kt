package com.serverdash.app.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.plugin.PluginRegistry
import com.serverdash.app.domain.repository.*
import com.serverdash.app.domain.usecase.*
import android.content.Context
import com.serverdash.app.widget.WidgetUpdateHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private val WHITESPACE_REGEX = "\\s+".toRegex()

data class ProcessInfo(
    val pid: Int,
    val user: String,
    val cpuPercent: Float,
    val memPercent: Float,
    val vsz: String,
    val rss: String,
    val command: String
)

enum class MetricDetailType { CPU, MEMORY, DISK, PROCESSES }

data class DashboardUiState(
    val services: List<Service> = emptyList(),
    val metrics: SystemMetrics? = null,
    val connectionState: ConnectionState = ConnectionState(),
    val activeAlerts: List<Alert> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val metricsHistory: List<SystemMetrics> = emptyList(),
    // Tabs
    val selectedTab: Int = 0,
    val availableTabs: List<String> = listOf("All"),
    // Search & Filters
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val statusFilters: Set<ServiceStatus> = emptySet(),
    val typeFilters: Set<ServiceType> = emptySet(),
    // plugin detection
    val isDetectingPlugins: Boolean = true,
    val detectedPlugins: Map<String, Boolean> = emptyMap(),
    val fleetAvailable: Boolean = false,
    val showNonFleetServices: Boolean = false,
    // metrics detail / process list
    val activeMetricDetail: MetricDetailType? = null,
    val processList: List<ProcessInfo> = emptyList(),
    val isLoadingProcesses: Boolean = false,
    val processSortBy: String = "cpu", // cpu, mem, pid
    // Root SSH migration
    val showRootSshMigration: Boolean = false,
    val currentAuthMethod: String = "" // "key" or "password"
) {
    val filteredServices: List<Service> get() {
        var result = services

        // Tab filter (index 0 = All, 1+ = specific group)
        if (selectedTab > 0 && selectedTab < availableTabs.size) {
            val tabGroup = availableTabs[selectedTab]
            result = result.filter { it.effectiveGroup == tabGroup }
        }

        // Search filter
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            result = result.filter {
                it.name.lowercase().contains(query) ||
                it.displayName.lowercase().contains(query) ||
                it.description.lowercase().contains(query) ||
                it.group.lowercase().contains(query)
            }
        }

        // Status filter
        if (statusFilters.isNotEmpty()) {
            result = result.filter { it.status in statusFilters }
        }

        // Type filter
        if (typeFilters.isNotEmpty()) {
            result = result.filter { it.type in typeFilters }
        }

        return result
    }

    val isFiltered: Boolean get() = searchQuery.isNotBlank() || statusFilters.isNotEmpty() || typeFilters.isNotEmpty() || selectedTab > 0
}

sealed interface DashboardEvent {
    data object Refresh : DashboardEvent
    data class NavigateToDetail(val service: Service) : DashboardEvent
    data object DismissError : DashboardEvent
    data object AcknowledgeAlerts : DashboardEvent
    // Tab events
    data class SelectTab(val index: Int) : DashboardEvent
    // Search & filter events
    data class UpdateSearch(val query: String) : DashboardEvent
    data object ToggleSearchVisibility : DashboardEvent
    data class ToggleStatusFilter(val status: ServiceStatus) : DashboardEvent
    data class ToggleTypeFilter(val type: ServiceType) : DashboardEvent
    data object ClearFilters : DashboardEvent
    data object ToggleShowNonFleetServices : DashboardEvent
    // metrics detail
    data class OpenMetricDetail(val type: MetricDetailType) : DashboardEvent
    data object CloseMetricDetail : DashboardEvent
    data class SortProcesses(val by: String) : DashboardEvent
    data object RefreshProcesses : DashboardEvent
    data class KillProcess(val pid: Int) : DashboardEvent
    data object LockApp : DashboardEvent
    data object MigrateToRootSsh : DashboardEvent
    data object DismissRootSshMigration : DashboardEvent
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val serviceRepository: ServiceRepository,
    private val serverRepository: ServerRepository,
    private val sshRepository: SshRepository,
    private val metricsRepository: MetricsRepository,
    private val alertRepository: AlertRepository,
    private val preferencesRepository: PreferencesRepository,
    private val refreshServiceStatus: RefreshServiceStatusUseCase,
    private val fetchMetrics: FetchSystemMetricsUseCase,
    private val evaluateAlertRules: EvaluateAlertRulesUseCase,
    private val pluginRegistry: PluginRegistry,
    private val fleetDiscoverServices: FleetDiscoverServicesUseCase,
    private val appLockManager: com.serverdash.app.core.security.AppLockManager
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    val preferences: StateFlow<AppPreferences> = preferencesRepository.observePreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences())

    private var pollingJob: Job? = null
    private var serverId: Long = 1L
    private val _navigateToDetail = MutableSharedFlow<Service>()
    val navigateToDetail: SharedFlow<Service> = _navigateToDetail.asSharedFlow()

    init {
        loadServerId()
    }

    private fun loadServerId() {
        viewModelScope.launch {
            val config = serverRepository.getServerConfig()
            serverId = config?.id ?: 1L
            // Reconnect SSH if we have a saved config but aren't connected
            if (config != null && !sshRepository.isConnected()) {
                sshRepository.connect(config)
            }

            // Detect if user has key-based auth with sudo password but no root SSH
            // Offer migration to root SSH (same key) for a cleaner setup
            if (config != null) {
                val isKeyAuth = config.authMethod is AuthMethod.KeyBased
                val hasSudoPassword = config.sudoPassword.isNotBlank()
                val noRootSsh = config.rootAccess is RootAccess.SudoPassword || config.rootAccess is RootAccess.None
                if (isKeyAuth && hasSudoPassword && noRootSsh) {
                    _state.update { it.copy(
                        showRootSshMigration = true,
                        currentAuthMethod = "key"
                    )}
                }
            }

            observeData()
            startPolling()
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            serviceRepository.observeServices(serverId).collect { services ->
                _state.update { state ->
                    val tabs = buildTabs(services)
                    val safeTab = if (state.selectedTab >= tabs.size) 0 else state.selectedTab
                    state.copy(services = services, isLoading = false, availableTabs = tabs, selectedTab = safeTab)
                }
            }
        }
        viewModelScope.launch {
            sshRepository.observeConnectionState().collect { conn ->
                _state.update { it.copy(connectionState = conn) }
                if (conn.isConnected) {
                    detectPlugins()
                }
            }
        }
        viewModelScope.launch {
            metricsRepository.observeLatestMetrics().collect { metrics ->
                _state.update { it.copy(metrics = metrics) }
            }
        }
        viewModelScope.launch {
            alertRepository.observeActiveAlerts().collect { alerts ->
                _state.update { it.copy(activeAlerts = alerts) }
            }
        }
    }

    private fun buildTabs(services: List<Service>): List<String> {
        val groups = services.map { it.effectiveGroup }.distinct().sorted()
        return listOf("All") + groups
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val prefs = preferencesRepository.getPreferences()
            while (true) {
                if (_state.value.connectionState.isConnected) {
                    refreshData()
                }
                delay(prefs.pollingIntervalSeconds * 1000L)
            }
        }
    }

    private fun detectPlugins() {
        viewModelScope.launch {
            _state.update { it.copy(isDetectingPlugins = true) }
            try {
                val detected = pluginRegistry.detectAll(sshRepository)
                _state.update { it.copy(
                    detectedPlugins = detected,
                    fleetAvailable = detected["fleet"] == true,
                    isDetectingPlugins = false
                )}
            } catch (_: Exception) {
                _state.update { it.copy(isDetectingPlugins = false) }
            }
        }
    }

    private suspend fun refreshData() {
        if (_state.value.fleetAvailable && !_state.value.showNonFleetServices) {
            fleetDiscoverServices(serverId)
        } else {
            refreshServiceStatus(serverId)
        }
        fetchMetrics()
        val currentState = _state.value
        if (currentState.metrics != null) {
            evaluateAlertRules(currentState.services, currentState.metrics!!, serverId)
        }
        val history = metricsRepository.getMetricsHistory(60).reversed()
        _state.update { it.copy(metricsHistory = history) }

        // Update widget data
        val currentStateForWidget = _state.value
        val config = serverRepository.getServerConfig()
        WidgetUpdateHelper.updateAllWidgets(
            context = appContext,
            isConnected = currentStateForWidget.connectionState.isConnected,
            hostname = config?.label ?: config?.host ?: "No server",
            metrics = currentStateForWidget.metrics,
            services = currentStateForWidget.services
        )
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.Refresh -> {
                viewModelScope.launch {
                    _state.update { it.copy(isRefreshing = true) }
                    refreshData()
                    _state.update { it.copy(isRefreshing = false) }
                }
            }
            is DashboardEvent.NavigateToDetail -> {
                viewModelScope.launch { _navigateToDetail.emit(event.service) }
            }
            is DashboardEvent.DismissError -> {
                _state.update { it.copy(error = null) }
            }
            is DashboardEvent.AcknowledgeAlerts -> {
                viewModelScope.launch {
                    _state.value.activeAlerts.forEach { alert ->
                        alertRepository.acknowledgeAlert(alert.id)
                    }
                }
            }
            is DashboardEvent.SelectTab -> {
                _state.update { it.copy(selectedTab = event.index) }
            }
            is DashboardEvent.UpdateSearch -> {
                _state.update { it.copy(searchQuery = event.query) }
            }
            is DashboardEvent.ToggleSearchVisibility -> {
                _state.update { it.copy(isSearchVisible = !it.isSearchVisible, searchQuery = if (it.isSearchVisible) "" else it.searchQuery) }
            }
            is DashboardEvent.ToggleStatusFilter -> {
                _state.update { state ->
                    val newFilters = state.statusFilters.toMutableSet()
                    if (event.status in newFilters) newFilters.remove(event.status) else newFilters.add(event.status)
                    state.copy(statusFilters = newFilters)
                }
            }
            is DashboardEvent.ToggleTypeFilter -> {
                _state.update { state ->
                    val newFilters = state.typeFilters.toMutableSet()
                    if (event.type in newFilters) newFilters.remove(event.type) else newFilters.add(event.type)
                    state.copy(typeFilters = newFilters)
                }
            }
            is DashboardEvent.ClearFilters -> {
                _state.update { it.copy(searchQuery = "", statusFilters = emptySet(), typeFilters = emptySet(), selectedTab = 0, isSearchVisible = false) }
            }
            is DashboardEvent.ToggleShowNonFleetServices -> {
                _state.update { it.copy(showNonFleetServices = !it.showNonFleetServices) }
                viewModelScope.launch { refreshData() }
            }
            is DashboardEvent.OpenMetricDetail -> {
                _state.update { it.copy(activeMetricDetail = event.type) }
                if (event.type == MetricDetailType.PROCESSES) loadProcesses()
            }
            is DashboardEvent.CloseMetricDetail -> {
                _state.update { it.copy(activeMetricDetail = null) }
            }
            is DashboardEvent.SortProcesses -> {
                _state.update { state ->
                    val sorted = sortProcessList(state.processList, event.by)
                    state.copy(processList = sorted, processSortBy = event.by)
                }
            }
            is DashboardEvent.RefreshProcesses -> loadProcesses()
            is DashboardEvent.KillProcess -> killProcess(event.pid)
            is DashboardEvent.LockApp -> appLockManager.lock()
            is DashboardEvent.DismissRootSshMigration -> {
                _state.update { it.copy(showRootSshMigration = false) }
            }
            is DashboardEvent.MigrateToRootSsh -> {
                viewModelScope.launch {
                    val config = serverRepository.getServerConfig() ?: return@launch
                    val updated = config.copy(
                        rootAccess = RootAccess.SameKeyAsUser,
                        sudoPassword = "" // Clear sudo password — no longer needed
                    )
                    serverRepository.saveServerConfig(updated)
                    // Reconnect with updated config so root SSH is used
                    sshRepository.connect(updated)
                    _state.update { it.copy(showRootSshMigration = false) }
                }
            }
        }
    }

    private fun loadProcesses() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingProcesses = true) }
            try {
                val cmd = "ps aux --sort=-%cpu 2>/dev/null | head -51"
                val result = sshRepository.executeCommand(cmd).getOrThrow()
                val lines = result.output.lines()
                val processes = lines.drop(1) // skip header
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        val parts = line.trim().split(WHITESPACE_REGEX, limit = 11)
                        if (parts.size >= 11) {
                            ProcessInfo(
                                pid = parts[1].toIntOrNull() ?: 0,
                                user = parts[0],
                                cpuPercent = parts[2].toFloatOrNull() ?: 0f,
                                memPercent = parts[3].toFloatOrNull() ?: 0f,
                                vsz = formatBytes(parts[4].toLongOrNull()?.times(1024) ?: 0),
                                rss = formatBytes(parts[5].toLongOrNull()?.times(1024) ?: 0),
                                command = parts[10]
                            )
                        } else null
                    }
                val sorted = sortProcessList(processes, _state.value.processSortBy)
                _state.update { it.copy(processList = sorted, isLoadingProcesses = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingProcesses = false, error = "Process list failed: ${e.message}") }
            }
        }
    }

    private fun killProcess(pid: Int) {
        viewModelScope.launch {
            try {
                // Try as current user first, then use root if available
                val userResult = sshRepository.executeCommand("kill $pid 2>/dev/null")
                if (userResult.getOrNull()?.exitCode != 0 && sshRepository.hasRootAccess()) {
                    sshRepository.executeSudoCommand("kill $pid 2>/dev/null")
                }
                delay(500)
                loadProcesses()
            } catch (_: Exception) { }
        }
    }

    private fun sortProcessList(list: List<ProcessInfo>, by: String): List<ProcessInfo> {
        return when (by) {
            "cpu" -> list.sortedByDescending { it.cpuPercent }
            "mem" -> list.sortedByDescending { it.memPercent }
            "pid" -> list.sortedBy { it.pid }
            else -> list
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1fG".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.0fM".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.0fK".format(bytes / 1024.0)
            else -> "${bytes}B"
        }
    }
}
