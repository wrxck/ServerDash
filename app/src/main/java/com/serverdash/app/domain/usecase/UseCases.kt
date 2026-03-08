package com.serverdash.app.domain.usecase

import android.util.Log
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

private const val TAG = "ServerDash"

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
            Log.d(TAG, "DiscoverServices: starting for serverId=$serverId")
            val systemdResult = sshRepository.executeCommand(
                "systemctl list-units --type=service --all --no-pager --no-legend"
            )
            Log.d(TAG, "DiscoverServices: systemctl done, success=${systemdResult.isSuccess}")

            val dockerResult = sshRepository.executeCommand(
                "docker ps -a --format '{{.Names}}\\t{{.Status}}' 2>/dev/null"
            )
            Log.d(TAG, "DiscoverServices: docker done, success=${dockerResult.isSuccess}")

            val services = mutableListOf<Service>()

            systemdResult.getOrNull()?.let { result ->
                val parsed = parseSystemctlOutput(result.output, serverId)
                Log.d(TAG, "DiscoverServices: parsed ${parsed.size} systemd services")
                services.addAll(parsed)
            }

            dockerResult.getOrNull()?.let { result ->
                val parsed = parseDockerOutput(result.output, serverId)
                Log.d(TAG, "DiscoverServices: parsed ${parsed.size} docker services")
                services.addAll(parsed)
            }

            Log.d(TAG, "DiscoverServices: saving ${services.size} services")
            serviceRepository.saveServices(services)
            Log.d(TAG, "DiscoverServices: save complete")
            Result.success(services)
        } catch (e: Exception) {
            Log.e(TAG, "DiscoverServices: failed", e)
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
            ServiceType.SYSTEMD -> sshRepository.wrapWithSudo("systemctl ${action.command} ${service.name}")
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
        return when (service.type) {
            ServiceType.SYSTEMD -> fetchSystemdLogs(service, lines)
            ServiceType.DOCKER -> fetchDockerLogs(service, lines)
        }
    }

    private suspend fun fetchSystemdLogs(service: Service, lines: Int): Result<List<ServiceLog>> {
        // Try with sudo first to get full logs (all users + system)
        val sudoCommand = sshRepository.wrapWithSudo("journalctl -u ${service.name} -n $lines --no-pager -o short-iso") + " 2>&1"
        val sudoResult = sshRepository.executeCommand(sudoCommand)
        val sudoOutput = sudoResult.getOrNull()?.output ?: ""
        val sudoLogs = parseJournalctlOutput(sudoOutput)

        // If sudo worked, use those logs
        if (sudoLogs.isNotEmpty()) return Result.success(sudoLogs)

        // If sudo failed (no password, wrong password, etc.), fall back to non-sudo
        val command = "journalctl -u ${service.name} -n $lines --no-pager -o short-iso 2>&1"
        val result = sshRepository.executeCommand(command)
        return result.map { cmdResult ->
            val logs = parseJournalctlOutput(cmdResult.output)
            if (logs.isEmpty() && cmdResult.output.isNotBlank()) {
                listOf(ServiceLog(timestamp = "", message = cmdResult.output.trim()))
            } else {
                logs
            }
        }.recoverCatching {
            listOf(ServiceLog(timestamp = "", message = "Failed to fetch logs: ${it.message}"))
        }
    }

    private fun parseJournalctlOutput(output: String): List<ServiceLog> {
        return output.lines()
            .filter { it.isNotBlank() && !it.startsWith("--") }
            .mapNotNull { line ->
                // short-iso format: 2024-01-15T10:30:45+0000 hostname service[pid]: message
                val isoMatch = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+\-]\d{4})\s+\S+\s+(.+)$""").find(line)
                if (isoMatch != null) {
                    ServiceLog(
                        timestamp = isoMatch.groupValues[1],
                        message = isoMatch.groupValues[2]
                    )
                } else if (line.isNotBlank() && !line.contains("No journal files")) {
                    ServiceLog(timestamp = "", message = line)
                } else null
            }
    }

    private suspend fun fetchDockerLogs(service: Service, lines: Int): Result<List<ServiceLog>> {
        val command = "docker logs --tail $lines ${service.name} 2>&1"
        var result = sshRepository.executeCommand(command)
        // Retry once after a brief delay if not connected (reconnection may be in progress)
        if (result.isFailure && result.exceptionOrNull()?.message?.contains("Not connected") == true) {
            delay(2000)
            result = sshRepository.executeCommand(command)
        }
        return result.map { cmdResult ->
            if (cmdResult.output.isBlank()) {
                listOf(ServiceLog(timestamp = "", message = "(no log output)"))
            } else {
                cmdResult.output.lines().filter { it.isNotBlank() }.map { line ->
                    // Docker logs often have timestamps at the start
                    val tsMatch = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z?)\s+(.+)$""").find(line)
                    if (tsMatch != null) {
                        ServiceLog(timestamp = tsMatch.groupValues[1], message = tsMatch.groupValues[2])
                    } else {
                        ServiceLog(timestamp = "", message = line)
                    }
                }
            }
        }.recoverCatching {
            listOf(ServiceLog(timestamp = "", message = "Failed to fetch docker logs: ${it.message}"))
        }
    }

    fun stream(service: Service): Flow<String> {
        return flow {
            val logFlow = sshRepository.startLogStream(service.name, service.type)
            logFlow.collect { line ->
                emit(line)
            }
        }
    }
}

class FetchSystemMetricsUseCase @Inject constructor(
    private val sshRepository: SshRepository,
    private val metricsRepository: MetricsRepository
) {
    suspend operator fun invoke(): Result<SystemMetrics> {
        val command = "cat /proc/loadavg && echo '---' && free -b && echo '---' && df -B1 / && echo '---' && cat /proc/uptime && echo '---' && nproc"
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
        var numCpus = 1

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

        // Parse nproc (CPU count)
        if (sections.size > 4) {
            numCpus = sections[4].trim().toIntOrNull() ?: 1
            if (numCpus < 1) numCpus = 1
        }

        // CPU usage: load average normalized by CPU count
        val cpuUsage = ((loadAvg1 / numCpus) * 100f).coerceIn(0f, 100f)

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

class DetectSystemUsersUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(): Result<List<SystemUser>> {
        val command = "getent passwd | awk -F: '(\$3 == 0 || \$3 >= 1000) && \$7 !~ /(nologin|false)/ {print \$1 \":\" \$6 \":\" \$3}'"
        return sshRepository.executeCommand(command).map { result ->
            result.output.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split(":", limit = 3)
                    if (parts.size == 3) {
                        SystemUser(
                            username = parts[0],
                            homeDirectory = parts[1],
                            uid = parts[2].toIntOrNull() ?: -1
                        )
                    } else null
                }
        }
    }
}

class DetectClaudeCodeUsersUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(users: List<SystemUser>): Result<List<SystemUser>> {
        if (users.isEmpty()) return Result.success(emptyList())
        // Check each user individually with sudo to handle restricted home dirs like /root
        val results = mutableMapOf<String, Boolean>()
        for (user in users) {
            val checkCmd = sshRepository.wrapWithSudo("test -d '${user.homeDirectory}/.claude'")
            val result = sshRepository.executeCommand("$checkCmd && echo YES || echo NO")
            val output = result.getOrNull()?.output?.trim() ?: "NO"
            results[user.username] = output.lines().last().trim() == "YES"
        }
        return Result.success(users.map { user ->
            user.copy(hasClaudeCode = results[user.username] == true)
        })
    }
}
