package com.serverdash.ide

import com.google.common.truth.Truth.assertThat
import com.serverdash.ide.model.RemoteFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fileProvider: FileProvider
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fileProvider = mockk(relaxed = true)
        coEvery { fileProvider.listFiles(any()) } returns Result.success(emptyList())
        viewModel = EditorViewModel(fileProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `openFile loads content and adds tab`() = runTest {
        coEvery { fileProvider.readFile("/home/test.kt") } returns
            Result.success("fun main() {}")

        viewModel.openFile(RemoteFile("test.kt", "/home/test.kt", false))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.openFiles).hasSize(1)
        assertThat(state.openFiles[0].name).isEqualTo("test.kt")
        assertThat(state.openFiles[0].content).isEqualTo("fun main() {}")
        assertThat(state.openFiles[0].language).isEqualTo("kotlin")
        assertThat(state.activeFileIndex).isEqualTo(0)
    }

    @Test
    fun `openFile does not duplicate already open file`() = runTest {
        coEvery { fileProvider.readFile("/home/test.kt") } returns
            Result.success("fun main() {}")

        viewModel.openFile(RemoteFile("test.kt", "/home/test.kt", false))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.openFile(RemoteFile("test.kt", "/home/test.kt", false))
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.state.value.openFiles).hasSize(1)
    }

    @Test
    fun `closeFile removes tab`() = runTest {
        coEvery { fileProvider.readFile("/home/test.kt") } returns
            Result.success("fun main() {}")

        viewModel.openFile(RemoteFile("test.kt", "/home/test.kt", false))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.closeFile(0)

        assertThat(viewModel.state.value.openFiles).isEmpty()
        assertThat(viewModel.state.value.activeFileIndex).isEqualTo(-1)
    }

    @Test
    fun `saveFile writes content via FileProvider`() = runTest {
        coEvery { fileProvider.readFile("/home/test.kt") } returns
            Result.success("original")
        coEvery { fileProvider.writeFile(any(), any()) } returns Result.success(Unit)

        viewModel.openFile(RemoteFile("test.kt", "/home/test.kt", false))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateContent("modified")
        viewModel.saveCurrentFile()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { fileProvider.writeFile("/home/test.kt", "modified") }
    }

    @Test
    fun `listFiles loads directory contents`() = runTest {
        val files = listOf(
            RemoteFile("src", "/home/src", true),
            RemoteFile("main.kt", "/home/main.kt", false),
        )
        coEvery { fileProvider.listFiles("/home") } returns Result.success(files)

        viewModel.navigateTo("/home")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.currentPath).isEqualTo("/home")
        assertThat(state.directoryContents).hasSize(2)
    }
}
