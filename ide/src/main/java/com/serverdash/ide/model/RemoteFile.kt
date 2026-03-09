package com.serverdash.ide.model

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val permissions: String = "",
    val modifiedAt: Long = 0,
)
