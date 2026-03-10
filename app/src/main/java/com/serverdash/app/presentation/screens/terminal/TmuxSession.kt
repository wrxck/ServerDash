package com.serverdash.app.presentation.screens.terminal

data class TmuxSession(
    val name: String,
    val created: String = "",
    val attached: Boolean = false,
    val size: String = "",
)
