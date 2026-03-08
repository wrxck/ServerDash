package com.serverdash.app.domain.plugin

import androidx.compose.ui.graphics.vector.ImageVector
import com.serverdash.app.domain.repository.SshRepository

data class PluginInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val isDetected: Boolean = false,
    val isEnabled: Boolean = true,
    val installInstructions: String? = null
)

interface ServerPlugin {
    val id: String
    val displayName: String
    val description: String
    val icon: ImageVector
    suspend fun detect(sshRepository: SshRepository): Boolean
    fun getInstallInstructions(): String?
}

class PluginRegistry {
    private val plugins = mutableListOf<ServerPlugin>()
    private val _detectedPlugins = mutableMapOf<String, Boolean>()

    fun register(plugin: ServerPlugin) {
        plugins.add(plugin)
    }

    fun getAll(): List<ServerPlugin> = plugins.toList()
    fun isDetected(pluginId: String): Boolean = _detectedPlugins[pluginId] ?: false

    suspend fun detectAll(sshRepository: SshRepository): Map<String, Boolean> {
        plugins.forEach { plugin ->
            _detectedPlugins[plugin.id] = plugin.detect(sshRepository)
        }
        return _detectedPlugins.toMap()
    }

    fun getDetectedIds(): Set<String> = _detectedPlugins.filterValues { it }.keys
}
