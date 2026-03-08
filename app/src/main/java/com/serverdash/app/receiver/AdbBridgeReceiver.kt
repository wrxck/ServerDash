package com.serverdash.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import com.serverdash.app.data.remote.ssh.SshSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AdbBridgeReceiver : BroadcastReceiver() {

    @Inject lateinit var sshManager: SshSessionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "ServerDashBridge"
        const val ACTION_EXEC_CMD = "com.serverdash.app.EXEC_CMD"
        const val ACTION_GET_STATUS = "com.serverdash.app.GET_STATUS"
        const val ACTION_GET_LOGS = "com.serverdash.app.GET_LOGS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Only allow in debug builds
        val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebug) {
            Log.w(TAG, "ADB bridge is only available in debug builds")
            return
        }

        when (intent.action) {
            ACTION_EXEC_CMD -> handleExecCommand(intent)
            ACTION_GET_STATUS -> handleGetStatus()
            ACTION_GET_LOGS -> handleGetLogs(intent)
            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    private fun handleExecCommand(intent: Intent) {
        val command = intent.getStringExtra("command")
        if (command.isNullOrBlank()) {
            Log.e(TAG, "EXEC_CMD: missing 'command' extra")
            return
        }

        Log.i(TAG, "EXEC_CMD: $command")
        val pendingResult = goAsync()

        scope.launch {
            try {
                if (!sshManager.isConnected()) {
                    Log.e(TAG, "EXEC_CMD: SSH not connected")
                    pendingResult.finish()
                    return@launch
                }

                val result = sshManager.executeCommand(command)
                result.fold(
                    onSuccess = { cmdResult ->
                        Log.i(TAG, "EXEC_CMD exit=${cmdResult.exitCode}")
                        // Split output into chunks for logcat (4000 char limit)
                        val output = cmdResult.output
                        if (output.isBlank()) {
                            Log.i(TAG, "OUTPUT: (empty)")
                        } else {
                            output.chunked(3800).forEach { chunk ->
                                Log.i(TAG, "OUTPUT: $chunk")
                            }
                        }
                        if (cmdResult.error.isNotBlank()) {
                            Log.e(TAG, "STDERR: ${cmdResult.error}")
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "EXEC_CMD failed: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "EXEC_CMD exception: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleGetStatus() {
        Log.i(TAG, "GET_STATUS: fetching current service statuses")
        val pendingResult = goAsync()

        scope.launch {
            try {
                if (!sshManager.isConnected()) {
                    Log.e(TAG, "GET_STATUS: SSH not connected")
                    pendingResult.finish()
                    return@launch
                }

                val result = sshManager.executeCommand(
                    "systemctl list-units --type=service --state=running --no-pager --no-legend 2>/dev/null; echo '---DOCKER---'; docker ps --format '{{.Names}}\\t{{.Status}}' 2>/dev/null"
                )
                result.fold(
                    onSuccess = { cmdResult ->
                        cmdResult.output.chunked(3800).forEach { chunk ->
                            Log.i(TAG, "STATUS: $chunk")
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "GET_STATUS failed: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "GET_STATUS exception: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleGetLogs(intent: Intent) {
        val serviceName = intent.getStringExtra("service")
        if (serviceName.isNullOrBlank()) {
            Log.e(TAG, "GET_LOGS: missing 'service' extra")
            return
        }

        val lines = intent.getIntExtra("lines", 50)
        Log.i(TAG, "GET_LOGS: $serviceName (last $lines lines)")
        val pendingResult = goAsync()

        scope.launch {
            try {
                if (!sshManager.isConnected()) {
                    Log.e(TAG, "GET_LOGS: SSH not connected")
                    pendingResult.finish()
                    return@launch
                }

                // Try journalctl first, then docker logs
                val command = "journalctl -u $serviceName -n $lines --no-pager -o short-iso 2>/dev/null || docker logs --tail $lines $serviceName 2>&1"
                val result = sshManager.executeCommand(command)
                result.fold(
                    onSuccess = { cmdResult ->
                        if (cmdResult.output.isBlank()) {
                            Log.i(TAG, "LOGS: (no output for $serviceName)")
                        } else {
                            cmdResult.output.chunked(3800).forEach { chunk ->
                                Log.i(TAG, "LOGS: $chunk")
                            }
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "GET_LOGS failed: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "GET_LOGS exception: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
