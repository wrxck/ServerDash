package com.serverdash.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.domain.model.*
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
    data class UpdateThemeMode(val mode: ThemeMode) : SettingsEvent
    data class UpdatePollingInterval(val seconds: Int) : SettingsEvent
    data class UpdateBrightness(val brightness: Float) : SettingsEvent
    data class UpdateKeepScreenOn(val enabled: Boolean) : SettingsEvent
    data class UpdateKioskMode(val enabled: Boolean) : SettingsEvent
    data class UpdatePixelShift(val enabled: Boolean) : SettingsEvent
    data class UpdateAutoStart(val enabled: Boolean) : SettingsEvent
    data class UpdateBgCheckInterval(val minutes: Int) : SettingsEvent
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
    private val sshRepository: SshRepository
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
            is SettingsEvent.UpdateThemeMode -> updatePref { it.copy(themeMode = event.mode) }
            is SettingsEvent.UpdatePollingInterval -> updatePref { it.copy(pollingIntervalSeconds = event.seconds) }
            is SettingsEvent.UpdateBrightness -> updatePref { it.copy(brightnessOverride = event.brightness) }
            is SettingsEvent.UpdateKeepScreenOn -> updatePref { it.copy(keepScreenOn = event.enabled) }
            is SettingsEvent.UpdateKioskMode -> updatePref { it.copy(kioskMode = event.enabled) }
            is SettingsEvent.UpdatePixelShift -> updatePref { it.copy(pixelShiftEnabled = event.enabled) }
            is SettingsEvent.UpdateAutoStart -> updatePref { it.copy(autoStartOnBoot = event.enabled) }
            is SettingsEvent.UpdateBgCheckInterval -> updatePref { it.copy(backgroundCheckIntervalMinutes = event.minutes) }
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
