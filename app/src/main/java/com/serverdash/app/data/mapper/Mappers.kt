package com.serverdash.app.data.mapper

import com.serverdash.app.data.local.db.*
import com.serverdash.app.domain.model.*

fun ServerConfigEntity.toDomain(): ServerConfig = ServerConfig(
    id = id,
    host = host,
    port = port,
    username = username,
    authMethod = when (authType) {
        "KEY" -> AuthMethod.KeyBased(privateKey, passphrase)
        else -> AuthMethod.Password(password)
    },
    label = label,
    sudoPassword = sudoPassword
)

fun ServerConfig.toEntity(): ServerConfigEntity = ServerConfigEntity(
    id = id,
    host = host,
    port = port,
    username = username,
    authType = when (authMethod) {
        is AuthMethod.Password -> "PASSWORD"
        is AuthMethod.KeyBased -> "KEY"
    },
    password = (authMethod as? AuthMethod.Password)?.password ?: "",
    privateKey = (authMethod as? AuthMethod.KeyBased)?.privateKey ?: "",
    passphrase = (authMethod as? AuthMethod.KeyBased)?.passphrase ?: "",
    label = label,
    sudoPassword = sudoPassword
)

fun ServiceEntity.toDomain(): Service = Service(
    id = id,
    serverId = serverId,
    name = name,
    displayName = displayName,
    type = when (type) {
        ServiceTypeDb.SYSTEMD -> ServiceType.SYSTEMD
        ServiceTypeDb.DOCKER -> ServiceType.DOCKER
    },
    status = when (status) {
        ServiceStatusDb.RUNNING -> ServiceStatus.RUNNING
        ServiceStatusDb.STOPPED -> ServiceStatus.STOPPED
        ServiceStatusDb.FAILED -> ServiceStatus.FAILED
        ServiceStatusDb.UNKNOWN -> ServiceStatus.UNKNOWN
    },
    isPinned = isPinned,
    subState = subState,
    description = description,
    group = group
)

fun Service.toEntity(): ServiceEntity = ServiceEntity(
    id = id,
    serverId = serverId,
    name = name,
    displayName = displayName,
    type = when (type) {
        ServiceType.SYSTEMD -> ServiceTypeDb.SYSTEMD
        ServiceType.DOCKER -> ServiceTypeDb.DOCKER
    },
    status = when (status) {
        ServiceStatus.RUNNING -> ServiceStatusDb.RUNNING
        ServiceStatus.STOPPED -> ServiceStatusDb.STOPPED
        ServiceStatus.FAILED -> ServiceStatusDb.FAILED
        ServiceStatus.UNKNOWN -> ServiceStatusDb.UNKNOWN
    },
    isPinned = isPinned,
    subState = subState,
    description = description,
    group = group
)

fun MetricsEntity.toDomain(): SystemMetrics = SystemMetrics(
    cpuUsage = cpuUsage,
    memoryUsed = memoryUsed,
    memoryTotal = memoryTotal,
    diskUsed = diskUsed,
    diskTotal = diskTotal,
    loadAvg1 = loadAvg1,
    loadAvg5 = loadAvg5,
    loadAvg15 = loadAvg15,
    uptimeSeconds = uptimeSeconds,
    timestamp = timestamp
)

fun SystemMetrics.toEntity(): MetricsEntity = MetricsEntity(
    cpuUsage = cpuUsage,
    memoryUsed = memoryUsed,
    memoryTotal = memoryTotal,
    diskUsed = diskUsed,
    diskTotal = diskTotal,
    loadAvg1 = loadAvg1,
    loadAvg5 = loadAvg5,
    loadAvg15 = loadAvg15,
    uptimeSeconds = uptimeSeconds,
    timestamp = timestamp
)
