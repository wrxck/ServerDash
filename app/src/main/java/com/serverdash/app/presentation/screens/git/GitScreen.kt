package com.serverdash.app.presentation.screens.git

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    onNavigateBack: () -> Unit,
    viewModel: GitViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbarHostState.showSnackbar(it); viewModel.onEvent(GitEvent.DismissError) } }
    LaunchedEffect(state.successMessage) { state.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.onEvent(GitEvent.DismissSuccess) } }

    // Dialogs
    if (state.showCommitDialog) CommitDialog(onDismiss = { viewModel.onEvent(GitEvent.DismissDialog) }, onCommit = { msg, addAll -> viewModel.onEvent(GitEvent.Commit(msg, addAll)) })
    if (state.showCreateBranchDialog) CreateBranchDialog(onDismiss = { viewModel.onEvent(GitEvent.DismissDialog) }, onCreate = { name -> viewModel.onEvent(GitEvent.CreateBranch(name)) })
    if (state.showCreatePrDialog) CreatePrDialog(
        branches = state.branches.filter { !it.isRemote }.map { it.name },
        onDismiss = { viewModel.onEvent(GitEvent.DismissDialog) },
        onCreate = { title, body, base, draft -> viewModel.onEvent(GitEvent.CreatePr(title, body, base, draft)) }
    )
    if (state.operationOutput != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(GitEvent.DismissOperationOutput) },
            title = { Text("Output") },
            text = { Text(state.operationOutput!!, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) },
            confirmButton = { TextButton(onClick = { viewModel.onEvent(GitEvent.DismissOperationOutput) }) { Text("OK") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.selectedRepo != null) {
                        Column {
                            Text(state.selectedRepo!!.name)
                            Text(state.selectedRepo!!.currentBranch, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    } else Text("Git")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.selectedRepo != null) viewModel.onEvent(GitEvent.DeselectRepo)
                        else onNavigateBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (state.selectedRepo != null) {
                        if (state.isOperating) {
                            CircularProgressIndicator(Modifier.size(20.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                        }
                        IconButton(onClick = { viewModel.onEvent(GitEvent.Fetch) }) { Icon(Icons.Default.Sync, "Fetch") }
                    } else {
                        IconButton(onClick = { viewModel.onEvent(GitEvent.LoadRepos) }) { Icon(Icons.Default.Refresh, "Refresh") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.showDiffViewer) {
                DiffViewer(state, viewModel)
            } else if (state.selectedRepo != null) {
                RepoDetailView(state, viewModel)
            } else {
                RepoListView(state, viewModel)
            }
        }
    }
}

// ── Repo List ─────────────────────────────────────────────────────

@Composable
private fun RepoListView(state: GitUiState, viewModel: GitViewModel) {
    if (state.isLoadingRepos) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.repos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FolderOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("No git repos found", style = MaterialTheme.typography.titleMedium)
                Text("Searched ~, /opt, /var/www, /srv", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.repos) { repo ->
            Card(Modifier.fillMaxWidth().clickable { viewModel.onEvent(GitEvent.SelectRepo(repo)) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (repo.isGitHub) Icons.Default.Code else Icons.Default.Folder,
                        null, Modifier.size(28.dp),
                        tint = if (repo.isGitHub) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(repo.name, style = MaterialTheme.typography.titleSmall)
                            if (repo.isDirty) {
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF0B866)))
                            }
                        }
                        Text(repo.path, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountTree, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(repo.currentBranch, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (repo.aheadBehind.first > 0 || repo.aheadBehind.second > 0) {
                                Spacer(Modifier.width(8.dp))
                                if (repo.aheadBehind.first > 0) Text("+${repo.aheadBehind.first}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF66BB6A))
                                if (repo.aheadBehind.second > 0) Text("-${repo.aheadBehind.second}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF5350))
                            }
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Repo Detail ───────────────────────────────────────────────────

@Composable
private fun RepoDetailView(state: GitUiState, viewModel: GitViewModel) {
    val tabs = buildList {
        add("Status")
        add("Branches")
        add("Log")
        if (state.isGhAvailable) {
            add("PRs")
            add("Issues")
        }
    }

    // Action bar
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AssistChip(onClick = { viewModel.onEvent(GitEvent.Pull) }, label = { Text("Pull") }, leadingIcon = { Icon(Icons.Default.Download, null, Modifier.size(16.dp)) })
        AssistChip(onClick = { viewModel.onEvent(GitEvent.Push) }, label = { Text("Push") }, leadingIcon = { Icon(Icons.Default.Upload, null, Modifier.size(16.dp)) })
        AssistChip(onClick = { viewModel.onEvent(GitEvent.ShowCommitDialog) }, label = { Text("Commit") }, leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) })
        AssistChip(onClick = { viewModel.onEvent(GitEvent.ShowCreateBranchDialog) }, label = { Text("Branch") }, leadingIcon = { Icon(Icons.Default.AccountTree, null, Modifier.size(16.dp)) })
        AssistChip(onClick = { viewModel.onEvent(GitEvent.Stash) }, label = { Text("Stash") })
        AssistChip(onClick = { viewModel.onEvent(GitEvent.StashPop) }, label = { Text("Pop") })
        if (state.isGhAvailable) {
            AssistChip(onClick = { viewModel.onEvent(GitEvent.ShowCreatePrDialog) }, label = { Text("New PR") }, leadingIcon = { Icon(Icons.Default.RateReview, null, Modifier.size(16.dp)) })
        }
    }

    ScrollableTabRow(selectedTabIndex = state.activeTab, edgePadding = 0.dp) {
        tabs.forEachIndexed { index, title ->
            Tab(selected = state.activeTab == index, onClick = { viewModel.onEvent(GitEvent.SelectTab(index)) }, text = { Text(title) })
        }
    }

    if (state.isLoadingDetail) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        when (state.activeTab) {
            0 -> StatusTab(state, viewModel)
            1 -> BranchesTab(state, viewModel)
            2 -> LogTab(state, viewModel)
            3 -> PrsTab(state, viewModel)
            4 -> IssuesTab(state, viewModel)
        }
    }
}

// ── Status Tab ────────────────────────────────────────────────────

@Composable
private fun StatusTab(state: GitUiState, viewModel: GitViewModel) {
    val status = state.gitStatus
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status.isEmpty) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = Color(0xFF66BB6A))
                        Spacer(Modifier.height(8.dp))
                        Text("Working tree clean", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (status.staged.isNotEmpty()) {
            item {
                FileChangeSection("Staged (${status.staged.size})", Color(0xFF66BB6A), status.staged,
                    onViewDiff = { viewModel.onEvent(GitEvent.ViewDiff(staged = true)) },
                    onUnstage = { files -> viewModel.onEvent(GitEvent.UnstageFiles(files.map { it.path })) })
            }
        }
        if (status.unstaged.isNotEmpty()) {
            item {
                FileChangeSection("Unstaged (${status.unstaged.size})", Color(0xFFF0B866), status.unstaged,
                    onViewDiff = { viewModel.onEvent(GitEvent.ViewDiff(staged = false)) },
                    onStage = { files -> viewModel.onEvent(GitEvent.StageFiles(files.map { it.path })) })
            }
        }
        if (status.untracked.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Untracked (${status.untracked.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { viewModel.onEvent(GitEvent.StageFiles(status.untracked)) }) { Text("Stage All") }
                        }
                        status.untracked.forEach { file ->
                            Text("? $file", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (status.conflicted.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEF5350).copy(alpha = 0.1f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Conflicts (${status.conflicted.size})", style = MaterialTheme.typography.titleSmall, color = Color(0xFFEF5350))
                        status.conflicted.forEach { file ->
                            Text("! $file", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Color(0xFFEF5350))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileChangeSection(
    title: String, accentColor: Color, files: List<GitFileChange>,
    onViewDiff: () -> Unit, onStage: ((List<GitFileChange>) -> Unit)? = null, onUnstage: ((List<GitFileChange>) -> Unit)? = null
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = accentColor)
                Row {
                    TextButton(onClick = onViewDiff) { Text("Diff") }
                    onStage?.let { TextButton(onClick = { it(files) }) { Text("Stage All") } }
                    onUnstage?.let { TextButton(onClick = { it(files) }) { Text("Unstage All") } }
                }
            }
            files.forEach { change ->
                Text(
                    "${change.status.symbol} ${change.path}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = when (change.status) {
                        FileChangeStatus.ADDED -> Color(0xFF66BB6A)
                        FileChangeStatus.DELETED -> Color(0xFFEF5350)
                        FileChangeStatus.MODIFIED -> Color(0xFFF0B866)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

// ── Branches Tab ──────────────────────────────────────────────────

@Composable
private fun BranchesTab(state: GitUiState, viewModel: GitViewModel) {
    val local = state.branches.filter { !it.isRemote }
    val remote = state.branches.filter { it.isRemote }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Local", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        items(local) { branch -> BranchRow(branch) { viewModel.onEvent(GitEvent.Checkout(branch.name)) } }
        if (remote.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)); Text("Remote", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(remote) { branch -> BranchRow(branch) { viewModel.onEvent(GitEvent.Checkout(branch.name)) } }
        }
    }
}

@Composable
private fun BranchRow(branch: GitBranch, onCheckout: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(enabled = !branch.isCurrent) { onCheckout() },
        colors = if (branch.isCurrent) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else CardDefaults.cardColors()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (branch.isCurrent) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Default.AccountTree, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(branch.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (branch.isCurrent) FontWeight.Bold else FontWeight.Normal)
                if (branch.aheadBehind.isNotBlank()) Text(branch.aheadBehind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(branch.lastCommit, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Log Tab ───────────────────────────────────────────────────────

@Composable
private fun LogTab(state: GitUiState, viewModel: GitViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(state.commits) { commit ->
            Card(Modifier.fillMaxWidth().clickable { viewModel.onEvent(GitEvent.ViewCommitDiff(commit)) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        commit.shortHash,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(commit.message, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row {
                            Text(commit.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(commit.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (commit.isHead) {
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("HEAD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── PRs Tab ───────────────────────────────────────────────────────

@Composable
private fun PrsTab(state: GitUiState, viewModel: GitViewModel) {
    if (!state.isGhAvailable) {
        GhNotAvailable()
        return
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("open", "closed", "all").forEach { filter ->
                FilterChip(selected = state.prFilter == filter, onClick = { viewModel.onEvent(GitEvent.SetPrFilter(filter)) }, label = { Text(filter.replaceFirstChar { it.uppercase() }) })
            }
        }
        if (state.isLoadingGh) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.pullRequests.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No pull requests", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.pullRequests) { pr -> PrCard(pr, viewModel) }
            }
        }
    }
}

@Composable
private fun PrCard(pr: GitHubPr, viewModel: GitViewModel) {
    Card(Modifier.fillMaxWidth().clickable { viewModel.onEvent(GitEvent.ViewPrDiff(pr)) }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${pr.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(pr.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                PrStateBadge(pr.state)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${pr.branch} -> ${pr.baseBranch}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("+${pr.additions}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF66BB6A))
                Text(" -${pr.deletions}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF5350))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pr.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pr.isDraft) { Spacer(Modifier.width(6.dp)); Text("DRAFT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (pr.reviewDecision.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    ReviewBadge(pr.reviewDecision)
                }
                if (pr.checksStatus.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    ChecksBadge(pr.checksStatus)
                }
            }
            if (pr.labels.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pr.labels.take(5).forEach { label ->
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrStateBadge(state: String) {
    val (color, text) = when (state.uppercase()) {
        "OPEN" -> Color(0xFF66BB6A) to "Open"
        "CLOSED" -> Color(0xFFEF5350) to "Closed"
        "MERGED" -> Color(0xFFCBB2F0) to "Merged"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to state
    }
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReviewBadge(decision: String) {
    val (color, text) = when (decision) {
        "APPROVED" -> Color(0xFF66BB6A) to "Approved"
        "CHANGES_REQUESTED" -> Color(0xFFEF5350) to "Changes"
        else -> Color(0xFFF0B866) to "Review"
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun ChecksBadge(status: String) {
    val (icon, color) = when (status) {
        "SUCCESS" -> Icons.Default.CheckCircle to Color(0xFF66BB6A)
        "FAILURE" -> Icons.Default.Error to Color(0xFFEF5350)
        else -> Icons.Default.Pending to Color(0xFFF0B866)
    }
    Icon(icon, status, Modifier.size(14.dp), tint = color)
}

// ── Issues Tab ────────────────────────────────────────────────────

@Composable
private fun IssuesTab(state: GitUiState, viewModel: GitViewModel) {
    if (!state.isGhAvailable) { GhNotAvailable(); return }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("open", "closed", "all").forEach { filter ->
                FilterChip(selected = state.issueFilter == filter, onClick = { viewModel.onEvent(GitEvent.SetIssueFilter(filter)) }, label = { Text(filter.replaceFirstChar { it.uppercase() }) })
            }
        }
        if (state.isLoadingGh) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.issues.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No issues", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.issues) { issue ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                if (issue.state == "OPEN") Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline,
                                null, Modifier.size(20.dp),
                                tint = if (issue.state == "OPEN") Color(0xFF66BB6A) else Color(0xFFCBB2F0)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text("#${issue.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text(issue.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Row {
                                    Text(issue.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (issue.commentCount > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(" ${issue.commentCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (issue.labels.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        issue.labels.take(4).forEach { label ->
                                            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                                Text(label, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GhNotAvailable() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("GitHub CLI not available", style = MaterialTheme.typography.titleMedium)
            Text("Install gh on the server for GitHub features", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Diff Viewer ───────────────────────────────────────────────────

@Composable
private fun DiffViewer(state: GitUiState, viewModel: GitViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.onEvent(GitEvent.DismissDiff) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(state.diffTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HorizontalDivider()

        if (state.diff.files.isNotEmpty()) {
            // Stats summary
            Text(state.diff.stats, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.diff.files) { file ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(file.path, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f))
                                Text("+${file.additions}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF66BB6A))
                                Text(" -${file.deletions}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF5350))
                            }
                            Spacer(Modifier.height(4.dp))
                            file.hunks.forEach { hunk ->
                                Column(Modifier.horizontalScroll(rememberScrollState())) {
                                    hunk.lines.forEach { line ->
                                        val (bg, fg) = when (line.type) {
                                            DiffLineType.ADDITION -> Color(0xFF66BB6A).copy(alpha = 0.15f) to Color(0xFF66BB6A)
                                            DiffLineType.DELETION -> Color(0xFFEF5350).copy(alpha = 0.15f) to Color(0xFFEF5350)
                                            DiffLineType.HEADER -> Color.Transparent to MaterialTheme.colorScheme.primary
                                            DiffLineType.CONTEXT -> Color.Transparent to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        }
                                        Text(
                                            line.content,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp),
                                            color = fg,
                                            modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (state.diffContent.isNotBlank()) {
            // Raw diff (e.g. from gh pr diff)
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                val lines = state.diffContent.lines()
                items(lines.size) { i ->
                    val line = lines[i]
                    val (bg, fg) = when {
                        line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF66BB6A).copy(alpha = 0.15f) to Color(0xFF66BB6A)
                        line.startsWith("-") && !line.startsWith("---") -> Color(0xFFEF5350).copy(alpha = 0.15f) to Color(0xFFEF5350)
                        line.startsWith("@@") -> Color.Transparent to MaterialTheme.colorScheme.primary
                        else -> Color.Transparent to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                    Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = fg, modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 4.dp))
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No changes", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────

@Composable
private fun CommitDialog(onDismiss: () -> Unit, onCommit: (String, Boolean) -> Unit) {
    var message by remember { mutableStateOf("") }
    var addAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Commit") },
        text = {
            Column {
                OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = addAll, onCheckedChange = { addAll = it })
                    Text("Stage all changes (git add -A)")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCommit(message, addAll) }, enabled = message.isNotBlank()) { Text("Commit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreateBranchDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Branch") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Branch name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreatePrDialog(branches: List<String>, onDismiss: () -> Unit, onCreate: (String, String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Pull Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(value = base, onValueChange = { base = it }, label = { Text("Base branch (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = draft, onCheckedChange = { draft = it })
                    Text("Draft PR")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(title, body, base, draft) }, enabled = title.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
