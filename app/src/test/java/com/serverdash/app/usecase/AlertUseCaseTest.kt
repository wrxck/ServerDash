package com.serverdash.app.usecase

import com.google.common.truth.Truth.assertThat
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.AlertRepository
import com.serverdash.app.domain.usecase.EvaluateAlertRulesUseCase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AlertUseCaseTest {

    private lateinit var alertRepository: AlertRepository
    private lateinit var useCase: EvaluateAlertRulesUseCase

    @Before
    fun setup() {
        alertRepository = mockk()
        useCase = EvaluateAlertRulesUseCase(alertRepository)
        coEvery { alertRepository.saveAlert(any()) } just runs
    }

    @Test
    fun `triggers alert when service is down`() = runTest {
        val rules = listOf(
            AlertRule(1, 1, "nginx down", AlertCondition.ServiceDown("nginx"), isEnabled = true)
        )
        coEvery { alertRepository.getAlertRules(1) } returns rules

        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.STOPPED)
        )
        val metrics = SystemMetrics()

        val alerts = useCase(services, metrics, 1)
        assertThat(alerts).hasSize(1)
        assertThat(alerts[0].message).contains("nginx")
    }

    @Test
    fun `no alert when service is running`() = runTest {
        val rules = listOf(
            AlertRule(1, 1, "nginx down", AlertCondition.ServiceDown("nginx"), isEnabled = true)
        )
        coEvery { alertRepository.getAlertRules(1) } returns rules

        val services = listOf(
            Service(1, 1, "nginx", "nginx", ServiceType.SYSTEMD, ServiceStatus.RUNNING)
        )
        val metrics = SystemMetrics()

        val alerts = useCase(services, metrics, 1)
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `triggers alert when cpu above threshold`() = runTest {
        val rules = listOf(
            AlertRule(1, 1, "High CPU", AlertCondition.CpuAbove(80f), isEnabled = true)
        )
        coEvery { alertRepository.getAlertRules(1) } returns rules

        val services = emptyList<Service>()
        val metrics = SystemMetrics(cpuUsage = 95f)

        val alerts = useCase(services, metrics, 1)
        assertThat(alerts).hasSize(1)
        assertThat(alerts[0].message).contains("CPU")
    }

    @Test
    fun `no alert when cpu below threshold`() = runTest {
        val rules = listOf(
            AlertRule(1, 1, "High CPU", AlertCondition.CpuAbove(80f), isEnabled = true)
        )
        coEvery { alertRepository.getAlertRules(1) } returns rules

        val services = emptyList<Service>()
        val metrics = SystemMetrics(cpuUsage = 50f)

        val alerts = useCase(services, metrics, 1)
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `disabled rules are skipped`() = runTest {
        val rules = listOf(
            AlertRule(1, 1, "High CPU", AlertCondition.CpuAbove(10f), isEnabled = false)
        )
        coEvery { alertRepository.getAlertRules(1) } returns rules

        val services = emptyList<Service>()
        val metrics = SystemMetrics(cpuUsage = 95f)

        val alerts = useCase(services, metrics, 1)
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `memory alert triggers at threshold`() = runTest {
        val rules = listOf(
            AlertRule(1, 1, "High Memory", AlertCondition.MemoryAbove(90f), isEnabled = true)
        )
        coEvery { alertRepository.getAlertRules(1) } returns rules

        val metrics = SystemMetrics(memoryUsed = 950, memoryTotal = 1000) // 95%

        val alerts = useCase(emptyList(), metrics, 1)
        assertThat(alerts).hasSize(1)
    }

    @Test
    fun `disk alert triggers at threshold`() = runTest {
        val rules = listOf(
            AlertRule(1, 1, "High Disk", AlertCondition.DiskAbove(85f), isEnabled = true)
        )
        coEvery { alertRepository.getAlertRules(1) } returns rules

        val metrics = SystemMetrics(diskUsed = 900, diskTotal = 1000) // 90%

        val alerts = useCase(emptyList(), metrics, 1)
        assertThat(alerts).hasSize(1)
    }
}
