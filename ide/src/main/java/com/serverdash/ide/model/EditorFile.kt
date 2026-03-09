package com.serverdash.ide.model

data class EditorFile(
    val path: String,
    val name: String,
    val content: String,
    val language: String,
    val isDirty: Boolean = false,
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,
)
