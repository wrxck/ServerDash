package com.serverdash.app.presentation.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.ServerRepository
import com.serverdash.app.domain.repository.ServiceRepository
import com.serverdash.app.domain.repository.MetricsRepository
import com.serverdash.app.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServiceDetailUiState(
    val service: Service? = null,
    val selectedTab: Int = 0,
    val isControlling: Boolean = false,
    val controlResult: String? = null,
    val logs: List<ServiceLog> = emptyList(),
    val isLoadingLogs: Boolean = false,
    val metricsHistory: List<SystemMetrics> = emptyList(),
    val configContent: String = "",
    val configPath: String = "",
    val isLoadingConfig: Boolean = false,
    val isSavingConfig: Boolean = false,
    val showConfirmDialog: ServiceAction? = null,
    val error: String? = null
)

sealed interface ServiceDetailEvent {
    data class SelectTab(val index: Int) : ServiceDetailEvent
    data class ControlService(val action: ServiceAction) : ServiceDetailEvent
    data class ConfirmAction(val action: ServiceAction) : ServiceDetailEvent
    data object DismissConfirmDialog : ServiceDetailEvent
    data object RefreshLogs : ServiceDetailEvent
    data class LoadConfig(val path: String) : ServiceDetailEvent
    data class UpdateConfig(val content: String) : ServiceDetailEvent
    data object SaveConfig : ServiceDetailEvent
    data object DismissError : ServiceDetailEvent
}

@HiltViewModel
class ServiceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
    private val serviceRepository: ServiceRepository,
    private val metricsRepository: MetricsRepository,
    private val controlService: ControlServiceUseCase,
    private val getServiceLogs: GetServiceLogsUseCase,
    private val readConfigFile: ReadConfigFileUseCase,
    private val writeConfigFile: WriteConfigFileUseCase
) : ViewModel() {

    private val serviceName: String = savedStateHandle.get<String>("serviceName") ?: ""
    private val serviceType: String = savedStateHandle.get<String>("serviceType") ?: "SYSTEMD"

    private val _state = MutableStateFlow(ServiceDetailUiState())
    val state: StateFlow<ServiceDetailUiState> = _state.asStateFlow()

    private var logStreamJob: Job? = null

    init {
        loadService()
        loadMetricsHistory()
    }

    private fun loadService() {
        viewModelScope.launch {
            val serverId = serverRepository.getServerConfig()?.id ?: 1L
            serviceRepository.observeServices(serverId).collect { services ->
                val service = services.find { it.name == serviceName }
                _state.update { it.copy(service = service) }
            }
        }
    }

    private fun loadMetricsHistory() {
        viewModelScope.launch {
            val history = metricsRepository.getMetricsHistory(60)
            _state.update { it.copy(metricsHistory = history) }
        }
    }

    fun onEvent(event: ServiceDetailEvent) {
        when (event) {
            is ServiceDetailEvent.SelectTab -> {
                _state.update { it.copy(selectedTab = event.index) }
                if (event.index == 1) loadLogs()
            }
            is ServiceDetailEvent.ControlService -> {
                _state.update { it.copy(showConfirmDialog = event.action) }
            }
            is ServiceDetailEvent.ConfirmAction -> {
                _state.update { it.copy(showConfirmDialog = null) }
                executeControl(event.action)
            }
            is ServiceDetailEvent.DismissConfirmDialog -> {
                _state.update { it.copy(showConfirmDialog = null) }
            }
            is ServiceDetailEvent.RefreshLogs -> loadLogs()
            is ServiceDetailEvent.LoadConfig -> {
                _state.update { it.copy(configPath = event.path) }
                loadConfigFile(event.path)
            }
            is ServiceDetailEvent.UpdateConfig -> {
                _state.update { it.copy(configContent = event.content) }
            }
            is ServiceDetailEvent.SaveConfig -> saveConfig()
            is ServiceDetailEvent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun executeControl(action: ServiceAction) {
        val service = _state.value.service ?: return
        viewModelScope.launch {
            _state.update { it.copy(isControlling = true) }
            controlService(service, action).fold(
                onSuccess = { result ->
                    _state.update { it.copy(
                        isControlling = false,
                        controlResult = if (result.exitCode == 0) "Success" else result.error
                    )}
                },
                onFailure = { e ->
                    _state.update { it.copy(isControlling = false, error = e.message) }
                }
            )
        }
    }

    private fun loadLogs() {
        val service = _state.value.service ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingLogs = true) }
            getServiceLogs(service).fold(
                onSuccess = { logs -> _state.update { it.copy(logs = logs, isLoadingLogs = false) } },
                onFailure = { e -> _state.update { it.copy(isLoadingLogs = false, error = "Failed to load logs: ${e.message}") } }
            )
        }
    }

    private fun loadConfigFile(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingConfig = true) }
            readConfigFile(path).fold(
                onSuccess = { content -> _state.update { it.copy(configContent = content, isLoadingConfig = false) } },
                onFailure = { e -> _state.update { it.copy(isLoadingConfig = false, error = e.message) } }
            )
        }
    }

    private fun saveConfig() {
        val s = _state.value
        if (s.configPath.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSavingConfig = true) }
            writeConfigFile(s.configPath, s.configContent).fold(
                onSuccess = { _state.update { it.copy(isSavingConfig = false, controlResult = "Config saved") } },
                onFailure = { e -> _state.update { it.copy(isSavingConfig = false, error = e.message) } }
            )
        }
    }
}
