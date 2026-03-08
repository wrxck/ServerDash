package com.serverdash.app.widget

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SharedPreferences-based data store for widget data.
 * Widget providers cannot use Hilt injection, so this uses a simple
 * SharedPreferences approach accessible from both the app and widget providers.
 */
object WidgetDataStore {

    private const val PREFS_NAME = "serverdash_widget_data"

    private const val KEY_IS_CONNECTED = "is_connected"
    private const val KEY_HOSTNAME = "hostname"
    private const val KEY_CPU_USAGE = "cpu_usage"
    private const val KEY_MEMORY_USAGE = "memory_usage"
    private const val KEY_DISK_USAGE = "disk_usage"
    private const val KEY_SERVICES_JSON = "services_json"
    private const val KEY_LAST_UPDATE = "last_update"

    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun updateConnectionStatus(context: Context, isConnected: Boolean, hostname: String) {
        prefs(context).edit()
            .putBoolean(KEY_IS_CONNECTED, isConnected)
            .putString(KEY_HOSTNAME, hostname)
            .apply()
    }

    fun updateMetrics(context: Context, cpuUsage: Float, memoryUsage: Float, diskUsage: Float) {
        prefs(context).edit()
            .putFloat(KEY_CPU_USAGE, cpuUsage)
            .putFloat(KEY_MEMORY_USAGE, memoryUsage)
            .putFloat(KEY_DISK_USAGE, diskUsage)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }

    fun updateServices(context: Context, services: List<WidgetServiceInfo>) {
        prefs(context).edit()
            .putString(KEY_SERVICES_JSON, json.encodeToString(services))
            .apply()
    }

    fun getWidgetData(context: Context): WidgetData {
        val p = prefs(context)
        val servicesJson = p.getString(KEY_SERVICES_JSON, "[]") ?: "[]"
        val services = try {
            json.decodeFromString<List<WidgetServiceInfo>>(servicesJson)
        } catch (_: Exception) {
            emptyList()
        }

        return WidgetData(
            isConnected = p.getBoolean(KEY_IS_CONNECTED, false),
            hostname = p.getString(KEY_HOSTNAME, "No server") ?: "No server",
            cpuUsage = p.getFloat(KEY_CPU_USAGE, 0f),
            memoryUsage = p.getFloat(KEY_MEMORY_USAGE, 0f),
            diskUsage = p.getFloat(KEY_DISK_USAGE, 0f),
            services = services,
            lastUpdate = p.getLong(KEY_LAST_UPDATE, 0L)
        )
    }
}

data class WidgetData(
    val isConnected: Boolean = false,
    val hostname: String = "No server",
    val cpuUsage: Float = 0f,
    val memoryUsage: Float = 0f,
    val diskUsage: Float = 0f,
    val services: List<WidgetServiceInfo> = emptyList(),
    val lastUpdate: Long = 0L
) {
    val runningCount: Int get() = services.count { it.status == "RUNNING" }
    val failedCount: Int get() = services.count { it.status == "FAILED" }
    val stoppedCount: Int get() = services.count { it.status == "STOPPED" || it.status == "UNKNOWN" }
    val failedServices: List<WidgetServiceInfo> get() = services.filter { it.status == "FAILED" }
}

@Serializable
data class WidgetServiceInfo(
    val name: String,
    val displayName: String,
    val status: String // "RUNNING", "FAILED", "STOPPED", "UNKNOWN"
)
