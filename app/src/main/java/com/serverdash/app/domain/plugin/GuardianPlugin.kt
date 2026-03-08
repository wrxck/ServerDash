package com.serverdash.app.domain.plugin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.serverdash.app.domain.repository.SshRepository

class GuardianPlugin : ServerPlugin {
    override val id = "guardian"
    override val displayName = "Guardian"
    override val description = "Security process monitor"
    override val icon: ImageVector = Icons.Default.Shield

    override suspend fun detect(sshRepository: SshRepository): Boolean {
        return try {
            val result = sshRepository.executeCommand(
                "test -f /usr/local/bin/guardiand && echo 'found'"
            )
            result.getOrNull()?.output?.trim() == "found"
        } catch (e: Exception) { false }
    }

    override fun getInstallInstructions(): String? = null
}
