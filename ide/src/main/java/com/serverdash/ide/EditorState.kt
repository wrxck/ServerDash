package com.serverdash.ide

import com.serverdash.ide.model.EditorConfig
import com.serverdash.ide.model.EditorFile
import com.serverdash.ide.model.RemoteFile

data class EditorState(
    val openFiles: List<EditorFile> = emptyList(),
    val activeFileIndex: Int = -1,
    val currentPath: String = "/",
    val directoryContents: List<RemoteFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val config: EditorConfig = EditorConfig(),
    val isFileTreeVisible: Boolean = true,
) {
    val activeFile: EditorFile?
        get() = openFiles.getOrNull(activeFileIndex)
}
