package com.serverdash.app.viewmodel

import com.google.common.truth.Truth.assertThat
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.*
import com.serverdash.app.presentation.screens.settings.SettingsEvent
import com.serverdash.app.presentation.screens.settings.SettingsViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var serverRepository: ServerRepository
    private lateinit var sshRepository: SshRepository
    private lateinit var pluginRegistry: com.serverdash.app.domain.plugin.PluginRegistry

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        preferencesRepository = mockk()
        serverRepository = mockk()
        sshRepository = mockk()
        pluginRegistry = com.serverdash.app.domain.plugin.PluginRegistry()

        every { preferencesRepository.observePreferences() } returns flowOf(AppPreferences())
        every { serverRepository.observeServerConfig() } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(preferencesRepository, serverRepository, sshRepository, pluginRegistry)

    @Test
    fun `initial state has default preferences`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.preferences.themeMode).isEqualTo(ThemeMode.AUTO)
    }

    @Test
    fun `update theme mode calls repository`() = runTest {
        coEvery { preferencesRepository.updatePreferences(any()) } just runs

        val vm = createViewModel()
        vm.onEvent(SettingsEvent.UpdateThemeMode(ThemeMode.DARK))
        advanceUntilIdle()

        coVerify { preferencesRepository.updatePreferences(any()) }
    }

    @Test
    fun `disconnect shows confirmation dialog`() {
        val vm = createViewModel()
        vm.onEvent(SettingsEvent.Disconnect)
        assertThat(vm.state.value.showDisconnectConfirm).isTrue()
    }

    @Test
    fun `dismiss disconnect hides dialog`() {
        val vm = createViewModel()
        vm.onEvent(SettingsEvent.Disconnect)
        vm.onEvent(SettingsEvent.DismissDisconnect)
        assertThat(vm.state.value.showDisconnectConfirm).isFalse()
    }

    @Test
    fun `confirm disconnect calls ssh disconnect`() = runTest {
        coEvery { sshRepository.disconnect() } just runs

        val vm = createViewModel()
        vm.onEvent(SettingsEvent.ConfirmDisconnect)
        advanceUntilIdle()

        coVerify { sshRepository.disconnect() }
    }

    @Test
    fun `reset shows confirmation dialog`() {
        val vm = createViewModel()
        vm.onEvent(SettingsEvent.ResetApp)
        assertThat(vm.state.value.showResetConfirm).isTrue()
    }

    @Test
    fun `confirm reset disconnects and deletes config`() = runTest {
        coEvery { sshRepository.disconnect() } just runs
        coEvery { serverRepository.deleteServerConfig() } just runs

        val vm = createViewModel()
        vm.onEvent(SettingsEvent.ConfirmReset)
        advanceUntilIdle()

        coVerify {
            sshRepository.disconnect()
            serverRepository.deleteServerConfig()
        }
    }

    @Test
    fun `update keep screen on calls repository`() = runTest {
        coEvery { preferencesRepository.updatePreferences(any()) } just runs

        val vm = createViewModel()
        vm.onEvent(SettingsEvent.UpdateKeepScreenOn(false))
        advanceUntilIdle()

        coVerify { preferencesRepository.updatePreferences(any()) }
    }
}
