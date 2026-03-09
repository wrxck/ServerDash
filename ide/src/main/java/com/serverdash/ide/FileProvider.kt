package com.serverdash.ide

import com.serverdash.ide.model.RemoteFile

interface FileProvider {
    suspend fun listFiles(path: String): Result<List<RemoteFile>>
    suspend fun readFile(path: String): Result<String>
    suspend fun writeFile(path: String, content: String): Result<Unit>
    suspend fun deleteFile(path: String): Result<Unit>
    suspend fun createFile(path: String): Result<Unit>
    suspend fun createDirectory(path: String): Result<Unit>
}
