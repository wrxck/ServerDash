package com.serverdash.app.presentation.screens.server

enum class ServerTab(val label: String) {
    PACKAGES("Packages"),
    FIREWALL("Firewall"),
    SYSTEM("System"),
    USERS("Users"),
    CRON("Cron"),
    SERVICES("Services"),
}

// region Packages
data class AptPackage(
    val name: String,
    val version: String = "",
    val description: String = "",
    val isInstalled: Boolean = false,
    val upgradeVersion: String? = null,
)
// endregion

// region Firewall
data class UfwState(
    val status: String = "inactive",
    val defaultIncoming: String = "deny",
    val defaultOutgoing: String = "allow",
    val logging: String = "off",
) {
    val isActive: Boolean get() = status.equals("active", ignoreCase = true)
}

data class UfwRule(
    val number: Int,
    val to: String,
    val action: String,
    val from: String,
    val comment: String = "",
)

data class IptablesRule(
    val num: Int,
    val target: String,
    val protocol: String,
    val source: String,
    val destination: String,
    val extra: String = "",
)

data class IptablesChain(
    val name: String,
    val policy: String,
    val rules: List<IptablesRule> = emptyList(),
)
// endregion

// region System
data class SystemInfo(
    val os: String = "",
    val kernel: String = "",
    val hostname: String = "",
    val timezone: String = "",
    val locale: String = "",
    val uptime: String = "",
    val arch: String = "",
)
// endregion

// region Users
data class ServerUser(
    val username: String,
    val uid: Int,
    val gid: Int = 0,
    val homeDir: String = "",
    val shell: String = "",
    val groups: List<String> = emptyList(),
    val hasSudo: Boolean = false,
)
// endregion

// region Cron
data class CronJob(
    val schedule: String,
    val command: String,
    val source: String = "user",
    val rawLine: String = "",
)
// endregion

// region Services
data class SystemctlService(
    val unit: String,
    val load: String = "",
    val active: String = "",
    val sub: String = "",
    val description: String = "",
    val isEnabled: Boolean = false,
)
// endregion

// region Confirmation / Biometric
sealed interface ServerAction {
    val label: String
    val requiresBiometric: Boolean

    // Packages
    data class InstallPackage(val packageName: String) : ServerAction {
        override val label = "Install $packageName"
        override val requiresBiometric = false
    }
    data class RemovePackage(val packageName: String) : ServerAction {
        override val label = "Remove $packageName"
        override val requiresBiometric = true
    }
    data object AptUpdate : ServerAction {
        override val label = "Update package lists"
        override val requiresBiometric = false
    }
    data object AptUpgrade : ServerAction {
        override val label = "Upgrade all packages"
        override val requiresBiometric = true
    }

    // Firewall
    data class AddUfwRule(val rule: String) : ServerAction {
        override val label = "Add firewall rule: $rule"
        override val requiresBiometric = false
    }
    data class DeleteUfwRule(val ruleNumber: Int) : ServerAction {
        override val label = "Delete firewall rule #$ruleNumber"
        override val requiresBiometric = false
    }
    data object EnableFirewall : ServerAction {
        override val label = "Enable firewall"
        override val requiresBiometric = false
    }
    data object DisableFirewall : ServerAction {
        override val label = "Disable firewall"
        override val requiresBiometric = true
    }
    data object ResetFirewall : ServerAction {
        override val label = "Reset firewall to defaults"
        override val requiresBiometric = true
    }
    data class SetDefaultPolicy(val direction: String, val policy: String) : ServerAction {
        override val label = "Set default $direction to $policy"
        override val requiresBiometric = false
    }

    // System
    data class SetHostname(val hostname: String) : ServerAction {
        override val label = "Set hostname to $hostname"
        override val requiresBiometric = false
    }
    data class SetTimezone(val timezone: String) : ServerAction {
        override val label = "Set timezone to $timezone"
        override val requiresBiometric = false
    }

    // Users
    data class AddUser(val username: String) : ServerAction {
        override val label = "Add user $username"
        override val requiresBiometric = false
    }
    data class DeleteUser(val username: String) : ServerAction {
        override val label = "Delete user $username"
        override val requiresBiometric = true
    }
    data class ModifyUserGroups(val username: String, val groups: String) : ServerAction {
        override val label = "Modify groups for $username"
        override val requiresBiometric = false
    }
    data class ToggleSudo(val username: String, val grant: Boolean) : ServerAction {
        override val label = if (grant) "Grant sudo to $username" else "Revoke sudo from $username"
        override val requiresBiometric = true
    }

    // Cron
    data class AddCronJob(val schedule: String, val command: String) : ServerAction {
        override val label = "Add cron job"
        override val requiresBiometric = false
    }
    data class DeleteCronJob(val rawLine: String) : ServerAction {
        override val label = "Delete cron job"
        override val requiresBiometric = false
    }

    // Services
    data class ServiceControl(val unit: String, val action: String) : ServerAction {
        override val label = "$action $unit"
        override val requiresBiometric = false
    }
    data class ServiceEnable(val unit: String, val enable: Boolean) : ServerAction {
        override val label = if (enable) "Enable $unit" else "Disable $unit"
        override val requiresBiometric = false
    }
}
// endregion
