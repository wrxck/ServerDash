package com.serverdash.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.serverdash.app.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_THEME_ID = stringPreferencesKey("selected_theme_id")
        val UNDO_DURATION = intPreferencesKey("undo_duration_seconds")
        val CUSTOM_THEMES_JSON = stringPreferencesKey("custom_themes_json")
        val POLLING_INTERVAL = intPreferencesKey("polling_interval")
        val BRIGHTNESS_OVERRIDE = floatPreferencesKey("brightness_override")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KIOSK_MODE = booleanPreferencesKey("kiosk_mode")
        val PIXEL_SHIFT = booleanPreferencesKey("pixel_shift")
        val AUTO_START_BOOT = booleanPreferencesKey("auto_start_boot")
        val BG_CHECK_INTERVAL = intPreferencesKey("bg_check_interval")
        // Dashboard
        val DASHBOARD_LAYOUT = stringPreferencesKey("dashboard_layout")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SERVICE_SORT_ORDER = stringPreferencesKey("service_sort_order")
        val SHOW_SERVICE_DESC = booleanPreferencesKey("show_service_desc")
        val COMPACT_CARDS = booleanPreferencesKey("compact_cards")
        // Metrics
        val METRICS_DISPLAY_MODE = stringPreferencesKey("metrics_display_mode")
        val SHOW_LOAD_AVERAGE = booleanPreferencesKey("show_load_average")
        val CPU_WARNING = floatPreferencesKey("cpu_warning_threshold")
        val MEM_WARNING = floatPreferencesKey("mem_warning_threshold")
        val DISK_WARNING = floatPreferencesKey("disk_warning_threshold")
        // Notifications
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFY_SERVICE_DOWN = booleanPreferencesKey("notify_service_down")
        val NOTIFY_HIGH_CPU = booleanPreferencesKey("notify_high_cpu")
        val NOTIFY_HIGH_MEMORY = booleanPreferencesKey("notify_high_memory")
        val NOTIFY_HIGH_DISK = booleanPreferencesKey("notify_high_disk")
        val NOTIFICATION_SOUND = booleanPreferencesKey("notification_sound")
        val NOTIFICATION_VIBRATE = booleanPreferencesKey("notification_vibrate")
        // Logs
        val LOG_FONT_SIZE = stringPreferencesKey("log_font_size")
        val LOG_LINE_COUNT = intPreferencesKey("log_line_count")
        val LOG_AUTO_REFRESH = booleanPreferencesKey("log_auto_refresh")
        val LOG_AUTO_REFRESH_SEC = intPreferencesKey("log_auto_refresh_sec")
        val LOG_WRAP_LINES = booleanPreferencesKey("log_wrap_lines")
        // Terminal
        val TERMINAL_FONT_SIZE = intPreferencesKey("terminal_font_size")
        val TERMINAL_MAX_HISTORY = intPreferencesKey("terminal_max_history")
        val TERMINAL_SHOW_TIMESTAMPS = booleanPreferencesKey("terminal_show_timestamps")
        // Connection
        val CONN_TIMEOUT = intPreferencesKey("conn_timeout")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val RECONNECT_DELAY = intPreferencesKey("reconnect_delay")
        val MAX_RECONNECT_ATTEMPTS = intPreferencesKey("max_reconnect_attempts")
        // Data
        val METRICS_RETENTION_HOURS = intPreferencesKey("metrics_retention_hours")
        val MAX_SERVICES = intPreferencesKey("max_services_displayed")
        val HIDE_UNKNOWN = booleanPreferencesKey("hide_unknown_services")
        // plugins
        val DISABLED_PLUGINS = stringSetPreferencesKey("disabled_plugins")
        // Privacy / Streaming mode
        val STREAMING_MODE = booleanPreferencesKey("streaming_mode")
        val PRIVACY_IPS = booleanPreferencesKey("privacy_ips")
        val PRIVACY_PORTS = booleanPreferencesKey("privacy_ports")
        val PRIVACY_EMAILS = booleanPreferencesKey("privacy_emails")
        val PRIVACY_HOSTNAMES = booleanPreferencesKey("privacy_hostnames")
        val PRIVACY_PATHS = booleanPreferencesKey("privacy_paths")
        val PRIVACY_SSH = booleanPreferencesKey("privacy_ssh")
        val PRIVACY_TOKENS = booleanPreferencesKey("privacy_tokens")
        val PRIVACY_PASSWORDS = booleanPreferencesKey("privacy_passwords")
        val PRIVACY_SERVICE_NAMES = booleanPreferencesKey("privacy_service_names")
        val PRIVACY_REDACTED_SERVICES = stringSetPreferencesKey("privacy_redacted_services")
        val PRIVACY_CUSTOM_PATTERNS = stringSetPreferencesKey("privacy_custom_patterns")
        val PRIVACY_REPLACEMENT_TEXT = stringPreferencesKey("privacy_replacement_text")
        // Fonts
        val HEADER_FONT = stringPreferencesKey("header_font")
        val BODY_FONT = stringPreferencesKey("body_font")
        val CODE_FONT = stringPreferencesKey("code_font")
        // Cache
        val CACHE_TTL = intPreferencesKey("cache_ttl_seconds")
        // App Lock
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_TIMEOUT = stringPreferencesKey("app_lock_timeout")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs -> readPrefs(prefs) }

    val customThemesJson: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_THEMES_JSON] ?: "[]"
    }

    suspend fun updateCustomThemesJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_THEMES_JSON] = json
        }
    }

    private fun readPrefs(prefs: Preferences): AppPreferences = AppPreferences(
        themeMode = ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.AUTO.name),
        selectedThemeId = prefs[Keys.SELECTED_THEME_ID] ?: "default_dark",
        undoDurationSeconds = prefs[Keys.UNDO_DURATION] ?: 5,
        pollingIntervalSeconds = prefs[Keys.POLLING_INTERVAL] ?: 10,
        brightnessOverride = prefs[Keys.BRIGHTNESS_OVERRIDE] ?: -1f,
        keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
        kioskMode = prefs[Keys.KIOSK_MODE] ?: false,
        pixelShiftEnabled = prefs[Keys.PIXEL_SHIFT] ?: false,
        autoStartOnBoot = prefs[Keys.AUTO_START_BOOT] ?: false,
        backgroundCheckIntervalMinutes = prefs[Keys.BG_CHECK_INTERVAL] ?: 15,
        // Dashboard
        dashboardLayout = try { DashboardLayout.valueOf(prefs[Keys.DASHBOARD_LAYOUT] ?: DashboardLayout.GRID.name) } catch (e: Exception) { DashboardLayout.GRID },
        gridColumns = prefs[Keys.GRID_COLUMNS] ?: 0,
        serviceSortOrder = try { ServiceSortOrder.valueOf(prefs[Keys.SERVICE_SORT_ORDER] ?: ServiceSortOrder.PINNED_FIRST.name) } catch (e: Exception) { ServiceSortOrder.PINNED_FIRST },
        showServiceDescription = prefs[Keys.SHOW_SERVICE_DESC] ?: false,
        compactCards = prefs[Keys.COMPACT_CARDS] ?: false,
        // Metrics
        metricsDisplayMode = try { MetricsDisplayMode.valueOf(prefs[Keys.METRICS_DISPLAY_MODE] ?: MetricsDisplayMode.COMPACT.name) } catch (e: Exception) { MetricsDisplayMode.COMPACT },
        showLoadAverage = prefs[Keys.SHOW_LOAD_AVERAGE] ?: false,
        cpuWarningThreshold = prefs[Keys.CPU_WARNING] ?: 80f,
        memoryWarningThreshold = prefs[Keys.MEM_WARNING] ?: 80f,
        diskWarningThreshold = prefs[Keys.DISK_WARNING] ?: 90f,
        // Notifications
        notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
        notifyOnServiceDown = prefs[Keys.NOTIFY_SERVICE_DOWN] ?: true,
        notifyOnHighCpu = prefs[Keys.NOTIFY_HIGH_CPU] ?: true,
        notifyOnHighMemory = prefs[Keys.NOTIFY_HIGH_MEMORY] ?: true,
        notifyOnHighDisk = prefs[Keys.NOTIFY_HIGH_DISK] ?: true,
        notificationSound = prefs[Keys.NOTIFICATION_SOUND] ?: true,
        notificationVibrate = prefs[Keys.NOTIFICATION_VIBRATE] ?: true,
        // Logs
        logFontSize = try { LogFontSize.valueOf(prefs[Keys.LOG_FONT_SIZE] ?: LogFontSize.MEDIUM.name) } catch (e: Exception) { LogFontSize.MEDIUM },
        logLineCount = prefs[Keys.LOG_LINE_COUNT] ?: 100,
        logAutoRefresh = prefs[Keys.LOG_AUTO_REFRESH] ?: false,
        logAutoRefreshSeconds = prefs[Keys.LOG_AUTO_REFRESH_SEC] ?: 5,
        logWrapLines = prefs[Keys.LOG_WRAP_LINES] ?: true,
        // Terminal
        terminalFontSize = prefs[Keys.TERMINAL_FONT_SIZE] ?: 14,
        terminalMaxHistory = prefs[Keys.TERMINAL_MAX_HISTORY] ?: 500,
        terminalShowTimestamps = prefs[Keys.TERMINAL_SHOW_TIMESTAMPS] ?: true,
        // Connection
        connectionTimeoutSeconds = prefs[Keys.CONN_TIMEOUT] ?: 30,
        autoReconnect = prefs[Keys.AUTO_RECONNECT] ?: true,
        autoReconnectDelaySeconds = prefs[Keys.RECONNECT_DELAY] ?: 5,
        maxReconnectAttempts = prefs[Keys.MAX_RECONNECT_ATTEMPTS] ?: 3,
        // Data
        metricsRetentionHours = prefs[Keys.METRICS_RETENTION_HOURS] ?: 24,
        maxServicesDisplayed = prefs[Keys.MAX_SERVICES] ?: 0,
        hideUnknownServices = prefs[Keys.HIDE_UNKNOWN] ?: false,
        // plugins
        disabledPlugins = prefs[Keys.DISABLED_PLUGINS] ?: emptySet(),
        // Privacy / Streaming mode
        streamingModeEnabled = prefs[Keys.STREAMING_MODE] ?: false,
        privacyFilterIps = prefs[Keys.PRIVACY_IPS] ?: true,
        privacyFilterPorts = prefs[Keys.PRIVACY_PORTS] ?: true,
        privacyFilterEmails = prefs[Keys.PRIVACY_EMAILS] ?: true,
        privacyFilterHostnames = prefs[Keys.PRIVACY_HOSTNAMES] ?: true,
        privacyFilterPaths = prefs[Keys.PRIVACY_PATHS] ?: true,
        privacyFilterSsh = prefs[Keys.PRIVACY_SSH] ?: true,
        privacyFilterTokens = prefs[Keys.PRIVACY_TOKENS] ?: true,
        privacyFilterPasswords = prefs[Keys.PRIVACY_PASSWORDS] ?: true,
        privacyFilterServiceNames = prefs[Keys.PRIVACY_SERVICE_NAMES] ?: false,
        privacyRedactedServiceNames = prefs[Keys.PRIVACY_REDACTED_SERVICES] ?: emptySet(),
        privacyCustomPatterns = prefs[Keys.PRIVACY_CUSTOM_PATTERNS] ?: emptySet(),
        privacyReplacementText = prefs[Keys.PRIVACY_REPLACEMENT_TEXT] ?: "[REDACTED]",
        // Fonts
        headerFont = prefs[Keys.HEADER_FONT] ?: "JetBrains Mono",
        bodyFont = prefs[Keys.BODY_FONT] ?: "JetBrains Mono",
        codeFont = prefs[Keys.CODE_FONT] ?: "JetBrains Mono",
        // Cache
        cacheTtlSeconds = prefs[Keys.CACHE_TTL] ?: 300,
        // App Lock
        appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
        appLockTimeout = try { LockTimeout.valueOf(prefs[Keys.APP_LOCK_TIMEOUT] ?: LockTimeout.IMMEDIATE.name) } catch (e: Exception) { LockTimeout.IMMEDIATE }
    )

    suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        context.dataStore.edit { prefs ->
            val current = readPrefs(prefs)
            val updated = transform(current)
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.SELECTED_THEME_ID] = updated.selectedThemeId
            prefs[Keys.UNDO_DURATION] = updated.undoDurationSeconds
            prefs[Keys.POLLING_INTERVAL] = updated.pollingIntervalSeconds
            prefs[Keys.BRIGHTNESS_OVERRIDE] = updated.brightnessOverride
            prefs[Keys.KEEP_SCREEN_ON] = updated.keepScreenOn
            prefs[Keys.KIOSK_MODE] = updated.kioskMode
            prefs[Keys.PIXEL_SHIFT] = updated.pixelShiftEnabled
            prefs[Keys.AUTO_START_BOOT] = updated.autoStartOnBoot
            prefs[Keys.BG_CHECK_INTERVAL] = updated.backgroundCheckIntervalMinutes
            // Dashboard
            prefs[Keys.DASHBOARD_LAYOUT] = updated.dashboardLayout.name
            prefs[Keys.GRID_COLUMNS] = updated.gridColumns
            prefs[Keys.SERVICE_SORT_ORDER] = updated.serviceSortOrder.name
            prefs[Keys.SHOW_SERVICE_DESC] = updated.showServiceDescription
            prefs[Keys.COMPACT_CARDS] = updated.compactCards
            // Metrics
            prefs[Keys.METRICS_DISPLAY_MODE] = updated.metricsDisplayMode.name
            prefs[Keys.SHOW_LOAD_AVERAGE] = updated.showLoadAverage
            prefs[Keys.CPU_WARNING] = updated.cpuWarningThreshold
            prefs[Keys.MEM_WARNING] = updated.memoryWarningThreshold
            prefs[Keys.DISK_WARNING] = updated.diskWarningThreshold
            // Notifications
            prefs[Keys.NOTIFICATIONS_ENABLED] = updated.notificationsEnabled
            prefs[Keys.NOTIFY_SERVICE_DOWN] = updated.notifyOnServiceDown
            prefs[Keys.NOTIFY_HIGH_CPU] = updated.notifyOnHighCpu
            prefs[Keys.NOTIFY_HIGH_MEMORY] = updated.notifyOnHighMemory
            prefs[Keys.NOTIFY_HIGH_DISK] = updated.notifyOnHighDisk
            prefs[Keys.NOTIFICATION_SOUND] = updated.notificationSound
            prefs[Keys.NOTIFICATION_VIBRATE] = updated.notificationVibrate
            // Logs
            prefs[Keys.LOG_FONT_SIZE] = updated.logFontSize.name
            prefs[Keys.LOG_LINE_COUNT] = updated.logLineCount
            prefs[Keys.LOG_AUTO_REFRESH] = updated.logAutoRefresh
            prefs[Keys.LOG_AUTO_REFRESH_SEC] = updated.logAutoRefreshSeconds
            prefs[Keys.LOG_WRAP_LINES] = updated.logWrapLines
            // Terminal
            prefs[Keys.TERMINAL_FONT_SIZE] = updated.terminalFontSize
            prefs[Keys.TERMINAL_MAX_HISTORY] = updated.terminalMaxHistory
            prefs[Keys.TERMINAL_SHOW_TIMESTAMPS] = updated.terminalShowTimestamps
            // Connection
            prefs[Keys.CONN_TIMEOUT] = updated.connectionTimeoutSeconds
            prefs[Keys.AUTO_RECONNECT] = updated.autoReconnect
            prefs[Keys.RECONNECT_DELAY] = updated.autoReconnectDelaySeconds
            prefs[Keys.MAX_RECONNECT_ATTEMPTS] = updated.maxReconnectAttempts
            // Data
            prefs[Keys.METRICS_RETENTION_HOURS] = updated.metricsRetentionHours
            prefs[Keys.MAX_SERVICES] = updated.maxServicesDisplayed
            prefs[Keys.HIDE_UNKNOWN] = updated.hideUnknownServices
            // plugins
            prefs[Keys.DISABLED_PLUGINS] = updated.disabledPlugins
            // Privacy / Streaming mode
            prefs[Keys.STREAMING_MODE] = updated.streamingModeEnabled
            prefs[Keys.PRIVACY_IPS] = updated.privacyFilterIps
            prefs[Keys.PRIVACY_PORTS] = updated.privacyFilterPorts
            prefs[Keys.PRIVACY_EMAILS] = updated.privacyFilterEmails
            prefs[Keys.PRIVACY_HOSTNAMES] = updated.privacyFilterHostnames
            prefs[Keys.PRIVACY_PATHS] = updated.privacyFilterPaths
            prefs[Keys.PRIVACY_SSH] = updated.privacyFilterSsh
            prefs[Keys.PRIVACY_TOKENS] = updated.privacyFilterTokens
            prefs[Keys.PRIVACY_PASSWORDS] = updated.privacyFilterPasswords
            prefs[Keys.PRIVACY_SERVICE_NAMES] = updated.privacyFilterServiceNames
            prefs[Keys.PRIVACY_REDACTED_SERVICES] = updated.privacyRedactedServiceNames
            prefs[Keys.PRIVACY_CUSTOM_PATTERNS] = updated.privacyCustomPatterns
            prefs[Keys.PRIVACY_REPLACEMENT_TEXT] = updated.privacyReplacementText
            // Fonts
            prefs[Keys.HEADER_FONT] = updated.headerFont
            prefs[Keys.BODY_FONT] = updated.bodyFont
            prefs[Keys.CODE_FONT] = updated.codeFont
            // Cache
            prefs[Keys.CACHE_TTL] = updated.cacheTtlSeconds
            // App Lock
            prefs[Keys.APP_LOCK_ENABLED] = updated.appLockEnabled
            prefs[Keys.APP_LOCK_TIMEOUT] = updated.appLockTimeout.name
        }
    }
}
