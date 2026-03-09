package com.serverdash.ide

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

@Composable
fun SyncThemeToMonaco(commands: MonacoCommands?) {
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val foreground = MaterialTheme.colorScheme.onSurface
    val lineHighlight = MaterialTheme.colorScheme.surfaceContainerHigh
    val selection = MaterialTheme.colorScheme.primaryContainer
    val lineNumber = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(commands, background) {
        commands ?: return@LaunchedEffect
        val theme = buildMonacoTheme(
            background = background,
            foreground = foreground,
            lineHighlight = lineHighlight,
            selection = selection,
            lineNumber = lineNumber,
        )
        commands.setTheme(theme)
    }
}

private fun buildMonacoTheme(
    background: Color,
    foreground: Color,
    lineHighlight: Color,
    selection: Color,
    lineNumber: Color,
): String {
    fun Color.toHex(): String {
        val argb = toArgb()
        return String.format("#%06X", 0xFFFFFF and argb)
    }

    return """{"base":"vs-dark","inherit":true,"rules":[],"colors":{"editor.background":"${background.toHex()}","editor.foreground":"${foreground.toHex()}","editor.lineHighlightBackground":"${lineHighlight.toHex()}","editor.selectionBackground":"${selection.toHex()}","editorLineNumber.foreground":"${lineNumber.toHex()}"}}"""
}
