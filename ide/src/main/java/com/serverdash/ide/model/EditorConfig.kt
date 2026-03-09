package com.serverdash.ide.model

import kotlinx.serialization.Serializable

@Serializable
data class EditorConfig(
    val fontSize: Int = 14,
    val wordWrap: Boolean = false,
    val lineNumbers: Boolean = true,
    val minimap: Boolean = false,
    val tabSize: Int = 4,
    val insertSpaces: Boolean = true,
    val renderWhitespace: String = "none",
)
