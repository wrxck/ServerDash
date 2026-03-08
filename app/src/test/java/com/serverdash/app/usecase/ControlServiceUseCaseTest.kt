package com.serverdash.app.usecase

import com.google.common.truth.Truth.assertThat
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.SshRepository
import com.serverdash.app.domain.usecase.ControlServiceUseCase
import com.serverdash.app.domain.usecase.ServiceAction
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ControlServiceUseCaseTest {

    private lateinit var sshRepository: SshRepository
    private lateinit var useCase: ControlServiceUseCase

    @Before
    fun setup() {
        sshRepository = mockk()
        useCase = ControlServiceUseCase(sshRepository)
    }

    @Test
    fun `systemd service uses sudo`() = runTest {
        val service = Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD)
        coEvery { sshRepository.executeSudoCommand(any()) } returns Result.success(
            CommandResult(0, "", "")
        )

        useCase(service, ServiceAction.START)

        coVerify { sshRepository.executeSudoCommand("systemctl start 'nginx'") }
    }

    @Test
    fun `docker service uses regular command`() = runTest {
        val service = Service(1, 1, "redis", "redis", ServiceType.DOCKER)
        coEvery { sshRepository.executeCommand(any()) } returns Result.success(
            CommandResult(0, "", "")
        )

        useCase(service, ServiceAction.STOP)

        coVerify { sshRepository.executeCommand("docker stop 'redis'") }
    }

    @Test
    fun `restart action sends correct command`() = runTest {
        val service = Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD)
        coEvery { sshRepository.executeSudoCommand(any()) } returns Result.success(
            CommandResult(0, "", "")
        )

        useCase(service, ServiceAction.RESTART)

        coVerify { sshRepository.executeSudoCommand("systemctl restart 'nginx'") }
    }

    @Test
    fun `returns failure when SSH fails`() = runTest {
        val service = Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD)
        coEvery { sshRepository.executeSudoCommand(any()) } returns Result.failure(Exception("timeout"))

        val result = useCase(service, ServiceAction.START)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `returns non-zero exit code on sudo failure`() = runTest {
        val service = Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD)
        coEvery { sshRepository.executeSudoCommand(any()) } returns Result.success(
            CommandResult(1, "", "Sudo authentication failed. Check your sudo password in Settings.")
        )

        val result = useCase(service, ServiceAction.START)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().exitCode).isEqualTo(1)
        assertThat(result.getOrThrow().error).contains("Sudo authentication failed")
    }
}
