package com.serverdash.app.core.util

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Markdown AST ──────────────────────────────────────────────────

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class UnorderedList(val items: List<ListItem>) : MdBlock
    data class OrderedList(val items: List<ListItem>) : MdBlock
    data class Blockquote(val text: String) : MdBlock
    data object HorizontalRule : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data class TaskList(val items: List<TaskItem>) : MdBlock
}

private data class ListItem(val text: String, val indent: Int = 0)
private data class TaskItem(val text: String, val checked: Boolean)

// ── Parser ────────────────────────────────────────────────────────

private fun parseMarkdown(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = markdown.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        when {
            // Code block
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
                i++ // skip closing ```
            }
            // Heading
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length.coerceAtMost(6)
                val text = trimmed.drop(level).trimStart()
                blocks.add(MdBlock.Heading(level, text))
                i++
            }
            // Horizontal rule
            trimmed.matches(Regex("^(---+|\\*\\*\\*+|___+)\\s*$")) -> {
                blocks.add(MdBlock.HorizontalRule)
                i++
            }
            // Table
            trimmed.startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().matches(Regex("^\\|[\\s:|-]+\\|$")) -> {
                val headers = parsePipeRow(trimmed)
                i += 2 // skip header and separator
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows.add(parsePipeRow(lines[i].trim()))
                    i++
                }
                blocks.add(MdBlock.Table(headers, rows))
            }
            // Task list
            trimmed.matches(Regex("^[-*+]\\s+\\[([ xX])]\\s+.*")) -> {
                val taskItems = mutableListOf<TaskItem>()
                while (i < lines.size) {
                    val tl = lines[i].trimStart()
                    val match = Regex("^[-*+]\\s+\\[([ xX])]\\s+(.*)").find(tl)
                    if (match != null) {
                        val checked = match.groupValues[1].lowercase() == "x"
                        taskItems.add(TaskItem(match.groupValues[2], checked))
                        i++
                    } else break
                }
                blocks.add(MdBlock.TaskList(taskItems))
            }
            // Unordered list
            trimmed.matches(Regex("^[-*+]\\s+.*")) -> {
                val items = mutableListOf<ListItem>()
                while (i < lines.size) {
                    val ll = lines[i]
                    val indent = ll.length - ll.trimStart().length
                    val ulMatch = Regex("^\\s*[-*+]\\s+(.*)").find(ll)
                    if (ulMatch != null) {
                        items.add(ListItem(ulMatch.groupValues[1], indent / 2))
                        i++
                    } else break
                }
                blocks.add(MdBlock.UnorderedList(items))
            }
            // Ordered list
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val items = mutableListOf<ListItem>()
                while (i < lines.size) {
                    val ll = lines[i]
                    val indent = ll.length - ll.trimStart().length
                    val olMatch = Regex("^\\s*\\d+\\.\\s+(.*)").find(ll)
                    if (olMatch != null) {
                        items.add(ListItem(olMatch.groupValues[1], indent / 2))
                        i++
                    } else break
                }
                blocks.add(MdBlock.OrderedList(items))
            }
            // Blockquote
            trimmed.startsWith(">") -> {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quoteLines.add(lines[i].trimStart().removePrefix(">").trimStart())
                    i++
                }
                blocks.add(MdBlock.Blockquote(quoteLines.joinToString("\n")))
            }
            // Empty line
            trimmed.isBlank() -> {
                i++
            }
            // Paragraph (collect consecutive non-blank lines)
            else -> {
                val paraLines = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank() &&
                    !lines[i].trimStart().startsWith("#") &&
                    !lines[i].trimStart().startsWith("```") &&
                    !lines[i].trimStart().startsWith(">") &&
                    !lines[i].trimStart().matches(Regex("^[-*+]\\s+.*")) &&
                    !lines[i].trimStart().matches(Regex("^\\d+\\.\\s+.*")) &&
                    !lines[i].trimStart().startsWith("|") &&
                    !lines[i].trimStart().matches(Regex("^(---+|\\*\\*\\*+|___+)\\s*$"))
                ) {
                    paraLines.add(lines[i])
                    i++
                }
                if (paraLines.isNotEmpty()) {
                    blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
                }
            }
        }
    }
    return blocks
}

private fun parsePipeRow(line: String): List<String> {
    return line.trim().removeSurrounding("|").split("|").map { it.trim() }
}

// ── Inline rendering ──────────────────────────────────────────────

@Composable
private fun renderInlineMarkdown(
    text: String,
    baseStyle: SpanStyle = SpanStyle()
): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeForeground = MaterialTheme.colorScheme.onSurfaceVariant

    return remember(text, baseStyle) {
        buildAnnotatedString {
            pushStyle(baseStyle)
            var i = 0
            val s = text

            while (i < s.length) {
                when {
                    // Bold italic ***text***
                    s.startsWith("***", i) -> {
                        val end = s.indexOf("***", i + 3)
                        if (end > 0) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                                append(s.substring(i + 3, end))
                            }
                            i = end + 3
                        } else {
                            append(s[i])
                            i++
                        }
                    }
                    // Bold **text**
                    s.startsWith("**", i) -> {
                        val end = s.indexOf("**", i + 2)
                        if (end > 0) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(s.substring(i + 2, end))
                            }
                            i = end + 2
                        } else {
                            append(s[i])
                            i++
                        }
                    }
                    // Italic *text* (but not ** handled above)
                    s[i] == '*' && (i == 0 || s[i - 1] != '*') && (i + 1 < s.length && s[i + 1] != '*') -> {
                        val end = s.indexOf('*', i + 1)
                        if (end > 0) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(s.substring(i + 1, end))
                            }
                            i = end + 1
                        } else {
                            append(s[i])
                            i++
                        }
                    }
                    // Inline code `code`
                    s[i] == '`' -> {
                        val end = s.indexOf('`', i + 1)
                        if (end > 0) {
                            withStyle(SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackground,
                                color = codeForeground,
                                fontSize = 13.sp
                            )) {
                                append(" ${s.substring(i + 1, end)} ")
                            }
                            i = end + 1
                        } else {
                            append(s[i])
                            i++
                        }
                    }
                    // Link [text](url)
                    s[i] == '[' -> {
                        val closeBracket = s.indexOf(']', i + 1)
                        if (closeBracket > 0 && closeBracket + 1 < s.length && s[closeBracket + 1] == '(') {
                            val closeParen = s.indexOf(')', closeBracket + 2)
                            if (closeParen > 0) {
                                val linkText = s.substring(i + 1, closeBracket)
                                val url = s.substring(closeBracket + 2, closeParen)
                                pushStringAnnotation("URL", url)
                                withStyle(SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )) {
                                    append(linkText)
                                }
                                pop()
                                i = closeParen + 1
                            } else {
                                append(s[i])
                                i++
                            }
                        } else {
                            append(s[i])
                            i++
                        }
                    }
                    // Image ![alt](url)
                    s[i] == '!' && i + 1 < s.length && s[i + 1] == '[' -> {
                        val closeBracket = s.indexOf(']', i + 2)
                        if (closeBracket > 0 && closeBracket + 1 < s.length && s[closeBracket + 1] == '(') {
                            val closeParen = s.indexOf(')', closeBracket + 2)
                            if (closeParen > 0) {
                                val alt = s.substring(i + 2, closeBracket)
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = linkColor)) {
                                    append("[Image: $alt]")
                                }
                                i = closeParen + 1
                            } else {
                                append(s[i])
                                i++
                            }
                        } else {
                            append(s[i])
                            i++
                        }
                    }
                    // Strikethrough ~~text~~
                    s.startsWith("~~", i) -> {
                        val end = s.indexOf("~~", i + 2)
                        if (end > 0) {
                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                                append(s.substring(i + 2, end))
                            }
                            i = end + 2
                        } else {
                            append(s[i])
                            i++
                        }
                    }
                    else -> {
                        append(s[i])
                        i++
                    }
                }
            }
            pop()
        }
    }
}

// ── Code block syntax highlighting (reuses codebase patterns) ─────

@Composable
private fun syntaxHighlightCode(code: String, language: String): AnnotatedString {
    val keyColor = MaterialTheme.colorScheme.primary
    val stringColor = Color(0xFF66BB6A)
    val numberColor = Color(0xFFF0B866)
    val commentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val keywordColor = Color(0xFFCBB2F0)
    val defaultColor = MaterialTheme.colorScheme.onSurface

    return remember(code, language) {
        buildAnnotatedString {
            // Simple keyword-based highlighting
            val keywords = when (language.lowercase()) {
                "kotlin", "kt" -> setOf("fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while", "return", "import", "package", "data", "sealed", "private", "public", "internal", "protected", "override", "suspend", "init", "companion", "try", "catch", "finally", "throw", "null", "true", "false", "is", "in", "as", "by", "this", "super")
                "java" -> setOf("class", "interface", "public", "private", "protected", "static", "final", "void", "int", "long", "double", "float", "boolean", "String", "if", "else", "for", "while", "return", "import", "package", "new", "try", "catch", "finally", "throw", "null", "true", "false", "this", "super", "extends", "implements")
                "python", "py" -> setOf("def", "class", "if", "elif", "else", "for", "while", "return", "import", "from", "as", "try", "except", "finally", "raise", "None", "True", "False", "self", "with", "yield", "lambda", "and", "or", "not", "in", "is", "pass", "break", "continue")
                "javascript", "js", "typescript", "ts" -> setOf("function", "const", "let", "var", "class", "if", "else", "for", "while", "return", "import", "export", "from", "default", "new", "try", "catch", "finally", "throw", "null", "undefined", "true", "false", "this", "async", "await", "of", "in", "typeof", "instanceof")
                "bash", "sh", "shell" -> setOf("if", "then", "else", "fi", "for", "do", "done", "while", "case", "esac", "function", "return", "exit", "echo", "export", "local", "readonly", "true", "false")
                "json" -> emptySet()
                else -> emptySet()
            }

            val lines = code.lines()
            lines.forEachIndexed { lineIdx, line ->
                if (lineIdx > 0) append("\n")

                // JSON special handling
                if (language.lowercase() == "json") {
                    var j = 0
                    while (j < line.length) {
                        when {
                            line[j] == '"' -> {
                                val end = line.indexOf('"', j + 1).let { if (it < 0) line.length else it + 1 }
                                val str = line.substring(j, end)
                                val afterStr = line.substring(end).trimStart()
                                val color = if (afterStr.startsWith(":")) keyColor else stringColor
                                withStyle(SpanStyle(color = color)) { append(str) }
                                j = end
                            }
                            line[j].isDigit() || (line[j] == '-' && j + 1 < line.length && line[j + 1].isDigit()) -> {
                                val start = j
                                while (j < line.length && (line[j].isDigit() || line[j] == '.' || line[j] == '-')) j++
                                withStyle(SpanStyle(color = numberColor)) { append(line.substring(start, j)) }
                            }
                            line.startsWith("true", j) -> { withStyle(SpanStyle(color = keywordColor)) { append("true") }; j += 4 }
                            line.startsWith("false", j) -> { withStyle(SpanStyle(color = keywordColor)) { append("false") }; j += 5 }
                            line.startsWith("null", j) -> { withStyle(SpanStyle(color = keywordColor)) { append("null") }; j += 4 }
                            else -> { withStyle(SpanStyle(color = defaultColor)) { append(line[j].toString()) }; j++ }
                        }
                    }
                    return@forEachIndexed
                }

                // Generic highlighting: comments, strings, numbers, keywords
                var j = 0
                while (j < line.length) {
                    when {
                        // Single-line comment
                        (line.startsWith("//", j) || line.startsWith("#", j) && language.lowercase() in listOf("python", "py", "bash", "sh", "shell", "yaml", "yml")) -> {
                            withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                                append(line.substring(j))
                            }
                            j = line.length
                        }
                        // String
                        line[j] == '"' || line[j] == '\'' -> {
                            val quote = line[j]
                            val start = j
                            j++
                            while (j < line.length && line[j] != quote) {
                                if (line[j] == '\\' && j + 1 < line.length) j++
                                j++
                            }
                            if (j < line.length) j++ // include closing quote
                            withStyle(SpanStyle(color = stringColor)) { append(line.substring(start, j)) }
                        }
                        // Number
                        line[j].isDigit() -> {
                            val start = j
                            while (j < line.length && (line[j].isDigit() || line[j] == '.' || line[j] == 'f' || line[j] == 'L')) j++
                            withStyle(SpanStyle(color = numberColor)) { append(line.substring(start, j)) }
                        }
                        // Word (potential keyword)
                        line[j].isLetter() || line[j] == '_' -> {
                            val start = j
                            while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                            val word = line.substring(start, j)
                            if (word in keywords) {
                                withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) { append(word) }
                            } else {
                                withStyle(SpanStyle(color = defaultColor)) { append(word) }
                            }
                        }
                        else -> {
                            withStyle(SpanStyle(color = defaultColor)) { append(line[j].toString()) }
                            j++
                        }
                    }
                }
            }
        }
    }
}

// ── Main composable ───────────────────────────────────────────────

@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineLarge
                            2 -> MaterialTheme.typography.headlineMedium
                            3 -> MaterialTheme.typography.headlineSmall
                            4 -> MaterialTheme.typography.titleLarge
                            5 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                        val annotated = renderInlineMarkdown(
                            block.text,
                            SpanStyle(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = annotated,
                            style = style
                        )
                        if (block.level <= 2) {
                            HorizontalDivider(Modifier.padding(top = 4.dp))
                        }
                    }

                    is MdBlock.Paragraph -> {
                        val annotated = renderInlineMarkdown(block.text)
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    is MdBlock.CodeBlock -> {
                        val codeBg = MaterialTheme.colorScheme.surfaceVariant
                        val langLabelColor = MaterialTheme.colorScheme.onSurfaceVariant

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(codeBg)
                        ) {
                            if (block.language.isNotBlank()) {
                                Text(
                                    block.language,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = langLabelColor,
                                    modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                                )
                            }
                            val highlighted = syntaxHighlightCode(block.code, block.language)
                            Text(
                                text = highlighted,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            )
                        }
                    }

                    is MdBlock.UnorderedList -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            block.items.forEach { item ->
                                Row(Modifier.padding(start = (item.indent * 16).dp)) {
                                    val bullet = when (item.indent) {
                                        0 -> "\u2022"
                                        1 -> "\u25E6"
                                        else -> "\u25AA"
                                    }
                                    Text(
                                        "$bullet  ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val annotated = renderInlineMarkdown(item.text)
                                    Text(
                                        text = annotated,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    is MdBlock.OrderedList -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            block.items.forEachIndexed { index, item ->
                                Row(Modifier.padding(start = (item.indent * 16).dp)) {
                                    Text(
                                        "${index + 1}. ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val annotated = renderInlineMarkdown(item.text)
                                    Text(
                                        text = annotated,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    is MdBlock.Blockquote -> {
                        val borderColor = MaterialTheme.colorScheme.primary
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    drawLine(
                                        color = borderColor,
                                        start = Offset(0f, 0f),
                                        end = Offset(0f, size.height),
                                        strokeWidth = 4.dp.toPx()
                                    )
                                }
                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            val annotated = renderInlineMarkdown(block.text)
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    is MdBlock.HorizontalRule -> {
                        HorizontalDivider(
                            Modifier.padding(vertical = 8.dp),
                            thickness = 2.dp
                        )
                    }

                    is MdBlock.TaskList -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            block.items.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = item.checked,
                                        onCheckedChange = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    val annotated = renderInlineMarkdown(item.text)
                                    Text(
                                        text = annotated,
                                        style = MaterialTheme.typography.bodyMedium.let {
                                            if (item.checked) it.copy(
                                                textDecoration = TextDecoration.LineThrough,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ) else it
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is MdBlock.Table -> {
                        val borderColor = MaterialTheme.colorScheme.outlineVariant
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            // Header row
                            Row(
                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                block.headers.forEach { header ->
                                    Text(
                                        header,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier
                                            .widthIn(min = 80.dp)
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = borderColor)
                            // Data rows
                            block.rows.forEachIndexed { rowIdx, row ->
                                Row(
                                    Modifier.let {
                                        if (rowIdx % 2 == 1) it.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        else it
                                    }
                                ) {
                                    row.forEach { cell ->
                                        Text(
                                            cell,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier
                                                .widthIn(min = 80.dp)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                if (rowIdx < block.rows.lastIndex) {
                                    HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Markdown Editor with Preview/Edit toggle ──────────────────────

@Composable
fun MarkdownEditorView(
    content: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    var isPreviewMode by remember { mutableStateOf(true) }

    Column(modifier.fillMaxSize()) {
        // Mode toggle
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.width(200.dp)) {
                SegmentedButton(
                    selected = isPreviewMode,
                    onClick = { isPreviewMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Icon(Icons.Default.Visibility, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Preview")
                }
                SegmentedButton(
                    selected = !isPreviewMode,
                    onClick = { isPreviewMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
            }
        }

        if (isPreviewMode) {
            // Rendered markdown preview
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    if (content.isBlank()) {
                        Text(
                            "Nothing to preview",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        MarkdownView(content)
                    }
                }
            }
        } else {
            // Code editor with line numbers
            Row(Modifier.fillMaxSize()) {
                // Line numbers
                val lineCount = content.lines().size
                val scrollState = rememberScrollState()
                Column(
                    Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(top = 16.dp, end = 4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lineCount.coerceAtLeast(1)) {
                        Text(
                            "$i",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Editor
                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    readOnly = readOnly
                )
            }
        }
    }
}
