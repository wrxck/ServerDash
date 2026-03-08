package com.serverdash.app.presentation.screens.claudeterminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.domain.repository.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID
import javax.inject.Inject

data class ClaudeSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val projectPath: String,
    val messages: List<ClaudeMessage> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class ClaudeMessage(
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

data class ClaudeTerminalUiState(
    val sessions: List<ClaudeSession> = emptyList(),
    val activeSessionId: String? = null,
    val inputText: String = "",
    val isProcessing: Boolean = false,
    val isCreatingSession: Boolean = false,
    val availableProjects: List<String> = emptyList(),
    val showNewSessionDialog: Boolean = false,
    val showSessionManager: Boolean = false,
    val error: String? = null
)

sealed interface ClaudeTerminalEvent {
    data class CreateSession(val name: String, val projectPath: String) : ClaudeTerminalEvent
    data class SendMessage(val text: String) : ClaudeTerminalEvent
    data class SwitchSession(val id: String) : ClaudeTerminalEvent
    data class DeleteSession(val id: String) : ClaudeTerminalEvent
    data class RenameSession(val id: String, val name: String) : ClaudeTerminalEvent
    data object ToggleSessionManager : ClaudeTerminalEvent
    data object DismissNewSessionDialog : ClaudeTerminalEvent
    data object ShowNewSessionDialog : ClaudeTerminalEvent
    data class UpdateInput(val text: String) : ClaudeTerminalEvent
    data object DismissError : ClaudeTerminalEvent
}

@HiltViewModel
class ClaudeTerminalViewModel @Inject constructor(
    private val sshRepository: SshRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ClaudeTerminalUiState())
    val state: StateFlow<ClaudeTerminalUiState> = _state.asStateFlow()

    init {
        loadAvailableProjects()
    }

    fun onEvent(event: ClaudeTerminalEvent) {
        when (event) {
            is ClaudeTerminalEvent.CreateSession -> createSession(event.name, event.projectPath)
            is ClaudeTerminalEvent.SendMessage -> sendMessage(event.text)
            is ClaudeTerminalEvent.SwitchSession -> switchSession(event.id)
            is ClaudeTerminalEvent.DeleteSession -> deleteSession(event.id)
            is ClaudeTerminalEvent.RenameSession -> renameSession(event.id, event.name)
            is ClaudeTerminalEvent.ToggleSessionManager -> _state.update { it.copy(showSessionManager = !it.showSessionManager) }
            is ClaudeTerminalEvent.DismissNewSessionDialog -> _state.update { it.copy(showNewSessionDialog = false) }
            is ClaudeTerminalEvent.ShowNewSessionDialog -> _state.update { it.copy(showNewSessionDialog = true) }
            is ClaudeTerminalEvent.UpdateInput -> _state.update { it.copy(inputText = event.text) }
            is ClaudeTerminalEvent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadAvailableProjects() {
        viewModelScope.launch {
            // Try to discover project directories from the Claude Code data
            val username = sshRepository.getConnectedUsername() ?: "root"
            val homeDir = if (username == "root") "/root" else "/home/$username"
            val result = sshRepository.executeCommand(
                "ls -d $homeDir/.claude/projects/*/ 2>/dev/null | head -20"
            )
            result.getOrNull()?.let { cmdResult ->
                val projects = cmdResult.output.lines()
                    .filter { it.isNotBlank() }
                    .map { path ->
                        // Decode the Claude project dir name back to a path
                        val dirName = path.trimEnd('/').substringAfterLast('/')
                        dirName.replace("-", "/").let { decoded ->
                            // The first character should be / for absolute paths
                            if (decoded.startsWith("/")) decoded else "/$decoded"
                        }
                    }
                _state.update { it.copy(availableProjects = projects) }
            }

            // Also try to find common project directories
            val findResult = sshRepository.executeCommand(
                "find $homeDir -maxdepth 3 -name '.git' -type d 2>/dev/null | head -20 | sed 's/\\/\\.git\$//'"
            )
            findResult.getOrNull()?.let { cmdResult ->
                val gitProjects = cmdResult.output.lines().filter { it.isNotBlank() }
                val current = _state.value.availableProjects
                val merged = (current + gitProjects).distinct()
                _state.update { it.copy(availableProjects = merged) }
            }
        }
    }

    private fun createSession(name: String, projectPath: String) {
        val session = ClaudeSession(
            name = name.ifBlank { projectPath.substringAfterLast('/').ifBlank { "New Session" } },
            projectPath = projectPath,
            messages = listOf(
                ClaudeMessage(
                    role = "system",
                    content = "Session created for project: $projectPath"
                )
            )
        )
        _state.update {
            it.copy(
                sessions = it.sessions + session,
                activeSessionId = session.id,
                showNewSessionDialog = false,
                isCreatingSession = false
            )
        }
    }

    private fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val sessionId = _state.value.activeSessionId ?: return
        val session = _state.value.sessions.find { it.id == sessionId } ?: return

        // Add user message
        val userMessage = ClaudeMessage(role = "user", content = trimmed)
        val updatedMessages = session.messages + userMessage
        updateSessionMessages(sessionId, updatedMessages)
        _state.update { it.copy(inputText = "", isProcessing = true) }

        viewModelScope.launch {
            try {
                val response = executeClaudeCommand(trimmed, session.projectPath, session.messages)
                val assistantMessage = ClaudeMessage(
                    role = "assistant",
                    content = response
                )
                val currentSession = _state.value.sessions.find { it.id == sessionId }
                if (currentSession != null) {
                    updateSessionMessages(sessionId, currentSession.messages + assistantMessage)
                }
            } catch (e: Exception) {
                val errorMessage = ClaudeMessage(
                    role = "system",
                    content = "Error: ${e.message ?: "Unknown error"}"
                )
                val currentSession = _state.value.sessions.find { it.id == sessionId }
                if (currentSession != null) {
                    updateSessionMessages(sessionId, currentSession.messages + errorMessage)
                }
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(isProcessing = false) }
            }
        }
    }

    private suspend fun executeClaudeCommand(
        prompt: String,
        projectPath: String,
        previousMessages: List<ClaudeMessage>
    ): String {
        // Build the context by replaying previous conversation turns
        val contextPrompt = buildContextPrompt(prompt, previousMessages)

        // Escape the prompt for shell
        val escapedPrompt = contextPrompt
            .replace("\\", "\\\\")
            .replace("'", "'\\''")
            .replace("\"", "\\\"")

        // Use claude --print for non-interactive single-turn execution
        val command = buildString {
            append("cd '${projectPath.replace("'", "'\\''")}' 2>/dev/null; ")
            append("claude --print --output-format json ")
            append("'$escapedPrompt' 2>/dev/null")
        }

        val result = sshRepository.executeCommand(command)
        val cmdResult = result.getOrElse { throw it }

        if (cmdResult.exitCode != 0 && cmdResult.output.isBlank()) {
            val errMsg = cmdResult.error.ifBlank { "Claude CLI returned exit code ${cmdResult.exitCode}" }
            throw Exception(errMsg)
        }

        return parseClaudeResponse(cmdResult.output)
    }

    private fun buildContextPrompt(
        currentPrompt: String,
        previousMessages: List<ClaudeMessage>
    ): String {
        // Filter to only user/assistant messages for context replay
        val conversationTurns = previousMessages.filter { it.role in listOf("user", "assistant") }

        if (conversationTurns.isEmpty()) {
            return currentPrompt
        }

        // Build a context string that replays the conversation
        return buildString {
            appendLine("Previous conversation context:")
            conversationTurns.forEach { msg ->
                when (msg.role) {
                    "user" -> appendLine("User: ${msg.content}")
                    "assistant" -> appendLine("Assistant: ${msg.content}")
                }
            }
            appendLine()
            appendLine("Current request:")
            append(currentPrompt)
        }
    }

    private fun parseClaudeResponse(output: String): String {
        if (output.isBlank()) return "(empty response)"

        return try {
            // Try parsing as JSON (--output-format json)
            val element = Json.parseToJsonElement(output.trim())
            when (element) {
                is JsonObject -> {
                    // Handle structured JSON response
                    val result = element["result"]?.jsonPrimitive?.content
                    val content = element["content"]?.let { contentElement ->
                        when (contentElement) {
                            is JsonPrimitive -> contentElement.content
                            is JsonArray -> contentElement.joinToString("\n") { item ->
                                when (item) {
                                    is JsonObject -> item["text"]?.jsonPrimitive?.content
                                        ?: item.toString()
                                    is JsonPrimitive -> item.content
                                    else -> item.toString()
                                }
                            }
                            else -> contentElement.toString()
                        }
                    }
                    val message = element["message"]?.jsonPrimitive?.content
                    val error = element["error"]?.jsonPrimitive?.content

                    error ?: result ?: content ?: message ?: output.trim()
                }
                is JsonPrimitive -> element.content
                else -> output.trim()
            }
        } catch (e: Exception) {
            // Not JSON, return as plain text
            output.trim()
        }
    }

    private fun updateSessionMessages(sessionId: String, messages: List<ClaudeMessage>) {
        _state.update { state ->
            state.copy(
                sessions = state.sessions.map { session ->
                    if (session.id == sessionId) session.copy(messages = messages)
                    else session
                }
            )
        }
    }

    private fun switchSession(id: String) {
        _state.update { it.copy(activeSessionId = id, showSessionManager = false) }
    }

    private fun deleteSession(id: String) {
        _state.update { state ->
            val newSessions = state.sessions.filter { it.id != id }
            val newActiveId = if (state.activeSessionId == id) {
                newSessions.lastOrNull()?.id
            } else {
                state.activeSessionId
            }
            state.copy(sessions = newSessions, activeSessionId = newActiveId)
        }
    }

    private fun renameSession(id: String, name: String) {
        _state.update { state ->
            state.copy(
                sessions = state.sessions.map { session ->
                    if (session.id == id) session.copy(name = name)
                    else session
                }
            )
        }
    }
}
