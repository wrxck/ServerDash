package com.serverdash.app.presentation.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.data.local.db.TerminalHistoryDao
import com.serverdash.app.data.local.db.TerminalHistoryEntity
import com.serverdash.app.domain.model.TerminalEntry
import com.serverdash.app.domain.usecase.ExecuteCommandUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TerminalUiState(
    val currentCommand: String = "",
    val entries: List<TerminalEntry> = emptyList(),
    val isExecuting: Boolean = false,
    val history: List<String> = emptyList(),
    val historyIndex: Int = -1
)

sealed interface TerminalEvent {
    data class UpdateCommand(val command: String) : TerminalEvent
    data object Execute : TerminalEvent
    data object ClearScreen : TerminalEvent
    data object HistoryUp : TerminalEvent
    data object HistoryDown : TerminalEvent
    data class QuickCommand(val command: String) : TerminalEvent
}

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val executeCommand: ExecuteCommandUseCase,
    private val terminalHistoryDao: TerminalHistoryDao
) : ViewModel() {

    private val _state = MutableStateFlow(TerminalUiState())
    val state: StateFlow<TerminalUiState> = _state.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            terminalHistoryDao.observeAll().collect { entities ->
                val entries = entities.map { TerminalEntry(it.id, it.command, it.output, it.timestamp, it.exitCode) }
                val history = entities.map { it.command }.distinct()
                _state.update { it.copy(entries = entries, history = history) }
            }
        }
    }

    fun onEvent(event: TerminalEvent) {
        when (event) {
            is TerminalEvent.UpdateCommand -> _state.update { it.copy(currentCommand = event.command) }
            is TerminalEvent.Execute -> execute()
            is TerminalEvent.ClearScreen -> _state.update { it.copy(entries = emptyList()) }
            is TerminalEvent.HistoryUp -> navigateHistory(-1)
            is TerminalEvent.HistoryDown -> navigateHistory(1)
            is TerminalEvent.QuickCommand -> {
                _state.update { it.copy(currentCommand = event.command) }
                execute()
            }
        }
    }

    private fun execute() {
        val command = _state.value.currentCommand.trim()
        if (command.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isExecuting = true, currentCommand = "") }
            executeCommand(command).fold(
                onSuccess = { result ->
                    val entry = TerminalEntry(
                        command = command,
                        output = result.output + (if (result.error.isNotBlank()) "\n${result.error}" else ""),
                        exitCode = result.exitCode
                    )
                    terminalHistoryDao.insert(
                        TerminalHistoryEntity(
                            command = command,
                            output = entry.output,
                            exitCode = result.exitCode,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    _state.update { it.copy(isExecuting = false, historyIndex = -1) }
                },
                onFailure = { e ->
                    terminalHistoryDao.insert(
                        TerminalHistoryEntity(
                            command = command,
                            output = "Error: ${e.message}",
                            exitCode = -1,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    _state.update { it.copy(isExecuting = false, historyIndex = -1) }
                }
            )
        }
    }

    private fun navigateHistory(direction: Int) {
        val history = _state.value.history
        if (history.isEmpty()) return
        val newIndex = (_state.value.historyIndex + direction).coerceIn(-1, history.size - 1)
        val command = if (newIndex >= 0) history[newIndex] else ""
        _state.update { it.copy(historyIndex = newIndex, currentCommand = command) }
    }
}
