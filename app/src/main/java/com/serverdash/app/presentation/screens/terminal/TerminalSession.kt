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

class TerminalSession(
    val id: String,
    val name: String,
    val isTmux: Boolean = false,
    private val sshSession: SshSessionManager.InteractiveSession,
    private val scope: CoroutineScope,
    defaultForeground: Color = Color(0xFFC0CAF5),
    defaultBackground: Color = Color(0xFF1A1B26),
) {
    val emulator: TerminalEmulator = TerminalEmulatorFactory.create(
        initialRows = 24,
        initialCols = 80,
        defaultForeground = defaultForeground,
        defaultBackground = defaultBackground,
        onKeyboardInput = { data ->
            sshSession.write(data)
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
        sshSession.close()
    }

    fun writeToSsh(data: ByteArray) {
        sshSession.write(data)
    }

    val isOpen: Boolean get() = sshSession.isOpen()
}
