package com.serverdash.app.viewmodel

import com.google.common.truth.Truth.assertThat
import com.serverdash.app.domain.model.CommandResult
import com.serverdash.app.domain.model.SystemUser
import com.serverdash.app.domain.repository.SshRepository
import com.serverdash.app.domain.usecase.DetectClaudeCodeUsersUseCase
import com.serverdash.app.domain.usecase.DetectSystemUsersUseCase
import com.serverdash.app.presentation.screens.claudecode.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClaudeCodeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var sshRepository: SshRepository
    private lateinit var detectSystemUsers: DetectSystemUsersUseCase
    private lateinit var detectClaudeCodeUsers: DetectClaudeCodeUsersUseCase
    private val cacheManager: com.serverdash.app.core.cache.ScreenCacheManager = mockk(relaxed = true)

    private val testUser = SystemUser(username = "matt", homeDirectory = "/home/matt", uid = 1000, hasClaudeCode = true)
    private val testUser2 = SystemUser(username = "alice", homeDirectory = "/home/alice", uid = 1001, hasClaudeCode = true)

    private fun cmdResult(output: String, exitCode: Int = 0, error: String = "") =
        Result.success(CommandResult(exitCode = exitCode, output = output, error = error))

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sshRepository = mockk(relaxed = true)
        detectSystemUsers = mockk()
        detectClaudeCodeUsers = mockk()

        // Default: connected as "matt"
        every { sshRepository.getConnectedUsername() } returns "matt"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helper: stub detection so init completes, then return VM ──────

    /**
     * Creates a VM that detects Claude Code successfully and loads users/overview/mcp.
     * Stubs all commands that fire during init (detectClaude -> loadUsers + loadOverview + loadMcpServers).
     */
    private fun createDetectedViewModel(): ClaudeCodeViewModel {
        stubDetected()
        stubUsers(listOf(testUser))
        stubOverview()
        stubMcpRead("{}")
        return ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
    }

    /** Creates a VM that does NOT detect Claude Code. */
    private fun createNotDetectedViewModel(): ClaudeCodeViewModel {
        coEvery { sshRepository.executeCommand(match { it.contains("claude --version") }) } returns
            cmdResult("NOT_FOUND")
        return ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
    }

    private fun stubDetected(version: String = "claude-code 1.0.23") {
        coEvery { sshRepository.executeCommand(match { it.contains("claude --version") }) } returns
            cmdResult(version)
    }

    private fun stubUsers(users: List<SystemUser>) {
        coEvery { detectSystemUsers() } returns Result.success(users.map { it.copy(hasClaudeCode = false) })
        coEvery { detectClaudeCodeUsers(any()) } returns Result.success(users)
    }

    private fun stubOverview(
        diskUsage: String = "152M",
        stats: String = """{"total_tokens":12345}""",
        projectCount: Int = 3,
        planCount: Int = 2,
        sessionCount: Int = 7,
        plugins: String = """["plugin-a","plugin-b"]""",
        skills: String = "summarize.sh\ntranslate.py",
        hooks: String = "pre-commit.sh\npost-push.sh"
    ) {
        val overviewOutput = buildString {
            append("===DU===\n$diskUsage\n")
            append("===STATS===\n$stats\n")
            append("===PROJECTS===\n$projectCount\n")
            append("===PLANS===\n$planCount\n")
            append("===SESSIONS===\n$sessionCount\n")
            append("===PLUGINS===\n$plugins\n")
            append("===SKILLS===\n$skills\n")
            append("===HOOKS===\n$hooks")
        }
        coEvery { sshRepository.executeCommand(match { it.contains("===DU===") }) } returns
            cmdResult(overviewOutput)
    }

    private fun stubMcpRead(json: String) {
        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.mcp.json") }) } returns
            cmdResult(json)
    }

    // ── 1. Initial detection ─────────────────────────────────────────────

    @Test
    fun `init detects Claude Code and sets version`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.isDetected).isTrue()
        assertThat(vm.state.value.claudeVersion).isEqualTo("claude-code 1.0.23")
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `init sets not detected when NOT_FOUND`() = runTest {
        val vm = createNotDetectedViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.isDetected).isFalse()
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `init sets not detected when command fails`() = runTest {
        coEvery { sshRepository.executeCommand(match { it.contains("claude --version") }) } returns
            Result.failure(RuntimeException("SSH error"))
        val vm = ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
        advanceUntilIdle()

        assertThat(vm.state.value.isDetected).isFalse()
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test
    fun `init sets not detected when output is blank`() = runTest {
        coEvery { sshRepository.executeCommand(match { it.contains("claude --version") }) } returns
            cmdResult("")
        val vm = ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
        advanceUntilIdle()

        assertThat(vm.state.value.isDetected).isFalse()
    }

    // ── 2. Tab selection triggers correct loaders ────────────────────────

    @Test
    fun `selecting tab 0 loads overview`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Stub fresh overview
        stubOverview(diskUsage = "200M")
        vm.onEvent(ClaudeCodeEvent.SelectTab(0))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedTab).isEqualTo(0)
        assertThat(vm.state.value.diskUsage).isEqualTo("200M")
    }

    @Test
    fun `selecting tab 1 loads MCP servers`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        stubMcpRead("""{"mcpServers":{"test-server":{"command":"node","args":["server.js"]}}}""")
        vm.onEvent(ClaudeCodeEvent.SelectTab(1))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedTab).isEqualTo(1)
        assertThat(vm.state.value.mcpServers).hasSize(1)
    }

    @Test
    fun `selecting tab 2 loads settings`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/settings.json") }) } returns
            cmdResult("""{"model":"opus"}""")
        vm.onEvent(ClaudeCodeEvent.SelectTab(2))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedTab).isEqualTo(2)
        assertThat(vm.state.value.settingsJson).contains("opus")
    }

    @Test
    fun `selecting tab 3 loads CLAUDE md`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/CLAUDE.md") }) } returns
            cmdResult("# My Project Rules")
        vm.onEvent(ClaudeCodeEvent.SelectTab(3))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedTab).isEqualTo(3)
        assertThat(vm.state.value.claudeMdContent).isEqualTo("# My Project Rules")
    }

    @Test
    fun `selecting tab 4 loads projects`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("for d in ~/.claude/projects") }) } returns
            cmdResult("-home-matt-fleet|5|Y\n-home-matt-web|2|N")
        vm.onEvent(ClaudeCodeEvent.SelectTab(4))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedTab).isEqualTo(4)
        assertThat(vm.state.value.projects).hasSize(2)
    }

    // ── 3. MCP server CRUD ───────────────────────────────────────────────

    @Test
    fun `loadMcpServers parses servers from mcp json`() = runTest {
        stubDetected()
        stubUsers(listOf(testUser))
        stubOverview()
        stubMcpRead("""{"mcpServers":{"filesystem":{"command":"npx","args":["-y","@modelcontextprotocol/server-filesystem","/tmp"],"env":{"DEBUG":"true"}},"memory":{"command":"npx","args":["-y","@modelcontextprotocol/server-memory"]}}}""")

        val vm = ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
        advanceUntilIdle()

        val servers = vm.state.value.mcpServers
        assertThat(servers).hasSize(2)
        assertThat(servers[0].name).isEqualTo("filesystem")
        assertThat(servers[0].command).isEqualTo("npx")
        assertThat(servers[0].args).containsExactly("-y", "@modelcontextprotocol/server-filesystem", "/tmp")
        assertThat(servers[0].env).containsEntry("DEBUG", "true")
        assertThat(servers[1].name).isEqualTo("memory")
    }

    @Test
    fun `ShowAddMcpServer opens dialog with empty server`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.ShowAddMcpServer)
        assertThat(vm.state.value.isAddingMcpServer).isTrue()
        assertThat(vm.state.value.editingMcpServer).isNotNull()
        assertThat(vm.state.value.editingMcpServer!!.name).isEmpty()
    }

    @Test
    fun `EditMcpServer opens dialog with existing server`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        val server = McpServer(name = "test", command = "node", args = listOf("index.js"))
        vm.onEvent(ClaudeCodeEvent.EditMcpServer(server))

        assertThat(vm.state.value.editingMcpServer).isEqualTo(server)
        assertThat(vm.state.value.isAddingMcpServer).isFalse()
    }

    @Test
    fun `SaveMcpServer writes updated config and refreshes`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Stub read for save (reads current config)
        stubMcpRead("""{"mcpServers":{}}""")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")

        val newServer = McpServer(name = "new-server", command = "python", args = listOf("serve.py"))
        vm.onEvent(ClaudeCodeEvent.SaveMcpServer(null, newServer))
        advanceUntilIdle()

        assertThat(vm.state.value.editingMcpServer).isNull()
        assertThat(vm.state.value.isAddingMcpServer).isFalse()
        assertThat(vm.state.value.successMessage).isEqualTo("MCP server saved")
    }

    @Test
    fun `DeleteMcpServer removes server and shows success`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        stubMcpRead("""{"mcpServers":{"old-server":{"command":"node","args":[]}}}""")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")

        vm.onEvent(ClaudeCodeEvent.DeleteMcpServer(McpServer(name = "old-server", command = "node")))
        advanceUntilIdle()

        assertThat(vm.state.value.successMessage).isEqualTo("MCP server 'old-server' deleted")
    }

    @Test
    fun `DismissMcpDialog clears editing state`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.ShowAddMcpServer)
        assertThat(vm.state.value.isAddingMcpServer).isTrue()

        vm.onEvent(ClaudeCodeEvent.DismissMcpDialog)
        assertThat(vm.state.value.editingMcpServer).isNull()
        assertThat(vm.state.value.isAddingMcpServer).isFalse()
    }

    // ── 4. Settings loading and saving ───────────────────────────────────

    @Test
    fun `loadSettings formats JSON and stores original`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/settings.json") }) } returns
            cmdResult("""{"model":"opus","verbose":true}""")
        vm.onEvent(ClaudeCodeEvent.LoadSettings)
        advanceUntilIdle()

        assertThat(vm.state.value.settingsJson).contains("opus")
        assertThat(vm.state.value.originalSettingsJson).isEqualTo(vm.state.value.settingsJson)
        assertThat(vm.state.value.isLoadingSettings).isFalse()
    }

    @Test
    fun `saveSettings validates JSON and writes file`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Load settings first
        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/settings.json") }) } returns
            cmdResult("""{"model":"opus"}""")
        vm.onEvent(ClaudeCodeEvent.LoadSettings)
        advanceUntilIdle()

        // Stub mkdir + write
        coEvery { sshRepository.executeCommand(match { it.contains("mkdir -p ~/.claude") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")

        vm.onEvent(ClaudeCodeEvent.UpdateSettingsJson("""{"model":"sonnet"}"""))
        vm.onEvent(ClaudeCodeEvent.SaveSettings)
        advanceUntilIdle()

        assertThat(vm.state.value.isSavingSettings).isFalse()
        assertThat(vm.state.value.editingSettings).isFalse()
        assertThat(vm.state.value.successMessage).isEqualTo("Settings saved")
    }

    @Test
    fun `saveSettings with invalid JSON sets error`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.UpdateSettingsJson("not valid json {{{"))
        vm.onEvent(ClaudeCodeEvent.SaveSettings)
        advanceUntilIdle()

        assertThat(vm.state.value.error).contains("Invalid JSON")
    }

    @Test
    fun `StartEditSettings sets editing flag`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.StartEditSettings)
        assertThat(vm.state.value.editingSettings).isTrue()
    }

    @Test
    fun `CancelEditSettings clears editing and reloads`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/settings.json") }) } returns
            cmdResult("""{"model":"opus"}""")

        vm.onEvent(ClaudeCodeEvent.StartEditSettings)
        vm.onEvent(ClaudeCodeEvent.CancelEditSettings)
        advanceUntilIdle()

        assertThat(vm.state.value.editingSettings).isFalse()
    }

    @Test
    fun `ToggleSettingsViewMode switches between UI and JSON`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.settingsViewMode).isEqualTo(SettingsViewMode.UI)

        vm.onEvent(ClaudeCodeEvent.ToggleSettingsViewMode)
        assertThat(vm.state.value.settingsViewMode).isEqualTo(SettingsViewMode.JSON)

        vm.onEvent(ClaudeCodeEvent.ToggleSettingsViewMode)
        assertThat(vm.state.value.settingsViewMode).isEqualTo(SettingsViewMode.UI)
    }

    @Test
    fun `RequestSaveSettings shows diff dialog when changes exist`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Load original
        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/settings.json") }) } returns
            cmdResult("""{"model":"opus"}""")
        vm.onEvent(ClaudeCodeEvent.LoadSettings)
        advanceUntilIdle()

        // Make a change
        vm.onEvent(ClaudeCodeEvent.UpdateSettingsJson("""{"model":"sonnet"}"""))
        vm.onEvent(ClaudeCodeEvent.RequestSaveSettings)

        assertThat(vm.state.value.showDiffDialog).isTrue()
        assertThat(vm.state.value.settingsDiff).isNotEmpty()
    }

    @Test
    fun `RequestSaveSettings shows no changes message when unchanged`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/settings.json") }) } returns
            cmdResult("""{"model":"opus"}""")
        vm.onEvent(ClaudeCodeEvent.LoadSettings)
        advanceUntilIdle()

        // Don't change anything
        vm.onEvent(ClaudeCodeEvent.RequestSaveSettings)

        assertThat(vm.state.value.showDiffDialog).isFalse()
        assertThat(vm.state.value.successMessage).isEqualTo("No changes to save")
    }

    @Test
    fun `ConfirmSaveSettings closes diff dialog and saves`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/settings.json") }) } returns
            cmdResult("""{"model":"opus"}""")
        vm.onEvent(ClaudeCodeEvent.LoadSettings)
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("mkdir -p ~/.claude") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")

        vm.onEvent(ClaudeCodeEvent.UpdateSettingsJson("""{"model":"sonnet"}"""))
        vm.onEvent(ClaudeCodeEvent.RequestSaveSettings)
        vm.onEvent(ClaudeCodeEvent.ConfirmSaveSettings)
        advanceUntilIdle()

        assertThat(vm.state.value.showDiffDialog).isFalse()
        assertThat(vm.state.value.successMessage).isEqualTo("Settings saved")
    }

    @Test
    fun `DismissDiffDialog closes the dialog`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Force the dialog open via state
        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/settings.json") }) } returns
            cmdResult("""{"model":"opus"}""")
        vm.onEvent(ClaudeCodeEvent.LoadSettings)
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.UpdateSettingsJson("""{"model":"sonnet"}"""))
        vm.onEvent(ClaudeCodeEvent.RequestSaveSettings)
        assertThat(vm.state.value.showDiffDialog).isTrue()

        vm.onEvent(ClaudeCodeEvent.DismissDiffDialog)
        assertThat(vm.state.value.showDiffDialog).isFalse()
    }

    // ── 5. CLAUDE.md loading and saving ──────────────────────────────────

    @Test
    fun `loadClaudeMd reads file content`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/CLAUDE.md") }) } returns
            cmdResult("# Rules\n- Be concise")
        vm.onEvent(ClaudeCodeEvent.LoadClaudeMd)
        advanceUntilIdle()

        assertThat(vm.state.value.claudeMdContent).isEqualTo("# Rules\n- Be concise")
        assertThat(vm.state.value.isLoadingClaudeMd).isFalse()
    }

    @Test
    fun `saveClaudeMd writes content and shows success`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("mkdir -p ~/.claude") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")

        vm.onEvent(ClaudeCodeEvent.UpdateClaudeMd("# Updated Rules"))
        vm.onEvent(ClaudeCodeEvent.SaveClaudeMd)
        advanceUntilIdle()

        assertThat(vm.state.value.isSavingClaudeMd).isFalse()
        assertThat(vm.state.value.editingClaudeMd).isFalse()
        assertThat(vm.state.value.successMessage).isEqualTo("CLAUDE.md saved")
    }

    @Test
    fun `StartEditClaudeMd sets editing flag`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.StartEditClaudeMd)
        assertThat(vm.state.value.editingClaudeMd).isTrue()
    }

    @Test
    fun `CancelEditClaudeMd clears editing and reloads`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/CLAUDE.md") }) } returns
            cmdResult("original content")

        vm.onEvent(ClaudeCodeEvent.StartEditClaudeMd)
        vm.onEvent(ClaudeCodeEvent.CancelEditClaudeMd)
        advanceUntilIdle()

        assertThat(vm.state.value.editingClaudeMd).isFalse()
    }

    // ── 6. Overview loading and data parsing ─────────────────────────────

    @Test
    fun `loadOverview parses disk usage, stats, counts, plugins, skills, hooks`() = runTest {
        stubDetected()
        stubUsers(listOf(testUser))
        stubMcpRead("{}")
        stubOverview(
            diskUsage = "256M",
            stats = """{"api_calls":100}""",
            projectCount = 5,
            planCount = 3,
            sessionCount = 12,
            plugins = """["plugin-x","plugin-y","plugin-z"]""",
            skills = "skill-a.sh\nskill-b.py",
            hooks = "hook-1.sh"
        )

        val vm = ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
        advanceUntilIdle()

        val s = vm.state.value
        assertThat(s.diskUsage).isEqualTo("256M")
        assertThat(s.usageStats).isEqualTo("""{"api_calls":100}""")
        assertThat(s.projectCount).isEqualTo(5)
        assertThat(s.planCount).isEqualTo(3)
        assertThat(s.sessionCount).isEqualTo(12)
        assertThat(s.installedPlugins).containsExactly("plugin-x", "plugin-y", "plugin-z")
        assertThat(s.customSkills).containsExactly("skill-a.sh", "skill-b.py")
        assertThat(s.hookFiles).containsExactly("hook-1.sh")
    }

    @Test
    fun `loadOverview handles empty output gracefully`() = runTest {
        stubDetected()
        stubUsers(listOf(testUser))
        stubMcpRead("{}")
        stubOverview(diskUsage = "", stats = "", projectCount = 0, planCount = 0, sessionCount = 0, plugins = "", skills = "", hooks = "")

        val vm = ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
        advanceUntilIdle()

        assertThat(vm.state.value.diskUsage).isEmpty()
        assertThat(vm.state.value.projectCount).isEqualTo(0)
        assertThat(vm.state.value.installedPlugins).isEmpty()
    }

    @Test
    fun `loadOverview parses plugins with object format`() = runTest {
        stubDetected()
        stubUsers(listOf(testUser))
        stubMcpRead("{}")
        stubOverview(plugins = """[{"name":"obj-plugin","version":"1.0"}]""")

        val vm = ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
        advanceUntilIdle()

        assertThat(vm.state.value.installedPlugins).containsExactly("obj-plugin")
    }

    // ── 7. Projects loading and memory viewing ───────────────────────────

    @Test
    fun `loadProjects parses project list with session counts and memory flag`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("for d in ~/.claude/projects") }) } returns
            cmdResult("-home-matt-fleet|5|Y\n-home-matt-web|2|N\n-home-matt-api|0|N")
        vm.onEvent(ClaudeCodeEvent.LoadProjects)
        advanceUntilIdle()

        val projects = vm.state.value.projects
        assertThat(projects).hasSize(3)
        // sorted by session count descending
        assertThat(projects[0].sessionCount).isEqualTo(5)
        assertThat(projects[0].displayName).isEqualTo("/home/matt/fleet")
        assertThat(projects[0].hasMemory).isTrue()
        assertThat(projects[1].sessionCount).isEqualTo(2)
        assertThat(projects[1].hasMemory).isFalse()
        assertThat(vm.state.value.isLoadingProjects).isFalse()
    }

    @Test
    fun `ViewProjectMemory reads MEMORY md and sets state`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        val project = ClaudeProject(path = "-home-matt-fleet", displayName = "/home/matt/fleet", hasMemory = true)
        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/projects/-home-matt-fleet/memory/MEMORY.md") }) } returns
            cmdResult("## Memory content\n- Important fact")

        vm.onEvent(ClaudeCodeEvent.ViewProjectMemory(project))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedProjectMemory).isEqualTo("## Memory content\n- Important fact")
        assertThat(vm.state.value.selectedProjectName).isEqualTo("/home/matt/fleet")
    }

    @Test
    fun `DismissProjectMemory clears memory state`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Set some memory state first
        val project = ClaudeProject(path = "-home-matt-fleet", displayName = "/home/matt/fleet", hasMemory = true)
        coEvery { sshRepository.executeCommand(match { it.contains("MEMORY.md") }) } returns
            cmdResult("memory content")
        vm.onEvent(ClaudeCodeEvent.ViewProjectMemory(project))
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.DismissProjectMemory)
        assertThat(vm.state.value.selectedProjectMemory).isNull()
        assertThat(vm.state.value.selectedProjectName).isNull()
    }

    // ── 8. Detail navigation ─────────────────────────────────────────────

    @Test
    fun `openDetail STORAGE loads storage breakdown`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        val storageOutput = buildString {
            append("===TOTAL===\n500M\n")
            append("===PROJECTS===\n300M\n")
            append("===PLANS===\n10M\n")
            append("===HOOKS===\n1M\n")
            append("===SKILLS===\n2M\n")
            append("===PLUGINS===\n50M\n")
            append("===STATS===\n5M\n")
            append("===CONFIG===\n4K")
        }
        coEvery { sshRepository.executeCommand(match { it.contains("===TOTAL===") && it.contains("===PLUGINS===") }) } returns
            cmdResult(storageOutput)

        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.STORAGE))
        advanceUntilIdle()

        assertThat(vm.state.value.activeDetail).isEqualTo(DetailView.STORAGE)
        assertThat(vm.state.value.storageBreakdown).hasSize(8)
        assertThat(vm.state.value.storageBreakdown[0].category).isEqualTo("Total")
        assertThat(vm.state.value.storageBreakdown[0].size).isEqualTo("500M")
        assertThat(vm.state.value.isLoadingDetail).isFalse()
    }

    @Test
    fun `openDetail PROJECTS sets activeDetail and loads projects`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("for d in ~/.claude/projects") }) } returns
            cmdResult("-home-matt-fleet|3|Y")

        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.PROJECTS))
        advanceUntilIdle()

        assertThat(vm.state.value.activeDetail).isEqualTo(DetailView.PROJECTS)
        assertThat(vm.state.value.isLoadingDetail).isFalse()
    }

    @Test
    fun `openDetail PLUGINS sets activeDetail immediately`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.PLUGINS))
        advanceUntilIdle()

        assertThat(vm.state.value.activeDetail).isEqualTo(DetailView.PLUGINS)
        assertThat(vm.state.value.isLoadingDetail).isFalse()
    }

    @Test
    fun `closeDetail clears activeDetail`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.PLUGINS))
        vm.onEvent(ClaudeCodeEvent.CloseDetail)

        assertThat(vm.state.value.activeDetail).isNull()
    }

    // ── 9. Hook script CRUD ──────────────────────────────────────────────

    @Test
    fun `openDetail HOOKS loads hook script list`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/hooks/") }) } returns
            cmdResult("pre-commit.sh\npost-push.sh\nlint.sh")

        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.HOOKS))
        advanceUntilIdle()

        assertThat(vm.state.value.hookScripts).hasSize(3)
        assertThat(vm.state.value.hookScripts[0].filename).isEqualTo("pre-commit.sh")
    }

    @Test
    fun `EditHook loads hook content`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/hooks/pre-commit.sh") }) } returns
            cmdResult("#!/bin/bash\necho 'lint'")

        vm.onEvent(ClaudeCodeEvent.EditHook(HookScript(filename = "pre-commit.sh")))
        advanceUntilIdle()

        assertThat(vm.state.value.editingHook).isNotNull()
        assertThat(vm.state.value.editingHook!!.content).isEqualTo("#!/bin/bash\necho 'lint'")
        assertThat(vm.state.value.editingHook!!.filename).isEqualTo("pre-commit.sh")
    }

    @Test
    fun `ShowAddHook opens dialog with empty new hook`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.ShowAddHook)

        assertThat(vm.state.value.isAddingHook).isTrue()
        assertThat(vm.state.value.editingHook).isNotNull()
        assertThat(vm.state.value.editingHook!!.isNew).isTrue()
        assertThat(vm.state.value.editingHook!!.filename).isEmpty()
    }

    @Test
    fun `SaveHook writes file, chmod, and refreshes`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("mkdir -p ~/.claude/hooks") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("chmod +x") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/hooks/") }) } returns cmdResult("new-hook.sh")
        stubOverview()

        vm.onEvent(ClaudeCodeEvent.SaveHook(null, HookScript(filename = "new-hook.sh", content = "#!/bin/bash\nexit 0")))
        advanceUntilIdle()

        assertThat(vm.state.value.editingHook).isNull()
        assertThat(vm.state.value.isAddingHook).isFalse()
        assertThat(vm.state.value.successMessage).isEqualTo("Hook 'new-hook.sh' saved")
    }

    @Test
    fun `SaveHook with rename deletes old file`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("mkdir -p ~/.claude/hooks") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("rm -f ~/.claude/hooks/old-name.sh") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("chmod +x") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/hooks/") }) } returns cmdResult("new-name.sh")
        stubOverview()

        vm.onEvent(ClaudeCodeEvent.SaveHook("old-name.sh", HookScript(filename = "new-name.sh", content = "#!/bin/bash")))
        advanceUntilIdle()

        coVerify { sshRepository.executeCommand(match { it.contains("rm -f ~/.claude/hooks/old-name.sh") }) }
    }

    @Test
    fun `DeleteHook removes file and updates list`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Load hooks first
        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/hooks/") }) } returns
            cmdResult("hook-a.sh\nhook-b.sh")
        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.HOOKS))
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("rm -f ~/.claude/hooks/hook-a.sh") }) } returns cmdResult("")
        stubOverview()

        vm.onEvent(ClaudeCodeEvent.DeleteHook(HookScript(filename = "hook-a.sh")))
        advanceUntilIdle()

        assertThat(vm.state.value.hookScripts.map { it.filename }).containsExactly("hook-b.sh")
        assertThat(vm.state.value.successMessage).isEqualTo("Hook 'hook-a.sh' deleted")
    }

    @Test
    fun `DismissHookDialog clears editing state`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.ShowAddHook)
        assertThat(vm.state.value.isAddingHook).isTrue()

        vm.onEvent(ClaudeCodeEvent.DismissHookDialog)
        assertThat(vm.state.value.editingHook).isNull()
        assertThat(vm.state.value.isAddingHook).isFalse()
    }

    // ── 10. Skill script CRUD ────────────────────────────────────────────

    @Test
    fun `openDetail SKILLS loads skill script list`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/skills/") }) } returns
            cmdResult("summarize.md\ntranslate.md")

        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.SKILLS))
        advanceUntilIdle()

        assertThat(vm.state.value.skillScripts).hasSize(2)
        assertThat(vm.state.value.skillScripts[0].filename).isEqualTo("summarize.md")
    }

    @Test
    fun `EditSkill loads skill content`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/skills/summarize.md") }) } returns
            cmdResult("# Summarize\nSummarize the given text concisely.")

        vm.onEvent(ClaudeCodeEvent.EditSkill(SkillScript(filename = "summarize.md")))
        advanceUntilIdle()

        assertThat(vm.state.value.editingSkill).isNotNull()
        assertThat(vm.state.value.editingSkill!!.content).contains("Summarize the given text")
    }

    @Test
    fun `ShowAddSkill opens dialog with empty new skill`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.ShowAddSkill)

        assertThat(vm.state.value.isAddingSkill).isTrue()
        assertThat(vm.state.value.editingSkill).isNotNull()
        assertThat(vm.state.value.editingSkill!!.isNew).isTrue()
    }

    @Test
    fun `SaveSkill writes file and refreshes`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("mkdir -p ~/.claude/skills") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/skills/") }) } returns cmdResult("new-skill.md")
        stubOverview()

        vm.onEvent(ClaudeCodeEvent.SaveSkill(null, SkillScript(filename = "new-skill.md", content = "# Skill")))
        advanceUntilIdle()

        assertThat(vm.state.value.editingSkill).isNull()
        assertThat(vm.state.value.isAddingSkill).isFalse()
        assertThat(vm.state.value.successMessage).isEqualTo("Skill 'new-skill.md' saved")
    }

    @Test
    fun `SaveSkill with rename deletes old file`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("mkdir -p ~/.claude/skills") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("rm -f ~/.claude/skills/old.md") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/skills/") }) } returns cmdResult("renamed.md")
        stubOverview()

        vm.onEvent(ClaudeCodeEvent.SaveSkill("old.md", SkillScript(filename = "renamed.md", content = "content")))
        advanceUntilIdle()

        coVerify { sshRepository.executeCommand(match { it.contains("rm -f ~/.claude/skills/old.md") }) }
    }

    @Test
    fun `DeleteSkill removes file and updates list`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Load skills first
        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/skills/") }) } returns
            cmdResult("skill-a.md\nskill-b.md")
        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.SKILLS))
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("rm -f ~/.claude/skills/skill-a.md") }) } returns cmdResult("")
        stubOverview()

        vm.onEvent(ClaudeCodeEvent.DeleteSkill(SkillScript(filename = "skill-a.md")))
        advanceUntilIdle()

        assertThat(vm.state.value.skillScripts.map { it.filename }).containsExactly("skill-b.md")
        assertThat(vm.state.value.successMessage).isEqualTo("Skill 'skill-a.md' deleted")
    }

    @Test
    fun `DismissSkillDialog clears editing state`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.ShowAddSkill)
        vm.onEvent(ClaudeCodeEvent.DismissSkillDialog)

        assertThat(vm.state.value.editingSkill).isNull()
        assertThat(vm.state.value.isAddingSkill).isFalse()
    }

    // ── 11. Plugin uninstall ─────────────────────────────────────────────

    @Test
    fun `UninstallPlugin removes plugin from list and writes updated JSON`() = runTest {
        stubDetected()
        stubUsers(listOf(testUser))
        stubMcpRead("{}")
        stubOverview(plugins = """["plugin-keep","plugin-remove","plugin-also-keep"]""")

        val vm = ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
        advanceUntilIdle()

        assertThat(vm.state.value.installedPlugins).hasSize(3)

        // Stub reading the plugins file for uninstall
        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/plugins/installed_plugins.json") }) } returns
            cmdResult("""["plugin-keep","plugin-remove","plugin-also-keep"]""")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")

        vm.onEvent(ClaudeCodeEvent.UninstallPlugin("plugin-remove"))
        advanceUntilIdle()

        assertThat(vm.state.value.installedPlugins).containsExactly("plugin-keep", "plugin-also-keep")
        assertThat(vm.state.value.successMessage).isEqualTo("Plugin 'plugin-remove' uninstalled")
    }

    // ── 12. Session loading/deleting ─────────────────────────────────────

    @Test
    fun `openDetail SESSIONS loads session list`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        val sessionsOutput = "1048576|2025-03-01+14:30:00|/home/matt/.claude/projects/-home-matt-fleet/session-abc.jsonl\n" +
            "2048|2025-02-28+10:00:00|/home/matt/.claude/projects/-home-matt-web/session-xyz.jsonl"
        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/projects/") && it.contains(".jsonl") }) } returns
            cmdResult(sessionsOutput)

        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.SESSIONS))
        advanceUntilIdle()

        val sessions = vm.state.value.sessionsList
        assertThat(sessions).hasSize(2)
        // sorted by modified descending
        assertThat(sessions[0].project).isEqualTo("-home-matt-fleet")
        assertThat(sessions[0].projectDisplay).isEqualTo("/home/matt/fleet")
        assertThat(sessions[0].filename).isEqualTo("session-abc.jsonl")
        assertThat(sessions[0].size).isEqualTo("1.0M")
        assertThat(sessions[1].size).isEqualTo("2K")
    }

    @Test
    fun `ViewSession reads last 5 lines of session file`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        val session = SessionInfo(
            project = "-home-matt-fleet",
            projectDisplay = "/home/matt/fleet",
            filename = "session-abc.jsonl",
            size = "1.0M",
            modified = "2025-03-01"
        )
        coEvery { sshRepository.executeCommand(match { it.contains("tail -5") && it.contains("session-abc.jsonl") }) } returns
            cmdResult("""{"role":"assistant","content":"Done!"}""")

        vm.onEvent(ClaudeCodeEvent.ViewSession(session))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedSessionContent).contains("Done!")
        assertThat(vm.state.value.selectedSessionName).isEqualTo("/home/matt/fleet / session-abc.jsonl")
    }

    @Test
    fun `DeleteSession removes file and updates list`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        val session1 = SessionInfo(project = "-home-matt-fleet", projectDisplay = "/home/matt/fleet", filename = "s1.jsonl", size = "1K", modified = "2025-03-01")
        val session2 = SessionInfo(project = "-home-matt-fleet", projectDisplay = "/home/matt/fleet", filename = "s2.jsonl", size = "2K", modified = "2025-02-28")

        // Load sessions
        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/projects/") && it.contains(".jsonl") }) } returns
            cmdResult("1024|2025-03-01+14:00:00|/home/matt/.claude/projects/-home-matt-fleet/s1.jsonl\n2048|2025-02-28+10:00:00|/home/matt/.claude/projects/-home-matt-fleet/s2.jsonl")
        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.SESSIONS))
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("rm -f") && it.contains("s1.jsonl") }) } returns cmdResult("")
        stubOverview()

        vm.onEvent(ClaudeCodeEvent.DeleteSession(session1))
        advanceUntilIdle()

        assertThat(vm.state.value.sessionsList).hasSize(1)
        assertThat(vm.state.value.sessionsList[0].filename).isEqualTo("s2.jsonl")
        assertThat(vm.state.value.successMessage).isEqualTo("Session deleted")
    }

    @Test
    fun `DismissSessionContent clears session content`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // View a session first
        val session = SessionInfo(project = "p", projectDisplay = "/p", filename = "s.jsonl", size = "1K", modified = "d")
        coEvery { sshRepository.executeCommand(match { it.contains("tail -5") }) } returns cmdResult("content")
        vm.onEvent(ClaudeCodeEvent.ViewSession(session))
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.DismissSessionContent)
        assertThat(vm.state.value.selectedSessionContent).isNull()
        assertThat(vm.state.value.selectedSessionName).isNull()
    }

    // ── 13. Plan loading/deleting ────────────────────────────────────────

    @Test
    fun `openDetail PLANS loads plan list`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/plans/") && it.contains("-maxdepth 1") }) } returns
            cmdResult("2048|migration-plan.md\n512|refactor-auth.md")

        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.PLANS))
        advanceUntilIdle()

        val plans = vm.state.value.plansList
        assertThat(plans).hasSize(2)
        assertThat(plans[0].name).isEqualTo("migration plan")
        assertThat(plans[0].path).isEqualTo("migration-plan.md")
        assertThat(plans[0].size).isEqualTo("2K")
        assertThat(plans[1].size).isEqualTo("512B")
    }

    @Test
    fun `ViewPlan reads plan content`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        val plan = PlanInfo(name = "migration plan", path = "migration-plan.md", size = "2K")
        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/plans/migration-plan.md") }) } returns
            cmdResult("# Migration Plan\n1. Step one\n2. Step two")

        vm.onEvent(ClaudeCodeEvent.ViewPlan(plan))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedPlanContent).contains("Migration Plan")
        assertThat(vm.state.value.selectedPlanName).isEqualTo("migration plan")
    }

    @Test
    fun `DeletePlan removes file and updates list`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Load plans
        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/plans/") && it.contains("-maxdepth 1") }) } returns
            cmdResult("2048|plan-a.md\n512|plan-b.md")
        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.PLANS))
        advanceUntilIdle()

        val planA = vm.state.value.plansList[0]
        coEvery { sshRepository.executeCommand(match { it.contains("rm -f") && it.contains("plan-a.md") }) } returns cmdResult("")
        stubOverview()

        vm.onEvent(ClaudeCodeEvent.DeletePlan(planA))
        advanceUntilIdle()

        assertThat(vm.state.value.plansList).hasSize(1)
        assertThat(vm.state.value.plansList[0].path).isEqualTo("plan-b.md")
        assertThat(vm.state.value.successMessage).isEqualTo("Plan deleted")
    }

    @Test
    fun `DismissPlanContent clears plan content`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        val plan = PlanInfo(name = "test", path = "test.md", size = "1K")
        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/plans/test.md") }) } returns cmdResult("content")
        vm.onEvent(ClaudeCodeEvent.ViewPlan(plan))
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.DismissPlanContent)
        assertThat(vm.state.value.selectedPlanContent).isNull()
        assertThat(vm.state.value.selectedPlanName).isNull()
    }

    // ── 14. User selection ───────────────────────────────────────────────

    @Test
    fun `loadUsers detects system users and Claude Code users`() = runTest {
        stubDetected()
        stubMcpRead("{}")
        stubOverview()

        val users = listOf(testUser, testUser2)
        coEvery { detectSystemUsers() } returns Result.success(users.map { it.copy(hasClaudeCode = false) })
        coEvery { detectClaudeCodeUsers(any()) } returns Result.success(users)

        val vm = ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
        advanceUntilIdle()

        assertThat(vm.state.value.claudeCodeUsers).hasSize(2)
        // Connected user "matt" should be selected by default
        assertThat(vm.state.value.selectedUser!!.username).isEqualTo("matt")
        assertThat(vm.state.value.isLoadingUsers).isFalse()
    }

    @Test
    fun `loadUsers selects first Claude user when connected user not in list`() = runTest {
        every { sshRepository.getConnectedUsername() } returns "root"
        stubDetected()
        stubMcpRead("{}")
        stubOverview()

        val users = listOf(testUser, testUser2)
        coEvery { detectSystemUsers() } returns Result.success(users.map { it.copy(hasClaudeCode = false) })
        coEvery { detectClaudeCodeUsers(any()) } returns Result.success(users)

        val vm = ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
        advanceUntilIdle()

        // "root" not in list, should pick first Claude user
        assertThat(vm.state.value.selectedUser!!.username).isEqualTo("matt")
    }

    @Test
    fun `SelectUser changes selected user and reloads all data`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Stub reloads triggered by selectUser
        stubOverview(diskUsage = "300M")
        stubMcpRead("{}")
        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/settings.json") }) } returns cmdResult("{}")
        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/CLAUDE.md") }) } returns cmdResult("")

        vm.onEvent(ClaudeCodeEvent.SelectUser(testUser2))
        advanceUntilIdle()

        assertThat(vm.state.value.selectedUser).isEqualTo(testUser2)
    }

    @Test
    fun `SelectUser for different user uses executeAsUser`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // alice is a different user from connected "matt"
        coEvery { sshRepository.executeAsUser(any(), eq("alice")) } returns cmdResult("")
        coEvery { sshRepository.readFileAsUser(any(), eq("alice")) } returns Result.success("{}")

        vm.onEvent(ClaudeCodeEvent.SelectUser(testUser2))
        advanceUntilIdle()

        // Verify that executeAsUser was called for the overview command
        coVerify { sshRepository.executeAsUser(match { it.contains("===DU===") }, "alice") }
    }

    @Test
    fun `RefreshUsers reloads user list`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Re-stub users for the refresh
        stubUsers(listOf(testUser))

        vm.onEvent(ClaudeCodeEvent.RefreshUsers)
        advanceUntilIdle()

        coVerify(atLeast = 2) { detectSystemUsers() }
    }

    // ── 15. Error/success message dismissal ──────────────────────────────

    @Test
    fun `DismissError clears error`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Force an error by saving invalid settings JSON
        vm.onEvent(ClaudeCodeEvent.UpdateSettingsJson("invalid{{{"))
        vm.onEvent(ClaudeCodeEvent.SaveSettings)
        advanceUntilIdle()

        assertThat(vm.state.value.error).isNotNull()

        vm.onEvent(ClaudeCodeEvent.DismissError)
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `DismissSuccess clears success message`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        // Trigger a success message via MCP save
        stubMcpRead("""{"mcpServers":{}}""")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")

        vm.onEvent(ClaudeCodeEvent.SaveMcpServer(null, McpServer(name = "test", command = "node")))
        advanceUntilIdle()

        assertThat(vm.state.value.successMessage).isNotNull()

        vm.onEvent(ClaudeCodeEvent.DismissSuccess)
        assertThat(vm.state.value.successMessage).isNull()
    }

    // ── Refresh event ────────────────────────────────────────────────────

    @Test
    fun `Refresh on tab 0 reloads overview`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        stubOverview(diskUsage = "999M")
        vm.onEvent(ClaudeCodeEvent.Refresh)
        advanceUntilIdle()

        assertThat(vm.state.value.diskUsage).isEqualTo("999M")
    }

    @Test
    fun `Refresh on tab 1 reloads MCP servers`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.SelectTab(1))
        stubMcpRead("""{"mcpServers":{"refreshed":{"command":"node","args":[]}}}""")
        vm.onEvent(ClaudeCodeEvent.Refresh)
        advanceUntilIdle()

        assertThat(vm.state.value.mcpServers).hasSize(1)
        assertThat(vm.state.value.mcpServers[0].name).isEqualTo("refreshed")
    }

    // ── Edge cases ───────────────────────────────────────────────────────

    @Test
    fun `loadMcpServers handles malformed JSON gracefully`() = runTest {
        stubDetected()
        stubUsers(listOf(testUser))
        stubOverview()
        stubMcpRead("not json at all {{{")

        val vm = ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
        advanceUntilIdle()

        assertThat(vm.state.value.mcpServers).isEmpty()
        assertThat(vm.state.value.isLoadingMcp).isFalse()
    }

    @Test
    fun `loadMcpServers handles empty mcp json`() = runTest {
        stubDetected()
        stubUsers(listOf(testUser))
        stubOverview()
        stubMcpRead("")

        val vm = ClaudeCodeViewModel(sshRepository, detectSystemUsers, detectClaudeCodeUsers, cacheManager)
        advanceUntilIdle()

        assertThat(vm.state.value.mcpServers).isEmpty()
    }

    @Test
    fun `loadSettings handles blank settings file`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("cat ~/.claude/settings.json") }) } returns cmdResult("")
        vm.onEvent(ClaudeCodeEvent.LoadSettings)
        advanceUntilIdle()

        assertThat(vm.state.value.settingsJson).isEqualTo("{}")
    }

    @Test
    fun `loadProjects handles empty project directory`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("for d in ~/.claude/projects") }) } returns cmdResult("")
        vm.onEvent(ClaudeCodeEvent.LoadProjects)
        advanceUntilIdle()

        assertThat(vm.state.value.projects).isEmpty()
        assertThat(vm.state.value.isLoadingProjects).isFalse()
    }

    @Test
    fun `ViewProjectMemory sets error when read fails`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        val project = ClaudeProject(path = "-home-matt-fleet", displayName = "/home/matt/fleet", hasMemory = true)
        coEvery { sshRepository.executeCommand(match { it.contains("MEMORY.md") }) } returns
            Result.failure(RuntimeException("Permission denied"))

        vm.onEvent(ClaudeCodeEvent.ViewProjectMemory(project))
        advanceUntilIdle()

        assertThat(vm.state.value.error).isEqualTo("Could not read project memory")
    }

    @Test
    fun `autoSaveSettings writes settings silently`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("mkdir -p ~/.claude") }) } returns cmdResult("")
        coEvery { sshRepository.executeCommand(match { it.contains("cat > ~/") }) } returns cmdResult("")

        vm.onEvent(ClaudeCodeEvent.UpdateSettingsJson("""{"auto":"saved"}"""))
        vm.onEvent(ClaudeCodeEvent.AutoSaveSettings)
        advanceUntilIdle()

        // No successMessage for auto-save
        assertThat(vm.state.value.successMessage).isNull()
    }

    @Test
    fun `UpdateSettingsJson updates settings in state`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.UpdateSettingsJson("""{"key":"value"}"""))
        assertThat(vm.state.value.settingsJson).isEqualTo("""{"key":"value"}""")
    }

    @Test
    fun `UpdateClaudeMd updates content in state`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        vm.onEvent(ClaudeCodeEvent.UpdateClaudeMd("new content"))
        assertThat(vm.state.value.claudeMdContent).isEqualTo("new content")
    }

    @Test
    fun `session size formatting for bytes`() = runTest {
        val vm = createDetectedViewModel()
        advanceUntilIdle()

        coEvery { sshRepository.executeCommand(match { it.contains("find ~/.claude/projects/") && it.contains(".jsonl") }) } returns
            cmdResult("500|2025-03-01+14:00:00|/home/matt/.claude/projects/-home-matt-test/small.jsonl")

        vm.onEvent(ClaudeCodeEvent.OpenDetail(DetailView.SESSIONS))
        advanceUntilIdle()

        assertThat(vm.state.value.sessionsList[0].size).isEqualTo("500B")
    }
}
