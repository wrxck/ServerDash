package com.serverdash.app.presentation.screens.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.serverdash.app.data.repository.SshFileProvider
import com.serverdash.ide.EditorViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EditorWrapperViewModel @Inject constructor(
    sshFileProvider: SshFileProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val initialPath: String = savedStateHandle.get<String>("path") ?: "/"
    val initialFile: String = savedStateHandle.get<String>("file") ?: ""

    init {
        val asUser = savedStateHandle.get<String>("asUser") ?: ""
        if (asUser.isNotEmpty()) {
            sshFileProvider.asUser = asUser
        }
    }

    val editorViewModel = EditorViewModel(sshFileProvider)
}
