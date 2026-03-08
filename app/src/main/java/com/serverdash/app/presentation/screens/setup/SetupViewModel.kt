package com.serverdash.app.presentation.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val currentStep: Int = 0,
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authType: String = "password",
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val isConnecting: Boolean = false,
    val isDiscovering: Boolean = false,
    val connectionError: String? = null,
    val discoveredServices: List<Service> = emptyList(),
    val selectedServices: Set<String> = emptySet(),
    val sudoPassword: String = "",
    val isComplete: Boolean = false,
    val serverId: Long = 1L
)

sealed interface SetupEvent {
    data class UpdateHost(val host: String) : SetupEvent
    data class UpdatePort(val port: String) : SetupEvent
    data class UpdateUsername(val username: String) : SetupEvent
    data class UpdateAuthType(val type: String) : SetupEvent
    data class UpdatePassword(val password: String) : SetupEvent
    data class UpdatePrivateKey(val key: String) : SetupEvent
    data class UpdatePassphrase(val passphrase: String) : SetupEvent
    data class UpdateSudoPassword(val password: String) : SetupEvent
    data object Connect : SetupEvent
    data object DiscoverServices : SetupEvent
    data class ToggleService(val serviceName: String) : SetupEvent
    data object SelectAll : SetupEvent
    data object CompleteSetup : SetupEvent
    data object NextStep : SetupEvent
    data object PrevStep : SetupEvent
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val connectToServer: ConnectToServerUseCase,
    private val discoverServices: DiscoverServicesUseCase,
    private val pinService: PinServiceUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun onEvent(event: SetupEvent) {
        when (event) {
            is SetupEvent.UpdateHost -> _state.update { it.copy(host = event.host, connectionError = null) }
            is SetupEvent.UpdatePort -> _state.update { it.copy(port = event.port) }
            is SetupEvent.UpdateUsername -> _state.update { it.copy(username = event.username) }
            is SetupEvent.UpdateAuthType -> _state.update { it.copy(authType = event.type) }
            is SetupEvent.UpdatePassword -> _state.update { it.copy(password = event.password) }
            is SetupEvent.UpdatePrivateKey -> _state.update { it.copy(privateKey = event.key) }
            is SetupEvent.UpdatePassphrase -> _state.update { it.copy(passphrase = event.passphrase) }
            is SetupEvent.UpdateSudoPassword -> _state.update { it.copy(sudoPassword = event.password) }
            is SetupEvent.Connect -> connect()
            is SetupEvent.DiscoverServices -> discover()
            is SetupEvent.ToggleService -> toggleService(event.serviceName)
            is SetupEvent.SelectAll -> selectAll()
            is SetupEvent.CompleteSetup -> completeSetup()
            is SetupEvent.NextStep -> _state.update { it.copy(currentStep = (it.currentStep + 1).coerceAtMost(3)) }
            is SetupEvent.PrevStep -> _state.update { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(0)) }
        }
    }

    private fun connect() {
        val s = _state.value
        if (s.host.isBlank() || s.username.isBlank()) {
            _state.update { it.copy(connectionError = "Host and username are required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isConnecting = true, connectionError = null) }
            val authMethod = if (s.authType == "key") {
                AuthMethod.KeyBased(s.privateKey, s.passphrase)
            } else {
                AuthMethod.Password(s.password)
            }
            val config = ServerConfig(
                host = s.host,
                port = s.port.toIntOrNull() ?: 22,
                username = s.username,
                authMethod = authMethod,
                sudoPassword = s.sudoPassword
            )
            connectToServer(config).fold(
                onSuccess = { id -> _state.update { it.copy(isConnecting = false, currentStep = 1, serverId = id) } },
                onFailure = { e -> _state.update { it.copy(isConnecting = false, connectionError = e.message) } }
            )
        }
    }

    private fun discover() {
        viewModelScope.launch {
            _state.update { it.copy(isDiscovering = true, connectionError = null) }
            discoverServices(_state.value.serverId).fold(
                onSuccess = { services ->
                    _state.update { it.copy(
                        isDiscovering = false,
                        discoveredServices = services,
                        selectedServices = services.map { s -> s.name }.toSet()
                    )}
                },
                onFailure = { e ->
                    _state.update { it.copy(isDiscovering = false, connectionError = "Discovery failed: ${e.message}") }
                }
            )
        }
    }

    private fun toggleService(name: String) {
        _state.update { state ->
            val selected = state.selectedServices.toMutableSet()
            if (name in selected) selected.remove(name) else selected.add(name)
            state.copy(selectedServices = selected)
        }
    }

    private fun selectAll() {
        _state.update { state ->
            state.copy(selectedServices = state.discoveredServices.map { it.name }.toSet())
        }
    }

    private fun completeSetup() {
        viewModelScope.launch {
            val state = _state.value
            state.discoveredServices.forEach { service ->
                if (service.name in state.selectedServices) {
                    pinService(service.id, true)
                }
            }
            _state.update { it.copy(isComplete = true) }
        }
    }
}
