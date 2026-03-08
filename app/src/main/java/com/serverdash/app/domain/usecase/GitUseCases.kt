package com.serverdash.app.domain.usecase

import com.serverdash.app.domain.model.*
import com.serverdash.app.domain.repository.SshRepository
import kotlinx.serialization.json.*
import javax.inject.Inject

class DiscoverGitReposUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(searchPaths: List<String> = listOf("~", "/opt", "/var/www", "/srv")): Result<List<GitRepo>> {
        val pathList = searchPaths.joinToString(" ")
        // Find .git dirs up to 4 levels deep, then get repo info for each
        val cmd = """
            for d in $(find $pathList -maxdepth 4 -name '.git' -type d 2>/dev/null | head -30); do
                repo=$(dirname "${'$'}d")
                cd "${'$'}repo" 2>/dev/null || continue
                branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
                dirty=$(git status --porcelain 2>/dev/null | head -1)
                remote=$(git remote get-url origin 2>/dev/null)
                msg=$(git log -1 --format='%s' 2>/dev/null)
                date=$(git log -1 --format='%ci' 2>/dev/null | cut -c1-16)
                ahead=$(git rev-list --count @{u}..HEAD 2>/dev/null || echo 0)
                behind=$(git rev-list --count HEAD..@{u} 2>/dev/null || echo 0)
                echo "${'$'}repo|${'$'}branch|${'$'}{dirty:+dirty}|${'$'}remote|${'$'}msg|${'$'}date|${'$'}ahead|${'$'}behind"
            done
        """.trimIndent()
        return sshRepository.executeCommand(cmd).map { result ->
            result.output.lines()
                .filter { it.contains("|") }
                .mapNotNull { line ->
                    val parts = line.split("|", limit = 8)
                    if (parts.size < 6) return@mapNotNull null
                    GitRepo(
                        path = parts[0].trim(),
                        name = parts[0].trim().split("/").last(),
                        currentBranch = parts[1].trim(),
                        isDirty = parts[2].trim() == "dirty",
                        remoteUrl = parts[3].trim(),
                        lastCommitMessage = parts[4].trim(),
                        lastCommitDate = parts.getOrNull(5)?.trim() ?: "",
                        aheadBehind = (parts.getOrNull(6)?.trim()?.toIntOrNull() ?: 0) to
                            (parts.getOrNull(7)?.trim()?.toIntOrNull() ?: 0)
                    )
                }
        }
    }
}

class GetGitStatusUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(repoPath: String): Result<GitStatus> {
        val cmd = "cd '$repoPath' && git status --porcelain=v1 2>/dev/null"
        return sshRepository.executeCommand(cmd).map { result ->
            val staged = mutableListOf<GitFileChange>()
            val unstaged = mutableListOf<GitFileChange>()
            val untracked = mutableListOf<String>()
            val conflicted = mutableListOf<String>()

            result.output.lines().filter { it.length >= 3 }.forEach { line ->
                val x = line[0] // index status
                val y = line[1] // worktree status
                val path = line.substring(3).trim()

                when {
                    x == '?' && y == '?' -> untracked.add(path)
                    x == 'U' || y == 'U' || (x == 'A' && y == 'A') || (x == 'D' && y == 'D') ->
                        conflicted.add(path)
                    else -> {
                        if (x != ' ' && x != '?') staged.add(GitFileChange(path, parseStatus(x)))
                        if (y != ' ' && y != '?') unstaged.add(GitFileChange(path, parseStatus(y)))
                    }
                }
            }
            GitStatus(staged, unstaged, untracked, conflicted)
        }
    }

    private fun parseStatus(c: Char): FileChangeStatus = when (c) {
        'A' -> FileChangeStatus.ADDED
        'M' -> FileChangeStatus.MODIFIED
        'D' -> FileChangeStatus.DELETED
        'R' -> FileChangeStatus.RENAMED
        'C' -> FileChangeStatus.COPIED
        else -> FileChangeStatus.MODIFIED
    }
}

class GetGitBranchesUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(repoPath: String): Result<List<GitBranch>> {
        val cmd = "cd '$repoPath' && git branch -a --format='%(refname:short)|%(HEAD)|%(objectname:short)|%(upstream:track)' 2>/dev/null"
        return sshRepository.executeCommand(cmd).map { result ->
            result.output.lines()
                .filter { it.contains("|") }
                .map { line ->
                    val parts = line.split("|", limit = 4)
                    val name = parts[0].trim()
                    GitBranch(
                        name = name,
                        isCurrent = parts.getOrNull(1)?.trim() == "*",
                        isRemote = name.startsWith("origin/"),
                        lastCommit = parts.getOrNull(2)?.trim() ?: "",
                        aheadBehind = parts.getOrNull(3)?.trim()?.removeSurrounding("[", "]") ?: ""
                    )
                }
        }
    }
}

class GetGitLogUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(repoPath: String, count: Int = 30, branch: String? = null): Result<List<GitCommit>> {
        val branchArg = branch?.let { "'${it.replace("'", "'\\''")}'" } ?: ""
        val cmd = "cd '$repoPath' && git log $branchArg -n $count --format='%H|%h|%an|%ci|%s' 2>/dev/null"
        return sshRepository.executeCommand(cmd).map { result ->
            val headHash = sshRepository.executeCommand("cd '$repoPath' && git rev-parse HEAD 2>/dev/null")
                .getOrNull()?.output?.trim() ?: ""
            result.output.lines()
                .filter { it.contains("|") }
                .map { line ->
                    val parts = line.split("|", limit = 5)
                    GitCommit(
                        hash = parts[0].trim(),
                        shortHash = parts.getOrNull(1)?.trim() ?: parts[0].take(7),
                        author = parts.getOrNull(2)?.trim() ?: "",
                        date = parts.getOrNull(3)?.trim()?.take(16) ?: "",
                        message = parts.getOrNull(4)?.trim() ?: "",
                        isHead = parts[0].trim() == headHash
                    )
                }
        }
    }
}

class GetGitDiffUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend operator fun invoke(repoPath: String, staged: Boolean = false, commitHash: String? = null): Result<GitDiff> {
        // Validate commit hash format to prevent injection
        val safeHash = commitHash?.takeIf { it.matches(Regex("^[0-9a-fA-F]{4,40}$")) }
        val diffCmd = when {
            safeHash != null -> "git diff $safeHash~1..$safeHash"
            staged -> "git diff --cached"
            else -> "git diff"
        }
        val statsCmd = when {
            safeHash != null -> "git diff --stat $safeHash~1..$safeHash"
            staged -> "git diff --cached --stat"
            else -> "git diff --stat"
        }
        val cmd = "cd '$repoPath' && echo '===DIFF===' && $diffCmd 2>/dev/null && echo '===STATS===' && $statsCmd 2>/dev/null"
        return sshRepository.executeCommand(cmd).map { result ->
            val sections = result.output.split("===DIFF===", "===STATS===")
            val diffText = sections.getOrNull(1)?.trim() ?: ""
            val statsText = sections.getOrNull(2)?.trim()?.lines()?.lastOrNull() ?: ""

            val files = parseDiff(diffText)
            GitDiff(files = files, stats = statsText)
        }
    }

    private fun parseDiff(diffText: String): List<DiffFile> {
        if (diffText.isBlank()) return emptyList()
        val files = mutableListOf<DiffFile>()
        var currentFile: String? = null
        var additions = 0
        var deletions = 0
        val hunks = mutableListOf<DiffHunk>()
        var currentHunkLines = mutableListOf<DiffLine>()
        var currentHunkHeader = ""

        for (line in diffText.lines()) {
            when {
                line.startsWith("diff --git") -> {
                    if (currentFile != null) {
                        if (currentHunkLines.isNotEmpty()) hunks.add(DiffHunk(currentHunkHeader, currentHunkLines.toList()))
                        files.add(DiffFile(currentFile, additions, deletions, hunks.toList()))
                    }
                    currentFile = line.substringAfter("b/").trim()
                    additions = 0; deletions = 0
                    hunks.clear(); currentHunkLines = mutableListOf(); currentHunkHeader = ""
                }
                line.startsWith("@@") -> {
                    if (currentHunkLines.isNotEmpty()) hunks.add(DiffHunk(currentHunkHeader, currentHunkLines.toList()))
                    currentHunkHeader = line
                    currentHunkLines = mutableListOf()
                    currentHunkLines.add(DiffLine(line, DiffLineType.HEADER))
                }
                line.startsWith("+") && !line.startsWith("+++") -> {
                    currentHunkLines.add(DiffLine(line, DiffLineType.ADDITION)); additions++
                }
                line.startsWith("-") && !line.startsWith("---") -> {
                    currentHunkLines.add(DiffLine(line, DiffLineType.DELETION)); deletions++
                }
                currentFile != null -> {
                    currentHunkLines.add(DiffLine(line, DiffLineType.CONTEXT))
                }
            }
        }
        if (currentFile != null) {
            if (currentHunkLines.isNotEmpty()) hunks.add(DiffHunk(currentHunkHeader, currentHunkLines.toList()))
            files.add(DiffFile(currentFile, additions, deletions, hunks.toList()))
        }
        return files
    }
}

class GitOperationUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend fun pull(repoPath: String): Result<String> {
        return sshRepository.executeCommand("cd '$repoPath' && git pull 2>&1").map { it.output }
    }

    suspend fun push(repoPath: String, force: Boolean = false): Result<String> {
        val forceFlag = if (force) " --force-with-lease" else ""
        return sshRepository.executeCommand("cd '$repoPath' && git push$forceFlag 2>&1").map { it.output }
    }

    suspend fun checkout(repoPath: String, branch: String): Result<String> {
        return sshRepository.executeCommand("cd '$repoPath' && git checkout '$branch' 2>&1").map { it.output }
    }

    suspend fun createBranch(repoPath: String, branch: String, startPoint: String = ""): Result<String> {
        val start = if (startPoint.isNotBlank()) " '$startPoint'" else ""
        return sshRepository.executeCommand("cd '$repoPath' && git checkout -b '$branch'$start 2>&1").map { it.output }
    }

    suspend fun commit(repoPath: String, message: String, addAll: Boolean = false): Result<String> {
        val addCmd = if (addAll) "git add -A && " else ""
        val escapedMsg = message.replace("'", "'\\''")
        return sshRepository.executeCommand("cd '$repoPath' && ${addCmd}git commit -m '$escapedMsg' 2>&1").map { it.output }
    }

    suspend fun stageFiles(repoPath: String, files: List<String>): Result<String> {
        val fileArgs = files.joinToString(" ") { "'$it'" }
        return sshRepository.executeCommand("cd '$repoPath' && git add $fileArgs 2>&1").map { it.output }
    }

    suspend fun unstageFiles(repoPath: String, files: List<String>): Result<String> {
        val fileArgs = files.joinToString(" ") { "'$it'" }
        return sshRepository.executeCommand("cd '$repoPath' && git restore --staged $fileArgs 2>&1").map { it.output }
    }

    suspend fun fetch(repoPath: String): Result<String> {
        return sshRepository.executeCommand("cd '$repoPath' && git fetch --all --prune 2>&1").map { it.output }
    }

    suspend fun stash(repoPath: String, pop: Boolean = false): Result<String> {
        val action = if (pop) "pop" else "push"
        return sshRepository.executeCommand("cd '$repoPath' && git stash $action 2>&1").map { it.output }
    }
}

// GitHub via gh CLI

class GitHubUseCase @Inject constructor(
    private val sshRepository: SshRepository
) {
    suspend fun isGhAvailable(): Boolean {
        val result = sshRepository.executeCommand("gh --version 2>/dev/null")
        return result.getOrNull()?.output?.contains("gh version") == true
    }

    suspend fun listPrs(repoPath: String, state: String = "open"): Result<List<GitHubPr>> {
        val cmd = "cd '$repoPath' && gh pr list --state $state --json number,title,state,author,headRefName,baseRefName,createdAt,updatedAt,additions,deletions,reviewDecision,isDraft,labels,statusCheckRollup --limit 30 2>/dev/null"
        return sshRepository.executeCommand(cmd).map { result ->
            parsePrs(result.output)
        }
    }

    suspend fun listIssues(repoPath: String, state: String = "open"): Result<List<GitHubIssue>> {
        val cmd = "cd '$repoPath' && gh issue list --state $state --json number,title,state,author,createdAt,labels,assignees,comments --limit 30 2>/dev/null"
        return sshRepository.executeCommand(cmd).map { result ->
            parseIssues(result.output)
        }
    }

    suspend fun getPrChecks(repoPath: String, prNumber: Int): Result<List<GitHubCheck>> {
        val cmd = "cd '$repoPath' && gh pr checks $prNumber --json name,state,conclusion,startedAt,completedAt 2>/dev/null"
        return sshRepository.executeCommand(cmd).map { result ->
            parseChecks(result.output)
        }
    }

    suspend fun createPr(repoPath: String, title: String, body: String, base: String = "", draft: Boolean = false): Result<String> {
        val baseFlag = if (base.isNotBlank()) " --base '$base'" else ""
        val draftFlag = if (draft) " --draft" else ""
        val escapedTitle = title.replace("'", "'\\''")
        val escapedBody = body.replace("'", "'\\''")
        val cmd = "cd '$repoPath' && gh pr create --title '$escapedTitle' --body '$escapedBody'$baseFlag$draftFlag 2>&1"
        return sshRepository.executeCommand(cmd).map { it.output }
    }

    suspend fun mergePr(repoPath: String, prNumber: Int, method: String = "merge"): Result<String> {
        val cmd = "cd '$repoPath' && gh pr merge $prNumber --$method 2>&1"
        return sshRepository.executeCommand(cmd).map { it.output }
    }

    suspend fun listReleases(repoPath: String): Result<List<GitHubRelease>> {
        val cmd = "cd '$repoPath' && gh release list --json tagName,name,isPrerelease,isDraft,publishedAt,author --limit 10 2>/dev/null"
        return sshRepository.executeCommand(cmd).map { result ->
            parseReleases(result.output)
        }
    }

    suspend fun viewPrDiff(repoPath: String, prNumber: Int): Result<String> {
        val cmd = "cd '$repoPath' && gh pr diff $prNumber 2>/dev/null"
        return sshRepository.executeCommand(cmd).map { it.output }
    }

    suspend fun triggerWorkflow(repoPath: String, workflow: String, ref: String = ""): Result<String> {
        val refFlag = if (ref.isNotBlank()) " --ref '$ref'" else ""
        val cmd = "cd '$repoPath' && gh workflow run '$workflow'$refFlag 2>&1"
        return sshRepository.executeCommand(cmd).map { it.output }
    }

    suspend fun listWorkflowRuns(repoPath: String, limit: Int = 10): Result<String> {
        val cmd = "cd '$repoPath' && gh run list --limit $limit --json databaseId,displayTitle,status,conclusion,headBranch,createdAt 2>/dev/null"
        return sshRepository.executeCommand(cmd).map { it.output }
    }

    private fun parsePrs(json: String): List<GitHubPr> {
        return try {
            Json.parseToJsonElement(json).jsonArray.map { el ->
                val obj = el.jsonObject
                GitHubPr(
                    number = obj["number"]?.jsonPrimitive?.int ?: 0,
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    state = obj["state"]?.jsonPrimitive?.content ?: "",
                    author = obj["author"]?.jsonObject?.get("login")?.jsonPrimitive?.content ?: "",
                    branch = obj["headRefName"]?.jsonPrimitive?.content ?: "",
                    baseBranch = obj["baseRefName"]?.jsonPrimitive?.content ?: "",
                    createdAt = obj["createdAt"]?.jsonPrimitive?.content ?: "",
                    updatedAt = obj["updatedAt"]?.jsonPrimitive?.content ?: "",
                    additions = obj["additions"]?.jsonPrimitive?.int ?: 0,
                    deletions = obj["deletions"]?.jsonPrimitive?.int ?: 0,
                    reviewDecision = obj["reviewDecision"]?.jsonPrimitive?.content ?: "",
                    isDraft = obj["isDraft"]?.jsonPrimitive?.boolean ?: false,
                    labels = obj["labels"]?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content } ?: emptyList(),
                    checksStatus = obj["statusCheckRollup"]?.jsonArray?.let { checks ->
                        when {
                            checks.any { it.jsonObject["conclusion"]?.jsonPrimitive?.content == "FAILURE" } -> "FAILURE"
                            checks.all { it.jsonObject["conclusion"]?.jsonPrimitive?.content == "SUCCESS" } -> "SUCCESS"
                            else -> "PENDING"
                        }
                    } ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun parseIssues(json: String): List<GitHubIssue> {
        return try {
            Json.parseToJsonElement(json).jsonArray.map { el ->
                val obj = el.jsonObject
                GitHubIssue(
                    number = obj["number"]?.jsonPrimitive?.int ?: 0,
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    state = obj["state"]?.jsonPrimitive?.content ?: "",
                    author = obj["author"]?.jsonObject?.get("login")?.jsonPrimitive?.content ?: "",
                    createdAt = obj["createdAt"]?.jsonPrimitive?.content ?: "",
                    labels = obj["labels"]?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content } ?: emptyList(),
                    assignees = obj["assignees"]?.jsonArray?.mapNotNull { it.jsonObject["login"]?.jsonPrimitive?.content } ?: emptyList(),
                    commentCount = obj["comments"]?.jsonObject?.get("totalCount")?.jsonPrimitive?.int ?: 0
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun parseChecks(json: String): List<GitHubCheck> {
        return try {
            Json.parseToJsonElement(json).jsonArray.map { el ->
                val obj = el.jsonObject
                GitHubCheck(
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    status = obj["state"]?.jsonPrimitive?.content ?: "",
                    conclusion = obj["conclusion"]?.jsonPrimitive?.content ?: "",
                    startedAt = obj["startedAt"]?.jsonPrimitive?.content ?: "",
                    completedAt = obj["completedAt"]?.jsonPrimitive?.content ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun parseReleases(json: String): List<GitHubRelease> {
        return try {
            Json.parseToJsonElement(json).jsonArray.map { el ->
                val obj = el.jsonObject
                GitHubRelease(
                    tagName = obj["tagName"]?.jsonPrimitive?.content ?: "",
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    isPrerelease = obj["isPrerelease"]?.jsonPrimitive?.boolean ?: false,
                    isDraft = obj["isDraft"]?.jsonPrimitive?.boolean ?: false,
                    publishedAt = obj["publishedAt"]?.jsonPrimitive?.content ?: "",
                    author = obj["author"]?.jsonObject?.get("login")?.jsonPrimitive?.content ?: ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }
}
