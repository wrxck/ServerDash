package com.serverdash.app.presentation.screens.terminal

import androidx.compose.ui.graphics.Color
import com.serverdash.app.data.remote.ssh.SshSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import java.util.concurrent.Executors

class TerminalSession(
    val id: String,
    val name: String,
    val isTmux: Boolean = false,
    private val sshSession: SshSessionManager.InteractiveSession,
    private val scope: CoroutineScope,
    defaultForeground: Color = Color.White,
    defaultBackground: Color = Color.Black,
) {
    // Dedicated single-thread executor for SSH writes to avoid
    // NetworkOnMainThreadException (termlib fires onKeyboardInput on main thread)
    private val writeExecutor = Executors.newSingleThreadExecutor()

    val emulator: TerminalEmulator = TerminalEmulatorFactory.create(
        initialRows = 24,
        initialCols = 80,
        defaultForeground = defaultForeground,
        defaultBackground = defaultBackground,
        onKeyboardInput = { data ->
            writeExecutor.execute {
                try {
                    sshSession.write(data)
                } catch (_: Exception) { }
            }
        },
    )

    private var readJob: Job? = null

    fun start() {
        readJob = scope.launch(Dispatchers.IO) {
            try {
                val buffer = ByteArray(8192)
                val stream = sshSession.inputStream
                while (isActive && sshSession.isOpen()) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead == -1) break
                    if (bytesRead > 0) {
                        emulator.writeInput(buffer, 0, bytesRead)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun resize(cols: Int, rows: Int) {
        emulator.resize(rows, cols)
        sshSession.resize(cols, rows)
    }

    fun close() {
        readJob?.cancel()
        readJob = null
        writeExecutor.shutdown()
        sshSession.close()
    }

    fun writeToSsh(data: ByteArray) {
        writeExecutor.execute {
            try {
                sshSession.write(data)
            } catch (_: Exception) { }
        }
    }

    val isOpen: Boolean get() = sshSession.isOpen()
}
