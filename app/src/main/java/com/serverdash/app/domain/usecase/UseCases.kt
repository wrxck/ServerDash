package com.serverdash.app.domain.usecase

import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ConnectToServerUseCase @Inject constructor(
    private val serverRepository: ServerRepository,
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(config: ServerConfig): Result<Long> {
        return sshRepository.connect(config).map {
            serverRepository.saveServerConfig(config)
        }
    }
}

class DisconnectFromServerUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke() = sshRepository.disconnect()
}

class DiscoverServicesUseCase @Inject constructor(
    private val serviceRepository: ServiceRepository,
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(serverId: Long): Result<List<Service>> {
        return try {
            val systemdResult = sshRepository.executeCommand(
                "systemctl list-units --type=service --all --no-pager --no-legend"
            )
            val dockerResult = sshRepository.executeCommand(
                "docker ps -a --format '{{.Names}}\\t{{.Status}}' 2>/dev/null"
            )

            val services = mutableListOf<Service>()

            systemdResult.getOrNull()?.let { result ->
                services.addAll(parseSystemctlOutput(result.output, serverId))
            }

            dockerResult.getOrNull()?.let { result ->
                services.addAll(parseDockerOutput(result.output, serverId))
            }

            serviceRepository.saveServices(services)
            Result.success(services)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSystemctlOutput(output: String, serverId: Long): List<Service> {
        return output.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex(), limit = 5)
                if (parts.size >= 4) {
                    val name = parts[0].removeSuffix(".service")
                    val activeState = parts[2]
                    val subState = parts[3]
                    val description = if (parts.size >= 5) parts[4] else ""
                    Service(
                        serverId = serverId,
                        name = name,
                        displayName = name,
                        type = ServiceType.SYSTEMD,
                        status = when (activeState) {
                            "active" -> ServiceStatus.RUNNING
                            "failed" -> ServiceStatus.FAILED
                            "inactive" -> ServiceStatus.STOPPED
                            else -> ServiceStatus.UNKNOWN
                        },
                        subState = subState,
                        description = description
                    )
                } else null
            }
    }

    private fun parseDockerOutput(output: String, serverId: Long): List<Service> {
        return output.lines()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("\t", limit = 2)
                val name = parts[0]
                val statusStr = if (parts.size > 1) parts[1] else ""
                Service(
                    serverId = serverId,
                    name = name,
                    displayName = name,
                    type = ServiceType.DOCKER,
                    status = when {
                        statusStr.startsWith("Up") -> ServiceStatus.RUNNING
                        statusStr.startsWith("Exited") -> ServiceStatus.STOPPED
                        else -> ServiceStatus.UNKNOWN
                    },
                    subState = statusStr
                )
            }
    }
}

class RefreshServiceStatusUseCase @Inject constructor(
    private val serviceRepository: ServiceRepository,
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(serverId: Long): Result<List<Service>> {
        return try {
            val command = buildString {
                append("echo '===SYSTEMCTL==='; ")
                append("systemctl list-units --type=service --all --no-pager --no-legend 2>/dev/null; ")
                append("echo '===DOCKER==='; ")
                append("docker ps -a --format '{{.Names}}\\t{{.Status}}' 2>/dev/null; ")
                append("echo '===METRICS==='; ")
                append("cat /proc/loadavg 2>/dev/null; ")
                append("free -b 2>/dev/null; ")
                append("df -B1 / 2>/dev/null; ")
                append("cat /proc/uptime 2>/dev/null")
            }

            val result = sshRepository.executeCommand(command).getOrThrow()
            val sections = result.output.split("===SYSTEMCTL===", "===DOCKER===", "===METRICS===")

            val services = serviceRepository.getServices(serverId).toMutableList()
            // Update statuses from current state
            if (sections.size > 1) {
                val systemctlOutput = sections[1].trim()
                updateServiceStatuses(services, systemctlOutput, ServiceType.SYSTEMD)
            }
            if (sections.size > 2) {
                val dockerOutput = sections[2].trim()
                updateServiceStatuses(services, dockerOutput, ServiceType.DOCKER)
            }

            serviceRepository.saveServices(services)
            Result.success(services)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun updateServiceStatuses(services: MutableList<Service>, output: String, type: ServiceType) {
        val statusMap = mutableMapOf<String, Pair<ServiceStatus, String>>()
        output.lines().filter { it.isNotBlank() }.forEach { line ->
            if (type == ServiceType.SYSTEMD) {
                val parts = line.trim().split("\\s+".toRegex(), limit = 5)
                if (parts.size >= 4) {
                    val name = parts[0].removeSuffix(".service")
                    val status = when (parts[2]) {
                        "active" -> ServiceStatus.RUNNING
                        "failed" -> ServiceStatus.FAILED
                        "inactive" -> ServiceStatus.STOPPED
                        else -> ServiceStatus.UNKNOWN
                    }
                    statusMap[name] = status to parts[3]
                }
            } else {
                val parts = line.split("\t", limit = 2)
                if (parts.isNotEmpty()) {
                    val name = parts[0]
                    val statusStr = if (parts.size > 1) parts[1] else ""
                    val status = when {
                        statusStr.startsWith("Up") -> ServiceStatus.RUNNING
                        statusStr.startsWith("Exited") -> ServiceStatus.STOPPED
                        else -> ServiceStatus.UNKNOWN
                    }
                    statusMap[name] = status to statusStr
                }
            }
        }

        services.forEachIndexed { index, service ->
            if (service.type == type) {
                statusMap[service.name]?.let { (status, subState) ->
                    services[index] = service.copy(status = status, subState = subState)
                }
            }
        }
    }
}

class ControlServiceUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(service: Service, action: ServiceAction): Result<CommandResult> {
        val command = when (service.type) {
            ServiceType.SYSTEMD -> "sudo systemctl ${action.command} ${service.name}"
            ServiceType.DOCKER -> "docker ${action.command} ${service.name}"
        }
        return sshRepository.executeCommand(command)
    }
}

enum class ServiceAction(val command: String) {
    START("start"),
    STOP("stop"),
    RESTART("restart")
}

class GetServiceLogsUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(service: Service, lines: Int = 100): Result<List<ServiceLog>> {
        val command = when (service.type) {
            ServiceType.SYSTEMD -> "journalctl -u ${service.name} -n $lines --no-pager"
            ServiceType.DOCKER -> "docker logs --tail $lines ${service.name} 2>&1"
        }
        return sshRepository.executeCommand(command).map { result ->
            result.output.lines().filter { it.isNotBlank() }.map { line ->
                ServiceLog(timestamp = "", message = line)
            }
        }
    }

    fun stream(service: Service): Flow<String> {
        // This will be implemented in SshRepository
        return kotlinx.coroutines.flow.flow {
            val command = when (service.type) {
                ServiceType.SYSTEMD -> "journalctl -u ${service.name} -f --no-pager"
                ServiceType.DOCKER -> "docker logs -f ${service.name} 2>&1"
            }
            // Delegate to SSH repository streaming
        }
    }
}

class FetchSystemMetricsUseCase @Inject constructor(
    private val sshRepository: SshRepository,
    private val metricsRepository: MetricsRepository
) {
    suspend operator fun invoke(): Result<SystemMetrics> {
        val command = "cat /proc/loadavg && echo '---' && free -b && echo '---' && df -B1 / && echo '---' && cat /proc/uptime"
        return sshRepository.executeCommand(command).map { result ->
            parseMetrics(result.output).also { metrics ->
                metricsRepository.saveMetrics(metrics)
            }
        }
    }

    private fun parseMetrics(output: String): SystemMetrics {
        val sections = output.split("---").map { it.trim() }

        var loadAvg1 = 0f; var loadAvg5 = 0f; var loadAvg15 = 0f
        var memUsed = 0L; var memTotal = 0L
        var diskUsed = 0L; var diskTotal = 0L
        var uptime = 0L

        // Parse /proc/loadavg
        if (sections.isNotEmpty()) {
            val parts = sections[0].split("\\s+".toRegex())
            if (parts.size >= 3) {
                loadAvg1 = parts[0].toFloatOrNull() ?: 0f
                loadAvg5 = parts[1].toFloatOrNull() ?: 0f
                loadAvg15 = parts[2].toFloatOrNull() ?: 0f
            }
        }

        // Parse free -b
        if (sections.size > 1) {
            val memLines = sections[1].lines()
            val memLine = memLines.find { it.startsWith("Mem:") }
            if (memLine != null) {
                val parts = memLine.split("\\s+".toRegex())
                if (parts.size >= 3) {
                    memTotal = parts[1].toLongOrNull() ?: 0
                    memUsed = parts[2].toLongOrNull() ?: 0
                }
            }
        }

        // Parse df
        if (sections.size > 2) {
            val dfLines = sections[2].lines()
            if (dfLines.size >= 2) {
                val parts = dfLines[1].split("\\s+".toRegex())
                if (parts.size >= 4) {
                    diskTotal = parts[1].toLongOrNull() ?: 0
                    diskUsed = parts[2].toLongOrNull() ?: 0
                }
            }
        }

        // Parse uptime
        if (sections.size > 3) {
            val uptimeParts = sections[3].split("\\s+".toRegex())
            uptime = uptimeParts[0].toDoubleOrNull()?.toLong() ?: 0
        }

        // Approximate CPU from load average
        val cpuUsage = (loadAvg1 * 100f).coerceIn(0f, 100f)

        return SystemMetrics(
            cpuUsage = cpuUsage,
            memoryUsed = memUsed,
            memoryTotal = memTotal,
            diskUsed = diskUsed,
            diskTotal = diskTotal,
            loadAvg1 = loadAvg1,
            loadAvg5 = loadAvg5,
            loadAvg15 = loadAvg15,
            uptimeSeconds = uptime
        )
    }
}

class EvaluateAlertRulesUseCase @Inject constructor(
    private val alertRepository: AlertRepository
) {
    suspend operator fun invoke(
        services: List<Service>,
        metrics: SystemMetrics,
        serverId: Long
    ): List<Alert> {
        val rules = alertRepository.getAlertRules(serverId).filter { it.isEnabled }
        val alerts = mutableListOf<Alert>()

        for (rule in rules) {
            val triggered = when (val condition = rule.condition) {
                is AlertCondition.ServiceDown -> {
                    services.any { it.name == condition.serviceName && it.status != ServiceStatus.RUNNING }
                }
                is AlertCondition.CpuAbove -> metrics.cpuUsage > condition.threshold
                is AlertCondition.MemoryAbove -> metrics.memoryUsagePercent > condition.threshold
                is AlertCondition.DiskAbove -> metrics.diskUsagePercent > condition.threshold
            }

            if (triggered) {
                val message = when (val condition = rule.condition) {
                    is AlertCondition.ServiceDown -> "Service ${condition.serviceName} is down"
                    is AlertCondition.CpuAbove -> "CPU usage at ${metrics.cpuUsage.toInt()}% (threshold: ${condition.threshold.toInt()}%)"
                    is AlertCondition.MemoryAbove -> "Memory usage at ${metrics.memoryUsagePercent.toInt()}% (threshold: ${condition.threshold.toInt()}%)"
                    is AlertCondition.DiskAbove -> "Disk usage at ${metrics.diskUsagePercent.toInt()}% (threshold: ${condition.threshold.toInt()}%)"
                }
                val alert = Alert(rule = rule, message = message)
                alertRepository.saveAlert(alert)
                alerts.add(alert)
            }
        }

        return alerts
    }
}

class ExecuteCommandUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(command: String): Result<CommandResult> {
        return sshRepository.executeCommand(command)
    }
}

class ReadConfigFileUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(path: String): Result<String> {
        return sshRepository.readFile(path)
    }
}

class WriteConfigFileUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(path: String, content: String): Result<Unit> {
        return sshRepository.writeFile(path, content)
    }
}

class PinServiceUseCase @Inject constructor(
    private val serviceRepository: ServiceRepository
) {
    suspend operator fun invoke(serviceId: Long, pinned: Boolean) {
        serviceRepository.pinService(serviceId, pinned)
    }
}
