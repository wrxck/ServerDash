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
    val label: String = host,
    val sudoPassword: String = ""
)

data class SystemUser(
    val username: String,
    val homeDirectory: String,
    val uid: Int,
    val hasClaudeCode: Boolean = false
)

sealed class AuthMethod {
    data class Password(val password: String) : AuthMethod()
    data class KeyBased(val privateKey: String, val passphrase: String = "") : AuthMethod()
}

data class FleetAppMetadata(
    val domains: List<String> = emptyList(),
    val composePath: String = "",
    val appType: String = "",
    val healthUrl: String? = null,
    val port: Int? = null,
    val containers: List<String> = emptyList()
)

data class Service(
    val id: Long = 0,
    val serverId: Long,
    val name: String,
    val displayName: String = name,
    val type: ServiceType,
    val status: ServiceStatus = ServiceStatus.UNKNOWN,
    val isPinned: Boolean = false,
    val subState: String = "",
    val description: String = "",
    val group: String = "",
    val fleetMetadata: FleetAppMetadata? = null
) {
    val effectiveGroup: String get() = group.ifBlank { type.name.lowercase().replaceFirstChar { it.uppercase() } }
}

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

enum class DashboardLayout { GRID, LIST, COMPACT }
enum class ServiceSortOrder { NAME, STATUS, TYPE, PINNED_FIRST }
enum class MetricsDisplayMode { COMPACT, EXPANDED, HIDDEN }
enum class LogFontSize { SMALL, MEDIUM, LARGE }

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val selectedThemeId: String = "default_dark",
    val undoDurationSeconds: Int = 5,
    val pollingIntervalSeconds: Int = 10,
    val brightnessOverride: Float = -1f,
    val keepScreenOn: Boolean = true,
    val kioskMode: Boolean = false,
    val pixelShiftEnabled: Boolean = false,
    val autoStartOnBoot: Boolean = false,
    val backgroundCheckIntervalMinutes: Int = 15,
    // Dashboard layout
    val dashboardLayout: DashboardLayout = DashboardLayout.GRID,
    val gridColumns: Int = 0, // 0 = auto
    val serviceSortOrder: ServiceSortOrder = ServiceSortOrder.PINNED_FIRST,
    val showServiceDescription: Boolean = false,
    val compactCards: Boolean = false,
    // Metrics display
    val metricsDisplayMode: MetricsDisplayMode = MetricsDisplayMode.COMPACT,
    val showLoadAverage: Boolean = false,
    val cpuWarningThreshold: Float = 80f,
    val memoryWarningThreshold: Float = 80f,
    val diskWarningThreshold: Float = 90f,
    // Notifications
    val notificationsEnabled: Boolean = true,
    val notifyOnServiceDown: Boolean = true,
    val notifyOnHighCpu: Boolean = true,
    val notifyOnHighMemory: Boolean = true,
    val notifyOnHighDisk: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationVibrate: Boolean = true,
    // Logs
    val logFontSize: LogFontSize = LogFontSize.MEDIUM,
    val logLineCount: Int = 100,
    val logAutoRefresh: Boolean = false,
    val logAutoRefreshSeconds: Int = 5,
    val logWrapLines: Boolean = true,
    // Terminal
    val terminalFontSize: Int = 14,
    val terminalMaxHistory: Int = 500,
    val terminalShowTimestamps: Boolean = true,
    // Connection
    val connectionTimeoutSeconds: Int = 30,
    val autoReconnect: Boolean = true,
    val autoReconnectDelaySeconds: Int = 5,
    val maxReconnectAttempts: Int = 3,
    // Data
    val metricsRetentionHours: Int = 24,
    val maxServicesDisplayed: Int = 0, // 0 = unlimited
    val hideUnknownServices: Boolean = false,
    // plugins
    val disabledPlugins: Set<String> = emptySet(),
    // Privacy / Streaming mode
    val streamingModeEnabled: Boolean = false,
    val privacyFilterIps: Boolean = true,
    val privacyFilterPorts: Boolean = true,
    val privacyFilterEmails: Boolean = true,
    val privacyFilterHostnames: Boolean = true,
    val privacyFilterPaths: Boolean = true,
    val privacyFilterSsh: Boolean = true,
    val privacyFilterTokens: Boolean = true,
    val privacyFilterPasswords: Boolean = true,
    val privacyFilterServiceNames: Boolean = false,
    val privacyRedactedServiceNames: Set<String> = emptySet(),
    val privacyCustomPatterns: Set<String> = emptySet(),
    val privacyReplacementText: String = "[REDACTED]",
    // Fonts
    val headerFont: String = "JetBrains Mono",
    val bodyFont: String = "JetBrains Mono",
    val codeFont: String = "JetBrains Mono"
)

data class ConnectionState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val error: String? = null,
    val lastConnected: Long = 0
)
