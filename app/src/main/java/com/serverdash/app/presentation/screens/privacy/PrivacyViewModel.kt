package com.serverdash.app.presentation.screens.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.core.privacy.PrivacyFilter
import com.serverdash.app.domain.model.AppPreferences
import com.serverdash.app.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacyUiState(
    val preferences: AppPreferences = AppPreferences(),
    val testInput: String = "",
    val testOutput: String = "",
    val newServiceName: String = "",
    val newPattern: String = "",
    val patternError: String? = null,
    val showPreview: Boolean = true
)

sealed interface PrivacyEvent {
    data class ToggleStreamingMode(val enabled: Boolean) : PrivacyEvent
    data class ToggleFilterIps(val enabled: Boolean) : PrivacyEvent
    data class ToggleFilterPorts(val enabled: Boolean) : PrivacyEvent
    data class ToggleFilterEmails(val enabled: Boolean) : PrivacyEvent
    data class ToggleFilterHostnames(val enabled: Boolean) : PrivacyEvent
    data class ToggleFilterPaths(val enabled: Boolean) : PrivacyEvent
    data class ToggleFilterSsh(val enabled: Boolean) : PrivacyEvent
    data class ToggleFilterTokens(val enabled: Boolean) : PrivacyEvent
    data class ToggleFilterPasswords(val enabled: Boolean) : PrivacyEvent
    data class ToggleFilterServiceNames(val enabled: Boolean) : PrivacyEvent
    // Service names
    data class UpdateNewServiceName(val name: String) : PrivacyEvent
    data object AddServiceName : PrivacyEvent
    data class RemoveServiceName(val name: String) : PrivacyEvent
    // Custom patterns
    data class UpdateNewPattern(val pattern: String) : PrivacyEvent
    data object AddPattern : PrivacyEvent
    data class RemovePattern(val pattern: String) : PrivacyEvent
    // Replacement text
    data class UpdateReplacementText(val text: String) : PrivacyEvent
    // Test
    data class UpdateTestInput(val text: String) : PrivacyEvent
    data object LoadSampleText : PrivacyEvent
    data class TogglePreview(val show: Boolean) : PrivacyEvent
}

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val privacyFilter: PrivacyFilter
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.observePreferences().collect { prefs ->
                _state.update { current ->
                    val updated = current.copy(preferences = prefs)
                    // Re-run filter on test input when prefs change
                    if (current.testInput.isNotEmpty()) {
                        updated.copy(testOutput = privacyFilter.filterWithPrefs(current.testInput, prefs))
                    } else {
                        updated
                    }
                }
            }
        }
    }

    fun onEvent(event: PrivacyEvent) {
        when (event) {
            is PrivacyEvent.ToggleStreamingMode -> updatePref { it.copy(streamingModeEnabled = event.enabled) }
            is PrivacyEvent.ToggleFilterIps -> updatePref { it.copy(privacyFilterIps = event.enabled) }
            is PrivacyEvent.ToggleFilterPorts -> updatePref { it.copy(privacyFilterPorts = event.enabled) }
            is PrivacyEvent.ToggleFilterEmails -> updatePref { it.copy(privacyFilterEmails = event.enabled) }
            is PrivacyEvent.ToggleFilterHostnames -> updatePref { it.copy(privacyFilterHostnames = event.enabled) }
            is PrivacyEvent.ToggleFilterPaths -> updatePref { it.copy(privacyFilterPaths = event.enabled) }
            is PrivacyEvent.ToggleFilterSsh -> updatePref { it.copy(privacyFilterSsh = event.enabled) }
            is PrivacyEvent.ToggleFilterTokens -> updatePref { it.copy(privacyFilterTokens = event.enabled) }
            is PrivacyEvent.ToggleFilterPasswords -> updatePref { it.copy(privacyFilterPasswords = event.enabled) }
            is PrivacyEvent.ToggleFilterServiceNames -> updatePref { it.copy(privacyFilterServiceNames = event.enabled) }

            is PrivacyEvent.UpdateNewServiceName -> _state.update { it.copy(newServiceName = event.name) }
            is PrivacyEvent.AddServiceName -> {
                val name = _state.value.newServiceName.trim()
                if (name.isNotBlank()) {
                    updatePref { prefs ->
                        prefs.copy(privacyRedactedServiceNames = prefs.privacyRedactedServiceNames + name)
                    }
                    _state.update { it.copy(newServiceName = "") }
                }
            }
            is PrivacyEvent.RemoveServiceName -> {
                updatePref { prefs ->
                    prefs.copy(privacyRedactedServiceNames = prefs.privacyRedactedServiceNames - event.name)
                }
            }

            is PrivacyEvent.UpdateNewPattern -> {
                _state.update { it.copy(newPattern = event.pattern, patternError = null) }
            }
            is PrivacyEvent.AddPattern -> {
                val pattern = _state.value.newPattern.trim()
                if (pattern.isNotBlank()) {
                    try {
                        Regex(pattern) // Validate
                        updatePref { prefs ->
                            prefs.copy(privacyCustomPatterns = prefs.privacyCustomPatterns + pattern)
                        }
                        _state.update { it.copy(newPattern = "", patternError = null) }
                    } catch (e: Exception) {
                        _state.update { it.copy(patternError = "Invalid regex: ${e.message}") }
                    }
                }
            }
            is PrivacyEvent.RemovePattern -> {
                updatePref { prefs ->
                    prefs.copy(privacyCustomPatterns = prefs.privacyCustomPatterns - event.pattern)
                }
            }

            is PrivacyEvent.UpdateReplacementText -> {
                if (event.text.isNotBlank()) {
                    updatePref { it.copy(privacyReplacementText = event.text) }
                }
            }

            is PrivacyEvent.UpdateTestInput -> {
                val prefs = _state.value.preferences
                val output = privacyFilter.filterWithPrefs(event.text, prefs)
                _state.update { it.copy(testInput = event.text, testOutput = output) }
            }
            is PrivacyEvent.LoadSampleText -> {
                val prefs = _state.value.preferences
                val sample = PrivacyFilter.SAMPLE_TEXT
                val output = privacyFilter.filterWithPrefs(sample, prefs)
                _state.update { it.copy(testInput = sample, testOutput = output) }
            }
            is PrivacyEvent.TogglePreview -> _state.update { it.copy(showPreview = event.show) }
        }
    }

    private fun updatePref(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch { preferencesRepository.updatePreferences(transform) }
    }
}
