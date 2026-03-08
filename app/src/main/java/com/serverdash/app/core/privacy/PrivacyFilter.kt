package com.serverdash.app.core.privacy

import com.serverdash.app.domain.model.AppPreferences
import com.serverdash.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacyFilter @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val preferences: StateFlow<AppPreferences> = preferencesRepository.observePreferences()
        .stateIn(scope, SharingStarted.Eagerly, AppPreferences())

    val isEnabled: StateFlow<Boolean> = preferencesRepository.observePreferences()
        .map { it.streamingModeEnabled }
        .stateIn(scope, SharingStarted.Eagerly, false)

    companion object {
        // IPv4: e.g. 192.168.1.1, 10.0.0.1
        private val IPV4_REGEX = Regex(
            """\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\b"""
        )

        // IPv6: simplified pattern covering common representations
        private val IPV6_REGEX = Regex(
            """(?i)\b(?:[0-9a-f]{1,4}:){2,7}[0-9a-f]{1,4}\b|(?i)\b(?:[0-9a-f]{1,4}:){1,6}:[0-9a-f]{1,4}\b|::(?:[0-9a-f]{1,4}:){0,5}[0-9a-f]{1,4}\b|(?i)[0-9a-f]{1,4}::(?:[0-9a-f]{1,4}:){0,4}[0-9a-f]{1,4}\b"""
        )

        // Port numbers in context: :8080, port 443, Port=3306
        private val PORT_REGEX = Regex(
            """(?<=:)\d{2,5}\b|(?i)(?<=port\s)\d{2,5}\b|(?i)(?<=port=)\d{2,5}\b"""
        )

        // Email addresses
        private val EMAIL_REGEX = Regex(
            """[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}"""
        )

        // Hostnames/domains: e.g. server.example.com, my-host.io
        private val HOSTNAME_REGEX = Regex(
            """(?<![@/])\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?\.)+(?:com|net|org|io|dev|app|co|us|uk|de|fr|info|biz|cloud|server|local|internal|lan|home|test)\b"""
        )

        // File paths with usernames: /home/username/..., /Users/username/..., /etc/... with user refs
        private val HOME_PATH_REGEX = Regex(
            """(?:/home/|/Users/)([a-zA-Z0-9_.\-]+)"""
        )

        // SSH connection strings: user@host
        private val SSH_REGEX = Regex(
            """[a-zA-Z0-9._\-]+@[a-zA-Z0-9.\-]+(?:\.[a-zA-Z]{2,})"""
        )

        // API keys/tokens: long hex strings (32+ chars) or base64-like tokens (40+ chars)
        private val HEX_TOKEN_REGEX = Regex(
            """\b[0-9a-fA-F]{32,}\b"""
        )
        private val BASE64_TOKEN_REGEX = Regex(
            """\b[A-Za-z0-9+/=_\-]{40,}\b"""
        )

        // Passwords in command output: password=..., passwd:..., --password ...
        private val PASSWORD_REGEX = Regex(
            """(?i)(?:password|passwd|pass|secret|token|api[_-]?key)\s*[=:]\s*\S+"""
        )

        // Sample text for preview
        const val SAMPLE_TEXT = """Server 192.168.1.100:8080 is running.
Connected as admin@prod-server.example.com via SSH.
Email alert sent to ops@company.io
Logs at /home/deploy/.config/app/logs
API key: a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2
password=SuperSecret123
Service nginx listening on port 443"""
    }

    /**
     * Filter sensitive information from the given text based on current preferences.
     * Thread-safe: reads a snapshot of preferences atomically.
     */
    fun filter(text: String): String {
        val prefs = preferences.value
        if (!prefs.streamingModeEnabled) return text

        var result = text
        val replacement = prefs.privacyReplacementText

        // Order matters: more specific patterns first to avoid partial matches

        // SSH connection strings (before email and hostname to catch user@host)
        if (prefs.privacyFilterSsh) {
            result = SSH_REGEX.replace(result, "[REDACTED SSH]")
        }

        // Email addresses
        if (prefs.privacyFilterEmails) {
            result = EMAIL_REGEX.replace(result, "[REDACTED EMAIL]")
        }

        // Passwords in command output
        if (prefs.privacyFilterPasswords) {
            result = PASSWORD_REGEX.replace(result, replacement)
        }

        // API keys/tokens (before general hex might match IPs)
        if (prefs.privacyFilterTokens) {
            result = BASE64_TOKEN_REGEX.replace(result, "[REDACTED TOKEN]")
            result = HEX_TOKEN_REGEX.replace(result, "[REDACTED TOKEN]")
        }

        // IP addresses
        if (prefs.privacyFilterIps) {
            result = IPV4_REGEX.replace(result, "[REDACTED IP]")
            result = IPV6_REGEX.replace(result, "[REDACTED IP]")
        }

        // Port numbers
        if (prefs.privacyFilterPorts) {
            result = PORT_REGEX.replace(result, "[REDACTED PORT]")
        }

        // Hostnames/domains
        if (prefs.privacyFilterHostnames) {
            result = HOSTNAME_REGEX.replace(result, "[REDACTED HOST]")
        }

        // File paths (redact username portion only)
        if (prefs.privacyFilterPaths) {
            result = HOME_PATH_REGEX.replace(result) { match ->
                val prefix = if (match.value.startsWith("/home/")) "/home/" else "/Users/"
                "${prefix}${replacement}"
            }
        }

        // Service names
        if (prefs.privacyFilterServiceNames && prefs.privacyRedactedServiceNames.isNotEmpty()) {
            for (serviceName in prefs.privacyRedactedServiceNames) {
                if (serviceName.isNotBlank()) {
                    result = result.replace(serviceName, "[SERVICE]", ignoreCase = true)
                }
            }
        }

        // Custom regex patterns
        for (pattern in prefs.privacyCustomPatterns) {
            if (pattern.isNotBlank()) {
                try {
                    val customRegex = Regex(pattern)
                    result = customRegex.replace(result, replacement)
                } catch (_: Exception) {
                    // Skip invalid regex patterns silently
                }
            }
        }

        return result
    }

    /**
     * Filter text with explicit preferences (useful for preview/testing without saving).
     */
    fun filterWithPrefs(text: String, prefs: AppPreferences): String {
        val saved = preferences.value
        // Temporarily use the provided prefs by delegating to a stateless version
        return filterStateless(text, prefs)
    }

    private fun filterStateless(text: String, prefs: AppPreferences): String {
        if (!prefs.streamingModeEnabled) return text

        var result = text
        val replacement = prefs.privacyReplacementText

        if (prefs.privacyFilterSsh) {
            result = SSH_REGEX.replace(result, "[REDACTED SSH]")
        }
        if (prefs.privacyFilterEmails) {
            result = EMAIL_REGEX.replace(result, "[REDACTED EMAIL]")
        }
        if (prefs.privacyFilterPasswords) {
            result = PASSWORD_REGEX.replace(result, replacement)
        }
        if (prefs.privacyFilterTokens) {
            result = BASE64_TOKEN_REGEX.replace(result, "[REDACTED TOKEN]")
            result = HEX_TOKEN_REGEX.replace(result, "[REDACTED TOKEN]")
        }
        if (prefs.privacyFilterIps) {
            result = IPV4_REGEX.replace(result, "[REDACTED IP]")
            result = IPV6_REGEX.replace(result, "[REDACTED IP]")
        }
        if (prefs.privacyFilterPorts) {
            result = PORT_REGEX.replace(result, "[REDACTED PORT]")
        }
        if (prefs.privacyFilterHostnames) {
            result = HOSTNAME_REGEX.replace(result, "[REDACTED HOST]")
        }
        if (prefs.privacyFilterPaths) {
            result = HOME_PATH_REGEX.replace(result) { match ->
                val prefix = if (match.value.startsWith("/home/")) "/home/" else "/Users/"
                "${prefix}${replacement}"
            }
        }
        if (prefs.privacyFilterServiceNames && prefs.privacyRedactedServiceNames.isNotEmpty()) {
            for (serviceName in prefs.privacyRedactedServiceNames) {
                if (serviceName.isNotBlank()) {
                    result = result.replace(serviceName, "[SERVICE]", ignoreCase = true)
                }
            }
        }
        for (pattern in prefs.privacyCustomPatterns) {
            if (pattern.isNotBlank()) {
                try {
                    result = Regex(pattern).replace(result, replacement)
                } catch (_: Exception) { }
            }
        }
        return result
    }
}
