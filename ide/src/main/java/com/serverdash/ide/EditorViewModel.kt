package com.serverdash.ide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.ide.model.EditorFile
import com.serverdash.ide.model.RemoteFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditorViewModel(
    private val fileProvider: FileProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    fun openFile(remoteFile: RemoteFile) {
        val existingIndex = _state.value.openFiles.indexOfFirst { it.path == remoteFile.path }
        if (existingIndex >= 0) {
            _state.update { it.copy(activeFileIndex = existingIndex) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            fileProvider.readFile(remoteFile.path)
                .onSuccess { content ->
                    val language = LanguageDetector.detect(remoteFile.name)
                    val file = EditorFile(
                        path = remoteFile.path,
                        name = remoteFile.name,
                        content = content,
                        language = language,
                    )
                    _state.update { state ->
                        val files = state.openFiles + file
                        state.copy(
                            openFiles = files,
                            activeFileIndex = files.size - 1,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun closeFile(index: Int) {
        _state.update { state ->
            val files = state.openFiles.toMutableList().apply { removeAt(index) }
            val newActive = when {
                files.isEmpty() -> -1
                index >= files.size -> files.size - 1
                else -> index
            }
            state.copy(openFiles = files, activeFileIndex = newActive)
        }
    }

    fun selectFile(index: Int) {
        _state.update { it.copy(activeFileIndex = index) }
    }

    fun updateContent(content: String) {
        _state.update { state ->
            val files = state.openFiles.toMutableList()
            val active = state.activeFileIndex
            if (active >= 0 && active < files.size) {
                files[active] = files[active].copy(content = content, isDirty = true)
            }
            state.copy(openFiles = files)
        }
    }

    fun saveCurrentFile() {
        val state = _state.value
        val file = state.activeFile ?: return

        viewModelScope.launch {
            fileProvider.writeFile(file.path, file.content)
                .onSuccess {
                    _state.update { s ->
                        val files = s.openFiles.toMutableList()
                        val active = s.activeFileIndex
                        if (active >= 0 && active < files.size) {
                            files[active] = files[active].copy(isDirty = false)
                        }
                        s.copy(openFiles = files, error = null)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = "Save failed: ${error.message}") }
                }
        }
    }

    fun navigateTo(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, currentPath = path) }
            fileProvider.listFiles(path)
                .onSuccess { files ->
                    val sorted = files.sortedWith(
                        compareByDescending<RemoteFile> { it.isDirectory }
                            .thenBy { it.name.lowercase() },
                    )
                    _state.update {
                        it.copy(directoryContents = sorted, isLoading = false, error = null)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun toggleFileTree() {
        _state.update { it.copy(isFileTreeVisible = !it.isFileTreeVisible) }
    }
}
