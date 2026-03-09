package com.serverdash.app.data.repository

import com.serverdash.app.domain.repository.SshRepository
import com.serverdash.ide.FileProvider
import com.serverdash.ide.model.RemoteFile
import javax.inject.Inject

class SshFileProvider @Inject constructor(
    private val sshRepository: SshRepository,
) : FileProvider {

    override suspend fun listFiles(path: String): Result<List<RemoteFile>> {
        return sshRepository.executeCommand(
            "ls -la --time-style=+%s '$path' | tail -n +2",
        ).map { result ->
            result.output.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { parseLsLine(it, path) }
        }
    }

    override suspend fun readFile(path: String): Result<String> {
        return sshRepository.readFile(path)
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> {
        return sshRepository.writeFile(path, content)
    }

    override suspend fun deleteFile(path: String): Result<Unit> {
        return sshRepository.executeCommand("rm -f '$path'").map { }
    }

    override suspend fun createFile(path: String): Result<Unit> {
        return sshRepository.executeCommand("touch '$path'").map { }
    }

    override suspend fun createDirectory(path: String): Result<Unit> {
        return sshRepository.executeCommand("mkdir -p '$path'").map { }
    }

    private fun parseLsLine(line: String, parentPath: String): RemoteFile? {
        val parts = line.split("\\s+".toRegex(), limit = 7)
        if (parts.size < 7) return null
        val name = parts[6]
        if (name == "." || name == "..") return null
        val permissions = parts[0]
        val isDirectory = permissions.startsWith('d')
        val size = parts[4].toLongOrNull() ?: 0
        val modifiedAt = parts[5].toLongOrNull() ?: 0
        val fullPath = if (parentPath == "/") "/$name" else "$parentPath/$name"
        return RemoteFile(
            name = name,
            path = fullPath,
            isDirectory = isDirectory,
            size = size,
            permissions = permissions,
            modifiedAt = modifiedAt,
        )
    }
}
