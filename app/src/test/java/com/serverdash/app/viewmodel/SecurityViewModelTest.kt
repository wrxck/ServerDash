package com.serverdash.app.viewmodel

import android.content.Context
import androidx.biometric.BiometricManager
import com.google.common.truth.Truth.assertThat
import com.serverdash.app.data.encryption.EncryptionManager
import com.serverdash.app.data.local.db.*
import com.serverdash.app.presentation.screens.security.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var encryptionManager: EncryptionManager
    private lateinit var terminalHistoryDao: TerminalHistoryDao
    private lateinit var metricsDao: MetricsDao
    private lateinit var alertDao: AlertDao
    private lateinit var serviceDao: ServiceDao

    private lateinit var mockBiometricManager: BiometricManager
    private lateinit var mockDbFile: File

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        encryptionManager = mockk(relaxed = true)
        terminalHistoryDao = mockk(relaxed = true)
        metricsDao = mockk(relaxed = true)
        alertDao = mockk(relaxed = true)
        serviceDao = mockk(relaxed = true)

        mockDbFile = mockk {
            every { exists() } returns true
            every { length() } returns 4096L
        }
        every { context.getDatabasePath(any()) } returns mockDbFile

        mockBiometricManager = mockk()
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns mockBiometricManager

        // Default: no biometric hardware, no device credential
        every { mockBiometricManager.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        // Default encryption state
        every { encryptionManager.isEncryptionEnabled } returns false
        every { encryptionManager.isBiometricEnabled } returns false

        // Default DAO responses
        coEvery { terminalHistoryDao.getRecent(any()) } returns emptyList()
        coEvery { metricsDao.getRecent(any()) } returns emptyList()
        coEvery { alertDao.getRules(any()) } returns emptyList()
        coEvery { serviceDao.getServices(any()) } returns emptyList()

        // Default DAO delete stubs
        coEvery { terminalHistoryDao.deleteAll() } just runs
        coEvery { metricsDao.deleteOlderThan(any()) } just runs
        coEvery { alertDao.deleteRule(any()) } just runs
        coEvery { serviceDao.deleteByServer(any()) } just runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(BiometricManager::class)
    }

    private fun createViewModel() = SecurityViewModel(
        context, encryptionManager, terminalHistoryDao, metricsDao, alertDao, serviceDao
    )

    // ---------------------------------------------------------------
    // 1. Initial state has correct defaults
    // ---------------------------------------------------------------

    @Test
    fun `initial state has encryption disabled`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.isEncryptionEnabled).isFalse()
        assertThat(vm.state.value.isBiometricEnabled).isFalse()
        assertThat(vm.state.value.biometricAvailable).isFalse()
        assertThat(vm.state.value.deviceSecure).isFalse()
    }

    @Test
    fun `initial state has zero data counts`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.terminalHistoryCount).isEqualTo(0)
        assertThat(vm.state.value.metricsCount).isEqualTo(0)
        assertThat(vm.state.value.alertRulesCount).isEqualTo(0)
        assertThat(vm.state.value.serviceCacheCount).isEqualTo(0)
    }

    @Test
    fun `initial state has no error or confirmation dialogs`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.encryptionError).isNull()
        assertThat(vm.state.value.showClearConfirmation).isNull()
        assertThat(vm.state.value.clearSuccess).isNull()
        assertThat(vm.state.value.encryptionJustEnabled).isFalse()
    }

    @Test
    fun `initial state reflects encryption enabled when manager reports it`() = runTest {
        every { encryptionManager.isEncryptionEnabled } returns true
        every { encryptionManager.isBiometricEnabled } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.isEncryptionEnabled).isTrue()
        assertThat(vm.state.value.isBiometricEnabled).isTrue()
    }

    // ---------------------------------------------------------------
    // 2. RefreshData loads all counts
    // ---------------------------------------------------------------

    @Test
    fun `refreshData loads data counts from DAOs`() = runTest {
        val terminalEntries = listOf(
            mockk<TerminalHistoryEntity>(), mockk<TerminalHistoryEntity>(), mockk<TerminalHistoryEntity>()
        )
        val metricsEntries = listOf(mockk<MetricsEntity>(), mockk<MetricsEntity>())
        val alertRules = listOf(mockk<AlertRuleEntity>())
        val services = listOf(mockk<ServiceEntity>(), mockk<ServiceEntity>(), mockk<ServiceEntity>(), mockk<ServiceEntity>())

        coEvery { terminalHistoryDao.getRecent(any()) } returns terminalEntries
        coEvery { metricsDao.getRecent(any()) } returns metricsEntries
        coEvery { alertDao.getRules(any()) } returns alertRules
        coEvery { serviceDao.getServices(any()) } returns services

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.terminalHistoryCount).isEqualTo(3)
        assertThat(vm.state.value.metricsCount).isEqualTo(2)
        assertThat(vm.state.value.alertRulesCount).isEqualTo(1)
        assertThat(vm.state.value.serviceCacheCount).isEqualTo(4)
    }

    @Test
    fun `refreshData handles DAO exceptions gracefully`() = runTest {
        coEvery { terminalHistoryDao.getRecent(any()) } throws RuntimeException("DB error")
        coEvery { metricsDao.getRecent(any()) } throws RuntimeException("DB error")
        coEvery { alertDao.getRules(any()) } throws RuntimeException("DB error")
        coEvery { serviceDao.getServices(any()) } throws RuntimeException("DB error")

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.terminalHistoryCount).isEqualTo(0)
        assertThat(vm.state.value.metricsCount).isEqualTo(0)
        assertThat(vm.state.value.alertRulesCount).isEqualTo(0)
        assertThat(vm.state.value.serviceCacheCount).isEqualTo(0)
    }

    @Test
    fun `RefreshData event reloads all counts`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        // Now change DAO responses and trigger refresh
        coEvery { terminalHistoryDao.getRecent(any()) } returns listOf(mockk(), mockk())
        coEvery { metricsDao.getRecent(any()) } returns listOf(mockk())

        vm.onEvent(SecurityEvent.RefreshData)
        advanceUntilIdle()

        assertThat(vm.state.value.terminalHistoryCount).isEqualTo(2)
        assertThat(vm.state.value.metricsCount).isEqualTo(1)
    }

    // ---------------------------------------------------------------
    // 3. Enable encryption (with/without biometric)
    // ---------------------------------------------------------------

    @Test
    fun `enable encryption without biometric updates state`() = runTest {
        every { encryptionManager.enableEncryption(false) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.EnableEncryption(withBiometric = false))
        advanceUntilIdle()

        assertThat(vm.state.value.isEncryptionEnabled).isTrue()
        assertThat(vm.state.value.isBiometricEnabled).isFalse()
        assertThat(vm.state.value.encryptionJustEnabled).isTrue()
        assertThat(vm.state.value.encryptionError).isNull()
    }

    @Test
    fun `enable encryption with biometric updates state`() = runTest {
        every { encryptionManager.enableEncryption(true) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.EnableEncryption(withBiometric = true))
        advanceUntilIdle()

        assertThat(vm.state.value.isEncryptionEnabled).isTrue()
        assertThat(vm.state.value.isBiometricEnabled).isTrue()
        assertThat(vm.state.value.encryptionJustEnabled).isTrue()
    }

    @Test
    fun `enable encryption re-runs checkup`() = runTest {
        every { encryptionManager.enableEncryption(false) } returns Result.success(Unit)
        // After enabling, make isEncryptionEnabled return true for checkup
        every { encryptionManager.isEncryptionEnabled } returns false andThen true

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.EnableEncryption(withBiometric = false))
        advanceUntilIdle()

        // Checkup should reflect the new state (encryption now passes)
        assertThat(vm.state.value.checkupComplete).isTrue()
    }

    // ---------------------------------------------------------------
    // 4. Encryption error handling
    // ---------------------------------------------------------------

    @Test
    fun `encryption failure sets error message`() = runTest {
        every { encryptionManager.enableEncryption(any()) } returns Result.failure(
            RuntimeException("KeyStore error")
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.EnableEncryption(withBiometric = false))
        advanceUntilIdle()

        assertThat(vm.state.value.encryptionError).isEqualTo("KeyStore error")
        assertThat(vm.state.value.isEncryptionEnabled).isFalse()
    }

    @Test
    fun `encryption failure with null message uses unknown error`() = runTest {
        every { encryptionManager.enableEncryption(any()) } returns Result.failure(
            RuntimeException()
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.EnableEncryption(withBiometric = false))
        advanceUntilIdle()

        assertThat(vm.state.value.encryptionError).isEqualTo("Unknown error")
    }

    // ---------------------------------------------------------------
    // 5. DismissError clears error
    // ---------------------------------------------------------------

    @Test
    fun `DismissError clears encryption error`() = runTest {
        every { encryptionManager.enableEncryption(any()) } returns Result.failure(
            RuntimeException("error")
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.EnableEncryption(withBiometric = false))
        assertThat(vm.state.value.encryptionError).isNotNull()

        vm.onEvent(SecurityEvent.DismissError)
        assertThat(vm.state.value.encryptionError).isNull()
    }

    // ---------------------------------------------------------------
    // 6. RequestClear sets showClearConfirmation
    // ---------------------------------------------------------------

    @Test
    fun `RequestClear sets showClearConfirmation to the category`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.TERMINAL_HISTORY))
        assertThat(vm.state.value.showClearConfirmation).isEqualTo(DataCategory.TERMINAL_HISTORY)
    }

    @Test
    fun `RequestClear for ALL_DATA sets correct category`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.ALL_DATA))
        assertThat(vm.state.value.showClearConfirmation).isEqualTo(DataCategory.ALL_DATA)
    }

    // ---------------------------------------------------------------
    // 7. DismissClearConfirmation clears it
    // ---------------------------------------------------------------

    @Test
    fun `DismissClearConfirmation clears showClearConfirmation`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.METRICS_HISTORY))
        assertThat(vm.state.value.showClearConfirmation).isNotNull()

        vm.onEvent(SecurityEvent.DismissClearConfirmation)
        assertThat(vm.state.value.showClearConfirmation).isNull()
    }

    // ---------------------------------------------------------------
    // 8. ConfirmClear performs actual deletion for each DataCategory
    // ---------------------------------------------------------------

    @Test
    fun `ConfirmClear for TERMINAL_HISTORY calls deleteAll on terminalHistoryDao`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.TERMINAL_HISTORY))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        coVerify { terminalHistoryDao.deleteAll() }
        assertThat(vm.state.value.terminalHistoryCount).isEqualTo(0)
        assertThat(vm.state.value.showClearConfirmation).isNull()
    }

    @Test
    fun `ConfirmClear for METRICS_HISTORY calls deleteOlderThan on metricsDao`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.METRICS_HISTORY))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        coVerify { metricsDao.deleteOlderThan(Long.MAX_VALUE) }
        assertThat(vm.state.value.metricsCount).isEqualTo(0)
    }

    @Test
    fun `ConfirmClear for ALERT_RULES fetches and deletes each rule`() = runTest {
        val rules = listOf(
            AlertRuleEntity(id = 10, serverId = 1, name = "r1", conditionType = "cpu", conditionValue = "80"),
            AlertRuleEntity(id = 20, serverId = 1, name = "r2", conditionType = "mem", conditionValue = "90")
        )
        coEvery { alertDao.getRules(1) } returns rules

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.ALERT_RULES))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        coVerify { alertDao.deleteRule(10) }
        coVerify { alertDao.deleteRule(20) }
        assertThat(vm.state.value.alertRulesCount).isEqualTo(0)
    }

    @Test
    fun `ConfirmClear for SERVICE_CACHE calls deleteByServer on serviceDao`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.SERVICE_CACHE))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        coVerify { serviceDao.deleteByServer(1) }
        assertThat(vm.state.value.serviceCacheCount).isEqualTo(0)
    }

    @Test
    fun `ConfirmClear for ALL_DATA clears everything`() = runTest {
        coEvery { alertDao.getRules(1) } returns listOf(
            AlertRuleEntity(id = 5, serverId = 1, name = "r", conditionType = "cpu", conditionValue = "80")
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.ALL_DATA))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        coVerify { terminalHistoryDao.deleteAll() }
        coVerify { metricsDao.deleteOlderThan(Long.MAX_VALUE) }
        coVerify { alertDao.deleteRule(5) }
        coVerify { serviceDao.deleteByServer(1) }

        assertThat(vm.state.value.terminalHistoryCount).isEqualTo(0)
        assertThat(vm.state.value.metricsCount).isEqualTo(0)
        assertThat(vm.state.value.alertRulesCount).isEqualTo(0)
        assertThat(vm.state.value.serviceCacheCount).isEqualTo(0)
    }

    @Test
    fun `ConfirmClear without prior RequestClear does nothing`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        coVerify(exactly = 0) { terminalHistoryDao.deleteAll() }
        coVerify(exactly = 0) { serviceDao.deleteByServer(any()) }
    }

    @Test
    fun `ConfirmClear handles exception and sets error in clearSuccess`() = runTest {
        coEvery { terminalHistoryDao.deleteAll() } throws RuntimeException("disk full")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.TERMINAL_HISTORY))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        assertThat(vm.state.value.clearSuccess).contains("Could not clear data")
        assertThat(vm.state.value.clearSuccess).contains("disk full")
    }

    // ---------------------------------------------------------------
    // 9. RunCheckup populates checkup items and issues
    // ---------------------------------------------------------------

    @Test
    fun `RunCheckup populates checkup items when encryption disabled and no biometrics`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RunCheckup)
        advanceUntilIdle()

        assertThat(vm.state.value.checkupComplete).isTrue()
        assertThat(vm.state.value.checkupItems).hasSize(3)

        // Database Encryption should fail
        val encItem = vm.state.value.checkupItems.first { it.label == "Database Encryption" }
        assertThat(encItem.passed).isFalse()

        // Biometric should fail (no hardware)
        val bioItem = vm.state.value.checkupItems.first { it.label == "Biometric Authentication" }
        assertThat(bioItem.passed).isFalse()

        // Device Lock should fail
        val lockItem = vm.state.value.checkupItems.first { it.label == "Device Lock" }
        assertThat(lockItem.passed).isFalse()
    }

    @Test
    fun `RunCheckup with encryption enabled shows passing encryption item`() = runTest {
        every { encryptionManager.isEncryptionEnabled } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        val encItem = vm.state.value.checkupItems.first { it.label == "Database Encryption" }
        assertThat(encItem.passed).isTrue()
    }

    @Test
    fun `RunCheckup creates issue when encryption is disabled`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val encIssue = vm.state.value.issues.find { it.title == "Database is not encrypted" }
        assertThat(encIssue).isNotNull()
        assertThat(encIssue!!.fixLabel).isEqualTo("Enable Encryption")
    }

    @Test
    fun `RunCheckup creates issue when no device screen lock`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val lockIssue = vm.state.value.issues.find { it.title == "No device screen lock set" }
        assertThat(lockIssue).isNotNull()
    }

    @Test
    fun `RunCheckup with biometric available and device secure shows passing items`() = runTest {
        every { mockBiometricManager.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_SUCCESS

        val vm = createViewModel()
        advanceUntilIdle()

        val bioItem = vm.state.value.checkupItems.first { it.label == "Biometric Authentication" }
        assertThat(bioItem.passed).isTrue()

        val lockItem = vm.state.value.checkupItems.first { it.label == "Device Lock" }
        assertThat(lockItem.passed).isTrue()

        // No device lock issue
        val lockIssue = vm.state.value.issues.find { it.title == "No device screen lock set" }
        assertThat(lockIssue).isNull()
    }

    @Test
    fun `RunCheckup creates biometric protection issue when enc enabled but bio not`() = runTest {
        every { encryptionManager.isEncryptionEnabled } returns true
        every { encryptionManager.isBiometricEnabled } returns false
        every { mockBiometricManager.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_SUCCESS

        val vm = createViewModel()
        advanceUntilIdle()

        val bioIssue = vm.state.value.issues.find { it.title == "Biometric protection available" }
        assertThat(bioIssue).isNotNull()
        assertThat(bioIssue!!.fixLabel).isEqualTo("Enable Biometrics")
    }

    @Test
    fun `RunCheckup does not create biometric issue when already enabled`() = runTest {
        every { encryptionManager.isEncryptionEnabled } returns true
        every { encryptionManager.isBiometricEnabled } returns true
        every { mockBiometricManager.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_SUCCESS

        val vm = createViewModel()
        advanceUntilIdle()

        val bioIssue = vm.state.value.issues.find { it.title == "Biometric protection available" }
        assertThat(bioIssue).isNull()
    }

    // ---------------------------------------------------------------
    // 10. FixAll auto-enables encryption
    // ---------------------------------------------------------------

    @Test
    fun `FixAll enables encryption when not enabled and no biometric`() = runTest {
        every { encryptionManager.enableEncryption(false) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.FixAll)
        advanceUntilIdle()

        verify { encryptionManager.enableEncryption(false) }
        assertThat(vm.state.value.isEncryptionEnabled).isTrue()
        assertThat(vm.state.value.isBiometricEnabled).isFalse()
        assertThat(vm.state.value.encryptionJustEnabled).isTrue()
    }

    @Test
    fun `FixAll enables encryption with biometric when biometric available`() = runTest {
        every { mockBiometricManager.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_SUCCESS
        every { encryptionManager.enableEncryption(true) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.FixAll)
        advanceUntilIdle()

        verify { encryptionManager.enableEncryption(true) }
        assertThat(vm.state.value.isEncryptionEnabled).isTrue()
        assertThat(vm.state.value.isBiometricEnabled).isTrue()
    }

    @Test
    fun `FixAll does nothing when encryption already enabled`() = runTest {
        every { encryptionManager.isEncryptionEnabled } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.FixAll)
        advanceUntilIdle()

        verify(exactly = 0) { encryptionManager.enableEncryption(any()) }
    }

    @Test
    fun `FixAll handles encryption failure`() = runTest {
        every { encryptionManager.enableEncryption(any()) } returns Result.failure(
            RuntimeException("key error")
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.FixAll)
        advanceUntilIdle()

        assertThat(vm.state.value.encryptionError).isEqualTo("key error")
    }

    // ---------------------------------------------------------------
    // 11. DismissSuccess clears success message
    // ---------------------------------------------------------------

    @Test
    fun `DismissSuccess clears clearSuccess message`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        // Trigger a clear to get a success message
        vm.onEvent(SecurityEvent.RequestClear(DataCategory.TERMINAL_HISTORY))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        assertThat(vm.state.value.clearSuccess).isNotNull()

        vm.onEvent(SecurityEvent.DismissSuccess)
        assertThat(vm.state.value.clearSuccess).isNull()
    }

    // ---------------------------------------------------------------
    // 12. Database info is loaded
    // ---------------------------------------------------------------

    @Test
    fun `database info is loaded from context`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.databaseSizeBytes).isEqualTo(4096L)
        assertThat(vm.state.value.databaseName).isEqualTo("serverdash.db")
    }

    @Test
    fun `database size is 0 when file does not exist`() = runTest {
        every { mockDbFile.exists() } returns false

        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.databaseSizeBytes).isEqualTo(0L)
    }

    @Test
    fun `database info refreshes on RefreshData`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.databaseSizeBytes).isEqualTo(4096L)

        every { mockDbFile.length() } returns 8192L
        vm.onEvent(SecurityEvent.RefreshData)
        advanceUntilIdle()

        assertThat(vm.state.value.databaseSizeBytes).isEqualTo(8192L)
    }

    // ---------------------------------------------------------------
    // 13. Clear success message is set after clearing
    // ---------------------------------------------------------------

    @Test
    fun `clearing terminal history sets correct success message`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.TERMINAL_HISTORY))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        assertThat(vm.state.value.clearSuccess).isEqualTo("Terminal history cleared")
    }

    @Test
    fun `clearing metrics history sets correct success message`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.METRICS_HISTORY))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        assertThat(vm.state.value.clearSuccess).isEqualTo("Metrics history cleared")
    }

    @Test
    fun `clearing alert rules sets correct success message`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.ALERT_RULES))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        assertThat(vm.state.value.clearSuccess).isEqualTo("Alert rules cleared")
    }

    @Test
    fun `clearing service cache sets correct success message`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.SERVICE_CACHE))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        assertThat(vm.state.value.clearSuccess).isEqualTo("Service cache cleared")
    }

    @Test
    fun `clearing all data sets correct success message`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(SecurityEvent.RequestClear(DataCategory.ALL_DATA))
        vm.onEvent(SecurityEvent.ConfirmClear)
        advanceUntilIdle()

        assertThat(vm.state.value.clearSuccess).isEqualTo("All data cleared")
    }
}
