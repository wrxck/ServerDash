package com.serverdash.app.domain.plugin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.ui.graphics.vector.ImageVector
import com.serverdash.app.domain.repository.SshRepository

class FleetPlugin : ServerPlugin {
    override val id = "fleet"
    override val displayName = "Fleet"
    override val description = "Docker production management"
    override val icon: ImageVector = Icons.Default.RocketLaunch

    override suspend fun detect(sshRepository: SshRepository): Boolean {
        return try {
            val result = sshRepository.executeCommand("which fleet 2>/dev/null")
            result.getOrNull()?.exitCode == 0 &&
                result.getOrNull()?.output?.trim()?.isNotBlank() == true
        } catch (e: Exception) { false }
    }

    override fun getInstallInstructions(): String =
        "Fleet CLI is not installed. Install from the fleet repository."
}
