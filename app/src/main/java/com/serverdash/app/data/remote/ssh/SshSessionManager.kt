package com.serverdash.app.data.remote.ssh

import com.serverdash.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.connection.channel.direct.Session.Shell
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshSessionManager @Inject constructor() {

    private val mutex = Mutex()
    private var client: SSHClient? = null
    private var currentConfig: ServerConfig? = null

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun connect(config: ServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                _connectionState.value = ConnectionState(isConnecting = true)

                val ssh = SSHClient()
                ssh.addHostKeyVerifier(PromiscuousVerifier())
                ssh.connection.keepAlive.keepAliveInterval = 30
                ssh.connect(config.host, config.port)

                when (config.authMethod) {
                    is AuthMethod.Password -> {
                        ssh.authPassword(config.username, config.authMethod.password)
                    }
                    is AuthMethod.KeyBased -> {
                        val keyContent = config.authMethod.privateKey
                        val passphrase = config.authMethod.passphrase
                        val passwordFinder = if (passphrase.isNotEmpty()) {
                            net.schmizz.sshj.userauth.password.PasswordUtils.createOneOff(
                                passphrase.toCharArray()
                            )
                        } else {
                            null
                        }
                        val keyProvider: KeyProvider = if (keyContent.trimStart().startsWith("-----")) {
                            // Key content pasted directly
                            ssh.loadKeys(keyContent, null, passwordFinder)
                        } else {
                            // Treat as file path
                            ssh.loadKeys(keyContent, passwordFinder)
                        }
                        ssh.authPublickey(config.username, keyProvider)
                    }
                }

                client = ssh
                currentConfig = config
                _connectionState.value = ConnectionState(
                    isConnected = true,
                    lastConnected = System.currentTimeMillis()
                )

                startKeepAlive()
                Result.success(Unit)
            } catch (e: Exception) {
                _connectionState.value = ConnectionState(error = e.message)
                Result.failure(e)
            }
        }
    }

    suspend fun disconnect() = mutex.withLock {
        reconnectJob?.cancel()
        try {
            client?.disconnect()
        } catch (_: Exception) {}
        client = null
        _connectionState.value = ConnectionState()
    }

    suspend fun executeCommand(command: String, timeoutSeconds: Long = 30): Result<CommandResult> {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val ssh = client ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                try {
                    val session: Session = ssh.startSession()
                    try {
                        val cmd = session.exec(command)
                        val output = IOUtils.readFully(cmd.inputStream).toString(Charsets.UTF_8)
                        val error = IOUtils.readFully(cmd.errorStream).toString(Charsets.UTF_8)
                        cmd.join(timeoutSeconds, TimeUnit.SECONDS)
                        val exitCode = cmd.exitStatus ?: -1
                        Result.success(CommandResult(exitCode = exitCode, output = output, error = error))
                    } finally {
                        session.close()
                    }
                } catch (e: Exception) {
                    handleConnectionError(e)
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun executeSudoCommand(command: String, timeoutSeconds: Long = 30): Result<CommandResult> {
        val password = currentConfig?.sudoPassword ?: ""
        if (password.isEmpty()) {
            // Try without password first (user may have NOPASSWD in sudoers)
            val result = executeCommand("sudo -n $command 2>&1", timeoutSeconds)
            val output = result.getOrNull()?.output ?: ""
            // If sudo requires a password we don't have, fall back to non-sudo
            if (output.contains("password is required") || output.contains("a terminal is required")) {
                return executeCommand(command, timeoutSeconds)
            }
            return result
        }
        // Use echo piped to sudo -S via sh -c for reliable password delivery.
        // This avoids SSHJ stdin timing issues with sudo -S reading from stdin directly.
        val escaped = password.replace("'", "'\\''")
        val wrappedCmd = "sh -c 'echo '\"'\"'${escaped}'\"'\"' | sudo -S -p \"\" $command 2>&1; echo \"===SUDO_EXIT=\$?===\"'"
        val result = executeCommand(wrappedCmd, timeoutSeconds)
        return result.map { cmdResult ->
            val fullOutput = cmdResult.output
            // Check for sudo auth failure in the combined output
            if (fullOutput.contains("incorrect password attempt") ||
                fullOutput.contains("Sorry, try again") ||
                fullOutput.contains("is not in the sudoers file")) {
                CommandResult(
                    exitCode = 1,
                    output = "",
                    error = "Sudo authentication failed. Check your sudo password in Settings."
                )
            } else {
                // Strip the exit code marker and password prompt noise
                val cleaned = fullOutput
                    .replace(Regex("\\[sudo\\] password for \\w+:\\s*"), "")
                    .replace(Regex("===SUDO_EXIT=\\d+===\\s*$"), "")
                    .trim()
                val exitMatch = Regex("===SUDO_EXIT=(\\d+)===").find(fullOutput)
                val realExit = exitMatch?.groupValues?.get(1)?.toIntOrNull() ?: cmdResult.exitCode
                CommandResult(exitCode = realExit, output = cleaned, error = cmdResult.error)
            }
        }
    }

    fun streamCommand(command: String): Flow<String> = flow {
        val ssh = client ?: throw IllegalStateException("Not connected")
        val session = ssh.startSession()
        try {
            val cmd = session.exec(command)
            val reader = BufferedReader(InputStreamReader(cmd.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(line!!)
            }
        } finally {
            session.close()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ssh = client ?: return@withContext Result.failure(IllegalStateException("Not connected"))
            try {
                val sftp = ssh.newSFTPClient()
                try {
                    val file = sftp.open(path)
                    val content = IOUtils.readFully(file.RemoteFileInputStream()).toString(Charsets.UTF_8)
                    file.close()
                    Result.success(content)
                } finally {
                    sftp.close()
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ssh = client ?: return@withContext Result.failure(IllegalStateException("Not connected"))
            try {
                val session = ssh.startSession()
                try {
                    val cmd = session.exec("cat > '$path' << 'SERVERDASH_EOF'\n$content\nSERVERDASH_EOF")
                    cmd.join(10, TimeUnit.SECONDS)
                    if (cmd.exitStatus == 0) Result.success(Unit)
                    else Result.failure(Exception("Failed to write file: exit code ${cmd.exitStatus}"))
                } finally {
                    session.close()
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Start an interactive PTY shell session for running TUI apps like `claude`.
     * Returns an InteractiveSession that allows reading output and writing input.
     */
    suspend fun startInteractiveShell(
        cols: Int = 120,
        rows: Int = 40,
        initialCommand: String? = null
    ): Result<InteractiveSession> = withContext(Dispatchers.IO) {
        val ssh = client ?: return@withContext Result.failure(IllegalStateException("Not connected"))
        try {
            val session = ssh.startSession()
            session.allocatePTY("xterm-256color", cols, rows, 0, 0, emptyMap())
            val shell = session.startShell()
            // Send initial command if provided
            if (initialCommand != null) {
                shell.outputStream.write("$initialCommand\n".toByteArray())
                shell.outputStream.flush()
            }
            Result.success(InteractiveSession(session, shell))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    class InteractiveSession(
        private val session: Session,
        private val shell: Shell
    ) {
        val inputStream get() = shell.inputStream
        val outputStream: OutputStream get() = shell.outputStream

        fun write(data: String) {
            outputStream.write(data.toByteArray())
            outputStream.flush()
        }

        fun write(data: ByteArray) {
            outputStream.write(data)
            outputStream.flush()
        }

        fun resize(cols: Int, rows: Int) {
            try {
                shell.changeWindowDimensions(cols, rows, 0, 0)
            } catch (_: Exception) { }
        }

        fun close() {
            try { shell.close() } catch (_: Exception) { }
            try { session.close() } catch (_: Exception) { }
        }

        fun isOpen(): Boolean = session.isOpen
    }

    fun isConnected(): Boolean = client?.isConnected == true

    fun getConnectedUsername(): String? = currentConfig?.username

    fun wrapWithSudo(command: String): String {
        val password = currentConfig?.sudoPassword ?: ""
        return if (password.isNotEmpty()) {
            val escaped = password.replace("'", "'\\''")
            "echo '${escaped}' | sudo -S -p '' $command"
        } else {
            "sudo -n $command"
        }
    }

    suspend fun executeAsUser(command: String, username: String, timeoutSeconds: Long = 30): Result<CommandResult> {
        return executeSudoCommand("-u $username $command", timeoutSeconds)
    }

    suspend fun readFileAsUser(path: String, username: String): Result<String> {
        return executeAsUser("cat '$path'", username).map { it.output }
    }

    suspend fun writeFileAsUser(path: String, content: String, username: String): Result<Unit> {
        return executeSudoCommand("-u $username tee '$path' > /dev/null << 'SERVERDASH_EOF'\n$content\nSERVERDASH_EOF").map { }
    }

    private fun startKeepAlive() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (isActive) {
                delay(30_000)
                if (!isConnected()) {
                    attemptReconnect()
                }
            }
        }
    }

    private suspend fun attemptReconnect() {
        val config = currentConfig ?: return
        var delay = 1000L
        repeat(5) { attempt ->
            delay(delay)
            val result = connect(config)
            if (result.isSuccess) return
            delay = (delay * 2).coerceAtMost(30_000)
        }
    }

    private fun handleConnectionError(e: Exception) {
        if (e is net.schmizz.sshj.connection.ConnectionException ||
            e is java.net.SocketException) {
            _connectionState.value = ConnectionState(error = "Connection lost: ${e.message}")
            scope.launch { attemptReconnect() }
        }
    }
}
