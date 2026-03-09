package com.serverdash.ide

object LanguageDetector {
    private val extensionMap = mapOf(
        "kt" to "kotlin", "kts" to "kotlin",
        "java" to "java",
        "js" to "javascript", "jsx" to "javascriptreact",
        "ts" to "typescript", "tsx" to "typescriptreact",
        "py" to "python", "rb" to "ruby", "rs" to "rust", "go" to "go",
        "c" to "c", "h" to "c", "cpp" to "cpp", "hpp" to "cpp",
        "cs" to "csharp", "swift" to "swift",
        "sh" to "shell", "bash" to "shell", "zsh" to "shell",
        "json" to "json", "xml" to "xml",
        "html" to "html", "htm" to "html",
        "css" to "css", "scss" to "scss", "less" to "less",
        "md" to "markdown",
        "yaml" to "yaml", "yml" to "yaml",
        "toml" to "toml", "ini" to "ini", "conf" to "ini",
        "sql" to "sql",
        "graphql" to "graphql", "gql" to "graphql",
        "r" to "r", "lua" to "lua", "php" to "php",
        "pl" to "perl", "dart" to "dart",
        "ex" to "elixir", "exs" to "elixir",
        "vue" to "vue", "svelte" to "svelte",
    )

    private val nameMap = mapOf(
        "Dockerfile" to "dockerfile",
        "Makefile" to "makefile",
        "CMakeLists.txt" to "cmake",
    )

    fun detect(filename: String): String {
        nameMap[filename]?.let { return it }
        val ext = filename.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty() || ext == filename.lowercase()) return "plaintext"
        return extensionMap[ext] ?: "plaintext"
    }
}
