package com.serverdash.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.core.theme.AppTheme
import com.serverdash.app.core.theme.BuiltInThemes
import com.serverdash.app.data.preferences.PreferencesManager
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.plugin.PluginRegistry
import com.serverdash.app.domain.repository.PreferencesRepository
import com.serverdash.app.domain.repository.ServerRepository
import com.serverdash.app.domain.repository.SshRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateState(
    val isChecking: Boolean = false,
    val latestVersion: String? = null,
    val releaseNotes: String? = null,
    val downloadUrl: String? = null,
    val apkAssets: List<ApkAsset> = emptyList(),
    val apkHashes: String? = null,
    val isUpdateAvailable: Boolean = false,
    val error: String? = null
)

data class ApkAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long = 0
)

data class SettingsUiState(
    val serverConfig: ServerConfig? = null,
    val preferences: AppPreferences = AppPreferences(),
    val showDisconnectConfirm: Boolean = false,
    val showResetConfirm: Boolean = false,
    val availableThemes: List<com.serverdash.app.core.theme.AppTheme> = emptyList(),
    val updateState: UpdateState = UpdateState(),
    val currentVersion: String = "0.1.1"
)

sealed interface SettingsEvent {
    // Display
    data class UpdateThemeMode(val mode: ThemeMode) : SettingsEvent
    data class UpdateBrightness(val brightness: Float) : SettingsEvent
    data class UpdateKeepScreenOn(val enabled: Boolean) : SettingsEvent
    data class UpdatePixelShift(val enabled: Boolean) : SettingsEvent
    data class UpdateUndoDuration(val seconds: Int) : SettingsEvent
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
    // App Lock
    data class UpdateAppLockEnabled(val enabled: Boolean) : SettingsEvent
    data class UpdateAppLockTimeout(val timeout: com.serverdash.app.domain.model.LockTimeout) : SettingsEvent
    // Kiosk
    data class UpdateKioskMode(val enabled: Boolean) : SettingsEvent
    data class UpdateAutoStart(val enabled: Boolean) : SettingsEvent
    // Danger zone
    data object Disconnect : SettingsEvent
    data object ConfirmDisconnect : SettingsEvent
    data object DismissDisconnect : SettingsEvent
    data object ResetApp : SettingsEvent
    data object ConfirmReset : SettingsEvent
    data class SelectTheme(val themeId: String) : SettingsEvent
    data object DismissReset : SettingsEvent
    // Updates
    data object CheckForUpdates : SettingsEvent
    data object DismissUpdateError : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val serverRepository: ServerRepository,
    private val sshRepository: SshRepository,
    private val preferencesManager: PreferencesManager,
    val pluginRegistry: PluginRegistry
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

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
        viewModelScope.launch {
            preferencesManager.customThemesJson.collect { jsonStr ->
                val customs = try { json.decodeFromString<List<AppTheme>>(jsonStr) } catch (_: Exception) { emptyList() }
                _state.update { it.copy(availableThemes = BuiltInThemes.all + customs) }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            // Display
            is SettingsEvent.UpdateThemeMode -> updatePref { it.copy(themeMode = event.mode) }
            is SettingsEvent.SelectTheme -> updatePref { it.copy(selectedThemeId = event.themeId) }
            is SettingsEvent.UpdateBrightness -> updatePref { it.copy(brightnessOverride = event.brightness) }
            is SettingsEvent.UpdateKeepScreenOn -> updatePref { it.copy(keepScreenOn = event.enabled) }
            is SettingsEvent.UpdatePixelShift -> updatePref { it.copy(pixelShiftEnabled = event.enabled) }
            is SettingsEvent.UpdateUndoDuration -> updatePref { it.copy(undoDurationSeconds = event.seconds) }
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
            // App Lock
            is SettingsEvent.UpdateAppLockEnabled -> updatePref { it.copy(appLockEnabled = event.enabled) }
            is SettingsEvent.UpdateAppLockTimeout -> updatePref { it.copy(appLockTimeout = event.timeout) }
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
            is SettingsEvent.CheckForUpdates -> checkForUpdates()
            is SettingsEvent.DismissUpdateError -> _state.update { it.copy(updateState = it.updateState.copy(error = null)) }
        }
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            _state.update { it.copy(updateState = it.updateState.copy(isChecking = true, error = null)) }
            try {
                val result = sshRepository.executeCommand("curl -s https://api.github.com/repos/wrxck/ServerDash/releases/latest")
                result.fold(
                    onSuccess = { cmdResult ->
                        try {
                            val releaseJson = Json.parseToJsonElement(cmdResult.output.trim()).jsonObject
                            val tagName = releaseJson["tag_name"]?.jsonPrimitive?.content ?: ""
                            val body = releaseJson["body"]?.jsonPrimitive?.content ?: ""
                            val assets = releaseJson["assets"]?.jsonArray?.mapNotNull { asset ->
                                try {
                                    val obj = asset.jsonObject
                                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                    val url = obj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                    val size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0
                                    ApkAsset(name = name, downloadUrl = url, size = size)
                                } catch (e: Exception) { null }
                            } ?: emptyList()

                            // Extract SHA-256 hashes from release body if present
                            val hashPattern = Regex("(?:SHA-?256|sha256)[:\\s]+([a-fA-F0-9]{64})", RegexOption.IGNORE_CASE)
                            val hashes = hashPattern.findAll(body).map { it.groupValues[1] }.joinToString("\n")

                            // Compare versions
                            val latestVersion = tagName.removePrefix("v").removePrefix("V")
                            val currentVersion = _state.value.currentVersion
                            val isUpdateAvailable = compareVersions(latestVersion, currentVersion) > 0

                            // Find APK download URL
                            val apkAsset = assets.firstOrNull { it.name.endsWith(".apk") }

                            _state.update { it.copy(
                                updateState = UpdateState(
                                    isChecking = false,
                                    latestVersion = tagName,
                                    releaseNotes = body,
                                    downloadUrl = apkAsset?.downloadUrl,
                                    apkAssets = assets,
                                    apkHashes = hashes.ifBlank { null },
                                    isUpdateAvailable = isUpdateAvailable,
                                    error = null
                                )
                            )}
                        } catch (e: Exception) {
                            _state.update { it.copy(
                                updateState = UpdateState(
                                    isChecking = false,
                                    error = "Failed to parse release info: ${e.message}"
                                )
                            )}
                        }
                    },
                    onFailure = { e ->
                        _state.update { it.copy(
                            updateState = UpdateState(
                                isChecking = false,
                                error = "Failed to check for updates: ${e.message}"
                            )
                        )}
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(
                    updateState = UpdateState(
                        isChecking = false,
                        error = "Network error: ${e.message}"
                    )
                )}
            }
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    private fun updatePref(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch { preferencesRepository.updatePreferences(transform) }
    }
}
