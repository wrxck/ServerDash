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
    val type: String = "stdio", // stdio, http, sse
    val command: String = "", // stdio servers
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val url: String = "", // http/sse servers
    val headers: Map<String, String> = emptyMap(), // http/sse auth headers
    val enabled: Boolean = true,
    val source: String = "" // e.g. "~/.claude.json", "project:/path"
)

@Serializable
data class ClaudePermission(
    val tool: String = "",
    val prompt: String = ""
)

enum class SettingsViewMode { UI, JSON }

data class SettingsDiffEntry(
    val path: String,
    val oldValue: String?,
    val newValue: String?,
    val type: DiffType
)

enum class DiffType { ADDED, REMOVED, CHANGED }

data class ClaudeProject(
    val path: String, // raw dir name like -home-matt-fleet
    val displayName: String, // decoded: /home/matt/fleet
    val sessionCount: Int = 0,
    val hasMemory: Boolean = false
)

enum class DetailView { STORAGE, PROJECTS, SESSIONS, PLANS, PLUGINS, HOOKS, SKILLS, USAGE }

data class StorageItem(val category: String, val size: String, val path: String)

data class SessionInfo(
    val project: String,
    val projectDisplay: String,
    val filename: String,
    val size: String,
    val modified: String,
    val fullPath: String = ""
)

data class PlanInfo(val name: String, val path: String, val size: String)

data class HookScript(val filename: String, val content: String = "", val isNew: Boolean = false)

data class SkillScript(val filename: String, val content: String = "", val isNew: Boolean = false)

data class ClaudeCodeUsage(
    val totalTokens: Long = 0,
    val totalCost: Double = 0.0,
    val sessions: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val rawEntries: Map<String, String> = emptyMap() // any other fields
)

data class SessionActivity(
    val projectName: String,
    val sessionCount: Int,
    val totalSize: String,
    val lastModifiedEpoch: Long = 0
)

data class ClaudeCodeUiState(
    val isDetected: Boolean = false,
    val isLoading: Boolean = true,
    val claudeVersion: String = "",
    val selectedTab: Int = 0,
    // mcp servers
    val mcpServers: List<McpServer> = emptyList(),
    val isLoadingMcp: Boolean = false,
    val mcpScanProgress: String = "", // status message shown during MCP scan
    val mcpDebugInfo: String = "", // diagnostic info for debugging MCP detection
    // settings
    val settingsJson: String = "",
    val originalSettingsJson: String = "",
    val settingsDiff: List<SettingsDiffEntry> = emptyList(),
    val showDiffDialog: Boolean = false,
    val settingsViewMode: SettingsViewMode = SettingsViewMode.UI,
    val isLoadingSettings: Boolean = false,
    val isSavingSettings: Boolean = false,
    // permissions
    val permissions: List<ClaudePermission> = emptyList(),
    val isLoadingPermissions: Boolean = false,
    // claude.md
    val claudeMdContent: String = "",
    val isLoadingClaudeMd: Boolean = false,
    val isSavingClaudeMd: Boolean = false,
    // users
    val systemUsers: List<SystemUser> = emptyList(),
    val claudeCodeUsers: List<SystemUser> = emptyList(),
    val selectedUser: SystemUser? = null,
    val isLoadingUsers: Boolean = false,
    // overview
    val diskUsage: String = "",
    val projectCount: Int = 0,
    val planCount: Int = 0,
    val sessionCount: Int = 0,
    val installedPlugins: List<String> = emptyList(),
    val customSkills: List<String> = emptyList(),
    val hookFiles: List<String> = emptyList(),
    val isLoadingOverview: Boolean = true,
    val usageStats: String = "", // raw JSON from cc-counter/stats.json
    val parsedUsage: ClaudeCodeUsage? = null,
    val sessionActivity: List<SessionActivity> = emptyList(),
    // projects
    val projects: List<ClaudeProject> = emptyList(),
    val isLoadingProjects: Boolean = false,
    val selectedProjectMemory: String? = null,
    val selectedProjectName: String? = null,
    val selectedProjectPath: String? = null,
    val editingProjectMemory: Boolean = false,
    val editedProjectMemory: String = "",
    val isSavingProjectMemory: Boolean = false,
    // detail navigation
    val activeDetail: DetailView? = null,
    val isLoadingDetail: Boolean = false,
    // storage detail
    val storageBreakdown: List<StorageItem> = emptyList(),
    // sessions detail
    val sessionsList: List<SessionInfo> = emptyList(),
    val selectedSessionContent: String? = null,
    val selectedSessionName: String? = null,
    val sessionSearchQuery: String = "",
    val sessionLines: List<String> = emptyList(),
    val isLoadingSession: Boolean = false,
    val sessionPrettyPrint: Boolean = true,
    val sessionFilterRole: String? = null, // null = all, "user", "assistant", "system", "tool"
    // plans detail
    val plansList: List<PlanInfo> = emptyList(),
    val selectedPlanContent: String? = null,
    val selectedPlanName: String? = null,
    // hooks CRUD
    val hookScripts: List<HookScript> = emptyList(),
    val editingHook: HookScript? = null,
    val isAddingHook: Boolean = false,
    // skills CRUD
    val skillScripts: List<SkillScript> = emptyList(),
    val editingSkill: SkillScript? = null,
    val isAddingSkill: Boolean = false,
    // general
    val error: String? = null,
    val successMessage: String? = null,
    // edit dialogues
    val editingMcpServer: McpServer? = null,
    val isAddingMcpServer: Boolean = false,
    val editingSettings: Boolean = false,
    val editingClaudeMd: Boolean = false,
    // ~/.mcp.json migration
    val showMcpMigrationDialog: Boolean = false,
    val mcpMigrationUser: String = "",
    val mcpMigrationSystemUser: SystemUser? = null,
    val mcpMigrationServers: List<McpServer> = emptyList(),
    val isMigrating: Boolean = false,
    // project claude.md CRUD
    val editingProjectClaudeMd: Boolean = false,
    val projectClaudeMdContent: String = "",
    val projectClaudeMdProject: ClaudeProject? = null,
    val isLoadingProjectClaudeMd: Boolean = false,
    val isSavingProjectClaudeMd: Boolean = false
)

sealed interface ClaudeCodeEvent {
    data class SelectTab(val index: Int) : ClaudeCodeEvent
    data object Refresh : ClaudeCodeEvent
    data object DismissError : ClaudeCodeEvent
    data object DismissSuccess : ClaudeCodeEvent
    data class SelectUser(val user: SystemUser) : ClaudeCodeEvent
    data object RefreshUsers : ClaudeCodeEvent
    // mcp
    data object LoadMcpServers : ClaudeCodeEvent
    data object ShowAddMcpServer : ClaudeCodeEvent
    data class EditMcpServer(val server: McpServer) : ClaudeCodeEvent
    data class SaveMcpServer(val original: McpServer?, val updated: McpServer) : ClaudeCodeEvent
    data class DeleteMcpServer(val server: McpServer) : ClaudeCodeEvent
    data object DismissMcpDialog : ClaudeCodeEvent
    // settings
    data object LoadSettings : ClaudeCodeEvent
    data class UpdateSettingsJson(val json: String) : ClaudeCodeEvent
    data object SaveSettings : ClaudeCodeEvent
    data object StartEditSettings : ClaudeCodeEvent
    data object CancelEditSettings : ClaudeCodeEvent
    data object ToggleSettingsViewMode : ClaudeCodeEvent
    data object AutoSaveSettings : ClaudeCodeEvent
    data object RequestSaveSettings : ClaudeCodeEvent
    data object ConfirmSaveSettings : ClaudeCodeEvent
    data object DismissDiffDialog : ClaudeCodeEvent
    // overview & projects
    data object LoadOverview : ClaudeCodeEvent
    data object LoadProjects : ClaudeCodeEvent
    data class ViewProjectMemory(val project: ClaudeProject) : ClaudeCodeEvent
    data object DismissProjectMemory : ClaudeCodeEvent
    data object StartEditProjectMemory : ClaudeCodeEvent
    data class UpdateEditedProjectMemory(val content: String) : ClaudeCodeEvent
    data object SaveProjectMemory : ClaudeCodeEvent
    data object CancelEditProjectMemory : ClaudeCodeEvent
    // detail navigation
    data class OpenDetail(val view: DetailView) : ClaudeCodeEvent
    data object CloseDetail : ClaudeCodeEvent
    // sessions
    data class ViewSession(val session: SessionInfo) : ClaudeCodeEvent
    data class DeleteSession(val session: SessionInfo) : ClaudeCodeEvent
    data object DismissSessionContent : ClaudeCodeEvent
    data class UpdateSessionSearch(val query: String) : ClaudeCodeEvent
    data object ToggleSessionPrettyPrint : ClaudeCodeEvent
    data class FilterSessionRole(val role: String?) : ClaudeCodeEvent
    // plans
    data class ViewPlan(val plan: PlanInfo) : ClaudeCodeEvent
    data class DeletePlan(val plan: PlanInfo) : ClaudeCodeEvent
    data object DismissPlanContent : ClaudeCodeEvent
    // hooks CRUD
    data class EditHook(val hook: HookScript) : ClaudeCodeEvent
    data object ShowAddHook : ClaudeCodeEvent
    data class SaveHook(val originalName: String?, val hook: HookScript) : ClaudeCodeEvent
    data class DeleteHook(val hook: HookScript) : ClaudeCodeEvent
    data object DismissHookDialog : ClaudeCodeEvent
    // skills CRUD
    data class EditSkill(val skill: SkillScript) : ClaudeCodeEvent
    data object ShowAddSkill : ClaudeCodeEvent
    data class SaveSkill(val originalName: String?, val skill: SkillScript) : ClaudeCodeEvent
    data class DeleteSkill(val skill: SkillScript) : ClaudeCodeEvent
    data object DismissSkillDialog : ClaudeCodeEvent
    // plugins
    data class UninstallPlugin(val name: String) : ClaudeCodeEvent
    // claude.md
    data object LoadClaudeMd : ClaudeCodeEvent
    data class UpdateClaudeMd(val content: String) : ClaudeCodeEvent
    data object SaveClaudeMd : ClaudeCodeEvent
    data object StartEditClaudeMd : ClaudeCodeEvent
    data object CancelEditClaudeMd : ClaudeCodeEvent
    // ~/.mcp.json migration
    data object DismissMcpMigration : ClaudeCodeEvent
    data object ConfirmMcpMigration : ClaudeCodeEvent
    // project claude.md CRUD
    data class ViewProjectClaudeMd(val project: ClaudeProject) : ClaudeCodeEvent
    data object DismissProjectClaudeMd : ClaudeCodeEvent
    data object StartEditProjectClaudeMd : ClaudeCodeEvent
    data class UpdateProjectClaudeMd(val content: String) : ClaudeCodeEvent
    data object SaveProjectClaudeMd : ClaudeCodeEvent
    data object CancelEditProjectClaudeMd : ClaudeCodeEvent
    data class CreateProjectClaudeMd(val project: ClaudeProject) : ClaudeCodeEvent
}

@HiltViewModel
class ClaudeCodeViewModel @Inject constructor(
    private val sshRepository: SshRepository,
    private val detectSystemUsers: DetectSystemUsersUseCase,
    private val detectClaudeCodeUsers: DetectClaudeCodeUsersUseCase,
    private val cacheManager: com.serverdash.app.core.cache.ScreenCacheManager
) : ViewModel() {

    private val _state = MutableStateFlow(ClaudeCodeUiState())
    val state: StateFlow<ClaudeCodeUiState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        detectClaude()
    }

    private fun invalidateCurrentTabCache() {
        when (_state.value.selectedTab) {
            0 -> cacheManager.invalidate(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_OVERVIEW)
            1 -> cacheManager.invalidate(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_MCP)
            2 -> cacheManager.invalidate(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_SETTINGS)
            3 -> cacheManager.invalidate(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_CLAUDE_MD)
        }
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
                            // Load users first, then MCP servers (which depend on user list)
                            loadUsersAndThen {
                                loadMcpServers()
                            }
                            loadOverview()
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
                _state.update { it.copy(selectedTab = event.index, activeDetail = null) }
                when (event.index) {
                    0 -> loadOverview(useCache = true)
                    1 -> loadMcpServers(useCache = true)
                    2 -> loadSettings(useCache = true)
                    3 -> loadClaudeMd(useCache = true)
                    4 -> loadProjects()
                }
            }
            is ClaudeCodeEvent.Refresh -> {
                invalidateCurrentTabCache()
                when (_state.value.selectedTab) {
                    0 -> loadOverview(useCache = false)
                    1 -> loadMcpServers(useCache = false)
                    2 -> loadSettings(useCache = false)
                    3 -> loadClaudeMd(useCache = false)
                    4 -> loadProjects()
                }
            }
            is ClaudeCodeEvent.DismissError -> _state.update { it.copy(error = null) }
            is ClaudeCodeEvent.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            is ClaudeCodeEvent.SelectUser -> selectUser(event.user)
            is ClaudeCodeEvent.RefreshUsers -> loadUsers()
            is ClaudeCodeEvent.LoadOverview -> loadOverview()
            is ClaudeCodeEvent.LoadProjects -> loadProjects()
            is ClaudeCodeEvent.ViewProjectMemory -> viewProjectMemory(event.project)
            is ClaudeCodeEvent.DismissProjectMemory -> _state.update { it.copy(selectedProjectMemory = null, selectedProjectName = null, selectedProjectPath = null, editingProjectMemory = false) }
            is ClaudeCodeEvent.StartEditProjectMemory -> _state.update { it.copy(editingProjectMemory = true, editedProjectMemory = it.selectedProjectMemory ?: "") }
            is ClaudeCodeEvent.UpdateEditedProjectMemory -> _state.update { it.copy(editedProjectMemory = event.content) }
            is ClaudeCodeEvent.CancelEditProjectMemory -> _state.update { it.copy(editingProjectMemory = false) }
            is ClaudeCodeEvent.SaveProjectMemory -> saveProjectMemory()
            // detail navigation
            is ClaudeCodeEvent.OpenDetail -> openDetail(event.view)
            is ClaudeCodeEvent.CloseDetail -> _state.update { it.copy(activeDetail = null) }
            // sessions
            is ClaudeCodeEvent.ViewSession -> viewSession(event.session)
            is ClaudeCodeEvent.DeleteSession -> deleteSession(event.session)
            is ClaudeCodeEvent.DismissSessionContent -> _state.update { it.copy(selectedSessionContent = null, selectedSessionName = null, sessionLines = emptyList(), sessionSearchQuery = "", sessionFilterRole = null) }
            is ClaudeCodeEvent.UpdateSessionSearch -> _state.update { it.copy(sessionSearchQuery = event.query) }
            is ClaudeCodeEvent.ToggleSessionPrettyPrint -> _state.update { it.copy(sessionPrettyPrint = !it.sessionPrettyPrint) }
            is ClaudeCodeEvent.FilterSessionRole -> _state.update { it.copy(sessionFilterRole = event.role) }
            // plans
            is ClaudeCodeEvent.ViewPlan -> viewPlan(event.plan)
            is ClaudeCodeEvent.DeletePlan -> deletePlan(event.plan)
            is ClaudeCodeEvent.DismissPlanContent -> _state.update { it.copy(selectedPlanContent = null, selectedPlanName = null) }
            // hooks
            is ClaudeCodeEvent.EditHook -> loadAndEditHook(event.hook)
            is ClaudeCodeEvent.ShowAddHook -> _state.update { it.copy(isAddingHook = true, editingHook = HookScript("", "", isNew = true)) }
            is ClaudeCodeEvent.SaveHook -> saveHook(event.originalName, event.hook)
            is ClaudeCodeEvent.DeleteHook -> deleteHook(event.hook)
            is ClaudeCodeEvent.DismissHookDialog -> _state.update { it.copy(editingHook = null, isAddingHook = false) }
            // skills
            is ClaudeCodeEvent.EditSkill -> loadAndEditSkill(event.skill)
            is ClaudeCodeEvent.ShowAddSkill -> _state.update { it.copy(isAddingSkill = true, editingSkill = SkillScript("", "", isNew = true)) }
            is ClaudeCodeEvent.SaveSkill -> saveSkill(event.originalName, event.skill)
            is ClaudeCodeEvent.DeleteSkill -> deleteSkill(event.skill)
            is ClaudeCodeEvent.DismissSkillDialog -> _state.update { it.copy(editingSkill = null, isAddingSkill = false) }
            // plugins
            is ClaudeCodeEvent.UninstallPlugin -> uninstallPlugin(event.name)
            is ClaudeCodeEvent.LoadMcpServers -> loadMcpServers()
            is ClaudeCodeEvent.ShowAddMcpServer -> _state.update { it.copy(isAddingMcpServer = true, editingMcpServer = McpServer()) }
            is ClaudeCodeEvent.EditMcpServer -> _state.update { it.copy(editingMcpServer = event.server, isAddingMcpServer = false) }
            is ClaudeCodeEvent.SaveMcpServer -> saveMcpServer(event.original, event.updated)
            is ClaudeCodeEvent.DeleteMcpServer -> deleteMcpServer(event.server)
            is ClaudeCodeEvent.DismissMcpDialog -> _state.update { it.copy(editingMcpServer = null, isAddingMcpServer = false) }
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
            is ClaudeCodeEvent.RequestSaveSettings -> {
                val diffs = computeDiff(_state.value.originalSettingsJson, _state.value.settingsJson)
                if (diffs.isEmpty()) {
                    _state.update { it.copy(successMessage = "No changes to save") }
                } else {
                    _state.update { it.copy(settingsDiff = diffs, showDiffDialog = true) }
                }
            }
            is ClaudeCodeEvent.ConfirmSaveSettings -> {
                _state.update { it.copy(showDiffDialog = false) }
                saveSettings()
            }
            is ClaudeCodeEvent.DismissDiffDialog -> {
                _state.update { it.copy(showDiffDialog = false) }
            }
            is ClaudeCodeEvent.LoadClaudeMd -> loadClaudeMd()
            is ClaudeCodeEvent.UpdateClaudeMd -> _state.update { it.copy(claudeMdContent = event.content) }
            is ClaudeCodeEvent.SaveClaudeMd -> saveClaudeMd()
            is ClaudeCodeEvent.StartEditClaudeMd -> _state.update { it.copy(editingClaudeMd = true) }
            is ClaudeCodeEvent.CancelEditClaudeMd -> {
                _state.update { it.copy(editingClaudeMd = false) }
                loadClaudeMd()
            }
            // ~/.mcp.json migration
            is ClaudeCodeEvent.DismissMcpMigration -> _state.update { it.copy(showMcpMigrationDialog = false) }
            is ClaudeCodeEvent.ConfirmMcpMigration -> migrateMcpJson()
            // project claude.md CRUD
            is ClaudeCodeEvent.ViewProjectClaudeMd -> loadProjectClaudeMd(event.project)
            is ClaudeCodeEvent.DismissProjectClaudeMd -> _state.update { it.copy(
                projectClaudeMdProject = null, projectClaudeMdContent = "", editingProjectClaudeMd = false
            )}
            is ClaudeCodeEvent.StartEditProjectClaudeMd -> _state.update { it.copy(editingProjectClaudeMd = true) }
            is ClaudeCodeEvent.UpdateProjectClaudeMd -> _state.update { it.copy(projectClaudeMdContent = event.content) }
            is ClaudeCodeEvent.SaveProjectClaudeMd -> saveProjectClaudeMd()
            is ClaudeCodeEvent.CancelEditProjectClaudeMd -> {
                _state.update { it.copy(editingProjectClaudeMd = false) }
                _state.value.projectClaudeMdProject?.let { loadProjectClaudeMd(it) }
            }
            is ClaudeCodeEvent.CreateProjectClaudeMd -> {
                _state.update { it.copy(
                    projectClaudeMdProject = event.project,
                    projectClaudeMdContent = "# ${event.project.displayName}\n\n",
                    editingProjectClaudeMd = true
                )}
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

    private fun computeDiff(original: String, current: String): List<SettingsDiffEntry> {
        val origObj = try { Json.parseToJsonElement(original).jsonObject } catch (e: Exception) { JsonObject(emptyMap()) }
        val currObj = try { Json.parseToJsonElement(current).jsonObject } catch (e: Exception) { JsonObject(emptyMap()) }
        return diffJsonObjects("", origObj, currObj)
    }

    private fun diffJsonObjects(prefix: String, old: JsonObject, new: JsonObject): List<SettingsDiffEntry> {
        val diffs = mutableListOf<SettingsDiffEntry>()
        val allKeys = (old.keys + new.keys).toSet()
        for (key in allKeys) {
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            val oldVal = old[key]
            val newVal = new[key]
            when {
                oldVal == null && newVal != null -> diffs.add(SettingsDiffEntry(path, null, newVal.toString(), DiffType.ADDED))
                oldVal != null && newVal == null -> diffs.add(SettingsDiffEntry(path, oldVal.toString(), null, DiffType.REMOVED))
                oldVal is JsonObject && newVal is JsonObject -> diffs.addAll(diffJsonObjects(path, oldVal, newVal))
                oldVal != newVal -> diffs.add(SettingsDiffEntry(path, oldVal.toString(), newVal.toString(), DiffType.CHANGED))
            }
        }
        return diffs
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
        loadUsersAndThen { }
    }

    private fun loadUsersAndThen(onComplete: () -> Unit) {
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
            onComplete()
        }
    }

    private fun selectUser(user: SystemUser) {
        _state.update { it.copy(selectedUser = user) }
        // Reload all data for the new user
        loadOverview()
        loadMcpServers()
        loadSettings()
        loadClaudeMd()
    }

    private fun loadMcpServers(useCache: Boolean = false) {
        viewModelScope.launch {
            if (useCache) {
                val cached: List<McpServer>? = cacheManager.get(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_MCP)
                if (cached != null) {
                    _state.update { it.copy(mcpServers = cached, isLoadingMcp = false) }
                    return@launch
                }
            }
            _state.update { it.copy(isLoadingMcp = true, mcpDebugInfo = "", mcpScanProgress = "Preparing scan…") }
            val debug = StringBuilder()
            try {
                val allServers = mutableMapOf<String, McpServer>()

                debug.appendLine("connectedUsername=${connectedUsername}")
                debug.appendLine("rootAccess=${sshRepository.hasRootAccess()}")
                debug.appendLine("claudeCodeUsers=${_state.value.claudeCodeUsers.map { "${it.username}@${it.homeDirectory}" }}")
                debug.appendLine("selectedUser=${_state.value.selectedUser?.username}")

                // Scan all Claude Code users — always include root and connected user
                val rootUser = SystemUser(username = "root", homeDirectory = "/root", uid = 0, hasClaudeCode = true)
                val usersToScan = buildList {
                    addAll(_state.value.claudeCodeUsers)
                    // Always include the connected user even if Claude detection missed them
                    val connected = connectedUsername
                    if (connected != null && none { it.username == connected }) {
                        val connUser = _state.value.systemUsers.find { it.username == connected }
                            ?: SystemUser(username = connected, homeDirectory = "/home/$connected", uid = -1, hasClaudeCode = false)
                        add(connUser)
                    }
                    _state.value.selectedUser?.let { sel ->
                        if (none { it.username == sel.username }) add(sel)
                    }
                    // Always ensure root is scanned for MCP configs
                    if (none { it.username == "root" }) add(rootUser)
                }.distinctBy { it.username }

                debug.appendLine("usersToScan=${usersToScan.map { "${it.username}@${it.homeDirectory}" }}")

                for ((idx, user) in usersToScan.withIndex()) {
                    val label = user.username
                    _state.update { it.copy(mcpScanProgress = "Scanning $label (${idx + 1}/${usersToScan.size})…") }

                    // Per official docs, MCP configs live in:
                    // 1. ~/.claude.json → top-level "mcpServers" (user-scoped)
                    // 2. ~/.claude.json → projects.<path>.mcpServers (local/project-scoped)
                    // 3. <project-dir>/.mcp.json → "mcpServers" (project-scoped, committed to VCS)

                    val rawClaudeJson = readFileForUser(user, ".claude.json")
                    debug.appendLine("[$label] .claude.json read: ${if (rawClaudeJson != null) "${rawClaudeJson.length} chars" else "NULL"}")
                    val claudeJsonContent = rawClaudeJson?.trim()?.ifBlank { "{}" } ?: "{}"
                    try {
                        val claudeRoot = Json.parseToJsonElement(claudeJsonContent).jsonObject
                        val topKeys = claudeRoot.keys.take(10)
                        debug.appendLine("[$label] .claude.json keys: $topKeys")

                        // 1. User-scoped: ~/.claude.json top-level mcpServers
                        val topMcp = claudeRoot["mcpServers"]?.jsonObject
                        debug.appendLine("[$label] top-level mcpServers: ${topMcp?.keys?.toList() ?: "null"}")
                        topMcp?.let { mcpObj ->
                            parseMcpObject(mcpObj).forEach { server ->
                                val key = "${label}:${server.name}"
                                allServers[key] = server.copy(source = "$label:~/.claude.json")
                            }
                        }

                        // 2. Local/project-scoped: ~/.claude.json projects.<path>.mcpServers
                        // Also collect project paths for scanning project-dir .mcp.json
                        val projectPaths = mutableListOf<String>()
                        claudeRoot["projects"]?.jsonObject?.entries?.forEach { (projectKey, projectVal) ->
                            try {
                                val projectObj = projectVal.jsonObject

                                // Collect enabled/disabled .mcp.json server toggles
                                val enabledFromMcpJson = projectObj["enabledMcpjsonServers"]?.jsonArray
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                                val disabledFromMcpJson = projectObj["disabledMcpjsonServers"]?.jsonArray
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                                // Local-scoped MCP servers for this project
                                projectObj["mcpServers"]?.jsonObject?.let { mcpObj ->
                                    parseMcpObject(mcpObj).forEach { server ->
                                        val key = "${label}:${server.name}:$projectKey"
                                        if (key !in allServers) {
                                            allServers[key] = server.copy(source = "$label:project:$projectKey")
                                        }
                                    }
                                }

                                // Decode project path from key (e.g. "-home-matt-fleet" → "/home/matt/fleet")
                                val decodedPath = projectKey.replace("-", "/")
                                    .replaceFirst("/", "") // remove leading empty segment
                                    .let { "/$it" }
                                projectPaths.add(decodedPath)

                                // 3. Project-scoped: <project-dir>/.mcp.json
                                readFileAbsolute(user, "$decodedPath/.mcp.json")?.let { content ->
                                    val jsonStr = content.trim().ifBlank { "{}" }
                                    val mcpRoot = try { Json.parseToJsonElement(jsonStr).jsonObject } catch (_: Exception) { null }
                                    // .mcp.json can have mcpServers key OR be flat (server entries at root)
                                    val mcpObj = mcpRoot?.get("mcpServers")?.jsonObject ?: mcpRoot
                                    if (mcpObj != null) {
                                        parseMcpObject(mcpObj).forEach { server ->
                                            val key = "${label}:${server.name}:mcp:$projectKey"
                                            if (key !in allServers) {
                                                // Apply enabled/disabled toggles from ~/.claude.json
                                                val isEnabled = when {
                                                    server.name in disabledFromMcpJson -> false
                                                    enabledFromMcpJson.isNotEmpty() -> server.name in enabledFromMcpJson
                                                    else -> true
                                                }
                                                allServers[key] = server.copy(
                                                    source = "$label:.mcp.json:$projectKey",
                                                    enabled = isEnabled
                                                )
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                    } catch (e: Exception) {
                        debug.appendLine("[$label] .claude.json parse error: ${e.message}")
                    }

                    // Also check ~/.mcp.json as a fallback (non-standard location)
                    // If servers found here that aren't already in ~/.claude.json, show migration dialog
                    readFileForUser(user, ".mcp.json")?.let { content ->
                        val jsonStr = content.trim().ifBlank { "{}" }
                        val homeMcpServers = parseMcpServersFromJson(jsonStr)
                        // Only flag servers not already present in ~/.claude.json
                        val newServers = homeMcpServers.filter { server ->
                            val key = "${label}:${server.name}"
                            key !in allServers
                        }
                        newServers.forEach { server ->
                            val key = "${label}:${server.name}"
                            allServers[key] = server.copy(source = "$label:~/.mcp.json")
                        }
                        if (newServers.isNotEmpty()) {
                            _state.update { it.copy(
                                showMcpMigrationDialog = true,
                                mcpMigrationUser = label,
                                mcpMigrationSystemUser = user,
                                mcpMigrationServers = newServers
                            )}
                        }
                    }

                    // Emit servers incrementally as each user is scanned
                    if (allServers.isNotEmpty()) {
                        _state.update { it.copy(mcpServers = allServers.values.toList()) }
                    }
                }

                // Fallback: single-command scan that finds AND reads all config files at once
                if (allServers.isEmpty()) {
                    _state.update { it.copy(mcpScanProgress = "Scanning for config files…") }
                    debug.appendLine("No servers from user scan, running fast find+read...")
                    // Single SSH call: find files (shallow, skip junk dirs) and cat them with delimiters
                    val findReadCmd = """find /home /root -maxdepth 3 \( -name node_modules -o -name .git -o -name .npm -o -name .cache -o -name .local -o -name .nvm -o -name .cargo -o -name .rustup -o -name .gradle -o -name .m2 -o -name vendor -o -name dist -o -name build -o -name target -o -name __pycache__ -o -name venv -o -name .venv -o -name .next -o -name .nuxt -o -name log -o -name logs -o -name tmp \) -prune -o \( -name '.claude.json' -o -name '.mcp.json' \) -type f -exec sh -c 'for f; do echo "===FILE:${'$'}f==="; cat "${'$'}f" 2>/dev/null; done' _ {} + 2>/dev/null"""
                    val output = sshRepository.executeSudoCommand(findReadCmd).getOrNull()?.output?.trim()
                    debug.appendLine("find+read output: ${if (output != null) "${output.length} chars" else "NULL"}")
                    if (!output.isNullOrBlank()) {
                        // Parse delimited output: ===FILE:/path=== followed by file content
                        val fileRegex = Regex("===FILE:(.+?)===")
                        val sections = output.split(fileRegex)
                        val paths = fileRegex.findAll(output).map { it.groupValues[1] }.toList()
                        // sections[0] is before first match (empty), sections[1..] are file contents
                        paths.forEachIndexed { i, filePath ->
                            val content = sections.getOrNull(i + 1)?.trim()
                            debug.appendLine("  $filePath: ${content?.length ?: 0} chars")
                            if (!content.isNullOrBlank()) {
                                val servers = parseMcpServersFromJson(content)
                                servers.forEach { server ->
                                    val key = "found:${server.name}:$filePath"
                                    if (key !in allServers) {
                                        allServers[key] = server.copy(source = filePath)
                                    }
                                }
                            }
                        }
                    }
                }

                debug.appendLine("Total servers found: ${allServers.size}")
                val serverList = allServers.values.toList()
                cacheManager.put(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_MCP, serverList)
                _state.update { it.copy(mcpServers = serverList, isLoadingMcp = false, mcpScanProgress = "", mcpDebugInfo = debug.toString()) }
            } catch (e: Exception) {
                debug.appendLine("EXCEPTION: ${e.message}")
                _state.update { it.copy(isLoadingMcp = false, mcpScanProgress = "", error = e.message, mcpDebugInfo = debug.toString()) }
            }
        }
    }

    /** Read a file from a specific user's home directory */
    private suspend fun readFileForUser(user: SystemUser, relativePath: String): String? {
        val fullPath = "${user.homeDirectory}/$relativePath"
        return readFileWithSudoFallback(user, fullPath)
    }

    /** Read a file at an absolute path, using sudo if the user differs from connected user */
    private suspend fun readFileAbsolute(user: SystemUser, absolutePath: String): String? {
        return readFileWithSudoFallback(user, absolutePath)
    }

    /** Read a file, using sudo for other users' files. Tries sudo first, then direct read. */
    private suspend fun readFileWithSudoFallback(user: SystemUser, path: String): String? {
        return if (user.username == connectedUsername) {
            // Direct read for connected user's own files
            sshRepository.executeCommand("cat \"$path\" 2>/dev/null").getOrNull()?.output?.takeIf { it.isNotBlank() }
        } else {
            // For other users: always use sudo to read (files may be 600 perms)
            val result = sshRepository.executeSudoCommand("cat \"$path\"")
            val output = result.getOrNull()?.output?.trim()
            // If sudo failed or returned auth error, return null
            if (output.isNullOrBlank() || output.contains("Permission denied") || output.contains("Sudo authentication failed")) null else output
        }
    }

    private fun parseMcpServersFromJson(jsonStr: String): List<McpServer> {
        return try {
            val root = Json.parseToJsonElement(jsonStr).jsonObject
            val mcpObj = root["mcpServers"]?.jsonObject ?: return emptyList()
            parseMcpObject(mcpObj)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseMcpObject(mcpObj: JsonObject): List<McpServer> {
        return mcpObj.entries.mapNotNull { (name, value) ->
            try {
                val obj = value.jsonObject
                val serverType = obj["type"]?.jsonPrimitive?.content ?: "stdio"
                McpServer(
                    name = name,
                    type = serverType,
                    command = obj["command"]?.jsonPrimitive?.content ?: "",
                    args = obj["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    env = obj["env"]?.jsonObject?.entries?.associate { (k, v) -> k to v.jsonPrimitive.content } ?: emptyMap(),
                    url = obj["url"]?.jsonPrimitive?.content ?: "",
                    headers = obj["headers"]?.jsonObject?.entries?.associate { (k, v) -> k to v.jsonPrimitive.content } ?: emptyMap()
                )
            } catch (_: Exception) { null }
        }
    }

    private fun saveMcpServer(original: McpServer?, updated: McpServer) {
        viewModelScope.launch {
            try {
                // Per official docs, user-scoped MCP servers go in ~/.claude.json
                val currentJson = readClaudeFile(".claude.json").getOrNull()?.trim()?.ifBlank { "{}" } ?: "{}"
                val root = try { Json.parseToJsonElement(currentJson).jsonObject.toMutableMap() } catch (e: Exception) { mutableMapOf() }

                val mcpObj = (root["mcpServers"]?.jsonObject?.toMutableMap() ?: mutableMapOf())

                // remove old entry if renaming
                if (original != null && original.name != updated.name && original.name.isNotBlank()) {
                    mcpObj.remove(original.name)
                }

                // add/update entry — support both stdio and http/sse
                val serverObj = buildJsonObject {
                    if (updated.type != "stdio") put("type", updated.type)
                    if (updated.command.isNotBlank()) put("command", updated.command)
                    if (updated.args.isNotEmpty()) putJsonArray("args") { updated.args.forEach { add(it) } }
                    if (updated.env.isNotEmpty()) putJsonObject("env") { updated.env.forEach { (k, v) -> put(k, v) } }
                    if (updated.url.isNotBlank()) put("url", updated.url)
                    if (updated.headers.isNotEmpty()) putJsonObject("headers") { updated.headers.forEach { (k, v) -> put(k, v) } }
                }
                mcpObj[updated.name] = serverObj

                root["mcpServers"] = JsonObject(mcpObj)
                val newJson = json.encodeToString(JsonObject.serializer(), JsonObject(root))

                writeClaudeFile(".claude.json", newJson)
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
                // Determine which file the server is from based on source
                val isFromHomeMcp = server.source.contains("~/.mcp.json")
                val configFile = if (isFromHomeMcp) ".mcp.json" else ".claude.json"

                val currentJson = readClaudeFile(configFile).getOrNull()?.trim()?.ifBlank { "{}" } ?: "{}"
                val root = try { Json.parseToJsonElement(currentJson).jsonObject.toMutableMap() } catch (e: Exception) { mutableMapOf() }

                val mcpObj = (root["mcpServers"]?.jsonObject?.toMutableMap() ?: mutableMapOf())
                mcpObj.remove(server.name)
                root["mcpServers"] = JsonObject(mcpObj)

                val newJson = json.encodeToString(JsonObject.serializer(), JsonObject(root))
                writeClaudeFile(configFile, newJson)
                _state.update { it.copy(successMessage = "MCP server '${server.name}' deleted") }
                loadMcpServers()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to delete: ${e.message}") }
            }
        }
    }

    /** Migrate servers from non-standard ~/.mcp.json into ~/.claude.json */
    private fun migrateMcpJson() {
        viewModelScope.launch {
            _state.update { it.copy(isMigrating = true) }
            try {
                // Use the stored migration user — NOT selectedUser (they can differ)
                val migrationUser = _state.value.mcpMigrationSystemUser
                    ?: _state.value.selectedUser
                    ?: return@launch

                // Read ~/.mcp.json servers
                val mcpJsonContent = readFileForUser(migrationUser, ".mcp.json")?.trim() ?: "{}"
                val mcpServers = parseMcpServersFromJson(mcpJsonContent)

                if (mcpServers.isEmpty()) {
                    // File exists but empty — just delete it
                    removeMcpJsonFile(migrationUser)
                    _state.update { it.copy(showMcpMigrationDialog = false, isMigrating = false) }
                    return@launch
                }

                // Read current ~/.claude.json for the migration user
                val claudeJsonPath = "${migrationUser.homeDirectory}/.claude.json"
                val claudeJsonContent = readFileWithSudoFallback(migrationUser, claudeJsonPath)?.trim()?.ifBlank { "{}" } ?: "{}"
                val claudeRoot = try { Json.parseToJsonElement(claudeJsonContent).jsonObject.toMutableMap() } catch (_: Exception) { mutableMapOf() }
                val existingMcp = claudeRoot["mcpServers"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

                // Merge servers into ~/.claude.json
                mcpServers.forEach { server ->
                    if (server.name !in existingMcp) {
                        existingMcp[server.name] = buildJsonObject {
                            if (server.type != "stdio") put("type", server.type)
                            if (server.command.isNotBlank()) put("command", server.command)
                            if (server.args.isNotEmpty()) putJsonArray("args") { server.args.forEach { add(it) } }
                            if (server.env.isNotEmpty()) putJsonObject("env") { server.env.forEach { (k, v) -> put(k, v) } }
                            if (server.url.isNotBlank()) put("url", server.url)
                            if (server.headers.isNotEmpty()) putJsonObject("headers") { server.headers.forEach { (k, v) -> put(k, v) } }
                        }
                    }
                }
                claudeRoot["mcpServers"] = JsonObject(existingMcp)
                val newClaudeJson = json.encodeToString(JsonObject.serializer(), JsonObject(claudeRoot))

                // Write the merged file for the correct user
                val writeCmd = "mkdir -p ${migrationUser.homeDirectory}/.claude && cat > '$claudeJsonPath' << 'SERVERDASH_EOF'\n$newClaudeJson\nSERVERDASH_EOF"
                if (migrationUser.username == connectedUsername) {
                    sshRepository.executeCommand(writeCmd)
                } else {
                    sshRepository.executeAsUser(writeCmd, migrationUser.username)
                }

                // Remove ~/.mcp.json and verify it's gone
                removeMcpJsonFile(migrationUser)

                _state.update { it.copy(
                    showMcpMigrationDialog = false,
                    isMigrating = false,
                    mcpMigrationSystemUser = null,
                    successMessage = "Migrated ${mcpServers.size} servers to ~/.claude.json"
                )}
                loadMcpServers()
            } catch (e: Exception) {
                _state.update { it.copy(isMigrating = false, error = "Migration failed: ${e.message}") }
            }
        }
    }

    /** Remove ~/.mcp.json for a specific user, with verification */
    private suspend fun removeMcpJsonFile(user: SystemUser) {
        val mcpPath = "${user.homeDirectory}/.mcp.json"
        val rmCmd = "rm -f '$mcpPath'"
        if (user.username == connectedUsername) {
            sshRepository.executeCommand(rmCmd)
        } else {
            sshRepository.executeSudoCommand(rmCmd)
        }
    }

    /** Load claude.md from a project directory */
    private fun loadProjectClaudeMd(project: ClaudeProject) {
        viewModelScope.launch {
            _state.update { it.copy(
                projectClaudeMdProject = project,
                isLoadingProjectClaudeMd = true,
                editingProjectClaudeMd = false
            )}
            try {
                val user = _state.value.selectedUser ?: return@launch
                val projectDir = project.displayName // decoded path like /home/matt/fleet
                val content = readFileAbsolute(user, "$projectDir/CLAUDE.md") ?: ""
                _state.update { it.copy(
                    projectClaudeMdContent = content,
                    isLoadingProjectClaudeMd = false
                )}
            } catch (e: Exception) {
                _state.update { it.copy(
                    isLoadingProjectClaudeMd = false,
                    error = "Failed to load CLAUDE.md: ${e.message}"
                )}
            }
        }
    }

    /** Save claude.md to a project directory */
    private fun saveProjectClaudeMd() {
        viewModelScope.launch {
            val project = _state.value.projectClaudeMdProject ?: return@launch
            _state.update { it.copy(isSavingProjectClaudeMd = true) }
            try {
                val user = _state.value.selectedUser ?: return@launch
                val projectDir = project.displayName
                val content = _state.value.projectClaudeMdContent
                val fullPath = "$projectDir/CLAUDE.md"

                if (user.username == connectedUsername) {
                    sshRepository.executeCommand("cat > \"$fullPath\" << 'SERVERDASH_EOF'\n$content\nSERVERDASH_EOF")
                } else {
                    // Write via sudo for other users
                    sshRepository.executeSudoCommand("tee \"$fullPath\" > /dev/null << 'SERVERDASH_EOF'\n$content\nSERVERDASH_EOF")
                }

                _state.update { it.copy(
                    isSavingProjectClaudeMd = false,
                    editingProjectClaudeMd = false,
                    successMessage = "CLAUDE.md saved"
                )}
            } catch (e: Exception) {
                _state.update { it.copy(
                    isSavingProjectClaudeMd = false,
                    error = "Failed to save CLAUDE.md: ${e.message}"
                )}
            }
        }
    }

    private fun loadSettings(useCache: Boolean = false) {
        viewModelScope.launch {
            if (useCache) {
                val cached: String? = cacheManager.get(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_SETTINGS)
                if (cached != null) {
                    _state.update { it.copy(settingsJson = cached, originalSettingsJson = cached, isLoadingSettings = false, editingSettings = false) }
                    return@launch
                }
            }
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
                        cacheManager.put(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_SETTINGS, formatted)
                        _state.update { it.copy(settingsJson = formatted, originalSettingsJson = formatted, isLoadingSettings = false, editingSettings = false) }
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
                _state.update { it.copy(isSavingSettings = false, editingSettings = false, originalSettingsJson = content, successMessage = "Settings saved") }
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

    private fun loadClaudeMd(useCache: Boolean = false) {
        viewModelScope.launch {
            if (useCache) {
                val cached: String? = cacheManager.get(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_CLAUDE_MD)
                if (cached != null) {
                    _state.update { it.copy(claudeMdContent = cached, isLoadingClaudeMd = false, editingClaudeMd = false) }
                    return@launch
                }
            }
            _state.update { it.copy(isLoadingClaudeMd = true) }
            try {
                val result = readClaudeFile(".claude/CLAUDE.md")
                result.fold(
                    onSuccess = { content ->
                        cacheManager.put(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_CLAUDE_MD, content)
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

    private fun parseUsageStats(statsJson: String): ClaudeCodeUsage? {
        if (statsJson.isBlank()) return null
        return try {
            val obj = Json.parseToJsonElement(statsJson).jsonObject
            val rawEntries = mutableMapOf<String, String>()
            obj.entries.forEach { (k, v) ->
                if (v is JsonPrimitive) rawEntries[k] = v.content
                else rawEntries[k] = v.toString()
            }
            ClaudeCodeUsage(
                totalTokens = obj["total_tokens"]?.jsonPrimitive?.longOrNull
                    ?: obj["totalTokens"]?.jsonPrimitive?.longOrNull ?: 0,
                totalCost = obj["total_cost"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["totalCost"]?.jsonPrimitive?.doubleOrNull
                    ?: obj["cost"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                sessions = obj["sessions"]?.jsonPrimitive?.intOrNull
                    ?: obj["session_count"]?.jsonPrimitive?.intOrNull
                    ?: obj["sessionCount"]?.jsonPrimitive?.intOrNull ?: 0,
                inputTokens = obj["input_tokens"]?.jsonPrimitive?.longOrNull
                    ?: obj["inputTokens"]?.jsonPrimitive?.longOrNull ?: 0,
                outputTokens = obj["output_tokens"]?.jsonPrimitive?.longOrNull
                    ?: obj["outputTokens"]?.jsonPrimitive?.longOrNull ?: 0,
                cacheReadTokens = obj["cache_read_tokens"]?.jsonPrimitive?.longOrNull
                    ?: obj["cacheReadTokens"]?.jsonPrimitive?.longOrNull ?: 0,
                cacheWriteTokens = obj["cache_write_tokens"]?.jsonPrimitive?.longOrNull
                    ?: obj["cacheWriteTokens"]?.jsonPrimitive?.longOrNull ?: 0,
                rawEntries = rawEntries
            )
        } catch (e: Exception) { null }
    }

    private fun loadOverview(useCache: Boolean = false) {
        viewModelScope.launch {
            if (useCache && !_state.value.isLoadingOverview && _state.value.diskUsage.isNotBlank()) {
                // Overview data already in state from a recent load, check if cache is valid
                val cached: Boolean? = cacheManager.get(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_OVERVIEW)
                if (cached == true) return@launch
            }
            _state.update { it.copy(isLoadingOverview = true) }
            try {
                val cmd = buildString {
                    append("echo '===DU==='; du -sh ~/.claude 2>/dev/null | awk '{print \$1}'; ")
                    append("echo '===STATS==='; cat ~/.claude/cc-counter/stats.json 2>/dev/null; ")
                    append("echo '===PROJECTS==='; ls -1 ~/.claude/projects/ 2>/dev/null | wc -l; ")
                    append("echo '===PLANS==='; ls -1 ~/.claude/plans/ 2>/dev/null | wc -l; ")
                    append("echo '===SESSIONS==='; find ~/.claude/projects/ -name '*.jsonl' 2>/dev/null | wc -l; ")
                    append("echo '===PLUGINS==='; cat ~/.claude/settings.json 2>/dev/null | grep -o '\"enabledPlugins\"[^}]*}' 2>/dev/null; ")
                    append("echo '===SKILLS==='; ls -1d ~/.claude/skills/*/ 2>/dev/null | xargs -I{} basename {}; ")
                    append("echo '===HOOKS==='; cat ~/.claude/settings.json 2>/dev/null | grep -c '\"hooks\"' 2>/dev/null; ")
                    append("echo '===ACTIVITY==='; for d in ~/.claude/projects/*/; do name=\$(basename \"\$d\"); count=\$(ls \"\$d\"/*.jsonl 2>/dev/null | wc -l); size=\$(du -sh \"\$d\" 2>/dev/null | awk '{print \$1}'); latest=\$(find \"\$d\" -name '*.jsonl' -printf '%T@\\n' 2>/dev/null | sort -rn | head -1); echo \"\$name|\$count|\$size|\${latest:-0}\"; done 2>/dev/null")
                }
                val result = executeForUser(cmd)
                result.fold(
                    onSuccess = { cmdResult ->
                        val sections = cmdResult.output.split("===DU===", "===STATS===", "===PROJECTS===", "===PLANS===", "===SESSIONS===", "===PLUGINS===", "===SKILLS===", "===HOOKS===", "===ACTIVITY===")
                        val diskUsage = sections.getOrNull(1)?.trim() ?: ""
                        val stats = sections.getOrNull(2)?.trim() ?: ""
                        val projectCount = sections.getOrNull(3)?.trim()?.toIntOrNull() ?: 0
                        val planCount = sections.getOrNull(4)?.trim()?.toIntOrNull() ?: 0
                        val sessionCount = sections.getOrNull(5)?.trim()?.toIntOrNull() ?: 0
                        val pluginsJson = sections.getOrNull(6)?.trim() ?: ""
                        val skills = sections.getOrNull(7)?.trim()?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                        val hooksCount = sections.getOrNull(8)?.trim()?.toIntOrNull() ?: 0
                        val hooks = if (hooksCount > 0) listOf("configured in settings.json") else emptyList()
                        val activityRaw = sections.getOrNull(9)?.trim() ?: ""

                        val plugins = try {
                            // Parse enabledPlugins from settings.json grep output
                            // Format: "enabledPlugins": {"name@marketplace": true, ...}
                            val jsonStr = "{${pluginsJson}}"
                            val obj = Json.parseToJsonElement(jsonStr).jsonObject
                            val enabled = obj["enabledPlugins"]?.jsonObject ?: JsonObject(emptyMap())
                            enabled.entries.filter { it.value.jsonPrimitive.boolean }.map { it.key }
                        } catch (e: Exception) { emptyList() }

                        val parsedUsage = parseUsageStats(stats)

                        val sessionActivities = activityRaw.lines()
                            .filter { it.contains("|") }
                            .mapNotNull { line ->
                                val parts = line.split("|", limit = 4)
                                if (parts.size < 3) return@mapNotNull null
                                val name = parts[0].trim()
                                if (name.isBlank()) return@mapNotNull null
                                SessionActivity(
                                    projectName = name,
                                    sessionCount = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
                                    totalSize = parts.getOrNull(2)?.trim() ?: "0",
                                    lastModifiedEpoch = parts.getOrNull(3)?.trim()?.toDoubleOrNull()?.toLong() ?: 0
                                )
                            }
                            .filter { it.sessionCount > 0 }
                            .sortedByDescending { it.lastModifiedEpoch }

                        cacheManager.put(com.serverdash.app.core.cache.ScreenCacheManager.CLAUDE_CODE_OVERVIEW, true)
                        _state.update { it.copy(
                            diskUsage = diskUsage,
                            usageStats = stats,
                            parsedUsage = parsedUsage,
                            sessionActivity = sessionActivities,
                            projectCount = projectCount,
                            planCount = planCount,
                            sessionCount = sessionCount,
                            installedPlugins = plugins,
                            customSkills = skills,
                            hookFiles = hooks,
                            isLoadingOverview = false
                        )}
                    },
                    onFailure = { _state.update { it.copy(isLoadingOverview = false) } }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingOverview = false) }
            }
        }
    }

    private fun loadProjects() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingProjects = true) }
            try {
                // Use the .active_project_path marker files or decode via test -d to find the real path
                val cmd = "for d in ~/.claude/projects/*/; do name=\$(basename \"\$d\"); sessions=\$(ls \"\$d\"/*.jsonl 2>/dev/null | wc -l); mem=\$(test -f \"\$d/memory/MEMORY.md\" && echo Y || echo N); realpath=\$(cat \"\$d/.active_project_path\" 2>/dev/null || echo \"\"); echo \"\$name|\$sessions|\$mem|\$realpath\"; done 2>/dev/null"
                val result = executeForUser(cmd)
                result.fold(
                    onSuccess = { cmdResult ->
                        val projects = cmdResult.output.lines()
                            .filter { it.contains("|") }
                            .map { line ->
                                val parts = line.split("|", limit = 4)
                                val rawName = parts[0].trim()
                                val realPath = parts.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }
                                // Use the real path if available, otherwise show the raw dir name
                                val displayName = realPath ?: rawName
                                ClaudeProject(
                                    path = rawName,
                                    displayName = displayName,
                                    sessionCount = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
                                    hasMemory = parts.getOrNull(2)?.trim() == "Y"
                                )
                            }
                            .sortedByDescending { it.sessionCount }
                        _state.update { it.copy(projects = projects, isLoadingProjects = false) }
                    },
                    onFailure = { _state.update { it.copy(isLoadingProjects = false) } }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingProjects = false) }
            }
        }
    }

    private fun viewProjectMemory(project: ClaudeProject) {
        viewModelScope.launch {
            val memoryPath = ".claude/projects/${project.path}/memory/MEMORY.md"
            val result = readClaudeFile(memoryPath)
            result.fold(
                onSuccess = { content -> _state.update { it.copy(
                    selectedProjectMemory = content,
                    selectedProjectName = project.displayName,
                    selectedProjectPath = project.path,
                    editingProjectMemory = false
                ) } },
                onFailure = { _state.update { it.copy(error = "Could not read project memory") } }
            )
        }
    }

    private fun saveProjectMemory() {
        val path = _state.value.selectedProjectPath ?: return
        val content = _state.value.editedProjectMemory
        val memoryPath = ".claude/projects/$path/memory/MEMORY.md"
        viewModelScope.launch {
            _state.update { it.copy(isSavingProjectMemory = true) }
            val escaped = content.replace("'", "'\\''")
            val result = executeForUser("cat > ~/$memoryPath << 'SERVERDASH_EOF'\n$escaped\nSERVERDASH_EOF")
            result.fold(
                onSuccess = {
                    _state.update { it.copy(
                        selectedProjectMemory = content,
                        editingProjectMemory = false,
                        isSavingProjectMemory = false,
                        successMessage = "Memory saved"
                    ) }
                },
                onFailure = {
                    _state.update { it.copy(isSavingProjectMemory = false, error = "Failed to save memory") }
                }
            )
        }
    }

    // ── Detail navigation ──────────────────────────────────────────────

    private fun openDetail(view: DetailView) {
        _state.update { it.copy(activeDetail = view, isLoadingDetail = true) }
        when (view) {
            DetailView.STORAGE -> loadStorageDetail()
            DetailView.PROJECTS -> { loadProjects(); _state.update { it.copy(isLoadingDetail = false) } }
            DetailView.SESSIONS -> loadSessionsDetail()
            DetailView.PLANS -> loadPlansDetail()
            DetailView.PLUGINS -> { _state.update { it.copy(isLoadingDetail = false) } } // already loaded
            DetailView.HOOKS -> loadHookScripts()
            DetailView.SKILLS -> loadSkillScripts()
            DetailView.USAGE -> { _state.update { it.copy(isLoadingDetail = false) } } // already loaded in overview
        }
    }

    // ── Storage breakdown ──────────────────────────────────────────────

    private fun loadStorageDetail() {
        viewModelScope.launch {
            try {
                val cmd = buildString {
                    append("echo '===TOTAL==='; du -sh ~/.claude 2>/dev/null | awk '{print \$1}'; ")
                    append("echo '===PROJECTS==='; du -sh ~/.claude/projects 2>/dev/null | awk '{print \$1}'; ")
                    append("echo '===PLANS==='; du -sh ~/.claude/plans 2>/dev/null | awk '{print \$1}'; ")
                    append("echo '===HOOKS==='; du -sh ~/.claude/hooks 2>/dev/null | awk '{print \$1}'; ")
                    append("echo '===SKILLS==='; du -sh ~/.claude/skills 2>/dev/null | awk '{print \$1}'; ")
                    append("echo '===PLUGINS==='; du -sh ~/.claude/plugins 2>/dev/null | awk '{print \$1}'; ")
                    append("echo '===STATS==='; du -sh ~/.claude/cc-counter 2>/dev/null | awk '{print \$1}'; ")
                    append("echo '===CONFIG==='; du -sh ~/.claude/settings.json ~/.claude/CLAUDE.md ~/.mcp.json 2>/dev/null | awk '{total+=\$1} END{print total\"K\"}'")
                }
                val result = executeForUser(cmd)
                result.fold(
                    onSuccess = { cmdResult ->
                        val sections = cmdResult.output.split("===TOTAL===", "===PROJECTS===", "===PLANS===", "===HOOKS===", "===SKILLS===", "===PLUGINS===", "===STATS===", "===CONFIG===")
                        val items = listOf(
                            StorageItem("Total", sections.getOrNull(1)?.trim() ?: "?", "~/.claude"),
                            StorageItem("Projects & Sessions", sections.getOrNull(2)?.trim() ?: "0", "~/.claude/projects/"),
                            StorageItem("Plans", sections.getOrNull(3)?.trim() ?: "0", "~/.claude/plans/"),
                            StorageItem("Hooks (in settings.json)", sections.getOrNull(4)?.trim() ?: "0", "~/.claude/settings.json"),
                            StorageItem("Custom Skills", sections.getOrNull(5)?.trim() ?: "0", "~/.claude/skills/"),
                            StorageItem("Plugins", sections.getOrNull(6)?.trim() ?: "0", "~/.claude/plugins/"),
                            StorageItem("Usage Stats", sections.getOrNull(7)?.trim() ?: "0", "~/.claude/cc-counter/"),
                            StorageItem("Config Files", sections.getOrNull(8)?.trim() ?: "0", "~/.claude/*.json")
                        )
                        _state.update { it.copy(storageBreakdown = items, isLoadingDetail = false) }
                    },
                    onFailure = { _state.update { it.copy(isLoadingDetail = false, error = "Failed to load storage info") } }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingDetail = false, error = e.message) }
            }
        }
    }

    // ── Sessions ───────────────────────────────────────────────────────

    private fun loadSessionsDetail() {
        viewModelScope.launch {
            try {
                // Use find with -printf for reliable parsing (no ls column ambiguity)
                val cmd = "find ~/.claude/projects/ -name '*.jsonl' -printf '%s|%T+|%p\\n' 2>/dev/null || find ~/.claude/projects/ -name '*.jsonl' -exec stat -c '%s|%Y|%n' {} \\; 2>/dev/null"
                val result = executeForUser(cmd)
                result.fold(
                    onSuccess = { cmdResult ->
                        val sessions = cmdResult.output.lines()
                            .filter { it.contains("|") && it.contains(".jsonl") }
                            .mapNotNull { line ->
                                val parts = line.split("|", limit = 3)
                                if (parts.size < 3) return@mapNotNull null
                                val fullPath = parts[2].trim()
                                val pathParts = fullPath.split("/")
                                val projectIdx = pathParts.indexOfLast { it == "projects" }
                                val projectRaw = if (projectIdx >= 0 && projectIdx + 1 < pathParts.size) pathParts[projectIdx + 1] else "unknown"
                                val projectDisplay = projectRaw
                                val filename = pathParts.lastOrNull() ?: ""
                                val sizeBytes = parts[0].trim().toLongOrNull() ?: 0
                                val sizeStr = when {
                                    sizeBytes >= 1_048_576 -> "%.1fM".format(sizeBytes / 1_048_576.0)
                                    sizeBytes >= 1024 -> "%.0fK".format(sizeBytes / 1024.0)
                                    else -> "${sizeBytes}B"
                                }
                                SessionInfo(
                                    project = projectRaw,
                                    projectDisplay = projectDisplay,
                                    filename = filename,
                                    size = sizeStr,
                                    modified = parts[1].trim().take(16), // truncate to readable date
                                    fullPath = fullPath
                                )
                            }
                            .sortedByDescending { it.modified }
                        _state.update { it.copy(sessionsList = sessions, isLoadingDetail = false) }
                    },
                    onFailure = { _state.update { it.copy(isLoadingDetail = false) } }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingDetail = false) }
            }
        }
    }

    private fun viewSession(session: SessionInfo) {
        viewModelScope.launch {
            _state.update { it.copy(
                isLoadingSession = true,
                selectedSessionName = "${session.projectDisplay} / ${session.filename}",
                selectedSessionContent = "",
                sessionLines = emptyList(),
                sessionSearchQuery = "",
                sessionFilterRole = null
            )}
            val result = executeForUser("cat '${session.fullPath}' 2>/dev/null")
            result.fold(
                onSuccess = { cmdResult ->
                    val lines = cmdResult.output.lines().filter { it.isNotBlank() }
                    _state.update { it.copy(
                        selectedSessionContent = cmdResult.output,
                        sessionLines = lines,
                        isLoadingSession = false
                    )}
                },
                onFailure = { _state.update { it.copy(error = "Could not read session", isLoadingSession = false) } }
            )
        }
    }

    private fun deleteSession(session: SessionInfo) {
        viewModelScope.launch {
            val result = executeForUser("rm -f '${session.fullPath}'")
            result.fold(
                onSuccess = {
                    _state.update { it.copy(
                        sessionsList = it.sessionsList.filter { s -> s != session },
                        successMessage = "Session deleted"
                    )}
                    loadOverview()
                },
                onFailure = { _state.update { it.copy(error = "Failed to delete session") } }
            )
        }
    }

    // ── Plans ──────────────────────────────────────────────────────────

    private fun loadPlansDetail() {
        viewModelScope.launch {
            try {
                // Use find for reliable listing, avoiding ls parsing issues
                val cmd = "find ~/.claude/plans/ -maxdepth 1 -type f -printf '%s|%f\\n' 2>/dev/null || for f in ~/.claude/plans/*; do [ -f \"\$f\" ] && echo \"\$(stat -c '%s' \"\$f\" 2>/dev/null || echo 0)|\$(basename \"\$f\")\"; done 2>/dev/null"
                val result = executeForUser(cmd)
                result.fold(
                    onSuccess = { cmdResult ->
                        val plans = cmdResult.output.lines()
                            .filter { it.contains("|") && it.trim().isNotBlank() }
                            .map { line ->
                                val parts = line.split("|", limit = 2)
                                val filename = parts.getOrNull(1)?.trim() ?: ""
                                val sizeBytes = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: 0
                                val sizeStr = when {
                                    sizeBytes >= 1_048_576 -> "%.1fM".format(sizeBytes / 1_048_576.0)
                                    sizeBytes >= 1024 -> "%.0fK".format(sizeBytes / 1024.0)
                                    else -> "${sizeBytes}B"
                                }
                                val displayName = filename.removeSuffix(".md").replace("-", " ").replace("_", " ")
                                PlanInfo(
                                    name = displayName,
                                    path = filename,
                                    size = sizeStr
                                )
                            }
                            .filter { it.path.isNotBlank() }
                        _state.update { it.copy(plansList = plans, isLoadingDetail = false) }
                    },
                    onFailure = { _state.update { it.copy(isLoadingDetail = false) } }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingDetail = false) }
            }
        }
    }

    private fun viewPlan(plan: PlanInfo) {
        viewModelScope.launch {
            val result = readClaudeFile(".claude/plans/${plan.path}")
            result.fold(
                onSuccess = { content -> _state.update { it.copy(selectedPlanContent = content, selectedPlanName = plan.name) } },
                onFailure = { _state.update { it.copy(error = "Could not read plan") } }
            )
        }
    }

    private fun deletePlan(plan: PlanInfo) {
        viewModelScope.launch {
            val result = executeForUser("rm -f ~/.claude/plans/${plan.path}")
            result.fold(
                onSuccess = {
                    _state.update { it.copy(
                        plansList = it.plansList.filter { p -> p != plan },
                        successMessage = "Plan deleted"
                    )}
                    loadOverview()
                },
                onFailure = { _state.update { it.copy(error = "Failed to delete plan") } }
            )
        }
    }

    // ── Hook scripts CRUD ──────────────────────────────────────────────

    private fun loadAndEditHook(hook: HookScript) {
        viewModelScope.launch {
            val result = readClaudeFile(".claude/hooks/${hook.filename}")
            val content = result.getOrNull() ?: ""
            _state.update { it.copy(editingHook = hook.copy(content = content), isAddingHook = false) }
        }
    }

    private fun loadAndEditSkill(skill: SkillScript) {
        viewModelScope.launch {
            val result = readClaudeFile(".claude/skills/${skill.filename}")
            val content = result.getOrNull() ?: ""
            _state.update { it.copy(editingSkill = skill.copy(content = content), isAddingSkill = false) }
        }
    }

    private fun loadHookScripts() {
        viewModelScope.launch {
            try {
                // Use find instead of glob to avoid bash no-match issues
                val cmd = "find ~/.claude/hooks/ -maxdepth 1 -type f -printf '%f\\n' 2>/dev/null || ls -1 ~/.claude/hooks/ 2>/dev/null"
                val result = executeForUser(cmd)
                result.fold(
                    onSuccess = { cmdResult ->
                        val hooks = cmdResult.output.lines()
                            .filter { it.isNotBlank() }
                            .map { HookScript(filename = it.trim()) }
                        _state.update { it.copy(hookScripts = hooks, isLoadingDetail = false) }
                    },
                    onFailure = { _state.update { it.copy(isLoadingDetail = false) } }
                )
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingDetail = false) }
            }
        }
    }

    private fun saveHook(originalName: String?, hook: HookScript) {
        viewModelScope.launch {
            try {
                executeForUser("mkdir -p ~/.claude/hooks")
                // If renaming, delete old file
                if (originalName != null && originalName != hook.filename && originalName.isNotBlank()) {
                    executeForUser("rm -f ~/.claude/hooks/$originalName")
                }
                writeClaudeFile(".claude/hooks/${hook.filename}", hook.content)
                executeForUser("chmod +x ~/.claude/hooks/${hook.filename}")
                _state.update { it.copy(editingHook = null, isAddingHook = false, successMessage = "Hook '${hook.filename}' saved") }
                loadHookScripts()
                loadOverview()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to save hook: ${e.message}") }
            }
        }
    }

    private fun deleteHook(hook: HookScript) {
        viewModelScope.launch {
            val result = executeForUser("rm -f ~/.claude/hooks/${hook.filename}")
            result.fold(
                onSuccess = {
                    _state.update { it.copy(
                        hookScripts = it.hookScripts.filter { h -> h.filename != hook.filename },
                        successMessage = "Hook '${hook.filename}' deleted"
                    )}
                    loadOverview()
                },
                onFailure = { _state.update { it.copy(error = "Failed to delete hook") } }
            )
        }
    }

    // ── Skill scripts CRUD ─────────────────────────────────────────────

    private fun loadSkillScripts() {
        viewModelScope.launch {
            try {
                val cmd = "ls -1 ~/.claude/skills/ 2>/dev/null"
                val result = executeForUser(cmd)
                result.fold(
                    onSuccess = { cmdResult ->
                        val skills = cmdResult.output.lines()
                            .filter { it.isNotBlank() }
                            .map { SkillScript(filename = it.trim()) }
                        _state.update { it.copy(skillScripts = skills, isLoadingDetail = false) }
                    },
                    onFailure = {
                        // Fall back to already-loaded overview skills if available
                        val fallback = _state.value.customSkills.map { SkillScript(filename = it) }
                        _state.update { it.copy(skillScripts = fallback, isLoadingDetail = false) }
                    }
                )
            } catch (e: Exception) {
                val fallback = _state.value.customSkills.map { SkillScript(filename = it) }
                _state.update { it.copy(skillScripts = fallback, isLoadingDetail = false) }
            }
        }
    }

    private fun saveSkill(originalName: String?, skill: SkillScript) {
        viewModelScope.launch {
            try {
                executeForUser("mkdir -p ~/.claude/skills")
                if (originalName != null && originalName != skill.filename && originalName.isNotBlank()) {
                    executeForUser("rm -f ~/.claude/skills/$originalName")
                }
                writeClaudeFile(".claude/skills/${skill.filename}", skill.content)
                _state.update { it.copy(editingSkill = null, isAddingSkill = false, successMessage = "Skill '${skill.filename}' saved") }
                loadSkillScripts()
                loadOverview()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to save skill: ${e.message}") }
            }
        }
    }

    private fun deleteSkill(skill: SkillScript) {
        viewModelScope.launch {
            val result = executeForUser("rm -f ~/.claude/skills/${skill.filename}")
            result.fold(
                onSuccess = {
                    _state.update { it.copy(
                        skillScripts = it.skillScripts.filter { s -> s.filename != skill.filename },
                        successMessage = "Skill '${skill.filename}' deleted"
                    )}
                    loadOverview()
                },
                onFailure = { _state.update { it.copy(error = "Failed to delete skill") } }
            )
        }
    }

    // ── Plugins ────────────────────────────────────────────────────────

    private fun uninstallPlugin(name: String) {
        viewModelScope.launch {
            try {
                // Plugins are in ~/.claude/settings.json under "enabledPlugins"
                val settingsContent = readClaudeFile(".claude/settings.json").getOrNull()?.trim() ?: "{}"
                val settings = try { Json.parseToJsonElement(settingsContent).jsonObject } catch (_: Exception) { JsonObject(emptyMap()) }
                val enabledPlugins = settings["enabledPlugins"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                enabledPlugins.remove(name)
                val updatedSettings = JsonObject(settings.toMutableMap().apply {
                    put("enabledPlugins", JsonObject(enabledPlugins))
                })
                writeClaudeFile(".claude/settings.json", json.encodeToString(JsonObject.serializer(), updatedSettings))
                _state.update { it.copy(
                    installedPlugins = it.installedPlugins.filter { p -> p != name },
                    successMessage = "Plugin '$name' disabled"
                )}
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to disable plugin: ${e.message}") }
            }
        }
    }
}
