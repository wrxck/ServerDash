package com.serverdash.app.usecase

import com.google.common.truth.Truth.assertThat
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.ServiceRepository
import com.serverdash.app.domain.repository.SshRepository
import com.serverdash.app.domain.usecase.RefreshServiceStatusUseCase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RefreshServiceStatusUseCaseTest {

    private lateinit var serviceRepository: ServiceRepository
    private lateinit var sshRepository: SshRepository
    private lateinit var useCase: RefreshServiceStatusUseCase

    @Before
    fun setup() {
        serviceRepository = mockk()
        sshRepository = mockk()
        useCase = RefreshServiceStatusUseCase(serviceRepository, sshRepository)
        coEvery { serviceRepository.saveServices(any()) } just runs
    }

    @Test
    fun `updates existing service statuses`() = runTest {
        val existingServices = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.UNKNOWN)
        )
        coEvery { serviceRepository.getServices(1) } returns existingServices

        val sshOutput = """
===SYSTEMCTL===
nginx.service                loaded active running  A high performance web server
===DOCKER===
===METRICS===
0.5 0.3 0.2 1/100 1234
        """.trimIndent()
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = sshOutput, error = "")
        )

        val result = useCase(1)
        assertThat(result.isSuccess).isTrue()

        val services = result.getOrThrow()
        assertThat(services[0].status).isEqualTo(ServiceStatus.RUNNING)
        assertThat(services[0].subState).isEqualTo("running")
    }

    @Test
    fun `discovers new services from systemctl output`() = runTest {
        coEvery { serviceRepository.getServices(1) } returns emptyList()

        val sshOutput = """
===SYSTEMCTL===
nginx.service                loaded active running  A high performance web server
mysql.service                loaded inactive dead    MySQL Community Server
===DOCKER===
redis	Up 2 hours
===METRICS===
0.5 0.3 0.2 1/100 1234
        """.trimIndent()
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = sshOutput, error = "")
        )

        val result = useCase(1)
        assertThat(result.isSuccess).isTrue()

        val services = result.getOrThrow()
        assertThat(services).hasSize(3)

        val nginx = services.find { it.name == "nginx" }
        assertThat(nginx).isNotNull()
        assertThat(nginx!!.status).isEqualTo(ServiceStatus.RUNNING)
        assertThat(nginx.type).isEqualTo(ServiceType.SYSTEMD)
        assertThat(nginx.group).isEqualTo("System")

        val mysql = services.find { it.name == "mysql" }
        assertThat(mysql).isNotNull()
        assertThat(mysql!!.status).isEqualTo(ServiceStatus.STOPPED)

        val redis = services.find { it.name == "redis" }
        assertThat(redis).isNotNull()
        assertThat(redis!!.status).isEqualTo(ServiceStatus.RUNNING)
        assertThat(redis.type).isEqualTo(ServiceType.DOCKER)
        assertThat(redis.group).isEqualTo("Docker")
    }

    @Test
    fun `does not duplicate existing services`() = runTest {
        val existing = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.STOPPED)
        )
        coEvery { serviceRepository.getServices(1) } returns existing

        val sshOutput = """
===SYSTEMCTL===
nginx.service                loaded active running  web server
===DOCKER===
===METRICS===
        """.trimIndent()
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = sshOutput, error = "")
        )

        val result = useCase(1)
        val services = result.getOrThrow()

        // Should update the existing one, not add a duplicate
        val nginxServices = services.filter { it.name == "nginx" }
        assertThat(nginxServices).hasSize(1)
        assertThat(nginxServices[0].status).isEqualTo(ServiceStatus.RUNNING)
    }

    @Test
    fun `handles SSH failure gracefully`() = runTest {
        coEvery { sshRepository.executeCommand(any()) } returns Result.failure(Exception("Connection lost"))

        val result = useCase(1)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `handles empty output`() = runTest {
        coEvery { serviceRepository.getServices(1) } returns emptyList()
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = "===SYSTEMCTL===\n===DOCKER===\n===METRICS===", error = "")
        )

        val result = useCase(1)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEmpty()
    }

    @Test
    fun `failed service status is correctly parsed`() = runTest {
        coEvery { serviceRepository.getServices(1) } returns emptyList()

        val sshOutput = """
===SYSTEMCTL===
fail2ban.service             loaded failed  failed  Fail2Ban Service
===DOCKER===
===METRICS===
        """.trimIndent()
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = sshOutput, error = "")
        )

        val result = useCase(1)
        val services = result.getOrThrow()
        assertThat(services).hasSize(1)
        assertThat(services[0].status).isEqualTo(ServiceStatus.FAILED)
    }

    @Test
    fun `docker exited containers are STOPPED`() = runTest {
        coEvery { serviceRepository.getServices(1) } returns emptyList()

        val sshOutput = """
===SYSTEMCTL===
===DOCKER===
myapp	Exited (0) 3 hours ago
===METRICS===
        """.trimIndent()
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(exitCode = 0, output = sshOutput, error = "")
        )

        val result = useCase(1)
        val services = result.getOrThrow()
        assertThat(services).hasSize(1)
        assertThat(services[0].status).isEqualTo(ServiceStatus.STOPPED)
        assertThat(services[0].type).isEqualTo(ServiceType.DOCKER)
    }
}
