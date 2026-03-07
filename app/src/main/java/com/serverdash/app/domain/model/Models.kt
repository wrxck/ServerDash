package com.serverdash.app.domain.model

import kotlinx.serialization.Serializable

enum class ServiceType { SYSTEMD, DOCKER }

enum class ServiceStatus { RUNNING, STOPPED, FAILED, UNKNOWN }

data class ServerConfig(
    val id: Long = 0,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authMethod: AuthMethod,
    val label: String = host
)

sealed class AuthMethod {
    data class Password(val password: String) : AuthMethod()
    data class KeyBased(val privateKey: String, val passphrase: String = "") : AuthMethod()
}

data class Service(
    val id: Long = 0,
    val serverId: Long,
    val name: String,
    val displayName: String = name,
    val type: ServiceType,
    val status: ServiceStatus = ServiceStatus.UNKNOWN,
    val isPinned: Boolean = false,
    val subState: String = "",
    val description: String = ""
)

data class SystemMetrics(
    val cpuUsage: Float = 0f,
    val memoryUsed: Long = 0,
    val memoryTotal: Long = 0,
    val diskUsed: Long = 0,
    val diskTotal: Long = 0,
    val loadAvg1: Float = 0f,
    val loadAvg5: Float = 0f,
    val loadAvg15: Float = 0f,
    val uptimeSeconds: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    val memoryUsagePercent: Float get() = if (memoryTotal > 0) memoryUsed.toFloat() / memoryTotal * 100f else 0f
    val diskUsagePercent: Float get() = if (diskTotal > 0) diskUsed.toFloat() / diskTotal * 100f else 0f
}

data class ServiceLog(
    val timestamp: String,
    val message: String,
    val priority: String = ""
)

data class AlertRule(
    val id: Long = 0,
    val serverId: Long,
    val name: String,
    val condition: AlertCondition,
    val isEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val webhookUrl: String = ""
)

sealed class AlertCondition {
    data class ServiceDown(val serviceName: String) : AlertCondition()
    data class CpuAbove(val threshold: Float) : AlertCondition()
    data class MemoryAbove(val threshold: Float) : AlertCondition()
    data class DiskAbove(val threshold: Float) : AlertCondition()
}

data class Alert(
    val id: Long = 0,
    val rule: AlertRule,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val acknowledged: Boolean = false
)

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String = ""
)

data class TerminalEntry(
    val id: Long = 0,
    val command: String,
    val output: String,
    val timestamp: Long = System.currentTimeMillis(),
    val exitCode: Int = 0
)

@Serializable
data class WebhookPayload(
    val server: String,
    val alert: String,
    val message: String,
    val timestamp: Long,
    val severity: String
)

enum class ThemeMode { AUTO, LIGHT, DARK, TRUE_BLACK }

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val pollingIntervalSeconds: Int = 10,
    val brightnessOverride: Float = -1f,
    val keepScreenOn: Boolean = true,
    val kioskMode: Boolean = false,
    val pixelShiftEnabled: Boolean = false,
    val autoStartOnBoot: Boolean = false,
    val backgroundCheckIntervalMinutes: Int = 15
)

data class ConnectionState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val error: String? = null,
    val lastConnected: Long = 0
)
