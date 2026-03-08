package com.serverdash.app.presentation.screens.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GitUiState(
    // repos
    val repos: List<GitRepo> = emptyList(),
    val isLoadingRepos: Boolean = true,
    val selectedRepo: GitRepo? = null,
    // repo detail
    val activeTab: Int = 0, // 0=status, 1=branches, 2=log, 3=PRs, 4=issues
    val gitStatus: GitStatus = GitStatus(),
    val branches: List<GitBranch> = emptyList(),
    val commits: List<GitCommit> = emptyList(),
    val diff: GitDiff = GitDiff(),
    val isLoadingDetail: Boolean = false,
    // github
    val isGhAvailable: Boolean = false,
    val pullRequests: List<GitHubPr> = emptyList(),
    val issues: List<GitHubIssue> = emptyList(),
    val checks: List<GitHubCheck> = emptyList(),
    val releases: List<GitHubRelease> = emptyList(),
    val isLoadingGh: Boolean = false,
    // operations
    val isOperating: Boolean = false,
    val operationOutput: String? = null,
    // dialogs
    val showCommitDialog: Boolean = false,
    val showCreatePrDialog: Boolean = false,
    val showCreateBranchDialog: Boolean = false,
    val showDiffViewer: Boolean = false,
    val diffContent: String = "",
    val diffTitle: String = "",
    val prFilter: String = "open", // open, closed, all
    val issueFilter: String = "open",
    // general
    val error: String? = null,
    val successMessage: String? = null
)

sealed interface GitEvent {
    data object LoadRepos : GitEvent
    data class SelectRepo(val repo: GitRepo) : GitEvent
    data object DeselectRepo : GitEvent
    data class SelectTab(val index: Int) : GitEvent
    // git operations
    data object Pull : GitEvent
    data object Push : GitEvent
    data object Fetch : GitEvent
    data class Checkout(val branch: String) : GitEvent
    data class CreateBranch(val name: String) : GitEvent
    data class Commit(val message: String, val addAll: Boolean) : GitEvent
    data class StageFiles(val files: List<String>) : GitEvent
    data class UnstageFiles(val files: List<String>) : GitEvent
    data object Stash : GitEvent
    data object StashPop : GitEvent
    // diff
    data class ViewDiff(val staged: Boolean) : GitEvent
    data class ViewCommitDiff(val commit: GitCommit) : GitEvent
    data class ViewPrDiff(val pr: GitHubPr) : GitEvent
    data object DismissDiff : GitEvent
    // github
    data class SetPrFilter(val filter: String) : GitEvent
    data class SetIssueFilter(val filter: String) : GitEvent
    data class CreatePr(val title: String, val body: String, val base: String, val draft: Boolean) : GitEvent
    data class MergePr(val pr: GitHubPr, val method: String) : GitEvent
    data class ViewPrChecks(val pr: GitHubPr) : GitEvent
    // dialogs
    data object ShowCommitDialog : GitEvent
    data object ShowCreatePrDialog : GitEvent
    data object ShowCreateBranchDialog : GitEvent
    data object DismissDialog : GitEvent
    data object DismissOperationOutput : GitEvent
    data object DismissError : GitEvent
    data object DismissSuccess : GitEvent
}

@HiltViewModel
class GitViewModel @Inject constructor(
    private val discoverRepos: DiscoverGitReposUseCase,
    private val getGitStatus: GetGitStatusUseCase,
    private val getGitBranches: GetGitBranchesUseCase,
    private val getGitLog: GetGitLogUseCase,
    private val getGitDiff: GetGitDiffUseCase,
    private val gitOps: GitOperationUseCase,
    private val github: GitHubUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(GitUiState())
    val state: StateFlow<GitUiState> = _state.asStateFlow()

    init {
        loadRepos()
    }

    fun onEvent(event: GitEvent) {
        when (event) {
            is GitEvent.LoadRepos -> loadRepos()
            is GitEvent.SelectRepo -> selectRepo(event.repo)
            is GitEvent.DeselectRepo -> _state.update { it.copy(selectedRepo = null) }
            is GitEvent.SelectTab -> {
                _state.update { it.copy(activeTab = event.index) }
                loadTabData(event.index)
            }
            // operations
            is GitEvent.Pull -> runOp("Pulling...") { gitOps.pull(repoPath()) }
            is GitEvent.Push -> runOp("Pushing...") { gitOps.push(repoPath()) }
            is GitEvent.Fetch -> runOp("Fetching...") { gitOps.fetch(repoPath()) }
            is GitEvent.Checkout -> runOp("Checking out ${event.branch}...") { gitOps.checkout(repoPath(), event.branch) }
            is GitEvent.CreateBranch -> {
                _state.update { it.copy(showCreateBranchDialog = false) }
                runOp("Creating branch ${event.name}...") { gitOps.createBranch(repoPath(), event.name) }
            }
            is GitEvent.Commit -> {
                _state.update { it.copy(showCommitDialog = false) }
                runOp("Committing...") { gitOps.commit(repoPath(), event.message, event.addAll) }
            }
            is GitEvent.StageFiles -> runOp("Staging...") { gitOps.stageFiles(repoPath(), event.files) }
            is GitEvent.UnstageFiles -> runOp("Unstaging...") { gitOps.unstageFiles(repoPath(), event.files) }
            is GitEvent.Stash -> runOp("Stashing...") { gitOps.stash(repoPath()) }
            is GitEvent.StashPop -> runOp("Popping stash...") { gitOps.stash(repoPath(), pop = true) }
            // diff
            is GitEvent.ViewDiff -> viewDiff(staged = event.staged)
            is GitEvent.ViewCommitDiff -> viewCommitDiff(event.commit)
            is GitEvent.ViewPrDiff -> viewPrDiff(event.pr)
            is GitEvent.DismissDiff -> _state.update { it.copy(showDiffViewer = false) }
            // github
            is GitEvent.SetPrFilter -> { _state.update { it.copy(prFilter = event.filter) }; loadPrs() }
            is GitEvent.SetIssueFilter -> { _state.update { it.copy(issueFilter = event.filter) }; loadIssues() }
            is GitEvent.CreatePr -> {
                _state.update { it.copy(showCreatePrDialog = false) }
                createPr(event.title, event.body, event.base, event.draft)
            }
            is GitEvent.MergePr -> mergePr(event.pr, event.method)
            is GitEvent.ViewPrChecks -> loadPrChecks(event.pr)
            // dialogs
            is GitEvent.ShowCommitDialog -> _state.update { it.copy(showCommitDialog = true) }
            is GitEvent.ShowCreatePrDialog -> _state.update { it.copy(showCreatePrDialog = true) }
            is GitEvent.ShowCreateBranchDialog -> _state.update { it.copy(showCreateBranchDialog = true) }
            is GitEvent.DismissDialog -> _state.update { it.copy(showCommitDialog = false, showCreatePrDialog = false, showCreateBranchDialog = false) }
            is GitEvent.DismissOperationOutput -> _state.update { it.copy(operationOutput = null) }
            is GitEvent.DismissError -> _state.update { it.copy(error = null) }
            is GitEvent.DismissSuccess -> _state.update { it.copy(successMessage = null) }
        }
    }

    private fun repoPath(): String = _state.value.selectedRepo?.path ?: ""

    private fun loadRepos() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingRepos = true) }
            discoverRepos().fold(
                onSuccess = { repos ->
                    _state.update { it.copy(repos = repos, isLoadingRepos = false) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoadingRepos = false, error = e.message) }
                }
            )
        }
    }

    private fun selectRepo(repo: GitRepo) {
        _state.update { it.copy(selectedRepo = repo, activeTab = 0, isLoadingDetail = true) }
        viewModelScope.launch {
            // Check gh availability
            val ghAvail = github.isGhAvailable() && repo.isGitHub
            _state.update { it.copy(isGhAvailable = ghAvail) }
            loadTabData(0)
        }
    }

    private fun loadTabData(tab: Int) {
        val path = repoPath()
        if (path.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingDetail = true) }
            when (tab) {
                0 -> { // Status
                    getGitStatus(path).fold(
                        onSuccess = { status -> _state.update { it.copy(gitStatus = status, isLoadingDetail = false) } },
                        onFailure = { _state.update { it.copy(isLoadingDetail = false) } }
                    )
                }
                1 -> { // Branches
                    getGitBranches(path).fold(
                        onSuccess = { branches -> _state.update { it.copy(branches = branches, isLoadingDetail = false) } },
                        onFailure = { _state.update { it.copy(isLoadingDetail = false) } }
                    )
                }
                2 -> { // Log
                    getGitLog(path).fold(
                        onSuccess = { commits -> _state.update { it.copy(commits = commits, isLoadingDetail = false) } },
                        onFailure = { _state.update { it.copy(isLoadingDetail = false) } }
                    )
                }
                3 -> { // PRs
                    if (_state.value.isGhAvailable) loadPrs()
                    else _state.update { it.copy(isLoadingDetail = false) }
                }
                4 -> { // Issues
                    if (_state.value.isGhAvailable) loadIssues()
                    else _state.update { it.copy(isLoadingDetail = false) }
                }
            }
        }
    }

    private fun loadPrs() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingGh = true) }
            github.listPrs(repoPath(), _state.value.prFilter).fold(
                onSuccess = { prs -> _state.update { it.copy(pullRequests = prs, isLoadingGh = false, isLoadingDetail = false) } },
                onFailure = { _state.update { it.copy(isLoadingGh = false, isLoadingDetail = false) } }
            )
        }
    }

    private fun loadIssues() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingGh = true) }
            github.listIssues(repoPath(), _state.value.issueFilter).fold(
                onSuccess = { issues -> _state.update { it.copy(issues = issues, isLoadingGh = false, isLoadingDetail = false) } },
                onFailure = { _state.update { it.copy(isLoadingGh = false, isLoadingDetail = false) } }
            )
        }
    }

    private fun loadPrChecks(pr: GitHubPr) {
        viewModelScope.launch {
            github.getPrChecks(repoPath(), pr.number).fold(
                onSuccess = { checks -> _state.update { it.copy(checks = checks) } },
                onFailure = { _state.update { it.copy(error = "Failed to load checks") } }
            )
        }
    }

    private fun runOp(label: String, operation: suspend () -> Result<String>) {
        viewModelScope.launch {
            _state.update { it.copy(isOperating = true) }
            operation().fold(
                onSuccess = { output ->
                    _state.update { it.copy(isOperating = false, operationOutput = output, successMessage = label.removeSuffix("...") + " complete") }
                    // Refresh current tab
                    loadTabData(_state.value.activeTab)
                    // Refresh repo info
                    refreshSelectedRepo()
                },
                onFailure = { e ->
                    _state.update { it.copy(isOperating = false, error = e.message) }
                }
            )
        }
    }

    private fun refreshSelectedRepo() {
        val repo = _state.value.selectedRepo ?: return
        viewModelScope.launch {
            discoverRepos(listOf(repo.path)).fold(
                onSuccess = { repos ->
                    repos.firstOrNull()?.let { updated ->
                        _state.update { it.copy(selectedRepo = updated) }
                    }
                },
                onFailure = {}
            )
        }
    }

    private fun viewDiff(staged: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingDetail = true) }
            getGitDiff(repoPath(), staged = staged).fold(
                onSuccess = { diff ->
                    val title = if (staged) "Staged Changes" else "Unstaged Changes"
                    _state.update { it.copy(diff = diff, showDiffViewer = true, diffTitle = title, isLoadingDetail = false) }
                },
                onFailure = { _state.update { it.copy(isLoadingDetail = false, error = "Failed to load diff") } }
            )
        }
    }

    private fun viewCommitDiff(commit: GitCommit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingDetail = true) }
            getGitDiff(repoPath(), commitHash = commit.hash).fold(
                onSuccess = { diff ->
                    _state.update { it.copy(diff = diff, showDiffViewer = true, diffTitle = "${commit.shortHash}: ${commit.message}", isLoadingDetail = false) }
                },
                onFailure = { _state.update { it.copy(isLoadingDetail = false, error = "Failed to load diff") } }
            )
        }
    }

    private fun viewPrDiff(pr: GitHubPr) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingDetail = true) }
            github.viewPrDiff(repoPath(), pr.number).fold(
                onSuccess = { diffText ->
                    _state.update { it.copy(diffContent = diffText, showDiffViewer = true, diffTitle = "PR #${pr.number}: ${pr.title}", isLoadingDetail = false) }
                },
                onFailure = { _state.update { it.copy(isLoadingDetail = false, error = "Failed to load PR diff") } }
            )
        }
    }

    private fun createPr(title: String, body: String, base: String, draft: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isOperating = true) }
            github.createPr(repoPath(), title, body, base, draft).fold(
                onSuccess = { output ->
                    _state.update { it.copy(isOperating = false, successMessage = "PR created", operationOutput = output) }
                    loadPrs()
                },
                onFailure = { e ->
                    _state.update { it.copy(isOperating = false, error = "Failed to create PR: ${e.message}") }
                }
            )
        }
    }

    private fun mergePr(pr: GitHubPr, method: String) {
        viewModelScope.launch {
            _state.update { it.copy(isOperating = true) }
            github.mergePr(repoPath(), pr.number, method).fold(
                onSuccess = {
                    _state.update { it.copy(isOperating = false, successMessage = "PR #${pr.number} merged") }
                    loadPrs()
                },
                onFailure = { e ->
                    _state.update { it.copy(isOperating = false, error = "Merge failed: ${e.message}") }
                }
            )
        }
    }
}
