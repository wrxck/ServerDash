package com.serverdash.app.presentation.screens.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.domain.model.CommandResult
import com.serverdash.app.domain.repository.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import javax.inject.Inject

data class FleetApp(
    val name: String,
    val displayName: String,
    val status: String,
    val type: String,
    val domains: List<String>,
    val containers: List<FleetContainer>,
    val composePath: String,
    val port: Int?
)

data class FleetContainer(
    val name: String,
    val state: String,
    val image: String
)

data class FleetUiState(
    val apps: List<FleetApp> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val fleetVersion: String = "",
    val commandOutput: String? = null
)

sealed interface FleetEvent {
    data object Refresh : FleetEvent
    data class RunCommand(val app: String, val action: String) : FleetEvent
    data object DismissOutput : FleetEvent
}

@HiltViewModel
class FleetViewModel @Inject constructor(
    private val sshRepository: SshRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FleetUiState())
    val state: StateFlow<FleetUiState> = _state.asStateFlow()

    init {
        loadFleetData()
    }

    private fun loadFleetData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // Get fleet version
                val versionResult = sshRepository.executeSudoCommand("fleet --version 2>/dev/null")
                val version = versionResult.getOrNull()?.output?.trim() ?: ""

                // Get fleet status as JSON
                val statusResult = sshRepository.executeSudoCommand("fleet list --json 2>/dev/null")
                val output = statusResult.getOrNull()?.output?.trim()
                if (output.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, error = "Could not get fleet status", fleetVersion = version) }
                    return@launch
                }

                val apps = parseFleetApps(output)
                _state.update { it.copy(apps = apps, isLoading = false, fleetVersion = version) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun parseFleetApps(jsonStr: String): List<FleetApp> {
        return try {
            val root = Json.parseToJsonElement(jsonStr).jsonObject
            val apps = root["apps"]?.jsonArray ?: return emptyList()
            apps.mapNotNull { element ->
                val app = element.jsonObject
                val name = app["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val systemdState = app["systemd"]?.jsonObject?.get("active")?.jsonPrimitive?.content ?: "unknown"
                val containers = app["containers"]?.jsonArray?.map { c ->
                    val co = c.jsonObject
                    FleetContainer(
                        name = co["name"]?.jsonPrimitive?.content ?: "",
                        state = co["state"]?.jsonPrimitive?.content ?: "unknown",
                        image = co["image"]?.jsonPrimitive?.content ?: ""
                    )
                } ?: emptyList()
                val containerUp = containers.any { it.state == "running" }
                val status = when {
                    containerUp -> "running"
                    systemdState == "active" -> "active"
                    systemdState == "failed" -> "failed"
                    else -> "stopped"
                }
                val domains = app["domains"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                FleetApp(
                    name = name,
                    displayName = app["displayName"]?.jsonPrimitive?.content ?: name,
                    status = status,
                    type = app["type"]?.jsonPrimitive?.content ?: "service",
                    domains = domains,
                    containers = containers,
                    composePath = app["composePath"]?.jsonPrimitive?.content ?: "",
                    port = app["port"]?.jsonPrimitive?.intOrNull
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun onEvent(event: FleetEvent) {
        when (event) {
            is FleetEvent.Refresh -> loadFleetData()
            is FleetEvent.RunCommand -> {
                viewModelScope.launch {
                    val cmd = when (event.action) {
                        "restart" -> "fleet restart ${event.app}"
                        "stop" -> "fleet stop ${event.app}"
                        "start" -> "fleet start ${event.app}"
                        "logs" -> "fleet logs ${event.app} --tail 50"
                        else -> return@launch
                    }
                    val result = sshRepository.executeSudoCommand(cmd)
                    val output = result.getOrNull()?.let { "${it.output}\n${it.error}".trim() }
                        ?: result.exceptionOrNull()?.message ?: "Command failed"
                    _state.update { it.copy(commandOutput = output) }
                    if (event.action != "logs") loadFleetData()
                }
            }
            is FleetEvent.DismissOutput -> {
                _state.update { it.copy(commandOutput = null) }
            }
        }
    }
}
