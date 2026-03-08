package com.serverdash.app.core.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

// ANSI standard colors (30-37)
private val ansiColors = mapOf(
    30 to Color(0xFF1E1E1E), // black
    31 to Color(0xFFEF5350), // red
    32 to Color(0xFF66BB6A), // green
    33 to Color(0xFFFFCA28), // yellow
    34 to Color(0xFF42A5F5), // blue
    35 to Color(0xFFCE93D8), // magenta
    36 to Color(0xFF4DD0E1), // cyan
    37 to Color(0xFFE0E0E0)  // white
)

// ANSI bright colors (90-97)
private val ansiBrightColors = mapOf(
    90 to Color(0xFF757575), // bright black (gray)
    91 to Color(0xFFFF8A80), // bright red
    92 to Color(0xFFA5D6A7), // bright green
    93 to Color(0xFFFFE082), // bright yellow
    94 to Color(0xFF90CAF9), // bright blue
    95 to Color(0xFFE1BEE7), // bright magenta
    96 to Color(0xFF80DEEA), // bright cyan
    97 to Color(0xFFFFFFFF)  // bright white
)

// Heuristic highlight colors
private val colorOrange = Color(0xFFFF9800)
private val colorCyan = Color(0xFF4DD0E1)
private val colorYellow = Color(0xFFFFCA28)
private val colorRed = Color(0xFFEF5350)
private val colorGreen = Color(0xFF66BB6A)
private val colorGray = Color(0xFF9E9E9E)
private val colorPrimary = Color(0xFF5CCFE6)

private val ansiEscapeRegex = Regex("""\x1B\[([0-9;]*)m""")

private val errorKeywords = setOf("error", "fail", "failed", "denied", "refused", "fatal", "panic", "critical")
private val successKeywords = setOf("ok", "success", "active", "running", "enabled", "started", "passed", "done")
private val warningKeywords = setOf("warn", "warning", "deprecated", "timeout", "slow", "retry")

fun highlightTerminalOutput(text: String): AnnotatedString {
    // Check if text contains ANSI escape codes
    if (ansiEscapeRegex.containsMatchIn(text)) {
        return parseAnsiCodes(text)
    }
    return applyHeuristicHighlighting(text)
}

private fun parseAnsiCodes(text: String): AnnotatedString = buildAnnotatedString {
    var currentColor: Color? = null
    var currentBold = false
    var currentDim = false
    var currentItalic = false
    var currentUnderline = false
    var lastEnd = 0

    for (match in ansiEscapeRegex.findAll(text)) {
        // Append text before this escape sequence with current style
        val segment = text.substring(lastEnd, match.range.first)
        if (segment.isNotEmpty()) {
            val style = buildSpanStyle(currentColor, currentBold, currentDim, currentItalic, currentUnderline)
            pushStyle(style)
            append(segment)
            pop()
        }
        lastEnd = match.range.last + 1

        // Parse the codes
        val codes = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
        if (codes.isEmpty() || codes.contains(0)) {
            // Reset
            currentColor = null
            currentBold = false
            currentDim = false
            currentItalic = false
            currentUnderline = false
        }
        for (code in codes) {
            when (code) {
                0 -> {
                    currentColor = null
                    currentBold = false
                    currentDim = false
                    currentItalic = false
                    currentUnderline = false
                }
                1 -> currentBold = true
                2 -> currentDim = true
                3 -> currentItalic = true
                4 -> currentUnderline = true
                in 30..37 -> currentColor = ansiColors[code]
                in 90..97 -> currentColor = ansiBrightColors[code]
            }
        }
    }

    // Append remaining text
    val remaining = text.substring(lastEnd)
    if (remaining.isNotEmpty()) {
        val style = buildSpanStyle(currentColor, currentBold, currentDim, currentItalic, currentUnderline)
        pushStyle(style)
        append(remaining)
        pop()
    }
}

private fun buildSpanStyle(
    color: Color?,
    bold: Boolean,
    dim: Boolean,
    italic: Boolean,
    underline: Boolean
): SpanStyle {
    val effectiveColor = when {
        dim && color != null -> color.copy(alpha = 0.6f)
        dim -> Color(0xFFE0E0E0).copy(alpha = 0.6f)
        color != null -> color
        else -> Color.Unspecified
    }
    return SpanStyle(
        color = effectiveColor,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (underline) TextDecoration.Underline else null
    )
}

// Heuristic patterns
private val urlRegex = Regex("""https?://[^\s]+""")
private val ipRegex = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")
private val percentRegex = Regex("""\b(\d+(?:\.\d+)?)%""")
private val filePathRegex = Regex("""(?:^|(?<=\s))(?:/[\w.@:~-]+)+/?|(?:^|(?<=\s))\./?[\w.@:~/-]+""")
private val numberRegex = Regex("""\b\d+(?:\.\d+)?\b""")
private val quotedStringRegex = Regex(""""[^"]*"|'[^']*'""")
private val allCapsHeaderRegex = Regex("""^[A-Z][A-Z0-9_/% ]{2,}""", RegexOption.MULTILINE)

private data class Highlight(val range: IntRange, val style: SpanStyle)

private fun applyHeuristicHighlighting(text: String): AnnotatedString = buildAnnotatedString {
    append(text)

    val highlights = mutableListOf<Highlight>()

    // Process line-level patterns first
    for (line in text.lineSequence()) {
        val lineStart = text.indexOf(line)
        if (lineStart < 0) continue

        // Comment lines (starting with #)
        val trimmed = line.trimStart()
        if (trimmed.startsWith("#")) {
            highlights.add(Highlight(
                lineStart until lineStart + line.length,
                SpanStyle(color = colorGray, fontStyle = FontStyle.Italic)
            ))
            continue // skip further highlighting for comment lines
        }

        // ALL CAPS headers at line start
        allCapsHeaderRegex.find(line)?.let { match ->
            highlights.add(Highlight(
                lineStart + match.range.first until lineStart + match.range.last + 1,
                SpanStyle(color = colorPrimary, fontWeight = FontWeight.Bold)
            ))
        }
    }

    // URLs (underlined cyan)
    for (match in urlRegex.findAll(text)) {
        highlights.add(Highlight(
            match.range,
            SpanStyle(color = colorCyan, textDecoration = TextDecoration.Underline)
        ))
    }

    // Quoted strings (green)
    for (match in quotedStringRegex.findAll(text)) {
        highlights.add(Highlight(match.range, SpanStyle(color = colorGreen)))
    }

    // IP addresses (yellow)
    for (match in ipRegex.findAll(text)) {
        highlights.add(Highlight(match.range, SpanStyle(color = colorYellow)))
    }

    // Percentages (color based on value)
    for (match in percentRegex.findAll(text)) {
        val value = match.groupValues[1].toFloatOrNull() ?: 0f
        val color = when {
            value > 85f -> colorRed
            value > 60f -> colorYellow
            else -> colorGreen
        }
        highlights.add(Highlight(match.range, SpanStyle(color = color, fontWeight = FontWeight.Bold)))
    }

    // File paths (cyan)
    for (match in filePathRegex.findAll(text)) {
        highlights.add(Highlight(match.range, SpanStyle(color = colorCyan)))
    }

    // Error/success/warning keywords
    val wordRegex = Regex("""\b\w+\b""")
    for (match in wordRegex.findAll(text)) {
        val word = match.value.lowercase()
        when {
            word in errorKeywords -> highlights.add(Highlight(match.range, SpanStyle(color = colorRed, fontWeight = FontWeight.Bold)))
            word in successKeywords -> highlights.add(Highlight(match.range, SpanStyle(color = colorGreen)))
            word in warningKeywords -> highlights.add(Highlight(match.range, SpanStyle(color = colorYellow)))
        }
    }

    // Numbers (orange) — applied last, lowest priority
    for (match in numberRegex.findAll(text)) {
        // Only add if not already covered by a percentage or IP
        val alreadyCovered = highlights.any { h ->
            match.range.first >= h.range.first && match.range.last <= h.range.last
        }
        if (!alreadyCovered) {
            highlights.add(Highlight(match.range, SpanStyle(color = colorOrange)))
        }
    }

    // Apply highlights — later entries override earlier ones at the span level,
    // which is fine since Compose merges spans with last-writer-wins.
    for (h in highlights) {
        addStyle(h.style, h.range.first, h.range.last + 1)
    }
}
