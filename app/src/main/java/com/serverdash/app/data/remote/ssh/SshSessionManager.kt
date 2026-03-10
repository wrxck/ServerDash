package com.serverdash.app.data.remote.ssh

import com.serverdash.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
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

private const val TAG = "SshSessionManager"

@Singleton
class SshSessionManager @Inject constructor() {

    private val userMutex = Mutex()
    private val rootMutex = Mutex()
    private var client: SSHClient? = null
    private var rootClient: SSHClient? = null
    private var cachedSftp: net.schmizz.sshj.sftp.SFTPClient? = null
    private var currentConfig: ServerConfig? = null

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun connect(config: ServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        userMutex.withLock {
            try {
                _connectionState.value = ConnectionState(isConnecting = true)

                val ssh = createAndAuthClient(config.host, config.port, config.username, config.authMethod)

                client = ssh
                currentConfig = config
                _connectionState.value = ConnectionState(
                    isConnected = true,
                    lastConnected = System.currentTimeMillis()
                )

                // Establish root shadow connection if configured
                connectRootShadow(config)

                startKeepAlive()
                Result.success(Unit)
            } catch (e: Exception) {
                _connectionState.value = ConnectionState(error = e.message)
                Result.failure(e)
            }
        }
    }

    /** Establish root SSH connection alongside user connection */
    private suspend fun connectRootShadow(config: ServerConfig) {
        Log.d(TAG, "connectRootShadow: rootAccess=${config.rootAccess::class.simpleName}, authMethod=${config.authMethod::class.simpleName}")
        if (config.rootAccess is RootAccess.None || config.rootAccess is RootAccess.SudoPassword) return

        rootMutex.withLock {
            try {
                // Disconnect existing root client
                try { rootClient?.disconnect() } catch (_: Exception) {}
                rootClient = null

                val rootAuth = when (config.rootAccess) {
                    is RootAccess.SameKeyAsUser -> config.authMethod
                    is RootAccess.SeparateKey -> AuthMethod.KeyBased(
                        privateKey = config.rootAccess.privateKey,
                        passphrase = config.rootAccess.passphrase
                    )
                    else -> return // SudoPassword/None don't use root SSH
                }

                Log.d(TAG, "Establishing root shadow connection to ${config.host}:${config.port}")
                val ssh = createAndAuthClient(config.host, config.port, "root", rootAuth)
                rootClient = ssh
                Log.d(TAG, "Root shadow connection established")
            } catch (e: Exception) {
                Log.e(TAG, "Root shadow connection failed: ${e.message}", e)
                // Non-fatal — root commands will fail with diagnostic when attempted
            }
        }
    }

    /** Create an SSH client, connect, and authenticate */
    private fun createAndAuthClient(host: String, port: Int, username: String, authMethod: AuthMethod): SSHClient {
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connection.keepAlive.keepAliveInterval = 30
        ssh.connect(host, port)

        Log.d(TAG, "Authenticating as '$username' with ${authMethod::class.simpleName}")
        when (authMethod) {
            is AuthMethod.Password -> {
                ssh.authPassword(username, authMethod.password)
            }
            is AuthMethod.KeyBased -> {
                Log.d(TAG, "Key length=${authMethod.privateKey.length}, hasPassphrase=${authMethod.passphrase.isNotEmpty()}, starts='${authMethod.privateKey.take(40)}'")
                val passwordFinder = if (authMethod.passphrase.isNotEmpty()) {
                    net.schmizz.sshj.userauth.password.PasswordUtils.createOneOff(
                        authMethod.passphrase.toCharArray()
                    )
                } else {
                    null
                }
                val keyProvider: KeyProvider = if (authMethod.privateKey.trimStart().startsWith("-----")) {
                    ssh.loadKeys(authMethod.privateKey, null, passwordFinder)
                } else {
                    ssh.loadKeys(authMethod.privateKey, passwordFinder)
                }
                ssh.authPublickey(username, keyProvider)
            }
        }
        Log.d(TAG, "Auth succeeded for '$username'")

        return ssh
    }

    suspend fun disconnect() {
        userMutex.withLock {
            reconnectJob?.cancel()
            rootMutex.withLock {
                try { rootClient?.disconnect() } catch (_: Exception) {}
                rootClient = null
            }
            invalidateSftp()
            try { client?.disconnect() } catch (_: Exception) {}
            client = null
            _connectionState.value = ConnectionState()
        }
    }

    suspend fun executeCommand(command: String, timeoutSeconds: Long = 30): Result<CommandResult> {
        return withContext(Dispatchers.IO) {
            userMutex.withLock {
                val ssh = client ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                try {
                    execOnClient(ssh, command, timeoutSeconds)
                } catch (e: Exception) {
                    handleConnectionError(e)
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun executeSudoCommand(command: String, timeoutSeconds: Long = 30): Result<CommandResult> {
        val config = currentConfig ?: return Result.failure(IllegalStateException("Not connected"))

        // Root SSH configured — run directly on the root connection
        if (config.rootAccess is RootAccess.SameKeyAsUser || config.rootAccess is RootAccess.SeparateKey) {
            return executeAsRoot(command, timeoutSeconds)
        }

        // Legacy sudo password approach
        if (config.rootAccess is RootAccess.SudoPassword && config.sudoPassword.isNotEmpty()) {
            val escaped = config.sudoPassword.replace("'", "'\\''")
            val wrappedCmd = "sh -c 'echo '\"'\"'${escaped}'\"'\"' | sudo -S -p \"\" $command 2>&1; echo \"===SUDO_EXIT=\$?===\"'"
            val result = executeCommand(wrappedCmd, timeoutSeconds)
            return result.map { cmdResult ->
                val fullOutput = cmdResult.output
                if (fullOutput.contains("incorrect password attempt") ||
                    fullOutput.contains("Sorry, try again") ||
                    fullOutput.contains("is not in the sudoers file")) {
                    CommandResult(
                        exitCode = 1,
                        output = "",
                        error = "Sudo authentication failed. Check your sudo password in Settings."
                    )
                } else {
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

        // No root access configured — fail explicitly
        return Result.failure(IllegalStateException("Root access not configured. Enable root SSH or set a sudo password in Settings."))
    }

    /** Execute a command directly as root via the shadow SSH connection */
    private suspend fun executeAsRoot(command: String, timeoutSeconds: Long = 30): Result<CommandResult> {
        return withContext(Dispatchers.IO) {
            rootMutex.withLock {
                val ssh = rootClient
                if (ssh == null || !ssh.isConnected) {
                    // Try to re-establish the root connection
                    val config = currentConfig ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                    try {
                        connectRootInner(config)
                    } catch (e: Exception) {
                        Log.e(TAG, "Root SSH reconnect failed: ${e.message}", e)
                        return@withContext Result.failure(IllegalStateException(buildDiagnostic(e, config), e))
                    }
                }
                val rootSsh = rootClient ?: return@withContext Result.failure(
                    IllegalStateException("Root SSH not available. Check root access configuration in Settings.")
                )
                try {
                    execOnClient(rootSsh, command, timeoutSeconds)
                } catch (e: Exception) {
                    Log.e(TAG, "Root command failed: ${e.message}")
                    // If it's a connection error, clear the client for reconnect next time
                    if (e is net.schmizz.sshj.connection.ConnectionException || e is java.net.SocketException) {
                        try { rootClient?.disconnect() } catch (_: Exception) {}
                        rootClient = null
                    }
                    Result.failure(e)
                }
            }
        }
    }

    /** Reconnect root client (must be called inside rootMutex) */
    private fun connectRootInner(config: ServerConfig) {
        try { rootClient?.disconnect() } catch (_: Exception) {}
        rootClient = null

        val rootAuth = when (config.rootAccess) {
            is RootAccess.SameKeyAsUser -> config.authMethod
            is RootAccess.SeparateKey -> AuthMethod.KeyBased(
                privateKey = config.rootAccess.privateKey,
                passphrase = config.rootAccess.passphrase
            )
            else -> throw IllegalStateException("Root SSH not configured")
        }

        Log.d(TAG, "Reconnecting root SSH to ${config.host}:${config.port}")
        val ssh = createAndAuthClient(config.host, config.port, "root", rootAuth)
        rootClient = ssh
        Log.d(TAG, "Root SSH reconnected")
    }

    /** Execute a command on a given SSH client */
    private fun execOnClient(ssh: SSHClient, command: String, timeoutSeconds: Long): Result<CommandResult> {
        val session: Session = ssh.startSession()
        try {
            val cmd = session.exec(command)
            val output = String(IOUtils.readFully(cmd.inputStream).toByteArray(), Charsets.UTF_8)
            val error = String(IOUtils.readFully(cmd.errorStream).toByteArray(), Charsets.UTF_8)
            cmd.join(timeoutSeconds, TimeUnit.SECONDS)
            val exitCode = cmd.exitStatus ?: -1
            return Result.success(CommandResult(exitCode = exitCode, output = output, error = error))
        } finally {
            session.close()
        }
    }

    private fun buildDiagnostic(e: Exception, config: ServerConfig): String {
        return when {
            e.message?.contains("Auth fail") == true || e.message?.contains("Exhausted") == true ->
                "Root SSH authentication failed. Check that:\n" +
                "• Root login is enabled in /etc/ssh/sshd_config (PermitRootLogin prohibit-password)\n" +
                "• Your SSH key is in /root/.ssh/authorized_keys\n" +
                "• SSH service was restarted after config changes"
            e.message?.contains("Connection refused") == true ->
                "Connection refused on port ${config.port}. Is the SSH service running?"
            e.message?.contains("timed out") == true || e.message?.contains("timeout") == true ->
                "Root SSH connection timed out. Check firewall rules and network connectivity."
            e.message?.contains("Host key") == true ->
                "SSH host key verification failed. The server key may have changed."
            else -> "Root SSH connection failed: ${e.message}"
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

    /** Get or create a cached SFTP client. Must be called inside userMutex. */
    private fun getOrCreateSftp(): net.schmizz.sshj.sftp.SFTPClient {
        cachedSftp?.let { return it }
        val ssh = client ?: throw IllegalStateException("Not connected")
        val sftp = ssh.newSFTPClient()
        cachedSftp = sftp
        return sftp
    }

    private fun invalidateSftp() {
        try { cachedSftp?.close() } catch (_: Exception) {}
        cachedSftp = null
    }

    suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        userMutex.withLock {
            try {
                val sftp = getOrCreateSftp()
                val file = sftp.open(path)
                val content = String(IOUtils.readFully(file.RemoteFileInputStream()).toByteArray(), Charsets.UTF_8)
                file.close()
                Result.success(content)
            } catch (e: Exception) {
                invalidateSftp()
                // Retry once with a fresh SFTP client
                try {
                    val sftp = getOrCreateSftp()
                    val file = sftp.open(path)
                    val content = String(IOUtils.readFully(file.RemoteFileInputStream()).toByteArray(), Charsets.UTF_8)
                    file.close()
                    Result.success(content)
                } catch (retryErr: Exception) {
                    invalidateSftp()
                    Result.failure(retryErr)
                }
            }
        }
    }

    suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        userMutex.withLock {
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

    fun hasRootAccess(): Boolean {
        val config = currentConfig ?: return false
        return config.rootAccess !is RootAccess.None
    }

    suspend fun executeAsUser(command: String, username: String, timeoutSeconds: Long = 30): Result<CommandResult> {
        val config = currentConfig ?: return Result.failure(IllegalStateException("Not connected"))
        // If root SSH is available, use su to run as the target user
        if (config.rootAccess is RootAccess.SameKeyAsUser || config.rootAccess is RootAccess.SeparateKey) {
            val escaped = command.replace("'", "'\\''")
            return executeAsRoot("su - $username -c '$escaped'", timeoutSeconds)
        }
        // Legacy sudo approach — executeSudoCommand already wraps with sudo
        return executeSudoCommand("-u $username $command", timeoutSeconds)
    }

    suspend fun readFileAsUser(path: String, username: String): Result<String> {
        return executeAsUser("cat '$path'", username).map { it.output }
    }

    suspend fun writeFileAsUser(path: String, content: String, username: String): Result<Unit> {
        val config = currentConfig
        if (config != null && (config.rootAccess is RootAccess.SameKeyAsUser || config.rootAccess is RootAccess.SeparateKey)) {
            // Root SSH: use tee directly as root, then chown to user
            return executeAsRoot("tee '$path' > /dev/null << 'SERVERDASH_EOF'\n$content\nSERVERDASH_EOF").map {
                executeAsRoot("chown $username:$username '$path'")
                Unit
            }
        }
        return executeSudoCommand("-u $username tee '$path' > /dev/null << 'SERVERDASH_EOF'\n$content\nSERVERDASH_EOF").map { }
    }

    private fun startKeepAlive() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (isActive) {
                delay(30_000)
                // Check user connection
                if (!isConnected()) {
                    attemptReconnect()
                }
                // Check root shadow connection
                val config = currentConfig
                if (config != null &&
                    (config.rootAccess is RootAccess.SameKeyAsUser || config.rootAccess is RootAccess.SeparateKey) &&
                    rootClient?.isConnected != true) {
                    Log.d(TAG, "Root shadow connection lost, reconnecting...")
                    rootMutex.withLock {
                        if (rootClient?.isConnected != true) {
                            try { connectRootInner(config) } catch (e: Exception) {
                                Log.e(TAG, "Root shadow reconnect failed: ${e.message}")
                            }
                        }
                    }
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
