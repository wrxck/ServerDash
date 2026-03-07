package com.serverdash.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.MetricsRepository
import com.serverdash.app.domain.repository.ServiceRepository
import com.serverdash.app.domain.usecase.*
import com.serverdash.app.presentation.screens.detail.ServiceDetailEvent
import com.serverdash.app.presentation.screens.detail.ServiceDetailViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var serviceRepository: ServiceRepository
    private lateinit var metricsRepository: MetricsRepository
    private lateinit var controlService: ControlServiceUseCase
    private lateinit var getServiceLogs: GetServiceLogsUseCase
    private lateinit var readConfigFile: ReadConfigFileUseCase
    private lateinit var writeConfigFile: WriteConfigFileUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = SavedStateHandle(mapOf("serviceName" to "nginx", "serviceType" to "SYSTEMD"))
        serviceRepository = mockk()
        metricsRepository = mockk()
        controlService = mockk()
        getServiceLogs = mockk()
        readConfigFile = mockk()
        writeConfigFile = mockk()

        val testService = Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING)
        every { serviceRepository.observeServices(any()) } returns flowOf(listOf(testService))
        coEvery { metricsRepository.getMetricsHistory(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ServiceDetailViewModel(
        savedStateHandle, serviceRepository, metricsRepository,
        controlService, getServiceLogs, readConfigFile, writeConfigFile
    )

    @Test
    fun `loads service from repository`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.service?.name).isEqualTo("nginx")
    }

    @Test
    fun `select tab updates state`() {
        val vm = createViewModel()
        vm.onEvent(ServiceDetailEvent.SelectTab(1))
        assertThat(vm.state.value.selectedTab).isEqualTo(1)
    }

    @Test
    fun `control service shows confirm dialog`() {
        val vm = createViewModel()
        vm.onEvent(ServiceDetailEvent.ControlService(ServiceAction.STOP))
        assertThat(vm.state.value.showConfirmDialog).isEqualTo(ServiceAction.STOP)
    }

    @Test
    fun `dismiss confirm dialog clears it`() {
        val vm = createViewModel()
        vm.onEvent(ServiceDetailEvent.ControlService(ServiceAction.STOP))
        vm.onEvent(ServiceDetailEvent.DismissConfirmDialog)
        assertThat(vm.state.value.showConfirmDialog).isNull()
    }

    @Test
    fun `confirm action executes control`() = runTest {
        coEvery { controlService(any(), any()) } returns Result.success(CommandResult(0, "OK"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(ServiceDetailEvent.ConfirmAction(ServiceAction.RESTART))
        advanceUntilIdle()

        coVerify { controlService(any(), ServiceAction.RESTART) }
        assertThat(vm.state.value.isControlling).isFalse()
    }

    @Test
    fun `load config reads file`() = runTest {
        coEvery { readConfigFile("/etc/nginx/nginx.conf") } returns Result.success("worker_processes 1;")

        val vm = createViewModel()
        vm.onEvent(ServiceDetailEvent.LoadConfig("/etc/nginx/nginx.conf"))
        advanceUntilIdle()

        assertThat(vm.state.value.configContent).isEqualTo("worker_processes 1;")
    }

    @Test
    fun `save config writes file`() = runTest {
        coEvery { writeConfigFile(any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        vm.onEvent(ServiceDetailEvent.LoadConfig("/etc/nginx/nginx.conf"))
        vm.onEvent(ServiceDetailEvent.UpdateConfig("new content"))
        vm.onEvent(ServiceDetailEvent.SaveConfig)
        advanceUntilIdle()

        coVerify { writeConfigFile("/etc/nginx/nginx.conf", "new content") }
    }
}
