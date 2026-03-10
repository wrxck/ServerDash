package com.serverdash.app.presentation.screens.guardian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.domain.repository.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuardianProcess(
    val name: String,
    val pid: String,
    val status: String,
    val cpu: String,
    val memory: String
)

data class GuardianUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val guardianStatus: String = "",
    val processes: List<GuardianProcess> = emptyList(),
    val recentEvents: List<String> = emptyList(),
    val guardianVersion: String = ""
)

sealed interface GuardianEvent {
    data object Refresh : GuardianEvent
}

@HiltViewModel
class GuardianViewModel @Inject constructor(
    private val sshRepository: SshRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GuardianUiState())
    val state: StateFlow<GuardianUiState> = _state.asStateFlow()

    init {
        loadGuardianData()
    }

    private fun loadGuardianData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // Get guardian version (guardiand is typically root-only)
                val versionResult = sshRepository.executeSudoCommand("guardiand --version 2>/dev/null")
                val version = versionResult.getOrNull()?.output?.trim() ?: ""

                // Get guardian status
                val statusResult = sshRepository.executeSudoCommand("guardiand status 2>/dev/null")
                val statusOutput = statusResult.getOrNull()?.output?.trim() ?: "Unknown"

                // Get monitored processes
                val listResult = sshRepository.executeSudoCommand("guardiand list 2>/dev/null")
                val processes = parseProcesses(listResult.getOrNull()?.output ?: "")

                // Get recent events
                val eventsResult = sshRepository.executeSudoCommand("guardiand events --tail 20 2>/dev/null")
                val events = eventsResult.getOrNull()?.output?.trim()?.lines()
                    ?.filter { it.isNotBlank() } ?: emptyList()

                _state.update { it.copy(
                    isLoading = false,
                    guardianStatus = statusOutput,
                    processes = processes,
                    recentEvents = events,
                    guardianVersion = version
                )}
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun parseProcesses(output: String): List<GuardianProcess> {
        if (output.isBlank()) return emptyList()
        return output.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 3) {
                GuardianProcess(
                    name = parts[0],
                    pid = parts.getOrElse(1) { "-" },
                    status = parts.getOrElse(2) { "unknown" },
                    cpu = parts.getOrElse(3) { "-" },
                    memory = parts.getOrElse(4) { "-" }
                )
            } else null
        }
    }

    fun onEvent(event: GuardianEvent) {
        when (event) {
            is GuardianEvent.Refresh -> loadGuardianData()
        }
    }
}
