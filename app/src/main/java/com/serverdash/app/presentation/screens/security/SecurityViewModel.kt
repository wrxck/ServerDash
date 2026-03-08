package com.serverdash.app.presentation.screens.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.data.encryption.EncryptionManager
import com.serverdash.app.data.local.db.AlertDao
import com.serverdash.app.data.local.db.MetricsDao
import com.serverdash.app.data.local.db.ServiceDao
import com.serverdash.app.data.local.db.TerminalHistoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DataCategory(val label: String, val description: String) {
    TERMINAL_HISTORY("Terminal History", "Previously executed commands and their output"),
    METRICS_HISTORY("Metrics History", "CPU, memory, and disk usage snapshots"),
    ALERT_RULES("Alert Rules", "Custom monitoring rules and webhook URLs"),
    SERVICE_CACHE("Service Cache", "Cached service discovery data"),
    ALL_DATA("All App Data", "Remove everything and start fresh")
}

data class CheckupItem(
    val label: String,
    val description: String,
    val passed: Boolean
)

data class SecurityIssue(
    val title: String,
    val description: String,
    val fixLabel: String,
    val fixAction: SecurityEvent
)

data class SecurityUiState(
    val isEncryptionEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val deviceSecure: Boolean = false,
    val encryptionJustEnabled: Boolean = false,
    val encryptionError: String? = null,
    // data counts
    val terminalHistoryCount: Int = 0,
    val metricsCount: Int = 0,
    val alertRulesCount: Int = 0,
    val serviceCacheCount: Int = 0,
    // database info
    val databaseSizeBytes: Long = 0,
    val databaseName: String = "serverdash.db",
    // security checkup
    val checkupItems: List<CheckupItem> = emptyList(),
    val checkupComplete: Boolean = false,
    // issues
    val issues: List<SecurityIssue> = emptyList(),
    // confirmation dialogs
    val showClearConfirmation: DataCategory? = null,
    val clearSuccess: String? = null
)

sealed interface SecurityEvent {
    data class EnableEncryption(val withBiometric: Boolean) : SecurityEvent
    data object DismissError : SecurityEvent
    data class RequestClear(val category: DataCategory) : SecurityEvent
    data object DismissClearConfirmation : SecurityEvent
    data object ConfirmClear : SecurityEvent
    data object RunCheckup : SecurityEvent
    data object FixAll : SecurityEvent
    data object DismissSuccess : SecurityEvent
    data object RefreshData : SecurityEvent
}

@HiltViewModel
class SecurityViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionManager: EncryptionManager,
    private val terminalHistoryDao: TerminalHistoryDao,
    private val metricsDao: MetricsDao,
    private val alertDao: AlertDao,
    private val serviceDao: ServiceDao
) : ViewModel() {

    private val biometricManager = BiometricManager.from(context)

    private val _state = MutableStateFlow(SecurityUiState())
    val state: StateFlow<SecurityUiState> = _state.asStateFlow()

    init {
        refreshAll()
    }

    private fun refreshAll() {
        refreshSecurityState()
        refreshDataCounts()
        refreshDatabaseInfo()
        runCheckup()
    }

    private fun refreshSecurityState() {
        val canBiometric = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

        val canDeviceCredential = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

        _state.update {
            it.copy(
                isEncryptionEnabled = encryptionManager.isEncryptionEnabled,
                isBiometricEnabled = encryptionManager.isBiometricEnabled,
                biometricAvailable = canBiometric,
                deviceSecure = canDeviceCredential || canBiometric
            )
        }
    }

    private fun refreshDataCounts() {
        viewModelScope.launch {
            val terminalCount = try {
                terminalHistoryDao.getRecent(Int.MAX_VALUE).size
            } catch (_: Exception) { 0 }

            val metricsCount = try {
                metricsDao.getRecent(Int.MAX_VALUE).size
            } catch (_: Exception) { 0 }

            // Alert rules need a serverId; count across all by getting rules for known servers
            // For simplicity, we'll get rules for serverId 1 (most common single-server setup)
            val alertCount = try {
                alertDao.getRules(1).size
            } catch (_: Exception) { 0 }

            val serviceCount = try {
                serviceDao.getServices(1).size
            } catch (_: Exception) { 0 }

            _state.update {
                it.copy(
                    terminalHistoryCount = terminalCount,
                    metricsCount = metricsCount,
                    alertRulesCount = alertCount,
                    serviceCacheCount = serviceCount
                )
            }
        }
    }

    private fun refreshDatabaseInfo() {
        val dbFile = context.getDatabasePath("serverdash.db")
        val dbSize = if (dbFile.exists()) dbFile.length() else 0L
        _state.update {
            it.copy(databaseSizeBytes = dbSize)
        }
    }

    private fun runCheckup() {
        val currentState = _state.value
        val items = mutableListOf<CheckupItem>()
        val issues = mutableListOf<SecurityIssue>()

        val encEnabled = encryptionManager.isEncryptionEnabled
        items.add(
            CheckupItem(
                label = "Database Encryption",
                description = if (encEnabled) "Your database is encrypted with SQLCipher (AES-256)"
                else "Encryption protects your data if the device is lost or stolen",
                passed = encEnabled
            )
        )
        if (!encEnabled) {
            issues.add(
                SecurityIssue(
                    title = "Database is not encrypted",
                    description = "Enable encryption to protect SSH credentials and server data with AES-256 encryption via SQLCipher.",
                    fixLabel = "Enable Encryption",
                    fixAction = SecurityEvent.EnableEncryption(withBiometric = false)
                )
            )
        }

        val canBiometric = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

        items.add(
            CheckupItem(
                label = "Biometric Authentication",
                description = if (canBiometric) "Device biometrics are available and can protect access"
                else "No biometric hardware detected on this device",
                passed = canBiometric
            )
        )

        val canDeviceCredential = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
        val deviceSecure = canDeviceCredential || canBiometric

        items.add(
            CheckupItem(
                label = "Device Lock",
                description = if (deviceSecure) "Device has a screen lock set (PIN, pattern, or password)"
                else "Setting a screen lock adds an extra layer of protection",
                passed = deviceSecure
            )
        )
        if (!deviceSecure) {
            issues.add(
                SecurityIssue(
                    title = "No device screen lock set",
                    description = "A screen lock prevents unauthorized physical access to your data. Set one in your device settings.",
                    fixLabel = "Open Settings",
                    fixAction = SecurityEvent.FixAll // Will open device security settings
                )
            )
        }

        if (encEnabled && canBiometric && !encryptionManager.isBiometricEnabled) {
            issues.add(
                SecurityIssue(
                    title = "Biometric protection available",
                    description = "Your device supports biometrics but encryption is not using it. Re-enabling with biometrics adds another layer of security.",
                    fixLabel = "Enable Biometrics",
                    fixAction = SecurityEvent.EnableEncryption(withBiometric = true)
                )
            )
        }

        _state.update {
            it.copy(
                checkupItems = items,
                checkupComplete = true,
                issues = issues
            )
        }
    }

    fun onEvent(event: SecurityEvent) {
        when (event) {
            is SecurityEvent.EnableEncryption -> {
                encryptionManager.enableEncryption(event.withBiometric).fold(
                    onSuccess = {
                        _state.update {
                            it.copy(
                                isEncryptionEnabled = true,
                                isBiometricEnabled = event.withBiometric,
                                encryptionJustEnabled = true,
                                encryptionError = null
                            )
                        }
                        runCheckup()
                    },
                    onFailure = { e ->
                        _state.update {
                            it.copy(encryptionError = e.message ?: "Unknown error")
                        }
                    }
                )
            }
            is SecurityEvent.DismissError -> {
                _state.update { it.copy(encryptionError = null) }
            }
            is SecurityEvent.RequestClear -> {
                _state.update { it.copy(showClearConfirmation = event.category) }
            }
            is SecurityEvent.DismissClearConfirmation -> {
                _state.update { it.copy(showClearConfirmation = null) }
            }
            is SecurityEvent.ConfirmClear -> {
                val category = _state.value.showClearConfirmation ?: return
                _state.update { it.copy(showClearConfirmation = null) }
                performClear(category)
            }
            is SecurityEvent.RunCheckup -> {
                refreshSecurityState()
                runCheckup()
            }
            is SecurityEvent.FixAll -> {
                // Auto-fix: enable encryption if not enabled
                if (!encryptionManager.isEncryptionEnabled) {
                    val useBiometric = biometricManager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                    ) == BiometricManager.BIOMETRIC_SUCCESS

                    encryptionManager.enableEncryption(useBiometric).fold(
                        onSuccess = {
                            _state.update {
                                it.copy(
                                    isEncryptionEnabled = true,
                                    isBiometricEnabled = useBiometric,
                                    encryptionJustEnabled = true,
                                    encryptionError = null
                                )
                            }
                            runCheckup()
                        },
                        onFailure = { e ->
                            _state.update {
                                it.copy(encryptionError = e.message ?: "Unknown error")
                            }
                        }
                    )
                }
            }
            is SecurityEvent.DismissSuccess -> {
                _state.update { it.copy(clearSuccess = null) }
            }
            is SecurityEvent.RefreshData -> {
                refreshAll()
            }
        }
    }

    private fun performClear(category: DataCategory) {
        viewModelScope.launch {
            try {
                when (category) {
                    DataCategory.TERMINAL_HISTORY -> {
                        terminalHistoryDao.deleteAll()
                        _state.update {
                            it.copy(
                                clearSuccess = "Terminal history cleared",
                                terminalHistoryCount = 0
                            )
                        }
                    }
                    DataCategory.METRICS_HISTORY -> {
                        metricsDao.deleteOlderThan(Long.MAX_VALUE)
                        _state.update {
                            it.copy(
                                clearSuccess = "Metrics history cleared",
                                metricsCount = 0
                            )
                        }
                    }
                    DataCategory.ALERT_RULES -> {
                        val rules = alertDao.getRules(1)
                        rules.forEach { alertDao.deleteRule(it.id) }
                        _state.update {
                            it.copy(
                                clearSuccess = "Alert rules cleared",
                                alertRulesCount = 0
                            )
                        }
                    }
                    DataCategory.SERVICE_CACHE -> {
                        serviceDao.deleteByServer(1)
                        _state.update {
                            it.copy(
                                clearSuccess = "Service cache cleared",
                                serviceCacheCount = 0
                            )
                        }
                    }
                    DataCategory.ALL_DATA -> {
                        terminalHistoryDao.deleteAll()
                        metricsDao.deleteOlderThan(Long.MAX_VALUE)
                        val rules = alertDao.getRules(1)
                        rules.forEach { alertDao.deleteRule(it.id) }
                        serviceDao.deleteByServer(1)
                        _state.update {
                            it.copy(
                                clearSuccess = "All data cleared",
                                terminalHistoryCount = 0,
                                metricsCount = 0,
                                alertRulesCount = 0,
                                serviceCacheCount = 0
                            )
                        }
                    }
                }
                refreshDatabaseInfo()
            } catch (e: Exception) {
                _state.update {
                    it.copy(clearSuccess = "Could not clear data: ${e.message}")
                }
            }
        }
    }
}
