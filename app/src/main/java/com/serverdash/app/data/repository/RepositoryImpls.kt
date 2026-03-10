package com.serverdash.app.data.repository

import com.serverdash.app.data.local.db.*
import com.serverdash.app.data.mapper.*
import com.serverdash.app.data.preferences.PreferencesManager
import com.serverdash.app.data.remote.ssh.SshSessionManager
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepositoryImpl @Inject constructor(
    private val dao: ServerConfigDao
) : ServerRepository {
    override suspend fun saveServerConfig(config: ServerConfig): Long {
        dao.deleteAll()
        return dao.insert(config.toEntity())
    }
    override suspend fun getServerConfig(): ServerConfig? = dao.getConfig()?.toDomain()
    override suspend fun deleteServerConfig() = dao.deleteAll()
    override fun observeServerConfig(): Flow<ServerConfig?> = dao.observeConfig().map { it?.toDomain() }
}

@Singleton
class ServiceRepositoryImpl @Inject constructor(
    private val dao: ServiceDao,
    private val sshManager: SshSessionManager
) : ServiceRepository {
    override suspend fun discoverServices(serverId: Long): List<Service> = dao.getServices(serverId).map { it.toDomain() }

    override suspend fun getServices(serverId: Long): List<Service> = dao.getServices(serverId).map { it.toDomain() }

    override suspend fun updateServiceStatus(serviceId: Long, status: ServiceStatus, subState: String) {
        val dbStatus = when (status) {
            ServiceStatus.RUNNING -> ServiceStatusDb.RUNNING
            ServiceStatus.STOPPED -> ServiceStatusDb.STOPPED
            ServiceStatus.FAILED -> ServiceStatusDb.FAILED
            ServiceStatus.UNKNOWN -> ServiceStatusDb.UNKNOWN
        }
        dao.updateStatus(serviceId, dbStatus, subState)
    }

    override suspend fun pinService(serviceId: Long, pinned: Boolean) = dao.updatePinned(serviceId, pinned)

    override suspend fun saveServices(services: List<Service>) {
        if (services.isNotEmpty()) {
            val serverId = services.first().serverId
            dao.syncServices(serverId, services.map { it.toEntity() })
        }
    }

    override suspend fun updateServiceGroup(serviceId: Long, group: String) = dao.updateGroup(serviceId, group)

    override fun observeServices(serverId: Long): Flow<List<Service>> =
        dao.observeServices(serverId)
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observePinnedServices(serverId: Long): Flow<List<Service>> =
        dao.observePinnedServices(serverId)
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeGroups(serverId: Long): Flow<List<String>> = dao.observeGroups(serverId)
}

@Singleton
class SshRepositoryImpl @Inject constructor(
    private val sshManager: SshSessionManager
) : SshRepository {
    override suspend fun connect(config: ServerConfig) = sshManager.connect(config)
    override suspend fun disconnect() = sshManager.disconnect()
    override suspend fun executeCommand(command: String) = sshManager.executeCommand(command)
    override suspend fun executeSudoCommand(command: String) = sshManager.executeSudoCommand(command)

    override suspend fun startLogStream(serviceName: String, serviceType: ServiceType): Flow<String> {
        val command = when (serviceType) {
            ServiceType.SYSTEMD -> "journalctl -u $serviceName -f --no-pager"
            ServiceType.DOCKER -> "docker logs -f $serviceName 2>&1"
        }
        return sshManager.streamCommand(command)
    }

    override suspend fun readFile(path: String) = sshManager.readFile(path)
    override suspend fun writeFile(path: String, content: String) = sshManager.writeFile(path, content)
    override fun observeConnectionState(): Flow<ConnectionState> = sshManager.connectionState
    override suspend fun isConnected(): Boolean = sshManager.isConnected()
    override suspend fun executeAsUser(command: String, username: String) = sshManager.executeAsUser(command, username)
    override suspend fun readFileAsUser(path: String, username: String) = sshManager.readFileAsUser(path, username)
    override suspend fun writeFileAsUser(path: String, content: String, username: String) = sshManager.writeFileAsUser(path, content, username)
    override fun getConnectedUsername(): String? = sshManager.getConnectedUsername()
    override fun hasRootAccess(): Boolean = sshManager.hasRootAccess()
}

@Singleton
class MetricsRepositoryImpl @Inject constructor(
    private val dao: MetricsDao,
    private val sshManager: SshSessionManager
) : MetricsRepository {
    override suspend fun fetchMetrics(): Result<SystemMetrics> {
        return dao.observeLatest().first()?.toDomain()?.let { Result.success(it) }
            ?: Result.failure(Exception("No metrics available"))
    }

    override suspend fun getMetricsHistory(limit: Int): List<SystemMetrics> = dao.getRecent(limit).map { it.toDomain() }

    override suspend fun saveMetrics(metrics: SystemMetrics) {
        dao.insert(metrics.toEntity())
        // Cleanup old metrics (keep last 24 hours)
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        dao.deleteOlderThan(cutoff)
    }

    override fun observeLatestMetrics(): Flow<SystemMetrics?> = dao.observeLatest().map { it?.toDomain() }
}

@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val dao: AlertDao
) : AlertRepository {
    override suspend fun saveAlertRule(rule: AlertRule): Long {
        val entity = AlertRuleEntity(
            id = rule.id,
            serverId = rule.serverId,
            name = rule.name,
            conditionType = when (rule.condition) {
                is AlertCondition.ServiceDown -> "SERVICE_DOWN"
                is AlertCondition.CpuAbove -> "CPU_ABOVE"
                is AlertCondition.MemoryAbove -> "MEMORY_ABOVE"
                is AlertCondition.DiskAbove -> "DISK_ABOVE"
            },
            conditionValue = when (rule.condition) {
                is AlertCondition.ServiceDown -> rule.condition.serviceName
                is AlertCondition.CpuAbove -> rule.condition.threshold.toString()
                is AlertCondition.MemoryAbove -> rule.condition.threshold.toString()
                is AlertCondition.DiskAbove -> rule.condition.threshold.toString()
            },
            isEnabled = rule.isEnabled,
            soundEnabled = rule.soundEnabled,
            webhookUrl = rule.webhookUrl
        )
        return dao.insertRule(entity)
    }

    override suspend fun getAlertRules(serverId: Long): List<AlertRule> = dao.getRules(serverId).map { it.toDomain() }

    override suspend fun deleteAlertRule(ruleId: Long) = dao.deleteRule(ruleId)

    override suspend fun saveAlert(alert: Alert) {
        dao.insertAlert(AlertEntity(
            ruleId = alert.rule.id,
            message = alert.message,
            timestamp = alert.timestamp,
            acknowledged = alert.acknowledged
        ))
    }

    override suspend fun getActiveAlerts(): List<Alert> {
        // Simplified - would need join in real impl
        return dao.getActiveAlerts().map { entity ->
            Alert(
                id = entity.id,
                rule = AlertRule(id = entity.ruleId, serverId = 0, name = "", condition = AlertCondition.CpuAbove(0f)),
                message = entity.message,
                timestamp = entity.timestamp,
                acknowledged = entity.acknowledged
            )
        }
    }

    override suspend fun acknowledgeAlert(alertId: Long) = dao.acknowledgeAlert(alertId)

    override fun observeAlertRules(serverId: Long): Flow<List<AlertRule>> = dao.observeRules(serverId).map { list -> list.map { it.toDomain() } }

    override fun observeActiveAlerts(): Flow<List<Alert>> = dao.observeActiveAlerts().map { list ->
        list.map { entity ->
            Alert(
                id = entity.id,
                rule = AlertRule(id = entity.ruleId, serverId = 0, name = "", condition = AlertCondition.CpuAbove(0f)),
                message = entity.message,
                timestamp = entity.timestamp,
                acknowledged = entity.acknowledged
            )
        }
    }

    private fun AlertRuleEntity.toDomain(): AlertRule = AlertRule(
        id = id,
        serverId = serverId,
        name = name,
        condition = when (conditionType) {
            "SERVICE_DOWN" -> AlertCondition.ServiceDown(conditionValue)
            "CPU_ABOVE" -> AlertCondition.CpuAbove(conditionValue.toFloatOrNull() ?: 0f)
            "MEMORY_ABOVE" -> AlertCondition.MemoryAbove(conditionValue.toFloatOrNull() ?: 0f)
            "DISK_ABOVE" -> AlertCondition.DiskAbove(conditionValue.toFloatOrNull() ?: 0f)
            else -> AlertCondition.CpuAbove(0f)
        },
        isEnabled = isEnabled,
        soundEnabled = soundEnabled,
        webhookUrl = webhookUrl
    )
}

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager
) : PreferencesRepository {
    override fun observePreferences(): Flow<AppPreferences> = preferencesManager.preferences
    override suspend fun getPreferences(): AppPreferences = preferencesManager.preferences.first()
    override suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences) = preferencesManager.updatePreferences(transform)
}
