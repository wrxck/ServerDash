package com.serverdash.app.domain.usecase

import com.serverdash.app.domain.repository.PreferencesRepository
import com.serverdash.app.domain.repository.SshRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildClaudeContextUseCase @Inject constructor(
    private val sshRepository: SshRepository,
    private val preferencesRepository: PreferencesRepository
) {
    sealed interface ContextRequest {
        data object FullSnapshot : ContextRequest
        data class ServiceDebug(
            val serviceName: String,
            val serviceType: String,
            val errorMessage: String? = null
        ) : ContextRequest
        data class MetricAlert(
            val metricType: String,
            val currentValue: String,
            val threshold: String
        ) : ContextRequest
        data class CustomError(
            val errorMessage: String,
            val source: String
        ) : ContextRequest
    }

    suspend fun build(request: ContextRequest): String {
        return when (request) {
            is ContextRequest.FullSnapshot -> buildFullSnapshot()
            is ContextRequest.ServiceDebug -> buildServiceDebug(request)
            is ContextRequest.MetricAlert -> buildMetricAlert(request)
            is ContextRequest.CustomError -> buildCustomError(request)
        }
    }

    private suspend fun buildFullSnapshot(): String {
        val metrics = sshRepository.executeCommand(
            "echo '=CPU='; top -bn1 | head -5; echo '=MEM='; free -h; echo '=DISK='; df -h /; echo '=LOAD='; uptime; echo '=SERVICES='; systemctl list-units --type=service --state=failed --no-legend 2>/dev/null | head -10"
        )
        return buildString {
            appendLine("[SERVER CONTEXT]")
            metrics.getOrNull()?.let { appendLine(it.output.take(800)) }
                ?: appendLine("(could not fetch server metrics)")
        }
    }

    private suspend fun buildServiceDebug(req: ContextRequest.ServiceDebug): String {
        val logsCmd = when (req.serviceType.lowercase()) {
            "systemd" -> "journalctl -u ${req.serviceName} -n 30 --no-pager -o short-iso 2>&1"
            "docker" -> "docker logs --tail 30 ${req.serviceName} 2>&1"
            else -> "echo 'Unknown service type'"
        }
        val statusCmd = when (req.serviceType.lowercase()) {
            "systemd" -> "systemctl status ${req.serviceName} 2>&1"
            "docker" -> "docker inspect --format='{{.State.Status}} {{.State.Error}}' ${req.serviceName} 2>&1"
            else -> "echo 'Unknown'"
        }
        val statusResult = sshRepository.executeCommand(statusCmd)
        val logsResult = sshRepository.executeCommand(logsCmd)

        return buildString {
            appendLine("[DEBUG CONTEXT: ${req.serviceName}]")
            req.errorMessage?.let { appendLine("Error: $it") }
            appendLine("[STATUS]")
            statusResult.getOrNull()?.let { appendLine(it.output.take(500)) }
            appendLine("[RECENT LOGS]")
            logsResult.getOrNull()?.let { appendLine(it.output.take(1500)) }
            appendLine("[END CONTEXT]")
            appendLine()
            appendLine("Please analyze the above context and help debug this ${req.serviceType} service '${req.serviceName}'. What's wrong and how can it be fixed?")
        }
    }

    private suspend fun buildMetricAlert(req: ContextRequest.MetricAlert): String {
        val diagnosticCmd = when (req.metricType.lowercase()) {
            "cpu" -> "ps aux --sort=-%cpu | head -10; echo '---'; top -bn1 | head -10"
            "memory", "mem" -> "ps aux --sort=-%mem | head -10; echo '---'; free -h"
            "disk" -> "df -h; echo '---'; du -sh /var/log/* 2>/dev/null | sort -rh | head -10"
            else -> "top -bn1 | head -15"
        }
        val result = sshRepository.executeCommand(diagnosticCmd)

        return buildString {
            appendLine("[METRIC ALERT: ${req.metricType}]")
            appendLine("Current: ${req.currentValue}, Threshold: ${req.threshold}")
            appendLine("[DIAGNOSTICS]")
            result.getOrNull()?.let { appendLine(it.output.take(1500)) }
            appendLine("[END CONTEXT]")
            appendLine()
            appendLine("The ${req.metricType} usage is at ${req.currentValue} (threshold: ${req.threshold}). Analyze the diagnostics above and suggest what's causing high usage and how to resolve it.")
        }
    }

    private suspend fun buildCustomError(req: ContextRequest.CustomError): String {
        return buildString {
            appendLine("[ERROR from ${req.source}]")
            appendLine(req.errorMessage.take(1000))
            appendLine("[END CONTEXT]")
            appendLine()
            appendLine("Please help debug this error from ${req.source}.")
        }
    }
}
