package com.serverdash.app.viewmodel

import com.google.common.truth.Truth.assertThat
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.plugin.PluginRegistry
import com.serverdash.app.domain.repository.*
import com.serverdash.app.domain.usecase.*
import com.serverdash.app.presentation.screens.dashboard.DashboardEvent
import com.serverdash.app.presentation.screens.dashboard.DashboardViewModel
import com.serverdash.app.presentation.screens.dashboard.MetricDetailType
import com.serverdash.app.widget.WidgetUpdateHelper
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val appContext: android.content.Context = mockk(relaxed = true)
    private lateinit var serviceRepository: ServiceRepository
    private lateinit var serverRepository: ServerRepository
    private lateinit var sshRepository: SshRepository
    private lateinit var metricsRepository: MetricsRepository
    private lateinit var alertRepository: AlertRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var refreshServiceStatus: RefreshServiceStatusUseCase
    private lateinit var fetchMetrics: FetchSystemMetricsUseCase
    private lateinit var evaluateAlertRules: EvaluateAlertRulesUseCase
    private lateinit var pluginRegistry: PluginRegistry
    private lateinit var fleetDiscoverServices: FleetDiscoverServicesUseCase
    private val appLockManager: com.serverdash.app.core.security.AppLockManager = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(WidgetUpdateHelper)
        coEvery { WidgetUpdateHelper.updateAllWidgets(any(), any(), any(), any(), any()) } just runs
        serviceRepository = mockk()
        serverRepository = mockk()
        sshRepository = mockk()
        metricsRepository = mockk()
        alertRepository = mockk()
        preferencesRepository = mockk()
        refreshServiceStatus = mockk()
        fetchMetrics = mockk()
        evaluateAlertRules = mockk()
        pluginRegistry = mockk()
        fleetDiscoverServices = mockk()

        // Default stubs
        every { serviceRepository.observeServices(any()) } returns flowOf(emptyList())
        every { sshRepository.observeConnectionState() } returns flowOf(ConnectionState())
        every { metricsRepository.observeLatestMetrics() } returns flowOf(null)
        every { alertRepository.observeActiveAlerts() } returns flowOf(emptyList())
        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences())
        coEvery { preferencesRepository.getPreferences() } returns AppPreferences()
        coEvery { serverRepository.getServerConfig() } returns null
        coEvery { sshRepository.isConnected() } returns false
        coEvery { metricsRepository.getMetricsHistory(any()) } returns emptyList()
        coEvery { pluginRegistry.detectAll(any()) } returns emptyMap()
        coEvery { refreshServiceStatus(any()) } returns Result.success(emptyList())
        coEvery { fetchMetrics() } returns Result.success(SystemMetrics())
        coEvery { evaluateAlertRules(any(), any(), any()) } returns emptyList()
        coEvery { fleetDiscoverServices(any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(WidgetUpdateHelper)
    }

    private fun createViewModel() = DashboardViewModel(
        appContext, serviceRepository, serverRepository, sshRepository, metricsRepository, alertRepository,
        preferencesRepository, refreshServiceStatus, fetchMetrics, evaluateAlertRules,
        pluginRegistry, fleetDiscoverServices, appLockManager
    )

    // -----------------------------------------------------------------------
    // Existing tests (fixed)
    // -----------------------------------------------------------------------

    @Test
    fun `initial state defaults`() = runTest {
        val vm = createViewModel()
        // Before any coroutines execute, state has defaults
        assertThat(vm.state.value.services).isEmpty()
        assertThat(vm.state.value.connectionState.isConnected).isFalse()
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
        // Use a MutableStateFlow so the connection flow doesn't complete immediately
        val connFlow = MutableStateFlow(ConnectionState(isConnected = true, lastConnected = 123))
        every { sshRepository.observeConnectionState() } returns connFlow

        val vm = createViewModel()
        // Advance enough for init to complete and first poll to start, but not loop
        advanceTimeBy(100)
        runCurrent()

        assertThat(vm.state.value.connectionState.isConnected).isTrue()

        // Disconnect to stop the polling loop so runTest can complete
        connFlow.value = ConnectionState(isConnected = false)
        advanceUntilIdle()
    }

    @Test
    fun `refresh event updates isRefreshing`() = runTest {
        coEvery { refreshServiceStatus(any()) } returns Result.success(emptyList())
        coEvery { fetchMetrics() } returns Result.success(SystemMetrics())
        coEvery { evaluateAlertRules(any(), any(), any()) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.Refresh)
        advanceTimeBy(500)
        runCurrent()

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

    // -----------------------------------------------------------------------
    // Metric detail tests
    // -----------------------------------------------------------------------

    @Test
    fun `OpenMetricDetail sets activeMetricDetail to CPU`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.CPU))
        advanceUntilIdle()

        assertThat(vm.state.value.activeMetricDetail).isEqualTo(MetricDetailType.CPU)
    }

    @Test
    fun `OpenMetricDetail sets activeMetricDetail to MEMORY`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.MEMORY))
        advanceUntilIdle()

        assertThat(vm.state.value.activeMetricDetail).isEqualTo(MetricDetailType.MEMORY)
    }

    @Test
    fun `OpenMetricDetail PROCESSES triggers process loading`() = runTest {
        val psOutput = "USER       PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND\n" +
            "root         1  2.5  0.1 225832  9876 ?        Ss   Mar01   0:15 /sbin/init\n" +
            "www-data   456  8.3  1.5 654321 15360 ?        S    Mar01   1:23 nginx: worker"
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = psOutput)
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.PROCESSES))
        advanceUntilIdle()

        assertThat(vm.state.value.activeMetricDetail).isEqualTo(MetricDetailType.PROCESSES)
        assertThat(vm.state.value.processList).hasSize(2)
        assertThat(vm.state.value.isLoadingProcesses).isFalse()
    }

    @Test
    fun `CloseMetricDetail clears activeMetricDetail`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.CPU))
        advanceUntilIdle()
        assertThat(vm.state.value.activeMetricDetail).isEqualTo(MetricDetailType.CPU)

        vm.onEvent(DashboardEvent.CloseMetricDetail)
        advanceUntilIdle()

        assertThat(vm.state.value.activeMetricDetail).isNull()
    }

    // -----------------------------------------------------------------------
    // Process list tests
    // -----------------------------------------------------------------------

    @Test
    fun `process list parses ps aux output correctly`() = runTest {
        val psOutput = "USER       PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND\n" +
            "root         1  2.5  0.1   1024   512 ?        Ss   Mar01   0:15 /sbin/init\n" +
            "nobody     200 15.0  3.2   2048  1024 ?        R    Mar01   2:00 /usr/bin/app"
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = psOutput)
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.PROCESSES))
        advanceUntilIdle()

        val processes = vm.state.value.processList
        assertThat(processes).hasSize(2)

        // Default sort is by CPU descending, so highest CPU first
        assertThat(processes[0].pid).isEqualTo(200)
        assertThat(processes[0].user).isEqualTo("nobody")
        assertThat(processes[0].cpuPercent).isEqualTo(15.0f)
        assertThat(processes[0].memPercent).isEqualTo(3.2f)
        assertThat(processes[0].command).isEqualTo("/usr/bin/app")

        assertThat(processes[1].pid).isEqualTo(1)
        assertThat(processes[1].user).isEqualTo("root")
    }

    @Test
    fun `process list loading failure sets error`() = runTest {
        coEvery { sshRepository.executeCommand(any()) } returns Result.failure(
            Exception("Connection lost")
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.PROCESSES))
        advanceUntilIdle()

        assertThat(vm.state.value.isLoadingProcesses).isFalse()
        assertThat(vm.state.value.error).contains("Process list failed")
    }

    // -----------------------------------------------------------------------
    // Process sorting tests
    // -----------------------------------------------------------------------

    @Test
    fun `SortProcesses by cpu sorts descending by CPU`() = runTest {
        val psOutput = "USER       PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND\n" +
            "root         1  2.5  0.1   1024   512 ?        Ss   Mar01   0:15 /sbin/init\n" +
            "nobody     200 15.0  3.2   2048  1024 ?        R    Mar01   2:00 /usr/bin/app\n" +
            "www        300  8.0  1.0   1024   512 ?        S    Mar01   0:30 nginx"
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = psOutput)
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.PROCESSES))
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.SortProcesses("cpu"))
        advanceUntilIdle()

        val processes = vm.state.value.processList
        assertThat(processes[0].cpuPercent).isEqualTo(15.0f)
        assertThat(processes[1].cpuPercent).isEqualTo(8.0f)
        assertThat(processes[2].cpuPercent).isEqualTo(2.5f)
        assertThat(vm.state.value.processSortBy).isEqualTo("cpu")
    }

    @Test
    fun `SortProcesses by mem sorts descending by memory`() = runTest {
        val psOutput = "USER       PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND\n" +
            "root         1  2.5  0.1   1024   512 ?        Ss   Mar01   0:15 /sbin/init\n" +
            "nobody     200 15.0  3.2   2048  1024 ?        R    Mar01   2:00 /usr/bin/app\n" +
            "www        300  8.0  1.0   1024   512 ?        S    Mar01   0:30 nginx"
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = psOutput)
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.PROCESSES))
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.SortProcesses("mem"))
        advanceUntilIdle()

        val processes = vm.state.value.processList
        assertThat(processes[0].memPercent).isEqualTo(3.2f)
        assertThat(processes[1].memPercent).isEqualTo(1.0f)
        assertThat(processes[2].memPercent).isEqualTo(0.1f)
        assertThat(vm.state.value.processSortBy).isEqualTo("mem")
    }

    @Test
    fun `SortProcesses by pid sorts ascending by PID`() = runTest {
        val psOutput = "USER       PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND\n" +
            "nobody     200 15.0  3.2   2048  1024 ?        R    Mar01   2:00 /usr/bin/app\n" +
            "root         1  2.5  0.1   1024   512 ?        Ss   Mar01   0:15 /sbin/init\n" +
            "www        300  8.0  1.0   1024   512 ?        S    Mar01   0:30 nginx"
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = psOutput)
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.OpenMetricDetail(MetricDetailType.PROCESSES))
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.SortProcesses("pid"))
        advanceUntilIdle()

        val processes = vm.state.value.processList
        assertThat(processes[0].pid).isEqualTo(1)
        assertThat(processes[1].pid).isEqualTo(200)
        assertThat(processes[2].pid).isEqualTo(300)
        assertThat(vm.state.value.processSortBy).isEqualTo("pid")
    }

    // -----------------------------------------------------------------------
    // Kill process test
    // -----------------------------------------------------------------------

    @Test
    fun `KillProcess sends kill command via SSH`() = runTest {
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = "")
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.KillProcess(1234))
        advanceUntilIdle()

        coVerify { sshRepository.executeCommand("kill 1234 2>/dev/null") }
    }

    // -----------------------------------------------------------------------
    // ToggleShowNonFleetServices test
    // -----------------------------------------------------------------------

    @Test
    fun `ToggleShowNonFleetServices toggles flag and triggers refresh`() = runTest {
        coEvery { refreshServiceStatus(any()) } returns Result.success(emptyList())
        coEvery { fetchMetrics() } returns Result.success(SystemMetrics())
        coEvery { evaluateAlertRules(any(), any(), any()) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.showNonFleetServices).isFalse()

        vm.onEvent(DashboardEvent.ToggleShowNonFleetServices)
        advanceUntilIdle()

        assertThat(vm.state.value.showNonFleetServices).isTrue()

        // Toggle again to verify it flips back
        vm.onEvent(DashboardEvent.ToggleShowNonFleetServices)
        advanceUntilIdle()

        assertThat(vm.state.value.showNonFleetServices).isFalse()
    }

    // -----------------------------------------------------------------------
    // Service filtering tests
    // -----------------------------------------------------------------------

    @Test
    fun `UpdateSearch filters services by name`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING),
            Service(2, 1, "mysql", "mysql", ServiceType.SYSTEMD, ServiceStatus.RUNNING),
            Service(3, 1, "redis", "redis", ServiceType.SYSTEMD, ServiceStatus.STOPPED)
        )
        every { serviceRepository.observeServices(any()) } returns flowOf(services)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.UpdateSearch("nginx"))
        advanceUntilIdle()

        assertThat(vm.state.value.searchQuery).isEqualTo("nginx")
        assertThat(vm.state.value.filteredServices).hasSize(1)
        assertThat(vm.state.value.filteredServices[0].name).isEqualTo("nginx")
    }

    @Test
    fun `ToggleStatusFilter filters services by status`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING),
            Service(2, 1, "mysql", "mysql", ServiceType.SYSTEMD, ServiceStatus.STOPPED),
            Service(3, 1, "redis", "redis", ServiceType.SYSTEMD, ServiceStatus.RUNNING)
        )
        every { serviceRepository.observeServices(any()) } returns flowOf(services)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.ToggleStatusFilter(ServiceStatus.RUNNING))
        advanceUntilIdle()

        assertThat(vm.state.value.statusFilters).containsExactly(ServiceStatus.RUNNING)
        assertThat(vm.state.value.filteredServices).hasSize(2)
        assertThat(vm.state.value.filteredServices.all { it.status == ServiceStatus.RUNNING }).isTrue()
    }

    @Test
    fun `ToggleStatusFilter removes filter when toggled again`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING),
            Service(2, 1, "mysql", "mysql", ServiceType.SYSTEMD, ServiceStatus.STOPPED)
        )
        every { serviceRepository.observeServices(any()) } returns flowOf(services)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.ToggleStatusFilter(ServiceStatus.RUNNING))
        advanceUntilIdle()
        assertThat(vm.state.value.statusFilters).containsExactly(ServiceStatus.RUNNING)

        vm.onEvent(DashboardEvent.ToggleStatusFilter(ServiceStatus.RUNNING))
        advanceUntilIdle()
        assertThat(vm.state.value.statusFilters).isEmpty()
        assertThat(vm.state.value.filteredServices).hasSize(2)
    }

    @Test
    fun `ToggleTypeFilter filters services by type`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING),
            Service(2, 1, "myapp", "myapp", ServiceType.DOCKER, ServiceStatus.RUNNING),
            Service(3, 1, "redis", "redis", ServiceType.DOCKER, ServiceStatus.STOPPED)
        )
        every { serviceRepository.observeServices(any()) } returns flowOf(services)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.ToggleTypeFilter(ServiceType.DOCKER))
        advanceUntilIdle()

        assertThat(vm.state.value.typeFilters).containsExactly(ServiceType.DOCKER)
        assertThat(vm.state.value.filteredServices).hasSize(2)
        assertThat(vm.state.value.filteredServices.all { it.type == ServiceType.DOCKER }).isTrue()
    }

    @Test
    fun `ClearFilters resets all filters and search`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING),
            Service(2, 1, "mysql", "mysql", ServiceType.SYSTEMD, ServiceStatus.STOPPED)
        )
        every { serviceRepository.observeServices(any()) } returns flowOf(services)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.UpdateSearch("nginx"))
        vm.onEvent(DashboardEvent.ToggleStatusFilter(ServiceStatus.RUNNING))
        vm.onEvent(DashboardEvent.ToggleTypeFilter(ServiceType.SYSTEMD))
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.ClearFilters)
        advanceUntilIdle()

        assertThat(vm.state.value.searchQuery).isEmpty()
        assertThat(vm.state.value.statusFilters).isEmpty()
        assertThat(vm.state.value.typeFilters).isEmpty()
        assertThat(vm.state.value.selectedTab).isEqualTo(0)
        assertThat(vm.state.value.filteredServices).hasSize(2)
    }

    @Test
    fun `combined search and status filter narrows results`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx-prod", "nginx-prod", ServiceType.SYSTEMD, ServiceStatus.RUNNING),
            Service(2, 1, "nginx-staging", "nginx-staging", ServiceType.SYSTEMD, ServiceStatus.STOPPED),
            Service(3, 1, "mysql", "mysql", ServiceType.SYSTEMD, ServiceStatus.RUNNING)
        )
        every { serviceRepository.observeServices(any()) } returns flowOf(services)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.UpdateSearch("nginx"))
        vm.onEvent(DashboardEvent.ToggleStatusFilter(ServiceStatus.RUNNING))
        advanceUntilIdle()

        assertThat(vm.state.value.filteredServices).hasSize(1)
        assertThat(vm.state.value.filteredServices[0].name).isEqualTo("nginx-prod")
    }

    // -----------------------------------------------------------------------
    // Tab selection tests
    // -----------------------------------------------------------------------

    @Test
    fun `SelectTab updates selectedTab`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING, group = "Web"),
            Service(2, 1, "myapp", "myapp", ServiceType.DOCKER, ServiceStatus.RUNNING, group = "Apps")
        )
        every { serviceRepository.observeServices(any()) } returns flowOf(services)

        val vm = createViewModel()
        advanceUntilIdle()

        // Tab 0 is "All", groups are sorted alphabetically so tabs are: All, Apps, Web
        assertThat(vm.state.value.availableTabs).containsExactly("All", "Apps", "Web").inOrder()

        vm.onEvent(DashboardEvent.SelectTab(2))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedTab).isEqualTo(2)
        assertThat(vm.state.value.filteredServices).hasSize(1)
        assertThat(vm.state.value.filteredServices[0].name).isEqualTo("nginx")
    }

    @Test
    fun `SelectTab 0 shows all services`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING, group = "Web"),
            Service(2, 1, "myapp", "myapp", ServiceType.DOCKER, ServiceStatus.RUNNING, group = "Apps")
        )
        every { serviceRepository.observeServices(any()) } returns flowOf(services)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.SelectTab(1))
        advanceUntilIdle()
        assertThat(vm.state.value.filteredServices).hasSize(1)

        vm.onEvent(DashboardEvent.SelectTab(0))
        advanceUntilIdle()
        assertThat(vm.state.value.filteredServices).hasSize(2)
    }

    @Test
    fun `isFiltered returns true when search is active`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.isFiltered).isFalse()

        vm.onEvent(DashboardEvent.UpdateSearch("test"))
        advanceUntilIdle()

        assertThat(vm.state.value.isFiltered).isTrue()
    }

    @Test
    fun `isFiltered returns true when tab is not All`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING, group = "Web")
        )
        every { serviceRepository.observeServices(any()) } returns flowOf(services)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(DashboardEvent.SelectTab(1))
        advanceUntilIdle()

        assertThat(vm.state.value.isFiltered).isTrue()
    }
}
