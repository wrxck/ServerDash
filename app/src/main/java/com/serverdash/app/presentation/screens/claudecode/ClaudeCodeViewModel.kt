package com.serverdash.app.presentation.screens.claudecode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.domain.model.CommandResult
import com.serverdash.app.domain.model.SystemUser
import com.serverdash.app.domain.repository.SshRepository
import com.serverdash.app.domain.usecase.DetectClaudeCodeUsersUseCase
import com.serverdash.app.domain.usecase.DetectSystemUsersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import javax.inject.Inject

@Serializable
data class McpServer(
    val name: String = "",
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)

@Serializable
data class ClaudePermission(
    val tool: String = "",
    val prompt: String = ""
)

enum class SettingsViewMode { UI, JSON }

data class ClaudeCodeUiState(
    val isDetected: Boolean = false,
    val isLoading: Boolean = true,
    val claudeVersion: String = "",
    val selectedTab: Int = 0,
    // MCP Servers
    val mcpServers: List<McpServer> = emptyList(),
    val isLoadingMcp: Boolean = false,
    // Settings (from settings.json)
    val settingsJson: String = "",
    val settingsViewMode: SettingsViewMode = SettingsViewMode.UI,
    val isLoadingSettings: Boolean = false,
    val isSavingSettings: Boolean = false,
    // Permissions
    val permissions: List<ClaudePermission> = emptyList(),
    val isLoadingPermissions: Boolean = false,
    // CLAUDE.md
    val claudeMdContent: String = "",
    val isLoadingClaudeMd: Boolean = false,
    val isSavingClaudeMd: Boolean = false,
    // Users
    val systemUsers: List<SystemUser> = emptyList(),
    val claudeCodeUsers: List<SystemUser> = emptyList(),
    val selectedUser: SystemUser? = null,
    val isLoadingUsers: Boolean = false,
    // General
    val error: String? = null,
    val successMessage: String? = null,
    // Edit dialogs
    val editingMcpServer: McpServer? = null,
    val isAddingMcpServer: Boolean = false,
    val editingSettings: Boolean = false,
    val editingClaudeMd: Boolean = false
)

sealed interface ClaudeCodeEvent {
    data class SelectTab(val index: Int) : ClaudeCodeEvent
    data object Refresh : ClaudeCodeEvent
    data object DismissError : ClaudeCodeEvent
    data object DismissSuccess : ClaudeCodeEvent
    data class SelectUser(val user: SystemUser) : ClaudeCodeEvent
    data object RefreshUsers : ClaudeCodeEvent
    // MCP
    data object LoadMcpServers : ClaudeCodeEvent
    data object ShowAddMcpServer : ClaudeCodeEvent
    data class EditMcpServer(val server: McpServer) : ClaudeCodeEvent
    data class SaveMcpServer(val original: McpServer?, val updated: McpServer) : ClaudeCodeEvent
    data class DeleteMcpServer(val server: McpServer) : ClaudeCodeEvent
    data object DismissMcpDialog : ClaudeCodeEvent
    // Settings
    data object LoadSettings : ClaudeCodeEvent
    data class UpdateSettingsJson(val json: String) : ClaudeCodeEvent
    data object SaveSettings : ClaudeCodeEvent
    data object StartEditSettings : ClaudeCodeEvent
    data object CancelEditSettings : ClaudeCodeEvent
    data object ToggleSettingsViewMode : ClaudeCodeEvent
    data object AutoSaveSettings : ClaudeCodeEvent
    // CLAUDE.md
    data object LoadClaudeMd : ClaudeCodeEvent
    data class UpdateClaudeMd(val content: String) : ClaudeCodeEvent
    data object SaveClaudeMd : ClaudeCodeEvent
    data object StartEditClaudeMd : ClaudeCodeEvent
    data object CancelEditClaudeMd : ClaudeCodeEvent
}

@HiltViewModel
class ClaudeCodeViewModel @Inject constructor(
    private val sshRepository: SshRepository,
    private val detectSystemUsers: DetectSystemUsersUseCase,
    private val detectClaudeCodeUsers: DetectClaudeCodeUsersUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ClaudeCodeUiState())
    val state: StateFlow<ClaudeCodeUiState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        detectClaude()
    }

    private fun detectClaude() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val result = sshRepository.executeCommand("claude --version 2>/dev/null || echo 'NOT_FOUND'")
                result.fold(
                    onSuccess = { cmdResult ->
                        val output = cmdResult.output.trim()
                        if (output.contains("NOT_FOUND") || output.isBlank()) {
                            _state.update { it.copy(isDetected = false, isLoading = false) }
                        } else {
                            _state.update { it.copy(isDetected = true, claudeVersion = output, isLoading = false) }
                            loadUsers()
                            loadMcpServers()
                        }
                    },
                    onFailure = {
                        _state.update { it.copy(isDetected = false, isLoading = false) }
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isDetected = false, isLoading = false, error = e.message) }
            }
        }
    }

    fun onEvent(event: ClaudeCodeEvent) {
        when (event) {
            is ClaudeCodeEvent.SelectTab -> {
                _state.update { it.copy(selectedTab = event.index) }
                when (event.index) {
                    0 -> loadMcpServers()
                    1 -> loadSettings()
                    2 -> loadClaudeMd()
                }
            }
            is ClaudeCodeEvent.Refresh -> {
                when (_state.value.selectedTab) {
                    0 -> loadMcpServers()
                    1 -> loadSettings()
                    2 -> loadClaudeMd()
                }
            }
            is ClaudeCodeEvent.DismissError -> _state.update { it.copy(error = null) }
            is ClaudeCodeEvent.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            is ClaudeCodeEvent.SelectUser -> selectUser(event.user)
            is ClaudeCodeEvent.RefreshUsers -> loadUsers()
            // MCP
            is ClaudeCodeEvent.LoadMcpServers -> loadMcpServers()
            is ClaudeCodeEvent.ShowAddMcpServer -> _state.update { it.copy(isAddingMcpServer = true, editingMcpServer = McpServer()) }
            is ClaudeCodeEvent.EditMcpServer -> _state.update { it.copy(editingMcpServer = event.server, isAddingMcpServer = false) }
            is ClaudeCodeEvent.SaveMcpServer -> saveMcpServer(event.original, event.updated)
            is ClaudeCodeEvent.DeleteMcpServer -> deleteMcpServer(event.server)
            is ClaudeCodeEvent.DismissMcpDialog -> _state.update { it.copy(editingMcpServer = null, isAddingMcpServer = false) }
            // Settings
            is ClaudeCodeEvent.LoadSettings -> loadSettings()
            is ClaudeCodeEvent.UpdateSettingsJson -> _state.update { it.copy(settingsJson = event.json) }
            is ClaudeCodeEvent.SaveSettings -> saveSettings()
            is ClaudeCodeEvent.StartEditSettings -> _state.update { it.copy(editingSettings = true) }
            is ClaudeCodeEvent.ToggleSettingsViewMode -> _state.update {
                it.copy(settingsViewMode = if (it.settingsViewMode == SettingsViewMode.UI) SettingsViewMode.JSON else SettingsViewMode.UI)
            }
            is ClaudeCodeEvent.AutoSaveSettings -> autoSaveSettings()
            is ClaudeCodeEvent.CancelEditSettings -> {
                _state.update { it.copy(editingSettings = false) }
                loadSettings()
            }
            // CLAUDE.md
            is ClaudeCodeEvent.LoadClaudeMd -> loadClaudeMd()
            is ClaudeCodeEvent.UpdateClaudeMd -> _state.update { it.copy(claudeMdContent = event.content) }
            is ClaudeCodeEvent.SaveClaudeMd -> saveClaudeMd()
            is ClaudeCodeEvent.StartEditClaudeMd -> _state.update { it.copy(editingClaudeMd = true) }
            is ClaudeCodeEvent.CancelEditClaudeMd -> {
                _state.update { it.copy(editingClaudeMd = false) }
                loadClaudeMd()
            }
        }
    }

    private val connectedUsername: String?
        get() = sshRepository.getConnectedUsername()

    private val isOtherUser: Boolean
        get() {
            val selected = _state.value.selectedUser ?: return false
            return selected.username != connectedUsername
        }

    private suspend fun readClaudeFile(relativePath: String): Result<String> {
        val selectedUser = _state.value.selectedUser
        return if (selectedUser != null && selectedUser.username != connectedUsername) {
            val fullPath = "${selectedUser.homeDirectory}/$relativePath"
            sshRepository.readFileAsUser(fullPath, selectedUser.username)
        } else {
            sshRepository.executeCommand("cat ~/$relativePath 2>/dev/null || echo ''").map { it.output }
        }
    }

    private suspend fun writeClaudeFile(relativePath: String, content: String): Result<Unit> {
        val selectedUser = _state.value.selectedUser
        return if (selectedUser != null && selectedUser.username != connectedUsername) {
            val fullPath = "${selectedUser.homeDirectory}/$relativePath"
            sshRepository.writeFileAsUser(fullPath, content, selectedUser.username)
        } else {
            sshRepository.executeCommand("mkdir -p ~/.claude && cat > ~/$relativePath << 'SERVERDASH_EOF'\n$content\nSERVERDASH_EOF").map { }
        }
    }

    private suspend fun executeForUser(command: String): Result<CommandResult> {
        val selectedUser = _state.value.selectedUser
        return if (selectedUser != null && selectedUser.username != connectedUsername) {
            sshRepository.executeAsUser(command, selectedUser.username)
        } else {
            sshRepository.executeCommand(command)
        }
    }

    private fun loadUsers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingUsers = true) }
            try {
                val usersResult = detectSystemUsers()
                usersResult.fold(
                    onSuccess = { users ->
                        val withClaude = detectClaudeCodeUsers(users).getOrDefault(users)
                        val claudeUsers = withClaude.filter { it.hasClaudeCode }
                        val defaultUser = claudeUsers.find { it.username == connectedUsername }
                            ?: claudeUsers.firstOrNull()
                        _state.update { it.copy(
                            systemUsers = withClaude,
                            claudeCodeUsers = claudeUsers,
                            selectedUser = defaultUser,
                            isLoadingUsers = false
                        )}
                    },
                    onFailure = {
                        _state.update { it.copy(isLoadingUsers = false) }
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingUsers = false) }
            }
        }
    }

    private fun selectUser(user: SystemUser) {
        _state.update { it.copy(selectedUser = user) }
        // Reload all data for the new user
        loadMcpServers()
        loadSettings()
        loadClaudeMd()
    }

    private fun loadMcpServers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMcp = true) }
            try {
                val result = readClaudeFile(".mcp.json")
                result.fold(
                    onSuccess = { content ->
                        val jsonStr = content.trim().ifBlank { "{}" }
                        val servers = parseMcpServers(jsonStr)
                        _state.update { it.copy(mcpServers = servers, isLoadingMcp = false) }
                    },
                    onFailure = { e ->
                        _state.update { it.copy(mcpServers = emptyList(), isLoadingMcp = false) }
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingMcp = false, error = e.message) }
            }
        }
    }

    private fun parseMcpServers(jsonStr: String): List<McpServer> {
        return try {
            val root = Json.parseToJsonElement(jsonStr).jsonObject
            val mcpObj = root["mcpServers"]?.jsonObject ?: return emptyList()
            mcpObj.entries.map { (name, value) ->
                val obj = value.jsonObject
                McpServer(
                    name = name,
                    command = obj["command"]?.jsonPrimitive?.content ?: "",
                    args = obj["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    env = obj["env"]?.jsonObject?.entries?.associate { (k, v) -> k to v.jsonPrimitive.content } ?: emptyMap()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveMcpServer(original: McpServer?, updated: McpServer) {
        viewModelScope.launch {
            try {
                // read current mcp config
                val currentJson = readClaudeFile(".mcp.json").getOrNull()?.trim()?.ifBlank { "{}" } ?: "{}"
                val root = try { Json.parseToJsonElement(currentJson).jsonObject.toMutableMap() } catch (e: Exception) { mutableMapOf() }

                val mcpObj = (root["mcpServers"]?.jsonObject?.toMutableMap() ?: mutableMapOf())

                // remove old entry if renaming
                if (original != null && original.name != updated.name && original.name.isNotBlank()) {
                    mcpObj.remove(original.name)
                }

                // add/update entry
                val serverObj = buildJsonObject {
                    put("command", updated.command)
                    putJsonArray("args") { updated.args.forEach { add(it) } }
                    if (updated.env.isNotEmpty()) {
                        putJsonObject("env") { updated.env.forEach { (k, v) -> put(k, v) } }
                    }
                }
                mcpObj[updated.name] = serverObj

                root["mcpServers"] = JsonObject(mcpObj)
                val newJson = json.encodeToString(JsonObject.serializer(), JsonObject(root))

                writeClaudeFile(".mcp.json", newJson)
                _state.update { it.copy(editingMcpServer = null, isAddingMcpServer = false, successMessage = "MCP server saved") }
                loadMcpServers()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to save MCP server: ${e.message}") }
            }
        }
    }

    private fun deleteMcpServer(server: McpServer) {
        viewModelScope.launch {
            try {
                val currentJson = readClaudeFile(".mcp.json").getOrNull()?.trim()?.ifBlank { "{}" } ?: "{}"
                val root = try { Json.parseToJsonElement(currentJson).jsonObject.toMutableMap() } catch (e: Exception) { mutableMapOf() }

                val mcpObj = (root["mcpServers"]?.jsonObject?.toMutableMap() ?: mutableMapOf())
                mcpObj.remove(server.name)
                root["mcpServers"] = JsonObject(mcpObj)

                val newJson = json.encodeToString(JsonObject.serializer(), JsonObject(root))
                writeClaudeFile(".mcp.json", newJson)
                _state.update { it.copy(successMessage = "MCP server '${server.name}' deleted") }
                loadMcpServers()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to delete: ${e.message}") }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingSettings = true) }
            try {
                val result = readClaudeFile(".claude/settings.json")
                result.fold(
                    onSuccess = { content ->
                        val raw = content.trim().ifBlank { "{}" }
                        val formatted = try {
                            val parsed = Json.parseToJsonElement(raw)
                            json.encodeToString(JsonElement.serializer(), parsed)
                        } catch (e: Exception) { raw }
                        _state.update { it.copy(settingsJson = formatted, isLoadingSettings = false, editingSettings = false) }
                    },
                    onFailure = { e ->
                        _state.update { it.copy(settingsJson = "{}", isLoadingSettings = false, editingSettings = false) }
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingSettings = false, error = e.message) }
            }
        }
    }

    private fun saveSettings() {
        viewModelScope.launch {
            _state.update { it.copy(isSavingSettings = true) }
            try {
                // Validate JSON
                Json.parseToJsonElement(_state.value.settingsJson)

                val content = _state.value.settingsJson
                executeForUser("mkdir -p ~/.claude")
                writeClaudeFile(".claude/settings.json", content)
                _state.update { it.copy(isSavingSettings = false, editingSettings = false, successMessage = "Settings saved") }
            } catch (e: Exception) {
                _state.update { it.copy(isSavingSettings = false, error = "Invalid JSON or save failed: ${e.message}") }
            }
        }
    }

    private fun autoSaveSettings() {
        viewModelScope.launch {
            try {
                Json.parseToJsonElement(_state.value.settingsJson)
                val content = _state.value.settingsJson
                executeForUser("mkdir -p ~/.claude")
                writeClaudeFile(".claude/settings.json", content)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Save failed: ${e.message}") }
            }
        }
    }

    private fun loadClaudeMd() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingClaudeMd = true) }
            try {
                val result = readClaudeFile(".claude/CLAUDE.md")
                result.fold(
                    onSuccess = { content ->
                        _state.update { it.copy(claudeMdContent = content, isLoadingClaudeMd = false, editingClaudeMd = false) }
                    },
                    onFailure = {
                        _state.update { it.copy(claudeMdContent = "", isLoadingClaudeMd = false, editingClaudeMd = false) }
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingClaudeMd = false, error = e.message) }
            }
        }
    }

    private fun saveClaudeMd() {
        viewModelScope.launch {
            _state.update { it.copy(isSavingClaudeMd = true) }
            try {
                val content = _state.value.claudeMdContent
                executeForUser("mkdir -p ~/.claude")
                writeClaudeFile(".claude/CLAUDE.md", content)
                _state.update { it.copy(isSavingClaudeMd = false, editingClaudeMd = false, successMessage = "CLAUDE.md saved") }
            } catch (e: Exception) {
                _state.update { it.copy(isSavingClaudeMd = false, error = "Failed to save: ${e.message}") }
            }
        }
    }
}
