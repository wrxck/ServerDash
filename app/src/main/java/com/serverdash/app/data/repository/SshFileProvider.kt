package com.serverdash.app.data.repository

import android.util.Log
import com.serverdash.app.domain.repository.SshRepository
import com.serverdash.ide.FileProvider
import com.serverdash.ide.model.RemoteFile
import javax.inject.Inject

private const val TAG = "SshFileProvider"

class SshFileProvider @Inject constructor(
    private val sshRepository: SshRepository,
) : FileProvider {

    /** Username to run file operations as (empty = connected user) */
    var asUser: String = ""

    /** True when asUser differs from the connected user (requires root SSH) */
    private val needsUserSwitch: Boolean
        get() = asUser.isNotEmpty() && asUser != sshRepository.getConnectedUsername()

    override suspend fun listFiles(path: String): Result<List<RemoteFile>> {
        Log.d(TAG, "listFiles path=$path asUser=$asUser")
        val cmd = "ls -la --time-style=+%s '$path' | tail -n +2"
        val result = if (needsUserSwitch) {
            sshRepository.executeAsUser(cmd, asUser)
        } else {
            sshRepository.executeCommand(cmd)
        }
        result.onFailure { Log.e(TAG, "listFiles FAILED: ${it.message}") }
        return result.map { r ->
            r.output.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { parseLsLine(it, path) }
        }
    }

    override suspend fun readFile(path: String): Result<String> {
        Log.d(TAG, "readFile path=$path asUser=$asUser")
        val result = if (needsUserSwitch) {
            sshRepository.readFileAsUser(path, asUser)
        } else {
            sshRepository.readFile(path)
        }
        result.onFailure { Log.e(TAG, "readFile FAILED: ${it.message}") }
        return result
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> {
        return if (needsUserSwitch) {
            sshRepository.writeFileAsUser(path, content, asUser)
        } else {
            sshRepository.writeFile(path, content)
        }
    }

    override suspend fun deleteFile(path: String): Result<Unit> {
        val cmd = "rm -f '$path'"
        return if (needsUserSwitch) {
            sshRepository.executeAsUser(cmd, asUser)
        } else {
            sshRepository.executeCommand(cmd)
        }.map { }
    }

    override suspend fun createFile(path: String): Result<Unit> {
        val cmd = "touch '$path'"
        return if (needsUserSwitch) {
            sshRepository.executeAsUser(cmd, asUser)
        } else {
            sshRepository.executeCommand(cmd)
        }.map { }
    }

    override suspend fun createDirectory(path: String): Result<Unit> {
        val cmd = "mkdir -p '$path'"
        return if (needsUserSwitch) {
            sshRepository.executeAsUser(cmd, asUser)
        } else {
            sshRepository.executeCommand(cmd)
        }.map { }
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
