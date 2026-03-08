package com.serverdash.app.presentation.screens.claudeterminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.data.remote.ssh.SshSessionManager
import com.serverdash.app.domain.repository.SshRepository
import com.serverdash.app.domain.usecase.BuildClaudeContextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TmuxSession(
    val name: String,
    val created: String = "",
    val attached: Boolean = false,
    val size: String = ""
)

data class ClaudeTerminalUiState(
    val terminalOutput: String = "",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val projectPath: String = "",
    val currentSessionName: String = "",
    val availableProjects: List<String> = emptyList(),
    val tmuxSessions: List<TmuxSession> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val showProjectPicker: Boolean = false,
    val showSessionList: Boolean = false,
    val error: String? = null,
    val cols: Int = 120,
    val rows: Int = 40
)

sealed interface ClaudeTerminalEvent {
    data class SendInput(val text: String) : ClaudeTerminalEvent
    data class SendSpecialKey(val key: SpecialKey) : ClaudeTerminalEvent
    data class NewSession(val projectPath: String, val name: String = "") : ClaudeTerminalEvent
    data class AttachSession(val name: String) : ClaudeTerminalEvent
    data class KillSession(val name: String) : ClaudeTerminalEvent
    data class Resize(val cols: Int, val rows: Int) : ClaudeTerminalEvent
    data object ShowProjectPicker : ClaudeTerminalEvent
    data object DismissProjectPicker : ClaudeTerminalEvent
    data object ShowSessionList : ClaudeTerminalEvent
    data object DismissSessionList : ClaudeTerminalEvent
    data object RefreshSessions : ClaudeTerminalEvent
    data object DismissError : ClaudeTerminalEvent
    data object Detach : ClaudeTerminalEvent
    data class StartWithContext(
        val contextType: String,
        val contextParams: String
    ) : ClaudeTerminalEvent
}

enum class SpecialKey(val bytes: ByteArray) {
    ENTER(byteArrayOf(13)),
    TAB(byteArrayOf(9)),
    ESCAPE(byteArrayOf(27)),
    BACKSPACE(byteArrayOf(127)),
    CTRL_C(byteArrayOf(3)),
    CTRL_D(byteArrayOf(4)),
    CTRL_Z(byteArrayOf(26)),
    ARROW_UP(byteArrayOf(27, 91, 65)),
    ARROW_DOWN(byteArrayOf(27, 91, 66)),
    ARROW_RIGHT(byteArrayOf(27, 91, 67)),
    ARROW_LEFT(byteArrayOf(27, 91, 68)),
}

private const val TMUX_PREFIX = "sd-claude-"

@HiltViewModel
class ClaudeTerminalViewModel @Inject constructor(
    private val sshRepository: SshRepository,
    private val sshSessionManager: SshSessionManager,
    private val buildClaudeContextUseCase: BuildClaudeContextUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ClaudeTerminalUiState())
    val state: StateFlow<ClaudeTerminalUiState> = _state.asStateFlow()

    private var interactiveSession: SshSessionManager.InteractiveSession? = null
    private var readJob: Job? = null

    private val outputBuffer = StringBuilder()
    private val maxOutputSize = 65536

    init {
        loadAvailableProjects()
        refreshTmuxSessions()
    }

    fun onEvent(event: ClaudeTerminalEvent) {
        when (event) {
            is ClaudeTerminalEvent.SendInput -> sendInput(event.text)
            is ClaudeTerminalEvent.SendSpecialKey -> sendSpecialKey(event.key)
            is ClaudeTerminalEvent.NewSession -> newTmuxSession(event.projectPath, event.name)
            is ClaudeTerminalEvent.AttachSession -> attachTmuxSession(event.name)
            is ClaudeTerminalEvent.KillSession -> killTmuxSession(event.name)
            is ClaudeTerminalEvent.Resize -> resize(event.cols, event.rows)
            is ClaudeTerminalEvent.ShowProjectPicker -> _state.update { it.copy(showProjectPicker = true) }
            is ClaudeTerminalEvent.DismissProjectPicker -> _state.update { it.copy(showProjectPicker = false) }
            is ClaudeTerminalEvent.ShowSessionList -> {
                refreshTmuxSessions()
                _state.update { it.copy(showSessionList = true) }
            }
            is ClaudeTerminalEvent.DismissSessionList -> _state.update { it.copy(showSessionList = false) }
            is ClaudeTerminalEvent.RefreshSessions -> refreshTmuxSessions()
            is ClaudeTerminalEvent.DismissError -> _state.update { it.copy(error = null) }
            is ClaudeTerminalEvent.Detach -> detachFromSession()
            is ClaudeTerminalEvent.StartWithContext -> startWithContext(event.contextType, event.contextParams)
        }
    }

    private fun loadAvailableProjects() {
        viewModelScope.launch {
            val username = sshSessionManager.getConnectedUsername() ?: "root"
            val homeDir = if (username == "root") "/root" else "/home/$username"
            val result = sshRepository.executeCommand(
                "find $homeDir -maxdepth 3 -name '.git' -type d 2>/dev/null | head -20 | sed 's/\\/\\.git\$//'"
            )
            result.getOrNull()?.let { cmdResult ->
                val projects = cmdResult.output.lines().filter { it.isNotBlank() }
                _state.update { it.copy(availableProjects = projects) }
            }
        }
    }

    private fun refreshTmuxSessions() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingSessions = true) }
            val result = sshRepository.executeCommand(
                "tmux list-sessions -F '#{session_name}|#{session_created_string}|#{session_attached}|#{session_width}x#{session_height}' 2>/dev/null | grep '^$TMUX_PREFIX'"
            )
            val sessions = result.getOrNull()?.output?.lines()
                ?.filter { it.isNotBlank() }
                ?.map { line ->
                    val parts = line.split("|", limit = 4)
                    TmuxSession(
                        name = parts.getOrElse(0) { "" },
                        created = parts.getOrElse(1) { "" },
                        attached = parts.getOrElse(2) { "0" } != "0",
                        size = parts.getOrElse(3) { "" }
                    )
                } ?: emptyList()
            _state.update { it.copy(tmuxSessions = sessions, isLoadingSessions = false) }
        }
    }

    /**
     * Create a new tmux session running `claude` in the given project directory.
     * The session persists on the server even if we disconnect.
     */
    private fun newTmuxSession(projectPath: String, name: String) {
        if (_state.value.isConnecting || _state.value.isConnected) return

        val sessionName = if (name.isNotBlank()) {
            "$TMUX_PREFIX${name.replace(" ", "-").replace(Regex("[^a-zA-Z0-9_-]"), "")}"
        } else {
            val slug = projectPath.substringAfterLast('/').ifBlank { "default" }
            "$TMUX_PREFIX$slug-${System.currentTimeMillis() / 1000 % 10000}"
        }

        _state.update { it.copy(isConnecting = true, projectPath = projectPath, showProjectPicker = false, currentSessionName = sessionName) }
        outputBuffer.clear()

        viewModelScope.launch {
            try {
                // Ensure tmux is available
                val tmuxCheck = sshRepository.executeCommand("which tmux 2>/dev/null")
                if (tmuxCheck.getOrNull()?.output?.isBlank() != false) {
                    _state.update { it.copy(isConnecting = false, error = "tmux is not installed on the server. Install with: sudo apt install tmux") }
                    return@launch
                }

                // Create tmux session with claude in the target directory
                val cdCmd = if (projectPath.isNotBlank()) "cd '${projectPath.replace("'", "'\\''")}' && " else ""
                val createCmd = "tmux new-session -d -s '$sessionName' -x ${_state.value.cols} -y ${_state.value.rows} '${cdCmd}claude; read -p \"[Claude exited. Press Enter to close]\"'"
                val createResult = sshRepository.executeCommand(createCmd)
                if (createResult.isFailure) {
                    _state.update { it.copy(isConnecting = false, error = "Failed to create tmux session: ${createResult.exceptionOrNull()?.message}") }
                    return@launch
                }

                // Now attach to it
                attachToTmux(sessionName)
            } catch (e: Exception) {
                _state.update { it.copy(isConnecting = false, error = "Error: ${e.message}") }
            }
        }
    }

    /**
     * Attach to an existing tmux session via PTY.
     */
    private fun attachTmuxSession(name: String) {
        if (_state.value.isConnecting || _state.value.isConnected) return
        _state.update { it.copy(isConnecting = true, currentSessionName = name, showSessionList = false) }
        outputBuffer.clear()

        viewModelScope.launch {
            attachToTmux(name)
        }
    }

    private suspend fun attachToTmux(sessionName: String) {
        try {
            val result = sshSessionManager.startInteractiveShell(
                cols = _state.value.cols,
                rows = _state.value.rows,
                initialCommand = "tmux attach-session -t '$sessionName'"
            )

            result.fold(
                onSuccess = { session ->
                    interactiveSession = session
                    _state.update { it.copy(isConnected = true, isConnecting = false) }
                    startReadingOutput(session)
                },
                onFailure = { e ->
                    _state.update { it.copy(isConnecting = false, error = "Failed to attach: ${e.message}") }
                }
            )
        } catch (e: Exception) {
            _state.update { it.copy(isConnecting = false, error = "Connection error: ${e.message}") }
        }
    }

    private fun killTmuxSession(name: String) {
        viewModelScope.launch {
            sshRepository.executeCommand("tmux kill-session -t '$name' 2>/dev/null")
            refreshTmuxSessions()
        }
    }

    /**
     * Detach from the tmux session without killing it.
     * Claude keeps running in the background on the server.
     */
    private fun detachFromSession() {
        // Send tmux detach key sequence: Ctrl+B, d
        interactiveSession?.write(byteArrayOf(2)) // Ctrl+B
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            interactiveSession?.write("d")
            kotlinx.coroutines.delay(300)
            // Close our SSH shell (tmux session stays alive on server)
            readJob?.cancel()
            readJob = null
            interactiveSession?.close()
            interactiveSession = null
            _state.update { it.copy(isConnected = false, terminalOutput = "") }
            outputBuffer.clear()
            refreshTmuxSessions()
        }
    }

    private fun startReadingOutput(session: SshSessionManager.InteractiveSession) {
        readJob?.cancel()
        readJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val buffer = ByteArray(8192)
                val stream = session.inputStream
                while (isActive && session.isOpen()) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead == -1) break
                    if (bytesRead > 0) {
                        val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        appendOutput(text)
                    }
                }
            } catch (_: Exception) { }
            finally {
                _state.update { it.copy(isConnected = false) }
                refreshTmuxSessions()
            }
        }
    }

    private fun appendOutput(text: String) {
        synchronized(outputBuffer) {
            outputBuffer.append(text)
            if (outputBuffer.length > maxOutputSize) {
                val excess = outputBuffer.length - maxOutputSize
                outputBuffer.delete(0, excess)
            }
        }
        _state.update { it.copy(terminalOutput = outputBuffer.toString()) }
    }

    private fun sendInput(text: String) {
        interactiveSession?.write(text)
    }

    private fun sendSpecialKey(key: SpecialKey) {
        interactiveSession?.write(key.bytes)
    }

    private fun resize(cols: Int, rows: Int) {
        _state.update { it.copy(cols = cols, rows = rows) }
        interactiveSession?.resize(cols, rows)
    }

    private fun startWithContext(contextType: String, contextParams: String) {
        val projectPath = contextParams.substringBefore("|").ifBlank { "/tmp" }
        newTmuxSession(projectPath, "debug-${System.currentTimeMillis() / 1000 % 10000}")
        viewModelScope.launch {
            _state.first { it.isConnected }
            kotlinx.coroutines.delay(3000) // Wait for Claude TUI to initialize
            try {
                val request = when (contextType) {
                    "service_debug" -> {
                        val parts = contextParams.split("|", limit = 3)
                        BuildClaudeContextUseCase.ContextRequest.ServiceDebug(
                            serviceName = parts.getOrElse(0) { "" },
                            serviceType = parts.getOrElse(1) { "systemd" },
                            errorMessage = parts.getOrElse(2) { "" }
                        )
                    }
                    else -> BuildClaudeContextUseCase.ContextRequest.FullSnapshot
                }
                val context = buildClaudeContextUseCase.build(request)
                sendInput(context)
                sendSpecialKey(SpecialKey.ENTER)
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Just close the SSH shell - tmux session stays alive on server
        readJob?.cancel()
        readJob = null
        interactiveSession?.close()
        interactiveSession = null
    }
}
