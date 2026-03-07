package com.serverdash.app.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.*
import com.serverdash.app.domain.usecase.*
import com.serverdash.app.presentation.screens.dashboard.DashboardEvent
import com.serverdash.app.presentation.screens.dashboard.DashboardViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var serviceRepository: ServiceRepository
    private lateinit var sshRepository: SshRepository
    private lateinit var metricsRepository: MetricsRepository
    private lateinit var alertRepository: AlertRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var refreshServiceStatus: RefreshServiceStatusUseCase
    private lateinit var fetchMetrics: FetchSystemMetricsUseCase
    private lateinit var evaluateAlertRules: EvaluateAlertRulesUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        serviceRepository = mockk()
        sshRepository = mockk()
        metricsRepository = mockk()
        alertRepository = mockk()
        preferencesRepository = mockk()
        refreshServiceStatus = mockk()
        fetchMetrics = mockk()
        evaluateAlertRules = mockk()

        every { serviceRepository.observeServices(any()) } returns flowOf(emptyList())
        every { sshRepository.observeConnectionState() } returns flowOf(ConnectionState())
        every { metricsRepository.observeLatestMetrics() } returns flowOf(null)
        every { alertRepository.observeActiveAlerts() } returns flowOf(emptyList())
        coEvery { preferencesRepository.getPreferences() } returns AppPreferences()
        coEvery { metricsRepository.getMetricsHistory(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DashboardViewModel(
        serviceRepository, sshRepository, metricsRepository, alertRepository,
        preferencesRepository, refreshServiceStatus, fetchMetrics, evaluateAlertRules
    )

    @Test
    fun `initial state is loading`() = runTest {
        val vm = createViewModel()
        assertThat(vm.state.value.isLoading).isTrue()
    }

    @Test
    fun `services are loaded from repository`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING),
            Service(2, 1, "mysql", "mysql", ServiceType.SYSTEMD, ServiceStatus.STOPPED)
        )
        every { serviceRepository.observeServices(any()) } returns flowOf(services)

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.services).hasSize(2)
        assertThat(vm.state.value.services[0].name).isEqualTo("nginx")
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `connection state is observed`() = runTest {
        val connState = ConnectionState(isConnected = true, lastConnected = 123)
        every { sshRepository.observeConnectionState() } returns flowOf(connState)

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.connectionState.isConnected).isTrue()
    }

    @Test
    fun `refresh event updates isRefreshing`() = runTest {
        coEvery { refreshServiceStatus(any()) } returns Result.success(emptyList())
        coEvery { fetchMetrics() } returns Result.success(SystemMetrics())
        coEvery { evaluateAlertRules(any(), any(), any()) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.Refresh)
        advanceUntilIdle()

        assertThat(vm.state.value.isRefreshing).isFalse()
    }

    @Test
    fun `acknowledge alerts calls repository`() = runTest {
        val alert = Alert(id = 1, rule = AlertRule(1, 1, "test", AlertCondition.CpuAbove(80f)), message = "CPU high")
        every { alertRepository.observeActiveAlerts() } returns flowOf(listOf(alert))
        coEvery { alertRepository.acknowledgeAlert(any()) } just runs

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.AcknowledgeAlerts)
        advanceUntilIdle()

        coVerify { alertRepository.acknowledgeAlert(1) }
    }
}
