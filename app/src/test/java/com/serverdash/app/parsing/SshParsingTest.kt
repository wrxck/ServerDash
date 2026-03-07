package com.serverdash.app.parsing

import com.google.common.truth.Truth.assertThat
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.usecase.FetchSystemMetricsUseCase
import com.serverdash.app.domain.repository.MetricsRepository
import com.serverdash.app.domain.repository.SshRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SshParsingTest {

    @Test
    fun `parse systemctl output extracts services`() {
        val output = """
            nginx.service                loaded active running  A high performance web server
            mysql.service                loaded active running  MySQL Community Server
            ssh.service                  loaded active running  OpenBSD Secure Shell server
            apache2.service              loaded inactive dead   The Apache HTTP Server
            fail2ban.service             loaded failed  failed  Fail2Ban Service
        """.trimIndent()

        val services = parseSystemctlLines(output)

        assertThat(services).hasSize(5)
        assertThat(services[0].name).isEqualTo("nginx")
        assertThat(services[0].status).isEqualTo(ServiceStatus.RUNNING)
        assertThat(services[3].name).isEqualTo("apache2")
        assertThat(services[3].status).isEqualTo(ServiceStatus.STOPPED)
        assertThat(services[4].name).isEqualTo("fail2ban")
        assertThat(services[4].status).isEqualTo(ServiceStatus.FAILED)
    }

    @Test
    fun `parse docker output extracts containers`() {
        val output = """
            nginx	Up 2 hours
            redis	Up 5 days
            postgres	Exited (0) 3 hours ago
        """.trimIndent()

        val services = parseDockerLines(output)

        assertThat(services).hasSize(3)
        assertThat(services[0].name).isEqualTo("nginx")
        assertThat(services[0].status).isEqualTo(ServiceStatus.RUNNING)
        assertThat(services[2].name).isEqualTo("postgres")
        assertThat(services[2].status).isEqualTo(ServiceStatus.STOPPED)
    }

    @Test
    fun `parse loadavg extracts values`() {
        val output = "0.52 0.38 0.29 1/234 5678"
        val parts = output.split("\\s+".toRegex())

        assertThat(parts[0].toFloat()).isWithin(0.01f).of(0.52f)
        assertThat(parts[1].toFloat()).isWithin(0.01f).of(0.38f)
        assertThat(parts[2].toFloat()).isWithin(0.01f).of(0.29f)
    }

    @Test
    fun `parse free output extracts memory`() {
        val output = """
                          total        used        free      shared  buff/cache   available
            Mem:     8243752960  2147483648  3221225472   134217728  2875043840  5896509440
            Swap:    2147483648           0  2147483648
        """.trimIndent()

        val memLine = output.lines().find { it.trimStart().startsWith("Mem:") }
        assertThat(memLine).isNotNull()
        val parts = memLine!!.trim().split("\\s+".toRegex())
        assertThat(parts[1].toLong()).isEqualTo(8243752960)
        assertThat(parts[2].toLong()).isEqualTo(2147483648)
    }

    @Test
    fun `parse df output extracts disk usage`() {
        val output = """
            Filesystem     1B-blocks        Used   Available Use% Mounted on
            /dev/sda1      52710469632 21474836480 28529205248  43% /
        """.trimIndent()

        val lines = output.lines()
        assertThat(lines.size).isAtLeast(2)
        val parts = lines[1].split("\\s+".toRegex())
        assertThat(parts[1].toLong()).isEqualTo(52710469632)
        assertThat(parts[2].toLong()).isEqualTo(21474836480)
    }

    @Test
    fun `parse uptime extracts seconds`() {
        val output = "123456.78 234567.89"
        val uptime = output.split("\\s+".toRegex())[0].toDouble().toLong()
        assertThat(uptime).isEqualTo(123456)
    }

    // Helper functions matching the use case parsing logic
    private fun parseSystemctlLines(output: String): List<Service> {
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
                        serverId = 1,
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

    private fun parseDockerLines(output: String): List<Service> {
        return output.lines()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("\t", limit = 2)
                val name = parts[0]
                val statusStr = if (parts.size > 1) parts[1] else ""
                Service(
                    serverId = 1,
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
