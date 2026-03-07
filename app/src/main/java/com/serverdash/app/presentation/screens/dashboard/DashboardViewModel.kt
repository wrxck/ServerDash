package com.serverdash.app.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.*
import com.serverdash.app.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val services: List<Service> = emptyList(),
    val metrics: SystemMetrics? = null,
    val connectionState: ConnectionState = ConnectionState(),
    val activeAlerts: List<Alert> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val metricsHistory: List<SystemMetrics> = emptyList()
)

sealed interface DashboardEvent {
    data object Refresh : DashboardEvent
    data class NavigateToDetail(val service: Service) : DashboardEvent
    data object DismissError : DashboardEvent
    data object AcknowledgeAlerts : DashboardEvent
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val serviceRepository: ServiceRepository,
    private val sshRepository: SshRepository,
    private val metricsRepository: MetricsRepository,
    private val alertRepository: AlertRepository,
    private val preferencesRepository: PreferencesRepository,
    private val refreshServiceStatus: RefreshServiceStatusUseCase,
    private val fetchMetrics: FetchSystemMetricsUseCase,
    private val evaluateAlertRules: EvaluateAlertRulesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private val _navigateToDetail = MutableSharedFlow<Service>()
    val navigateToDetail: SharedFlow<Service> = _navigateToDetail.asSharedFlow()

    init {
        observeData()
        startPolling()
    }

    private fun observeData() {
        viewModelScope.launch {
            serviceRepository.observeServices(1L).collect { services ->
                _state.update { it.copy(services = services, isLoading = false) }
            }
        }
        viewModelScope.launch {
            sshRepository.observeConnectionState().collect { conn ->
                _state.update { it.copy(connectionState = conn) }
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

    private suspend fun refreshData() {
        refreshServiceStatus(1L)
        fetchMetrics()
        val currentState = _state.value
        if (currentState.metrics != null) {
            evaluateAlertRules(currentState.services, currentState.metrics!!, 1L)
        }
        val history = metricsRepository.getMetricsHistory(60)
        _state.update { it.copy(metricsHistory = history) }
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
        }
    }
}
