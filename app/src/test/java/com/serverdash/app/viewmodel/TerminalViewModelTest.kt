package com.serverdash.app.viewmodel

import com.google.common.truth.Truth.assertThat
import com.serverdash.app.data.local.db.TerminalHistoryDao
import com.serverdash.app.data.local.db.TerminalHistoryEntity
import com.serverdash.app.domain.model.CommandResult
import com.serverdash.app.domain.usecase.ExecuteCommandUseCase
import com.serverdash.app.presentation.screens.terminal.TerminalEvent
import com.serverdash.app.presentation.screens.terminal.TerminalViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var executeCommand: ExecuteCommandUseCase
    private lateinit var terminalHistoryDao: TerminalHistoryDao

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        executeCommand = mockk()
        terminalHistoryDao = mockk()
        every { terminalHistoryDao.observeAll() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = TerminalViewModel(executeCommand, terminalHistoryDao)

    @Test
    fun `initial state has empty command`() {
        val vm = createViewModel()
        assertThat(vm.state.value.currentCommand).isEmpty()
    }

    @Test
    fun `update command updates state`() {
        val vm = createViewModel()
        vm.onEvent(TerminalEvent.UpdateCommand("ls -la"))
        assertThat(vm.state.value.currentCommand).isEqualTo("ls -la")
    }

    @Test
    fun `execute blank command does nothing`() = runTest {
        val vm = createViewModel()
        vm.onEvent(TerminalEvent.Execute)
        coVerify(exactly = 0) { executeCommand(any()) }
    }

    @Test
    fun `successful command execution adds entry`() = runTest {
        coEvery { executeCommand(any()) } returns Result.success(CommandResult(0, "output"))
        coEvery { terminalHistoryDao.insert(any()) } just runs

        val vm = createViewModel()
        vm.onEvent(TerminalEvent.UpdateCommand("uptime"))
        vm.onEvent(TerminalEvent.Execute)
        advanceUntilIdle()

        assertThat(vm.state.value.isExecuting).isFalse()
        assertThat(vm.state.value.currentCommand).isEmpty()
        coVerify { terminalHistoryDao.insert(match { it.command == "uptime" }) }
    }

    @Test
    fun `failed command execution still saves to history`() = runTest {
        coEvery { executeCommand(any()) } returns Result.failure(Exception("conn lost"))
        coEvery { terminalHistoryDao.insert(any()) } just runs

        val vm = createViewModel()
        vm.onEvent(TerminalEvent.UpdateCommand("bad-cmd"))
        vm.onEvent(TerminalEvent.Execute)
        advanceUntilIdle()

        coVerify { terminalHistoryDao.insert(match { it.command == "bad-cmd" && it.exitCode == -1 }) }
    }

    @Test
    fun `clear screen empties entries`() {
        val vm = createViewModel()
        vm.onEvent(TerminalEvent.ClearScreen)
        assertThat(vm.state.value.entries).isEmpty()
    }
}
