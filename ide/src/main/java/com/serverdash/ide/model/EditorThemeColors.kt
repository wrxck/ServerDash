package com.serverdash.ide.model

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Editor & syntax highlighting colours passed from the app theme layer
 * into the ide module via CompositionLocal.
 * Values are ARGB longs (0 = not set, fall back to MaterialTheme defaults).
 */
data class EditorThemeColors(
    // Editor chrome
    val editorBackground: Long = 0,
    val editorForeground: Long = 0,
    val editorLineHighlight: Long = 0,
    val editorSelection: Long = 0,
    val editorLineNumber: Long = 0,
    val editorGutter: Long = 0,
    val editorCursor: Long = 0,
    val editorWhitespace: Long = 0,
    val editorIndentGuide: Long = 0,
    val editorBracketMatch: Long = 0,
    // Syntax tokens
    val syntaxKeyword: Long = 0,
    val syntaxString: Long = 0,
    val syntaxComment: Long = 0,
    val syntaxFunction: Long = 0,
    val syntaxNumber: Long = 0,
    val syntaxType: Long = 0,
    val syntaxOperator: Long = 0,
    val syntaxVariable: Long = 0,
    val syntaxConstant: Long = 0,
    val syntaxTag: Long = 0,
    val syntaxAttribute: Long = 0,
    val syntaxProperty: Long = 0,
    val syntaxRegex: Long = 0,
    val syntaxPunctuation: Long = 0,
)

val LocalEditorThemeColors = staticCompositionLocalOf { EditorThemeColors() }
