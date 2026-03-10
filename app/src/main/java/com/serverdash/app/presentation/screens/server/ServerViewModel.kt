package com.serverdash.app.presentation.screens.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.core.cache.ScreenCacheManager
import com.serverdash.app.domain.model.CommandResult
import com.serverdash.app.domain.repository.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

data class ServerUiState(
    val selectedTab: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null,

    // Confirmation flow
    val pendingConfirmation: ServerAction? = null,
    val pendingBiometricAction: ServerAction? = null,

    // Packages
    val installedPackages: List<AptPackage> = emptyList(),
    val searchResults: List<AptPackage> = emptyList(),
    val upgradeablePackages: List<AptPackage> = emptyList(),
    val packageSearchQuery: String = "",
    val isSearchingPackages: Boolean = false,
    val aptOperationRunning: Boolean = false,
    val aptOutputLines: List<String> = emptyList(),
    val aptOutputExpanded: Boolean = false,
    val packagesLoaded: Boolean = false,

    // Firewall
    val ufwState: UfwState = UfwState(),
    val ufwRules: List<UfwRule> = emptyList(),
    val iptablesChains: List<IptablesChain> = emptyList(),
    val showIptables: Boolean = false,
    val firewallLoaded: Boolean = false,
    val newRulePort: String = "",
    val newRuleProtocol: String = "tcp",
    val newRuleAction: String = "allow",
    val newRuleFrom: String = "any",

    // System
    val systemInfo: SystemInfo = SystemInfo(),
    val systemLoaded: Boolean = false,
    val editingHostname: String? = null,
    val editingTimezone: String? = null,

    // Users
    val users: List<ServerUser> = emptyList(),
    val usersLoaded: Boolean = false,
    val newUsername: String = "",
    val expandedUser: String? = null,

    // Cron
    val cronJobs: List<CronJob> = emptyList(),
    val cronLoaded: Boolean = false,
    val newCronSchedule: String = "",
    val newCronCommand: String = "",

    // Services
    val services: List<SystemctlService> = emptyList(),
    val servicesLoaded: Boolean = false,
    val serviceSearchQuery: String = "",
    val serviceLogs: List<String> = emptyList(),
    val serviceLogsUnit: String? = null,
    val isLoadingServiceLogs: Boolean = false,
) {
    val filteredServices: List<SystemctlService>
        get() = if (serviceSearchQuery.isBlank()) services
        else services.filter {
            it.unit.contains(serviceSearchQuery, ignoreCase = true) ||
                it.description.contains(serviceSearchQuery, ignoreCase = true)
        }
}

sealed interface ServerEvent {
    data class SelectTab(val index: Int) : ServerEvent
    data object Refresh : ServerEvent
    data object DismissError : ServerEvent
    data object DismissSnackbar : ServerEvent

    // Confirmation flow
    data class RequestAction(val action: ServerAction) : ServerEvent
    data object ConfirmAction : ServerEvent
    data object DismissConfirmation : ServerEvent
    data object BiometricSucceeded : ServerEvent
    data object BiometricCancelled : ServerEvent

    // Packages
    data class UpdatePackageSearch(val query: String) : ServerEvent
    data object SearchPackages : ServerEvent
    data object ClearSearch : ServerEvent

    // Firewall
    data object ToggleIptablesView : ServerEvent
    data class UpdateNewRulePort(val port: String) : ServerEvent
    data class UpdateNewRuleProtocol(val protocol: String) : ServerEvent
    data class UpdateNewRuleAction(val action: String) : ServerEvent
    data class UpdateNewRuleFrom(val from: String) : ServerEvent

    // System
    data class StartEditHostname(val current: String) : ServerEvent
    data class UpdateEditingHostname(val hostname: String) : ServerEvent
    data object CancelEditHostname : ServerEvent
    data class StartEditTimezone(val current: String) : ServerEvent
    data class UpdateEditingTimezone(val timezone: String) : ServerEvent
    data object CancelEditTimezone : ServerEvent

    // Users
    data class UpdateNewUsername(val username: String) : ServerEvent
    data class ToggleExpandUser(val username: String) : ServerEvent

    // Cron
    data class UpdateNewCronSchedule(val schedule: String) : ServerEvent
    data class UpdateNewCronCommand(val command: String) : ServerEvent

    // Services
    data class UpdateServiceSearch(val query: String) : ServerEvent
    data class ViewServiceLogs(val unit: String) : ServerEvent
    data object DismissServiceLogs : ServerEvent

    // Apt streaming
    data object ToggleAptOutput : ServerEvent
}

private val WHITESPACE = "\\s+".toRegex()

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val sshRepository: SshRepository,
    private val cacheManager: ScreenCacheManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerUiState())
    val state: StateFlow<ServerUiState> = _state.asStateFlow()

    private var aptStreamJob: Job? = null

    init {
        loadTabData(0)
    }

    fun onEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.SelectTab -> {
                _state.update { it.copy(selectedTab = event.index) }
                loadTabData(event.index)
            }
            is ServerEvent.Refresh -> loadTabData(_state.value.selectedTab, force = true)
            is ServerEvent.DismissError -> _state.update { it.copy(error = null) }
            is ServerEvent.DismissSnackbar -> _state.update { it.copy(snackbarMessage = null) }

            // Confirmation flow
            is ServerEvent.RequestAction -> handleActionRequest(event.action)
            is ServerEvent.ConfirmAction -> {
                val action = _state.value.pendingConfirmation
                _state.update { it.copy(pendingConfirmation = null) }
                if (action != null) {
                    if (action.requiresBiometric) {
                        _state.update { it.copy(pendingBiometricAction = action) }
                    } else {
                        executeAction(action)
                    }
                }
            }
            is ServerEvent.DismissConfirmation -> _state.update { it.copy(pendingConfirmation = null) }
            is ServerEvent.BiometricSucceeded -> {
                val action = _state.value.pendingBiometricAction
                _state.update { it.copy(pendingBiometricAction = null) }
                if (action != null) executeAction(action)
            }
            is ServerEvent.BiometricCancelled -> _state.update { it.copy(pendingBiometricAction = null) }

            // Packages
            is ServerEvent.UpdatePackageSearch -> _state.update { it.copy(packageSearchQuery = event.query) }
            is ServerEvent.SearchPackages -> searchPackages()
            is ServerEvent.ClearSearch -> _state.update { it.copy(searchResults = emptyList(), packageSearchQuery = "") }

            // Firewall
            is ServerEvent.ToggleIptablesView -> {
                val show = !_state.value.showIptables
                _state.update { it.copy(showIptables = show) }
                if (show && _state.value.iptablesChains.isEmpty()) loadIptables()
            }
            is ServerEvent.UpdateNewRulePort -> _state.update { it.copy(newRulePort = event.port) }
            is ServerEvent.UpdateNewRuleProtocol -> _state.update { it.copy(newRuleProtocol = event.protocol) }
            is ServerEvent.UpdateNewRuleAction -> _state.update { it.copy(newRuleAction = event.action) }
            is ServerEvent.UpdateNewRuleFrom -> _state.update { it.copy(newRuleFrom = event.from) }

            // System
            is ServerEvent.StartEditHostname -> _state.update { it.copy(editingHostname = event.current) }
            is ServerEvent.UpdateEditingHostname -> _state.update { it.copy(editingHostname = event.hostname) }
            is ServerEvent.CancelEditHostname -> _state.update { it.copy(editingHostname = null) }
            is ServerEvent.StartEditTimezone -> _state.update { it.copy(editingTimezone = event.current) }
            is ServerEvent.UpdateEditingTimezone -> _state.update { it.copy(editingTimezone = event.timezone) }
            is ServerEvent.CancelEditTimezone -> _state.update { it.copy(editingTimezone = null) }

            // Users
            is ServerEvent.UpdateNewUsername -> _state.update { it.copy(newUsername = event.username) }
            is ServerEvent.ToggleExpandUser -> {
                val current = _state.value.expandedUser
                _state.update { it.copy(expandedUser = if (current == event.username) null else event.username) }
            }

            // Cron
            is ServerEvent.UpdateNewCronSchedule -> _state.update { it.copy(newCronSchedule = event.schedule) }
            is ServerEvent.UpdateNewCronCommand -> _state.update { it.copy(newCronCommand = event.command) }

            // Services
            is ServerEvent.UpdateServiceSearch -> _state.update { it.copy(serviceSearchQuery = event.query) }
            is ServerEvent.ViewServiceLogs -> loadServiceLogs(event.unit)
            is ServerEvent.DismissServiceLogs -> _state.update {
                it.copy(serviceLogs = emptyList(), serviceLogsUnit = null)
            }

            is ServerEvent.ToggleAptOutput -> _state.update { it.copy(aptOutputExpanded = !it.aptOutputExpanded) }
        }
    }

    private fun loadTabData(tabIndex: Int, force: Boolean = false) {
        val tab = ServerTab.entries[tabIndex]
        when (tab) {
            ServerTab.PACKAGES -> if (force || !_state.value.packagesLoaded) loadPackages()
            ServerTab.FIREWALL -> if (force || !_state.value.firewallLoaded) loadFirewall()
            ServerTab.SYSTEM -> if (force || !_state.value.systemLoaded) loadSystem()
            ServerTab.USERS -> if (force || !_state.value.usersLoaded) loadUsers()
            ServerTab.CRON -> if (force || !_state.value.cronLoaded) loadCron()
            ServerTab.SERVICES -> if (force || !_state.value.servicesLoaded) loadServices()
        }
    }

    private fun handleActionRequest(action: ServerAction) {
        // All actions go through confirmation dialog first
        _state.update { it.copy(pendingConfirmation = action) }
    }

    private fun executeAction(action: ServerAction) {
        viewModelScope.launch {
            when (action) {
                is ServerAction.InstallPackage -> runAptCommand("apt-get install -y ${shellQuote(action.packageName)}")
                is ServerAction.RemovePackage -> runAptCommand("apt-get remove -y ${shellQuote(action.packageName)}")
                is ServerAction.AptUpdate -> runAptCommand("apt-get update")
                is ServerAction.AptUpgrade -> runAptCommand("DEBIAN_FRONTEND=noninteractive apt-get upgrade -y")
                is ServerAction.AddUfwRule -> executeUfwAction(action)
                is ServerAction.DeleteUfwRule -> executeUfwAction(action)
                is ServerAction.EnableFirewall -> executeUfwAction(action)
                is ServerAction.DisableFirewall -> executeUfwAction(action)
                is ServerAction.ResetFirewall -> executeUfwAction(action)
                is ServerAction.SetDefaultPolicy -> executeUfwAction(action)
                is ServerAction.SetHostname -> executeSystemAction(action)
                is ServerAction.SetTimezone -> executeSystemAction(action)
                is ServerAction.AddUser -> executeUserAction(action)
                is ServerAction.DeleteUser -> executeUserAction(action)
                is ServerAction.ModifyUserGroups -> executeUserAction(action)
                is ServerAction.ToggleSudo -> executeUserAction(action)
                is ServerAction.AddCronJob -> executeCronAction(action)
                is ServerAction.DeleteCronJob -> executeCronAction(action)
                is ServerAction.ServiceControl -> executeServiceAction(action)
                is ServerAction.ServiceEnable -> executeServiceAction(action)
            }
        }
    }

    // region Packages
    private fun loadPackages() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val cached = cacheManager.get<List<AptPackage>>(CACHE_INSTALLED_PKGS)
                if (cached != null) {
                    _state.update { it.copy(installedPackages = cached, packagesLoaded = true, isLoading = false) }
                    loadUpgradeablePackages()
                    return@launch
                }

                val result = sshRepository.executeCommand(
                    "dpkg --get-selections | grep -v deinstall | head -500",
                ).getOrThrow()

                val packages = result.output.lines()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        val parts = line.split(WHITESPACE, limit = 2)
                        AptPackage(
                            name = parts[0].substringBefore(":"),
                            isInstalled = true,
                        )
                    }

                cacheManager.put(CACHE_INSTALLED_PKGS, packages)
                _state.update { it.copy(installedPackages = packages, packagesLoaded = true, isLoading = false) }
                loadUpgradeablePackages()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load packages: ${e.message}") }
            }
        }
    }

    private fun loadUpgradeablePackages() {
        viewModelScope.launch {
            try {
                val result = sshRepository.executeCommand("apt list --upgradeable 2>/dev/null").getOrThrow()
                val upgradeable = result.output.lines()
                    .filter { it.contains("/") && it.contains("upgradable") }
                    .mapNotNull { line ->
                        // Format: package/source version arch [upgradable from: old-version]
                        val name = line.substringBefore("/")
                        val newVer = line.substringAfter(" ").substringBefore(" ")
                        val oldVer = line.substringAfter("from: ", "").substringBefore("]")
                        if (name.isNotBlank()) AptPackage(
                            name = name,
                            version = oldVer,
                            upgradeVersion = newVer,
                            isInstalled = true,
                        ) else null
                    }
                _state.update { it.copy(upgradeablePackages = upgradeable) }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    private fun searchPackages() {
        val query = _state.value.packageSearchQuery.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSearchingPackages = true) }
            try {
                val result = sshRepository.executeCommand(
                    "apt-cache search '$query' 2>/dev/null | head -50",
                ).getOrThrow()

                val installed = _state.value.installedPackages.map { it.name }.toSet()
                val results = result.output.lines()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        val parts = line.split(" - ", limit = 2)
                        AptPackage(
                            name = parts[0].trim(),
                            description = if (parts.size > 1) parts[1].trim() else "",
                            isInstalled = parts[0].trim() in installed,
                        )
                    }

                _state.update { it.copy(searchResults = results, isSearchingPackages = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isSearchingPackages = false, error = "Search failed: ${e.message}") }
            }
        }
    }

    private fun runAptCommand(command: String) {
        aptStreamJob?.cancel()
        _state.update { it.copy(aptOperationRunning = true, aptOutputLines = emptyList(), aptOutputExpanded = false) }
        aptStreamJob = viewModelScope.launch {
            try {
                val result = sshRepository.executeSudoCommand(command)
                result.fold(
                    onSuccess = { cmdResult ->
                        val lines = cmdResult.output.lines().filter { it.isNotBlank() }
                        _state.update {
                            it.copy(
                                aptOperationRunning = false,
                                aptOutputLines = lines,
                                snackbarMessage = if (cmdResult.exitCode == 0) "Operation completed" else "Operation failed (exit ${cmdResult.exitCode})",
                            )
                        }
                        // Refresh package lists
                        cacheManager.invalidatePrefix(CACHE_PREFIX)
                        _state.update { it.copy(packagesLoaded = false) }
                        loadPackages()
                    },
                    onFailure = { e ->
                        _state.update {
                            it.copy(aptOperationRunning = false, error = "apt failed: ${e.message}")
                        }
                    },
                )
            } catch (e: Exception) {
                _state.update { it.copy(aptOperationRunning = false, error = "apt failed: ${e.message}") }
            }
        }
    }
    // endregion

    // region Firewall
    private fun loadFirewall() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val result = sshRepository.executeSudoCommand("ufw status verbose 2>/dev/null")
                result.fold(
                    onSuccess = { cmdResult ->
                        val lines = cmdResult.output.lines()
                        val state = parseUfwStatus(lines)
                        val rules = parseUfwRules(lines)
                        _state.update {
                            it.copy(
                                ufwState = state,
                                ufwRules = rules,
                                firewallLoaded = true,
                                isLoading = false,
                            )
                        }
                    },
                    onFailure = { e ->
                        _state.update { it.copy(isLoading = false, error = "Failed to load firewall: ${e.message}") }
                    },
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load firewall: ${e.message}") }
            }
        }
    }

    private fun parseUfwStatus(lines: List<String>): UfwState {
        var status = "inactive"
        var defaultIn = "deny"
        var defaultOut = "allow"
        var logging = "off"
        for (line in lines) {
            val trimmed = line.trim().lowercase()
            when {
                trimmed.startsWith("status:") -> status = trimmed.substringAfter("status:").trim()
                trimmed.startsWith("default:") -> {
                    val defaults = trimmed.substringAfter("default:").trim()
                    // "deny (incoming), allow (outgoing), disabled (routed)"
                    val parts = defaults.split(",").map { it.trim() }
                    for (part in parts) {
                        when {
                            part.contains("incoming") -> defaultIn = part.substringBefore("(").trim()
                            part.contains("outgoing") -> defaultOut = part.substringBefore("(").trim()
                        }
                    }
                }
                trimmed.startsWith("logging:") -> logging = trimmed.substringAfter("logging:").trim()
            }
        }
        return UfwState(status = status, defaultIncoming = defaultIn, defaultOutgoing = defaultOut, logging = logging)
    }

    private fun parseUfwRules(lines: List<String>): List<UfwRule> {
        val rules = mutableListOf<UfwRule>()
        // Find lines after "---" separator
        val ruleStart = lines.indexOfFirst { it.trim().startsWith("---") }
        if (ruleStart < 0) return rules

        var ruleNum = 1
        for (i in (ruleStart + 1) until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            // Format: "To                         Action      From"
            // "22/tcp                     ALLOW IN    Anywhere"
            val parts = line.split(WHITESPACE, limit = 4)
            if (parts.size >= 3) {
                val to = parts[0]
                val action = if (parts.size >= 4) "${parts[1]} ${parts[2]}" else parts[1]
                val from = if (parts.size >= 4) parts[3] else if (parts.size >= 3) parts[2] else "Anywhere"
                rules.add(UfwRule(number = ruleNum++, to = to, action = action, from = from))
            }
        }
        return rules
    }

    private fun loadIptables() {
        viewModelScope.launch {
            try {
                val result = sshRepository.executeSudoCommand("iptables -L -n -v --line-numbers 2>/dev/null")
                result.onSuccess { cmdResult ->
                    val chains = parseIptablesOutput(cmdResult.output)
                    _state.update { it.copy(iptablesChains = chains) }
                }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    private fun parseIptablesOutput(output: String): List<IptablesChain> {
        val chains = mutableListOf<IptablesChain>()
        var currentChain: String? = null
        var currentPolicy = ""
        var currentRules = mutableListOf<IptablesRule>()

        for (line in output.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Chain ") -> {
                    if (currentChain != null) {
                        chains.add(IptablesChain(currentChain, currentPolicy, currentRules.toList()))
                    }
                    // "Chain INPUT (policy ACCEPT 0 packets, 0 bytes)"
                    currentChain = trimmed.substringAfter("Chain ").substringBefore(" ")
                    currentPolicy = trimmed.substringAfter("policy ", "").substringBefore(")")
                        .substringBefore(" ")
                    currentRules = mutableListOf()
                }
                trimmed.startsWith("num") || trimmed.startsWith("pkts") || trimmed.isBlank() -> {
                    // skip headers
                }
                currentChain != null && trimmed.isNotEmpty() -> {
                    val parts = trimmed.split(WHITESPACE, limit = 10)
                    if (parts.size >= 8) {
                        currentRules.add(
                            IptablesRule(
                                num = parts[0].toIntOrNull() ?: 0,
                                target = parts[3],
                                protocol = parts[4],
                                source = parts[5],
                                destination = parts[6],
                                extra = if (parts.size > 7) parts.drop(7).joinToString(" ") else "",
                            ),
                        )
                    }
                }
            }
        }
        if (currentChain != null) {
            chains.add(IptablesChain(currentChain, currentPolicy, currentRules.toList()))
        }
        return chains
    }

    private suspend fun executeUfwAction(action: ServerAction) {
        _state.update { it.copy(isLoading = true) }
        val command = when (action) {
            is ServerAction.AddUfwRule -> {
                val s = _state.value
                val proto = if (s.newRuleProtocol.isNotBlank()) "/${s.newRuleProtocol}" else ""
                val from = if (s.newRuleFrom.isNotBlank() && s.newRuleFrom != "any") "from ${s.newRuleFrom} " else ""
                "ufw ${s.newRuleAction} ${from}to any port ${s.newRulePort}$proto"
            }
            is ServerAction.DeleteUfwRule -> "ufw --force delete ${action.ruleNumber}"
            is ServerAction.EnableFirewall -> "ufw --force enable"
            is ServerAction.DisableFirewall -> "ufw --force disable"
            is ServerAction.ResetFirewall -> "ufw --force reset"
            is ServerAction.SetDefaultPolicy -> "ufw default ${action.policy} ${action.direction}"
            else -> return
        }

        try {
            val result = sshRepository.executeSudoCommand(command).getOrThrow()
            _state.update {
                it.copy(
                    isLoading = false,
                    snackbarMessage = if (result.exitCode == 0) "Firewall updated" else "Failed: ${result.error}",
                    newRulePort = "",
                    newRuleFrom = "any",
                )
            }
            _state.update { it.copy(firewallLoaded = false) }
            loadFirewall()
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = "Firewall command failed: ${e.message}") }
        }
    }
    // endregion

    // region System
    private fun loadSystem() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val cached = cacheManager.get<SystemInfo>(CACHE_SYSTEM_INFO)
                if (cached != null) {
                    _state.update { it.copy(systemInfo = cached, systemLoaded = true, isLoading = false) }
                    return@launch
                }

                // Run all commands at once
                val cmd = listOf(
                    "cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | head -1",
                    "uname -r",
                    "uname -m",
                    "hostname",
                    "timedatectl show --property=Timezone --value 2>/dev/null || cat /etc/timezone 2>/dev/null",
                    "locale 2>/dev/null | head -1",
                    "uptime -p 2>/dev/null || uptime",
                ).joinToString(" && echo '---SEPARATOR---' && ")

                val result = sshRepository.executeCommand(cmd).getOrThrow()
                val sections = result.output.split("---SEPARATOR---").map { it.trim() }

                val info = SystemInfo(
                    os = sections.getOrElse(0) { "" }.substringAfter("=").removeSurrounding("\""),
                    kernel = sections.getOrElse(1) { "" },
                    arch = sections.getOrElse(2) { "" },
                    hostname = sections.getOrElse(3) { "" },
                    timezone = sections.getOrElse(4) { "" },
                    locale = sections.getOrElse(5) { "" }.substringAfter("=").removeSurrounding("\""),
                    uptime = sections.getOrElse(6) { "" }.removePrefix("up "),
                )

                cacheManager.put(CACHE_SYSTEM_INFO, info)
                _state.update { it.copy(systemInfo = info, systemLoaded = true, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load system info: ${e.message}") }
            }
        }
    }

    private suspend fun executeSystemAction(action: ServerAction) {
        _state.update { it.copy(isLoading = true) }
        val command = when (action) {
            is ServerAction.SetHostname -> "hostnamectl set-hostname '${action.hostname}'"
            is ServerAction.SetTimezone -> "timedatectl set-timezone '${action.timezone}'"
            else -> return
        }
        try {
            val result = sshRepository.executeSudoCommand(command).getOrThrow()
            _state.update {
                it.copy(
                    isLoading = false,
                    snackbarMessage = if (result.exitCode == 0) "System updated" else "Failed: ${result.error}",
                    editingHostname = null,
                    editingTimezone = null,
                )
            }
            cacheManager.invalidate(CACHE_SYSTEM_INFO)
            _state.update { it.copy(systemLoaded = false) }
            loadSystem()
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = "System command failed: ${e.message}") }
        }
    }
    // endregion

    // region Users
    private fun loadUsers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                // Get real users (uid >= 1000 or uid 0)
                val passwdResult = sshRepository.executeCommand(
                    "awk -F: '(\$3 >= 1000 || \$3 == 0) && \$7 !~ /nologin|false/ {print \$1\":\"\$3\":\"\$4\":\"\$6\":\"\$7}' /etc/passwd",
                ).getOrThrow()

                val users = passwdResult.output.lines()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        val parts = line.split(":")
                        ServerUser(
                            username = parts.getOrElse(0) { "" },
                            uid = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0,
                            gid = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0,
                            homeDir = parts.getOrElse(3) { "" },
                            shell = parts.getOrElse(4) { "" },
                        )
                    }

                // Get groups for each user
                val groupsResult = sshRepository.executeCommand(
                    "for u in ${users.joinToString(" ") { it.username }}; do echo \"\$u:\$(groups \$u 2>/dev/null | cut -d: -f2)\"; done",
                ).getOrThrow()

                val groupsMap = groupsResult.output.lines()
                    .filter { it.contains(":") }
                    .associate { line ->
                        val parts = line.split(":", limit = 2)
                        parts[0].trim() to parts.getOrElse(1) { "" }.trim().split(WHITESPACE).filter { it.isNotBlank() }
                    }

                val enrichedUsers = users.map { user ->
                    val groups = groupsMap[user.username] ?: emptyList()
                    user.copy(
                        groups = groups,
                        hasSudo = "sudo" in groups || user.uid == 0,
                    )
                }

                _state.update { it.copy(users = enrichedUsers, usersLoaded = true, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load users: ${e.message}") }
            }
        }
    }

    private suspend fun executeUserAction(action: ServerAction) {
        _state.update { it.copy(isLoading = true) }
        val command = when (action) {
            is ServerAction.AddUser -> "useradd -m -s /bin/bash '${action.username}'"
            is ServerAction.DeleteUser -> "userdel -r '${action.username}'"
            is ServerAction.ModifyUserGroups -> "usermod -G '${action.groups}' '${action.username}'"
            is ServerAction.ToggleSudo -> {
                if (action.grant) "usermod -aG sudo '${action.username}'"
                else "gpasswd -d '${action.username}' sudo"
            }
            else -> return
        }
        try {
            val result = sshRepository.executeSudoCommand(command).getOrThrow()
            _state.update {
                it.copy(
                    isLoading = false,
                    snackbarMessage = if (result.exitCode == 0) "User updated" else "Failed: ${result.error}",
                    newUsername = "",
                )
            }
            _state.update { it.copy(usersLoaded = false) }
            loadUsers()
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = "User command failed: ${e.message}") }
        }
    }
    // endregion

    // region Cron
    private fun loadCron() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val allJobs = mutableListOf<CronJob>()

                // User crontab
                val userResult = sshRepository.executeCommand("crontab -l 2>/dev/null")
                userResult.onSuccess { cmdResult ->
                    cmdResult.output.lines()
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                        .forEach { line ->
                            parseCronLine(line, "user")?.let { allJobs.add(it) }
                        }
                }

                // System crontab
                val sysResult = sshRepository.executeSudoCommand("cat /etc/crontab 2>/dev/null")
                sysResult.onSuccess { cmdResult ->
                    cmdResult.output.lines()
                        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("SHELL=") &&
                            !it.startsWith("PATH=") && !it.startsWith("MAILTO=") }
                        .forEach { line ->
                            parseCronLine(line, "/etc/crontab")?.let { allJobs.add(it) }
                        }
                }

                // /etc/cron.d/*
                val cronDResult = sshRepository.executeSudoCommand(
                    "for f in /etc/cron.d/*; do [ -f \"\$f\" ] && echo \"===\$f===\" && cat \"\$f\" 2>/dev/null; done",
                )
                cronDResult.onSuccess { cmdResult ->
                    var currentFile = ""
                    cmdResult.output.lines().forEach { line ->
                        when {
                            line.startsWith("===") && line.endsWith("===") -> {
                                currentFile = line.removeSurrounding("===")
                            }
                            line.isNotBlank() && !line.startsWith("#") && currentFile.isNotBlank() -> {
                                parseCronLine(line, currentFile)?.let { allJobs.add(it) }
                            }
                        }
                    }
                }

                _state.update { it.copy(cronJobs = allJobs, cronLoaded = true, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load cron: ${e.message}") }
            }
        }
    }

    private fun parseCronLine(line: String, source: String): CronJob? {
        val parts = line.trim().split(WHITESPACE)
        if (parts.size < 6) return null
        val schedule = parts.take(5).joinToString(" ")
        // System crontabs have user field before command
        val command = if (source != "user" && parts.size > 6) {
            parts.drop(6).joinToString(" ")
        } else {
            parts.drop(5).joinToString(" ")
        }
        return CronJob(schedule = schedule, command = command, source = source, rawLine = line.trim())
    }

    private suspend fun executeCronAction(action: ServerAction) {
        _state.update { it.copy(isLoading = true) }
        try {
            when (action) {
                is ServerAction.AddCronJob -> {
                    // Append to user crontab
                    val newLine = "${action.schedule} ${action.command}"
                    val result = sshRepository.executeCommand(
                        "(crontab -l 2>/dev/null; echo '$newLine') | crontab -",
                    ).getOrThrow()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            snackbarMessage = if (result.exitCode == 0) "Cron job added" else "Failed: ${result.error}",
                            newCronSchedule = "",
                            newCronCommand = "",
                        )
                    }
                }
                is ServerAction.DeleteCronJob -> {
                    // Remove line from user crontab
                    val escapedLine = action.rawLine.replace("'", "'\\''")
                    val result = sshRepository.executeCommand(
                        "crontab -l 2>/dev/null | grep -v -F '$escapedLine' | crontab -",
                    ).getOrThrow()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            snackbarMessage = if (result.exitCode == 0) "Cron job removed" else "Failed: ${result.error}",
                        )
                    }
                }
                else -> return
            }
            _state.update { it.copy(cronLoaded = false) }
            loadCron()
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = "Cron command failed: ${e.message}") }
        }
    }
    // endregion

    // region Services
    private fun loadServices() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val result = sshRepository.executeCommand(
                    "systemctl list-units --type=service --all --no-pager --no-legend",
                ).getOrThrow()

                val services = result.output.lines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        val parts = line.trim().split(WHITESPACE, limit = 5)
                        if (parts.size >= 4) {
                            SystemctlService(
                                unit = parts[0].removeSuffix(".service"),
                                load = parts[1],
                                active = parts[2],
                                sub = parts[3],
                                description = if (parts.size >= 5) parts[4] else "",
                            )
                        } else {
                            null
                        }
                    }

                // Check enabled status for services
                val enabledResult = sshRepository.executeCommand(
                    "systemctl list-unit-files --type=service --no-pager --no-legend",
                ).getOrNull()

                val enabledMap = enabledResult?.output?.lines()
                    ?.filter { it.isNotBlank() }
                    ?.associate { line ->
                        val parts = line.trim().split(WHITESPACE, limit = 3)
                        val unit = parts[0].removeSuffix(".service")
                        val status = parts.getOrElse(1) { "" }
                        unit to (status == "enabled")
                    } ?: emptyMap()

                val enrichedServices = services.map { svc ->
                    svc.copy(isEnabled = enabledMap[svc.unit] ?: false)
                }

                _state.update { it.copy(services = enrichedServices, servicesLoaded = true, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load services: ${e.message}") }
            }
        }
    }

    private fun loadServiceLogs(unit: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingServiceLogs = true, serviceLogsUnit = unit) }
            try {
                val result = sshRepository.executeSudoCommand(
                    "journalctl -u $unit --no-pager -n 100 2>/dev/null",
                ).getOrThrow()
                _state.update {
                    it.copy(
                        serviceLogs = result.output.lines().filter { l -> l.isNotBlank() },
                        isLoadingServiceLogs = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingServiceLogs = false, error = "Failed to load logs: ${e.message}") }
            }
        }
    }

    private suspend fun executeServiceAction(action: ServerAction) {
        _state.update { it.copy(isLoading = true) }
        val command = when (action) {
            is ServerAction.ServiceControl -> "systemctl ${action.action} ${action.unit}"
            is ServerAction.ServiceEnable -> {
                if (action.enable) "systemctl enable ${action.unit}" else "systemctl disable ${action.unit}"
            }
            else -> return
        }
        try {
            val result = sshRepository.executeSudoCommand(command).getOrThrow()
            _state.update {
                it.copy(
                    isLoading = false,
                    snackbarMessage = if (result.exitCode == 0) "Service updated" else "Failed: ${result.error}",
                )
            }
            _state.update { it.copy(servicesLoaded = false) }
            loadServices()
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = "Service command failed: ${e.message}") }
        }
    }
    // endregion

    companion object {
        private const val CACHE_PREFIX = "server_"
        private const val CACHE_INSTALLED_PKGS = "${CACHE_PREFIX}installed_packages"
        private const val CACHE_SYSTEM_INFO = "${CACHE_PREFIX}system_info"
    }
}
