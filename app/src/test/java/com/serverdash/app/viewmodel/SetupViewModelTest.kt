package com.serverdash.app.viewmodel

import com.google.common.truth.Truth.assertThat
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.usecase.*
import com.serverdash.app.presentation.screens.setup.SetupEvent
import com.serverdash.app.presentation.screens.setup.SetupViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var connectToServer: ConnectToServerUseCase
    private lateinit var discoverServices: DiscoverServicesUseCase
    private lateinit var pinService: PinServiceUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        connectToServer = mockk()
        discoverServices = mockk()
        pinService = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SetupViewModel(connectToServer, discoverServices, pinService)

    @Test
    fun `initial state has step 0`() {
        val vm = createViewModel()
        assertThat(vm.state.value.currentStep).isEqualTo(0)
    }

    @Test
    fun `update host updates state`() {
        val vm = createViewModel()
        vm.onEvent(SetupEvent.UpdateHost("192.168.1.1"))
        assertThat(vm.state.value.host).isEqualTo("192.168.1.1")
    }

    @Test
    fun `connect with empty host shows error`() = runTest {
        val vm = createViewModel()
        vm.onEvent(SetupEvent.UpdateUsername("user"))
        vm.onEvent(SetupEvent.Connect)
        assertThat(vm.state.value.connectionError).isNotNull()
    }

    @Test
    fun `connect with empty username shows error`() = runTest {
        val vm = createViewModel()
        vm.onEvent(SetupEvent.UpdateHost("host"))
        vm.onEvent(SetupEvent.Connect)
        assertThat(vm.state.value.connectionError).isNotNull()
    }

    @Test
    fun `successful connection advances step`() = runTest {
        coEvery { connectToServer(any()) } returns Result.success(1L)

        val vm = createViewModel()
        vm.onEvent(SetupEvent.UpdateHost("host"))
        vm.onEvent(SetupEvent.UpdateUsername("user"))
        vm.onEvent(SetupEvent.UpdatePassword("pass"))
        vm.onEvent(SetupEvent.Connect)
        advanceUntilIdle()

        assertThat(vm.state.value.currentStep).isEqualTo(1)
        assertThat(vm.state.value.isConnecting).isFalse()
    }

    @Test
    fun `failed connection shows error`() = runTest {
        coEvery { connectToServer(any()) } returns Result.failure(Exception("Connection refused"))

        val vm = createViewModel()
        vm.onEvent(SetupEvent.UpdateHost("host"))
        vm.onEvent(SetupEvent.UpdateUsername("user"))
        vm.onEvent(SetupEvent.UpdatePassword("pass"))
        vm.onEvent(SetupEvent.Connect)
        advanceUntilIdle()

        assertThat(vm.state.value.connectionError).contains("Connection refused")
        assertThat(vm.state.value.currentStep).isEqualTo(0)
    }

    @Test
    fun `discover services updates state`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING)
        )
        coEvery { discoverServices(any()) } returns Result.success(services)

        val vm = createViewModel()
        vm.onEvent(SetupEvent.DiscoverServices)
        advanceUntilIdle()

        assertThat(vm.state.value.discoveredServices).hasSize(1)
        assertThat(vm.state.value.selectedServices).contains("nginx")
    }

    @Test
    fun `toggle service updates selection`() = runTest {
        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING)
        )
        coEvery { discoverServices(any()) } returns Result.success(services)

        val vm = createViewModel()
        vm.onEvent(SetupEvent.DiscoverServices)
        advanceUntilIdle()

        vm.onEvent(SetupEvent.ToggleService("nginx"))
        assertThat(vm.state.value.selectedServices).doesNotContain("nginx")

        vm.onEvent(SetupEvent.ToggleService("nginx"))
        assertThat(vm.state.value.selectedServices).contains("nginx")
    }
}
