package com.serverdash.app.domain.plugin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector
import com.serverdash.app.domain.repository.SshRepository

class ClaudeCodePlugin : ServerPlugin {
    override val id = "claude-code"
    override val displayName = "Claude Code"
    override val description = "Manage settings, MCP servers, and CLAUDE.md per user"
    override val icon: ImageVector = Icons.Default.SmartToy

    override suspend fun detect(sshRepository: SshRepository): Boolean {
        return try {
            val result = sshRepository.executeCommand("claude --version 2>/dev/null")
            result.getOrNull()?.let {
                it.exitCode == 0 && !it.output.contains("NOT_FOUND")
                    && it.output.trim().isNotBlank()
            } ?: false
        } catch (e: Exception) { false }
    }

    override fun getInstallInstructions(): String =
        "Install Claude Code: npm install -g @anthropic-ai/claude-code"
}
