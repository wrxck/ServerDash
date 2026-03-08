package com.serverdash.app.domain.model

data class GitRepo(
    val path: String,
    val name: String,
    val currentBranch: String = "",
    val isDirty: Boolean = false,
    val aheadBehind: Pair<Int, Int> = 0 to 0, // ahead, behind
    val remoteUrl: String = "",
    val lastCommitMessage: String = "",
    val lastCommitDate: String = ""
) {
    val isGitHub: Boolean get() = remoteUrl.contains("github.com")
    val ghOwnerRepo: String? get() {
        val match = Regex("github\\.com[:/]([^/]+/[^/.]+)").find(remoteUrl)
        return match?.groupValues?.get(1)?.removeSuffix(".git")
    }
}

data class GitBranch(
    val name: String,
    val isCurrent: Boolean = false,
    val isRemote: Boolean = false,
    val lastCommit: String = "",
    val aheadBehind: String = "" // e.g. "ahead 2, behind 1"
)

data class GitCommit(
    val hash: String,
    val shortHash: String = hash.take(7),
    val author: String,
    val date: String,
    val message: String,
    val isHead: Boolean = false
)

data class GitStatus(
    val staged: List<GitFileChange> = emptyList(),
    val unstaged: List<GitFileChange> = emptyList(),
    val untracked: List<String> = emptyList(),
    val conflicted: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = staged.isEmpty() && unstaged.isEmpty() && untracked.isEmpty() && conflicted.isEmpty()
    val totalChanges: Int get() = staged.size + unstaged.size + untracked.size + conflicted.size
}

data class GitFileChange(
    val path: String,
    val status: FileChangeStatus
)

enum class FileChangeStatus(val symbol: String) {
    ADDED("A"), MODIFIED("M"), DELETED("D"), RENAMED("R"), COPIED("C"), UNMERGED("U")
}

data class GitDiff(
    val files: List<DiffFile> = emptyList(),
    val stats: String = "" // e.g. "3 files changed, 10 insertions(+), 5 deletions(-)"
)

data class DiffFile(
    val path: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val hunks: List<DiffHunk> = emptyList()
)

data class DiffHunk(
    val header: String, // @@ -1,5 +1,7 @@
    val lines: List<DiffLine> = emptyList()
)

data class DiffLine(
    val content: String,
    val type: DiffLineType
)

enum class DiffLineType { CONTEXT, ADDITION, DELETION, HEADER }

// GitHub models (parsed from gh CLI JSON output)

data class GitHubPr(
    val number: Int,
    val title: String,
    val state: String, // OPEN, CLOSED, MERGED
    val author: String,
    val branch: String,
    val baseBranch: String,
    val createdAt: String,
    val updatedAt: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val reviewDecision: String = "", // APPROVED, CHANGES_REQUESTED, REVIEW_REQUIRED
    val isDraft: Boolean = false,
    val labels: List<String> = emptyList(),
    val checksStatus: String = "" // SUCCESS, FAILURE, PENDING
)

data class GitHubIssue(
    val number: Int,
    val title: String,
    val state: String, // OPEN, CLOSED
    val author: String,
    val createdAt: String,
    val labels: List<String> = emptyList(),
    val assignees: List<String> = emptyList(),
    val commentCount: Int = 0
)

data class GitHubCheck(
    val name: String,
    val status: String, // completed, in_progress, queued
    val conclusion: String, // success, failure, neutral, cancelled, skipped
    val startedAt: String = "",
    val completedAt: String = ""
)

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val isPrerelease: Boolean = false,
    val isDraft: Boolean = false,
    val publishedAt: String = "",
    val author: String = ""
)
