package com.serverdash.app.presentation.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.data.remote.ssh.SshSessionManager
import com.serverdash.app.domain.repository.SshRepository
import com.serverdash.app.domain.usecase.BuildClaudeContextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class UnifiedTerminalState(
    val sessions: List<TerminalSessionInfo> = emptyList(),
    val activeSessionIndex: Int = -1,
    val isConnecting: Boolean = false,
    val availableProjects: List<String> = emptyList(),
    val tmuxSessions: List<TmuxSession> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val showProjectPicker: Boolean = false,
    val showSessionList: Boolean = false,
    val error: String? = null,
    val cols: Int = 120,
    val rows: Int = 40,
)

data class TerminalSessionInfo(
    val id: String,
    val name: String,
    val isTmux: Boolean = false,
)

sealed interface UnifiedTerminalEvent {
    data object NewShellSession : UnifiedTerminalEvent
    data class NewTmuxSession(
        val projectPath: String,
        val name: String = "",
    ) : UnifiedTerminalEvent
    data class AttachTmuxSession(val name: String) : UnifiedTerminalEvent
    data class KillTmuxSession(val name: String) : UnifiedTerminalEvent
    data class SelectSession(val index: Int) : UnifiedTerminalEvent
    data class CloseSession(val index: Int) : UnifiedTerminalEvent
    data class Resize(val cols: Int, val rows: Int) : UnifiedTerminalEvent
    data object DetachSession : UnifiedTerminalEvent
    data object ShowProjectPicker : UnifiedTerminalEvent
    data object DismissProjectPicker : UnifiedTerminalEvent
    data object ShowSessionList : UnifiedTerminalEvent
    data object DismissSessionList : UnifiedTerminalEvent
    data object RefreshSessions : UnifiedTerminalEvent
    data object DismissError : UnifiedTerminalEvent
    data class StartWithContext(
        val contextType: String,
        val contextParams: String,
    ) : UnifiedTerminalEvent
}

private const val TMUX_PREFIX = "sd-claude-"

@HiltViewModel
class UnifiedTerminalViewModel @Inject constructor(
    private val sshRepository: SshRepository,
    private val sshSessionManager: SshSessionManager,
    private val buildClaudeContextUseCase: BuildClaudeContextUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UnifiedTerminalState())
    val state: StateFlow<UnifiedTerminalState> = _state.asStateFlow()

    private val terminalSessions = mutableListOf<TerminalSession>()
    private var shellCount = 0

    init {
        loadAvailableProjects()
        refreshTmuxSessions()
    }

    fun onEvent(event: UnifiedTerminalEvent) {
        when (event) {
            is UnifiedTerminalEvent.NewShellSession -> newShellSession()
            is UnifiedTerminalEvent.NewTmuxSession -> newTmuxSession(event.projectPath, event.name)
            is UnifiedTerminalEvent.AttachTmuxSession -> attachTmuxSession(event.name)
            is UnifiedTerminalEvent.KillTmuxSession -> killTmuxSession(event.name)
            is UnifiedTerminalEvent.SelectSession -> selectSession(event.index)
            is UnifiedTerminalEvent.CloseSession -> closeSession(event.index)
            is UnifiedTerminalEvent.Resize -> resize(event.cols, event.rows)
            is UnifiedTerminalEvent.DetachSession -> detachSession()
            is UnifiedTerminalEvent.ShowProjectPicker -> _state.update { it.copy(showProjectPicker = true) }
            is UnifiedTerminalEvent.DismissProjectPicker -> _state.update { it.copy(showProjectPicker = false) }
            is UnifiedTerminalEvent.ShowSessionList -> {
                refreshTmuxSessions()
                _state.update { it.copy(showSessionList = true) }
            }
            is UnifiedTerminalEvent.DismissSessionList -> _state.update { it.copy(showSessionList = false) }
            is UnifiedTerminalEvent.RefreshSessions -> refreshTmuxSessions()
            is UnifiedTerminalEvent.DismissError -> _state.update { it.copy(error = null) }
            is UnifiedTerminalEvent.StartWithContext -> startWithContext(event.contextType, event.contextParams)
        }
    }

    fun getActiveSession(): TerminalSession? {
        val index = _state.value.activeSessionIndex
        return if (index in terminalSessions.indices) terminalSessions[index] else null
    }

    private fun newShellSession() {
        if (_state.value.isConnecting) return
        _state.update { it.copy(isConnecting = true) }

        viewModelScope.launch {
            try {
                val result = sshSessionManager.startInteractiveShell(
                    cols = _state.value.cols,
                    rows = _state.value.rows,
                )
                result.fold(
                    onSuccess = { sshSession ->
                        shellCount++
                        val id = UUID.randomUUID().toString()
                        val session = TerminalSession(
                            id = id,
                            name = "Shell $shellCount",
                            isTmux = false,
                            sshSession = sshSession,
                            scope = viewModelScope,
                        )
                        session.start()
                        terminalSessions.add(session)
                        val newIndex = terminalSessions.size - 1
                        _state.update {
                            it.copy(
                                isConnecting = false,
                                sessions = buildSessionInfoList(),
                                activeSessionIndex = newIndex,
                            )
                        }
                    },
                    onFailure = { e ->
                        _state.update {
                            it.copy(isConnecting = false, error = "Failed to start shell: ${e.message}")
                        }
                    },
                )
            } catch (e: Exception) {
                _state.update { it.copy(isConnecting = false, error = "Connection error: ${e.message}") }
            }
        }
    }

    private fun newTmuxSession(projectPath: String, name: String) {
        if (_state.value.isConnecting) return

        val sessionName = if (name.isNotBlank()) {
            "$TMUX_PREFIX${name.replace(" ", "-").replace(Regex("[^a-zA-Z0-9_-]"), "")}"
        } else {
            val slug = projectPath.substringAfterLast('/').ifBlank { "default" }
            "$TMUX_PREFIX$slug-${System.currentTimeMillis() / 1000 % 10000}"
        }

        _state.update { it.copy(isConnecting = true, showProjectPicker = false) }

        viewModelScope.launch {
            try {
                // check tmux is available
                val tmuxCheck = sshRepository.executeCommand("which tmux 2>/dev/null")
                if (tmuxCheck.getOrNull()?.output?.isBlank() != false) {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            error = "tmux is not installed on the server. Install with: sudo apt install tmux",
                        )
                    }
                    return@launch
                }

                // create tmux session with claude in the target directory
                val cdCmd = if (projectPath.isNotBlank()) {
                    "cd '${projectPath.replace("'", "'\\''")}' && "
                } else {
                    ""
                }
                val createCmd = "tmux new-session -d -s '$sessionName' " +
                    "-x ${_state.value.cols} -y ${_state.value.rows} " +
                    "'${cdCmd}claude; read -p \"[Claude exited. Press Enter to close]\"'"
                val createResult = sshRepository.executeCommand(createCmd)
                if (createResult.isFailure) {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            error = "Failed to create tmux session: ${createResult.exceptionOrNull()?.message}",
                        )
                    }
                    return@launch
                }

                // attach to the new session
                attachToTmux(sessionName)
            } catch (e: Exception) {
                _state.update { it.copy(isConnecting = false, error = "Error: ${e.message}") }
            }
        }
    }

    private fun attachTmuxSession(name: String) {
        if (_state.value.isConnecting) return
        _state.update { it.copy(isConnecting = true, showSessionList = false) }

        viewModelScope.launch {
            attachToTmux(name)
        }
    }

    private suspend fun attachToTmux(sessionName: String) {
        try {
            val result = sshSessionManager.startInteractiveShell(
                cols = _state.value.cols,
                rows = _state.value.rows,
                initialCommand = "tmux attach-session -t '$sessionName'",
            )

            result.fold(
                onSuccess = { sshSession ->
                    val id = UUID.randomUUID().toString()
                    val session = TerminalSession(
                        id = id,
                        name = sessionName,
                        isTmux = true,
                        sshSession = sshSession,
                        scope = viewModelScope,
                    )
                    session.start()
                    terminalSessions.add(session)
                    val newIndex = terminalSessions.size - 1
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            sessions = buildSessionInfoList(),
                            activeSessionIndex = newIndex,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isConnecting = false, error = "Failed to attach: ${e.message}")
                    }
                },
            )
        } catch (e: Exception) {
            _state.update { it.copy(isConnecting = false, error = "Connection error: ${e.message}") }
        }
    }

    private fun detachSession() {
        val activeIndex = _state.value.activeSessionIndex
        if (activeIndex !in terminalSessions.indices) return
        val session = terminalSessions[activeIndex]
        if (!session.isTmux) return

        // send tmux detach key sequence: ctrl+b, d
        session.writeToSsh(byteArrayOf(2)) // ctrl+b
        viewModelScope.launch {
            delay(100)
            session.writeToSsh("d".toByteArray())
            delay(300)
            // close the ssh shell (tmux session stays alive on server)
            session.close()
            terminalSessions.removeAt(activeIndex)
            val newIndex = when {
                terminalSessions.isEmpty() -> -1
                activeIndex >= terminalSessions.size -> terminalSessions.size - 1
                else -> activeIndex
            }
            _state.update {
                it.copy(
                    sessions = buildSessionInfoList(),
                    activeSessionIndex = newIndex,
                )
            }
            refreshTmuxSessions()
        }
    }

    private fun closeSession(index: Int) {
        if (index !in terminalSessions.indices) return
        val session = terminalSessions[index]
        session.close()
        terminalSessions.removeAt(index)

        val activeIndex = _state.value.activeSessionIndex
        val newIndex = when {
            terminalSessions.isEmpty() -> -1
            index < activeIndex -> activeIndex - 1
            index == activeIndex && activeIndex >= terminalSessions.size -> terminalSessions.size - 1
            index == activeIndex -> activeIndex
            else -> activeIndex
        }
        _state.update {
            it.copy(
                sessions = buildSessionInfoList(),
                activeSessionIndex = newIndex,
            )
        }
    }

    private fun selectSession(index: Int) {
        if (index in terminalSessions.indices) {
            _state.update { it.copy(activeSessionIndex = index) }
        }
    }

    private fun resize(cols: Int, rows: Int) {
        _state.update { it.copy(cols = cols, rows = rows) }
        getActiveSession()?.resize(cols, rows)
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
                        size = parts.getOrElse(3) { "" },
                    )
                } ?: emptyList()
            _state.update { it.copy(tmuxSessions = sessions, isLoadingSessions = false) }
        }
    }

    private fun killTmuxSession(name: String) {
        viewModelScope.launch {
            sshRepository.executeCommand("tmux kill-session -t '$name' 2>/dev/null")
            refreshTmuxSessions()
        }
    }

    private fun startWithContext(contextType: String, contextParams: String) {
        val projectPath = contextParams.substringBefore("|").ifBlank { "/tmp" }
        newTmuxSession(projectPath, "debug-${System.currentTimeMillis() / 1000 % 10000}")
        viewModelScope.launch {
            // wait until a tmux session is connected
            _state.first { it.sessions.any { s -> s.isTmux } && !it.isConnecting }
            delay(3000) // wait for claude TUI to initialise
            try {
                val request = when (contextType) {
                    "service_debug" -> {
                        val parts = contextParams.split("|", limit = 3)
                        BuildClaudeContextUseCase.ContextRequest.ServiceDebug(
                            serviceName = parts.getOrElse(0) { "" },
                            serviceType = parts.getOrElse(1) { "systemd" },
                            errorMessage = parts.getOrElse(2) { "" },
                        )
                    }
                    else -> BuildClaudeContextUseCase.ContextRequest.FullSnapshot
                }
                val context = buildClaudeContextUseCase.build(request)
                val activeSession = getActiveSession() ?: return@launch
                activeSession.writeToSsh(context.toByteArray())
                activeSession.writeToSsh(byteArrayOf(13)) // enter
            } catch (_: Exception) { }
        }
    }

    private fun buildSessionInfoList(): List<TerminalSessionInfo> {
        return terminalSessions.map { session ->
            TerminalSessionInfo(
                id = session.id,
                name = session.name,
                isTmux = session.isTmux,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        terminalSessions.forEach { it.close() }
        terminalSessions.clear()
    }
}
