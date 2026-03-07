package com.serverdash.app.domain.repository

import com.serverdash.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    suspend fun saveServerConfig(config: ServerConfig): Long
    suspend fun getServerConfig(): ServerConfig?
    suspend fun deleteServerConfig()
    fun observeServerConfig(): Flow<ServerConfig?>
}

interface ServiceRepository {
    suspend fun discoverServices(serverId: Long): List<Service>
    suspend fun getServices(serverId: Long): List<Service>
    suspend fun updateServiceStatus(serviceId: Long, status: ServiceStatus, subState: String = "")
    suspend fun pinService(serviceId: Long, pinned: Boolean)
    suspend fun saveServices(services: List<Service>)
    fun observeServices(serverId: Long): Flow<List<Service>>
    fun observePinnedServices(serverId: Long): Flow<List<Service>>
}

interface SshRepository {
    suspend fun connect(config: ServerConfig): Result<Unit>
    suspend fun disconnect()
    suspend fun executeCommand(command: String): Result<CommandResult>
    suspend fun startLogStream(serviceName: String, serviceType: ServiceType): Flow<String>
    suspend fun readFile(path: String): Result<String>
    suspend fun writeFile(path: String, content: String): Result<Unit>
    fun observeConnectionState(): Flow<ConnectionState>
    suspend fun isConnected(): Boolean
}

interface MetricsRepository {
    suspend fun fetchMetrics(): Result<SystemMetrics>
    suspend fun getMetricsHistory(limit: Int = 60): List<SystemMetrics>
    suspend fun saveMetrics(metrics: SystemMetrics)
    fun observeLatestMetrics(): Flow<SystemMetrics?>
}

interface AlertRepository {
    suspend fun saveAlertRule(rule: AlertRule): Long
    suspend fun getAlertRules(serverId: Long): List<AlertRule>
    suspend fun deleteAlertRule(ruleId: Long)
    suspend fun saveAlert(alert: Alert)
    suspend fun getActiveAlerts(): List<Alert>
    suspend fun acknowledgeAlert(alertId: Long)
    fun observeAlertRules(serverId: Long): Flow<List<AlertRule>>
    fun observeActiveAlerts(): Flow<List<Alert>>
}

interface PreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>
    suspend fun getPreferences(): AppPreferences
    suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences)
}
