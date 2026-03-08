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
import java.io.BufferedReader
import java.io.InputStreamReader
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

    fun isConnected(): Boolean = client?.isConnected == true

    fun getConnectedUsername(): String? = currentConfig?.username

    fun wrapWithSudo(command: String): String {
        val password = currentConfig?.sudoPassword ?: ""
        return if (password.isNotEmpty()) {
            val escaped = password.replace("'", "'\\''")
            "echo '${escaped}' | sudo -S $command"
        } else {
            "sudo $command"
        }
    }

    suspend fun executeAsUser(command: String, username: String, timeoutSeconds: Long = 30): Result<CommandResult> {
        val password = currentConfig?.sudoPassword ?: ""
        val wrapped = if (password.isNotEmpty()) {
            val escaped = password.replace("'", "'\\''")
            "echo '${escaped}' | sudo -S -u $username $command"
        } else {
            "sudo -u $username $command"
        }
        return executeCommand(wrapped, timeoutSeconds)
    }

    suspend fun readFileAsUser(path: String, username: String): Result<String> {
        return executeAsUser("cat '$path'", username).map { it.output }
    }

    suspend fun writeFileAsUser(path: String, content: String, username: String): Result<Unit> {
        val password = currentConfig?.sudoPassword ?: ""
        val sudoPrefix = if (password.isNotEmpty()) {
            val escaped = password.replace("'", "'\\''")
            "echo '${escaped}' | sudo -S -u $username"
        } else {
            "sudo -u $username"
        }
        val command = "$sudoPrefix tee '$path' > /dev/null << 'SERVERDASH_EOF'\n$content\nSERVERDASH_EOF"
        return executeCommand(command).map { }
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
