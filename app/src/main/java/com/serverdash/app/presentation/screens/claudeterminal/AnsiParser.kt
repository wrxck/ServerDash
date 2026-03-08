package com.serverdash.app.presentation.screens.claudeterminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Parses ANSI escape sequences and produces an AnnotatedString with color/style spans.
 * Supports SGR (Select Graphic Rendition) codes for foreground/background colors,
 * bold, italic, underline, and 256-color / truecolor modes.
 */
object AnsiParser {

    // Standard 16 ANSI colors (normal + bright) — Tokyo Night palette
    private val ANSI_COLORS = arrayOf(
        Color(0xFF414868), // 0 black
        Color(0xFFF7768E), // 1 red
        Color(0xFF9ECE6A), // 2 green
        Color(0xFFE0AF68), // 3 yellow
        Color(0xFF7AA2F7), // 4 blue
        Color(0xFFBB9AF7), // 5 magenta
        Color(0xFF7DCFFF), // 6 cyan
        Color(0xFFC0CAF5), // 7 white
        // Bright variants
        Color(0xFF565F89), // 8 bright black
        Color(0xFFF7768E), // 9 bright red
        Color(0xFF9ECE6A), // 10 bright green
        Color(0xFFE0AF68), // 11 bright yellow
        Color(0xFF7AA2F7), // 12 bright blue
        Color(0xFFBB9AF7), // 13 bright magenta
        Color(0xFF7DCFFF), // 14 bright cyan
        Color(0xFFC0CAF5), // 15 bright white
    )

    // 6x6x6 color cube for 256-color mode (indices 16-231)
    private val COLOR_CUBE: Array<Color> by lazy {
        Array(216) { i ->
            val r = (i / 36) * 51
            val g = ((i % 36) / 6) * 51
            val b = (i % 6) * 51
            Color(0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
        }
    }

    // Grayscale ramp for 256-color mode (indices 232-255)
    private val GRAYSCALE: Array<Color> by lazy {
        Array(24) { i ->
            val v = 8 + i * 10
            Color(0xFF000000 or (v.toLong() shl 16) or (v.toLong() shl 8) or v.toLong())
        }
    }

    private val DEFAULT_FG = Color(0xFFC0CAF5)

    // Regex to match all ESC sequences we want to handle or strip
    // Matches: CSI sequences (including DEC private modes with ?), OSC sequences, other escapes
    private val ESC_SEQUENCE = Regex(
        "\u001B" +
        "(?:" +
            "\\[[?]?[0-9;]*[a-zA-Z~]" +   // CSI: \e[...X or \e[?...X (DEC private mode)
            "|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)" + // OSC: \e]...BEL or \e]...ST
            "|[()][0-9A-B]" +               // Character set
            "|[=>]" +                        // Keypad mode
            "|\\[\\?[0-9;]*[hl]" +          // DEC private mode set/reset
        ")"
    )

    fun parse(text: String): AnnotatedString {
        val cleaned = text.replace("\r", "")
        val builder = AnnotatedString.Builder()

        var fg: Color? = null
        var bg: Color? = null
        var bold = false
        var italic = false
        var underline = false
        var dim = false

        var pos = 0
        val matcher = ESC_SEQUENCE.findAll(cleaned)

        for (match in matcher) {
            // Append text before this escape sequence
            if (match.range.first > pos) {
                val segment = cleaned.substring(pos, match.range.first)
                if (segment.isNotEmpty()) {
                    val style = buildStyle(fg, bg, bold, italic, underline, dim)
                    builder.pushStyle(style)
                    builder.append(segment)
                    builder.pop()
                }
            }
            pos = match.range.last + 1

            val seq = match.value
            // Only process SGR sequences (ending in 'm')
            if (seq.startsWith("\u001B[") && seq.endsWith("m") && !seq.contains("?")) {
                val codes = seq.substring(2, seq.length - 1)
                    .split(";")
                    .mapNotNull { it.toIntOrNull() }
                    .ifEmpty { listOf(0) }

                var i = 0
                while (i < codes.size) {
                    when (codes[i]) {
                        0 -> { fg = null; bg = null; bold = false; italic = false; underline = false; dim = false }
                        1 -> bold = true
                        2 -> dim = true
                        3 -> italic = true
                        4 -> underline = true
                        22 -> { bold = false; dim = false }
                        23 -> italic = false
                        24 -> underline = false
                        in 30..37 -> fg = ANSI_COLORS[codes[i] - 30]
                        38 -> {
                            // Extended foreground color
                            val result = parseExtendedColor(codes, i + 1)
                            fg = result.first
                            i = result.second
                        }
                        39 -> fg = null
                        in 40..47 -> bg = ANSI_COLORS[codes[i] - 40]
                        48 -> {
                            // Extended background color
                            val result = parseExtendedColor(codes, i + 1)
                            bg = result.first
                            i = result.second
                        }
                        49 -> bg = null
                        in 90..97 -> fg = ANSI_COLORS[codes[i] - 90 + 8]
                        in 100..107 -> bg = ANSI_COLORS[codes[i] - 100 + 8]
                    }
                    i++
                }
            }
            // All other escape sequences are silently stripped
        }

        // Append remaining text
        if (pos < cleaned.length) {
            val segment = cleaned.substring(pos)
            if (segment.isNotEmpty()) {
                val style = buildStyle(fg, bg, bold, italic, underline, dim)
                builder.pushStyle(style)
                builder.append(segment)
                builder.pop()
            }
        }

        return builder.toAnnotatedString()
    }

    private fun buildStyle(
        fg: Color?, bg: Color?,
        bold: Boolean, italic: Boolean, underline: Boolean, dim: Boolean
    ): SpanStyle {
        var color = fg ?: DEFAULT_FG
        if (dim) color = color.copy(alpha = 0.6f)
        return SpanStyle(
            color = color,
            background = bg ?: Color.Unspecified,
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (underline) TextDecoration.Underline else null
        )
    }

    /**
     * Parse extended color (256-color or truecolor).
     * For 38;5;N (256-color) or 38;2;R;G;B (truecolor).
     * Returns the parsed Color and the last consumed index.
     */
    private fun parseExtendedColor(codes: List<Int>, startIdx: Int): Pair<Color?, Int> {
        if (startIdx >= codes.size) return null to startIdx - 1
        return when (codes[startIdx]) {
            5 -> {
                // 256-color mode: 38;5;N
                if (startIdx + 1 < codes.size) {
                    val n = codes[startIdx + 1]
                    val color = when {
                        n < 16 -> ANSI_COLORS[n]
                        n < 232 -> COLOR_CUBE[n - 16]
                        n < 256 -> GRAYSCALE[n - 232]
                        else -> null
                    }
                    color to startIdx + 1
                } else null to startIdx
            }
            2 -> {
                // Truecolor: 38;2;R;G;B
                if (startIdx + 3 < codes.size) {
                    val r = codes[startIdx + 1].coerceIn(0, 255)
                    val g = codes[startIdx + 2].coerceIn(0, 255)
                    val b = codes[startIdx + 3].coerceIn(0, 255)
                    Color(0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()) to startIdx + 3
                } else null to startIdx
            }
            else -> null to startIdx - 1
        }
    }
}
