package com.serverdash.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.plugin.PluginRegistry
import com.serverdash.app.domain.repository.PreferencesRepository
import com.serverdash.app.domain.repository.ServerRepository
import com.serverdash.app.domain.repository.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverConfig: ServerConfig? = null,
    val preferences: AppPreferences = AppPreferences(),
    val showDisconnectConfirm: Boolean = false,
    val showResetConfirm: Boolean = false
)

sealed interface SettingsEvent {
    // Display
    data class UpdateThemeMode(val mode: ThemeMode) : SettingsEvent
    data class UpdateBrightness(val brightness: Float) : SettingsEvent
    data class UpdateKeepScreenOn(val enabled: Boolean) : SettingsEvent
    data class UpdatePixelShift(val enabled: Boolean) : SettingsEvent
    // Dashboard layout
    data class UpdateDashboardLayout(val layout: DashboardLayout) : SettingsEvent
    data class UpdateGridColumns(val columns: Int) : SettingsEvent
    data class UpdateServiceSortOrder(val order: ServiceSortOrder) : SettingsEvent
    data class UpdateShowServiceDescription(val show: Boolean) : SettingsEvent
    data class UpdateCompactCards(val compact: Boolean) : SettingsEvent
    // Monitoring
    data class UpdatePollingInterval(val seconds: Int) : SettingsEvent
    data class UpdateBgCheckInterval(val minutes: Int) : SettingsEvent
    // Metrics
    data class UpdateMetricsDisplayMode(val mode: MetricsDisplayMode) : SettingsEvent
    data class UpdateShowLoadAverage(val show: Boolean) : SettingsEvent
    data class UpdateCpuWarning(val threshold: Float) : SettingsEvent
    data class UpdateMemoryWarning(val threshold: Float) : SettingsEvent
    data class UpdateDiskWarning(val threshold: Float) : SettingsEvent
    // Notifications
    data class UpdateNotificationsEnabled(val enabled: Boolean) : SettingsEvent
    data class UpdateNotifyServiceDown(val enabled: Boolean) : SettingsEvent
    data class UpdateNotifyHighCpu(val enabled: Boolean) : SettingsEvent
    data class UpdateNotifyHighMemory(val enabled: Boolean) : SettingsEvent
    data class UpdateNotifyHighDisk(val enabled: Boolean) : SettingsEvent
    data class UpdateNotificationSound(val enabled: Boolean) : SettingsEvent
    data class UpdateNotificationVibrate(val enabled: Boolean) : SettingsEvent
    // Logs
    data class UpdateLogFontSize(val size: LogFontSize) : SettingsEvent
    data class UpdateLogLineCount(val count: Int) : SettingsEvent
    data class UpdateLogAutoRefresh(val enabled: Boolean) : SettingsEvent
    data class UpdateLogAutoRefreshSeconds(val seconds: Int) : SettingsEvent
    data class UpdateLogWrapLines(val wrap: Boolean) : SettingsEvent
    // Terminal
    data class UpdateTerminalFontSize(val size: Int) : SettingsEvent
    data class UpdateTerminalMaxHistory(val max: Int) : SettingsEvent
    data class UpdateTerminalShowTimestamps(val show: Boolean) : SettingsEvent
    // plugins
    data class TogglePlugin(val pluginId: String) : SettingsEvent
    // connection
    data class UpdateConnectionTimeout(val seconds: Int) : SettingsEvent
    data class UpdateAutoReconnect(val enabled: Boolean) : SettingsEvent
    data class UpdateReconnectDelay(val seconds: Int) : SettingsEvent
    data class UpdateMaxReconnectAttempts(val attempts: Int) : SettingsEvent
    // Data
    data class UpdateMetricsRetention(val hours: Int) : SettingsEvent
    data class UpdateMaxServicesDisplayed(val max: Int) : SettingsEvent
    data class UpdateHideUnknownServices(val hide: Boolean) : SettingsEvent
    // Kiosk
    data class UpdateKioskMode(val enabled: Boolean) : SettingsEvent
    data class UpdateAutoStart(val enabled: Boolean) : SettingsEvent
    // Danger zone
    data object Disconnect : SettingsEvent
    data object ConfirmDisconnect : SettingsEvent
    data object DismissDisconnect : SettingsEvent
    data object ResetApp : SettingsEvent
    data object ConfirmReset : SettingsEvent
    data object DismissReset : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val serverRepository: ServerRepository,
    private val sshRepository: SshRepository,
    val pluginRegistry: PluginRegistry
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val preferences: StateFlow<AppPreferences> = preferencesRepository.observePreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences())

    init {
        viewModelScope.launch {
            serverRepository.observeServerConfig().collect { config ->
                _state.update { it.copy(serverConfig = config) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.observePreferences().collect { prefs ->
                _state.update { it.copy(preferences = prefs) }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            // Display
            is SettingsEvent.UpdateThemeMode -> updatePref { it.copy(themeMode = event.mode) }
            is SettingsEvent.UpdateBrightness -> updatePref { it.copy(brightnessOverride = event.brightness) }
            is SettingsEvent.UpdateKeepScreenOn -> updatePref { it.copy(keepScreenOn = event.enabled) }
            is SettingsEvent.UpdatePixelShift -> updatePref { it.copy(pixelShiftEnabled = event.enabled) }
            // Dashboard
            is SettingsEvent.UpdateDashboardLayout -> updatePref { it.copy(dashboardLayout = event.layout) }
            is SettingsEvent.UpdateGridColumns -> updatePref { it.copy(gridColumns = event.columns) }
            is SettingsEvent.UpdateServiceSortOrder -> updatePref { it.copy(serviceSortOrder = event.order) }
            is SettingsEvent.UpdateShowServiceDescription -> updatePref { it.copy(showServiceDescription = event.show) }
            is SettingsEvent.UpdateCompactCards -> updatePref { it.copy(compactCards = event.compact) }
            // Monitoring
            is SettingsEvent.UpdatePollingInterval -> updatePref { it.copy(pollingIntervalSeconds = event.seconds) }
            is SettingsEvent.UpdateBgCheckInterval -> updatePref { it.copy(backgroundCheckIntervalMinutes = event.minutes) }
            // Metrics
            is SettingsEvent.UpdateMetricsDisplayMode -> updatePref { it.copy(metricsDisplayMode = event.mode) }
            is SettingsEvent.UpdateShowLoadAverage -> updatePref { it.copy(showLoadAverage = event.show) }
            is SettingsEvent.UpdateCpuWarning -> updatePref { it.copy(cpuWarningThreshold = event.threshold) }
            is SettingsEvent.UpdateMemoryWarning -> updatePref { it.copy(memoryWarningThreshold = event.threshold) }
            is SettingsEvent.UpdateDiskWarning -> updatePref { it.copy(diskWarningThreshold = event.threshold) }
            // Notifications
            is SettingsEvent.UpdateNotificationsEnabled -> updatePref { it.copy(notificationsEnabled = event.enabled) }
            is SettingsEvent.UpdateNotifyServiceDown -> updatePref { it.copy(notifyOnServiceDown = event.enabled) }
            is SettingsEvent.UpdateNotifyHighCpu -> updatePref { it.copy(notifyOnHighCpu = event.enabled) }
            is SettingsEvent.UpdateNotifyHighMemory -> updatePref { it.copy(notifyOnHighMemory = event.enabled) }
            is SettingsEvent.UpdateNotifyHighDisk -> updatePref { it.copy(notifyOnHighDisk = event.enabled) }
            is SettingsEvent.UpdateNotificationSound -> updatePref { it.copy(notificationSound = event.enabled) }
            is SettingsEvent.UpdateNotificationVibrate -> updatePref { it.copy(notificationVibrate = event.enabled) }
            // Logs
            is SettingsEvent.UpdateLogFontSize -> updatePref { it.copy(logFontSize = event.size) }
            is SettingsEvent.UpdateLogLineCount -> updatePref { it.copy(logLineCount = event.count) }
            is SettingsEvent.UpdateLogAutoRefresh -> updatePref { it.copy(logAutoRefresh = event.enabled) }
            is SettingsEvent.UpdateLogAutoRefreshSeconds -> updatePref { it.copy(logAutoRefreshSeconds = event.seconds) }
            is SettingsEvent.UpdateLogWrapLines -> updatePref { it.copy(logWrapLines = event.wrap) }
            // Terminal
            is SettingsEvent.UpdateTerminalFontSize -> updatePref { it.copy(terminalFontSize = event.size) }
            is SettingsEvent.UpdateTerminalMaxHistory -> updatePref { it.copy(terminalMaxHistory = event.max) }
            is SettingsEvent.UpdateTerminalShowTimestamps -> updatePref { it.copy(terminalShowTimestamps = event.show) }
            // Connection
            is SettingsEvent.UpdateConnectionTimeout -> updatePref { it.copy(connectionTimeoutSeconds = event.seconds) }
            is SettingsEvent.UpdateAutoReconnect -> updatePref { it.copy(autoReconnect = event.enabled) }
            is SettingsEvent.UpdateReconnectDelay -> updatePref { it.copy(autoReconnectDelaySeconds = event.seconds) }
            is SettingsEvent.UpdateMaxReconnectAttempts -> updatePref { it.copy(maxReconnectAttempts = event.attempts) }
            // Data
            is SettingsEvent.UpdateMetricsRetention -> updatePref { it.copy(metricsRetentionHours = event.hours) }
            is SettingsEvent.UpdateMaxServicesDisplayed -> updatePref { it.copy(maxServicesDisplayed = event.max) }
            is SettingsEvent.UpdateHideUnknownServices -> updatePref { it.copy(hideUnknownServices = event.hide) }
            // Kiosk
            is SettingsEvent.UpdateKioskMode -> updatePref { it.copy(kioskMode = event.enabled) }
            is SettingsEvent.UpdateAutoStart -> updatePref { it.copy(autoStartOnBoot = event.enabled) }
            // plugins
            is SettingsEvent.TogglePlugin -> {
                updatePref { prefs ->
                    val current = prefs.disabledPlugins.toMutableSet()
                    if (current.contains(event.pluginId)) current.remove(event.pluginId)
                    else current.add(event.pluginId)
                    prefs.copy(disabledPlugins = current)
                }
            }
            // danger zone
            is SettingsEvent.Disconnect -> _state.update { it.copy(showDisconnectConfirm = true) }
            is SettingsEvent.ConfirmDisconnect -> {
                viewModelScope.launch {
                    sshRepository.disconnect()
                    _state.update { it.copy(showDisconnectConfirm = false) }
                }
            }
            is SettingsEvent.DismissDisconnect -> _state.update { it.copy(showDisconnectConfirm = false) }
            is SettingsEvent.ResetApp -> _state.update { it.copy(showResetConfirm = true) }
            is SettingsEvent.ConfirmReset -> {
                viewModelScope.launch {
                    sshRepository.disconnect()
                    serverRepository.deleteServerConfig()
                    _state.update { it.copy(showResetConfirm = false) }
                }
            }
            is SettingsEvent.DismissReset -> _state.update { it.copy(showResetConfirm = false) }
        }
    }

    private fun updatePref(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch { preferencesRepository.updatePreferences(transform) }
    }
}
