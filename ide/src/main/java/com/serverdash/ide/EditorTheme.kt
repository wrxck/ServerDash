package com.serverdash.ide

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.serverdash.ide.model.EditorThemeColors
import com.serverdash.ide.model.LocalEditorThemeColors

@Composable
fun SyncThemeToMonaco(commands: MonacoCommands?) {
    val tc = LocalEditorThemeColors.current
    val scheme = MaterialTheme.colorScheme

    // Resolve editor chrome: prefer explicit theme color, fall back to MaterialTheme
    val background = tc.editorBackground.colorOrDefault(scheme.surfaceContainerLowest)
    val foreground = tc.editorForeground.colorOrDefault(scheme.onSurface)
    val lineHighlight = tc.editorLineHighlight.colorOrDefault(scheme.surfaceContainerHigh)
    val selection = tc.editorSelection.colorOrDefault(scheme.primaryContainer)
    val lineNumber = tc.editorLineNumber.colorOrDefault(scheme.onSurfaceVariant)
    val gutter = tc.editorGutter.colorOrDefault(scheme.surfaceContainerLowest)
    val cursor = tc.editorCursor.colorOrDefault(scheme.primary)
    val whitespace = tc.editorWhitespace.colorOrDefault(scheme.outlineVariant)
    val indentGuide = tc.editorIndentGuide.colorOrDefault(scheme.outlineVariant)
    val bracketMatch = tc.editorBracketMatch.colorOrDefault(scheme.tertiaryContainer)

    LaunchedEffect(commands, tc, background) {
        commands ?: return@LaunchedEffect
        val theme = buildMonacoTheme(
            background = background,
            foreground = foreground,
            lineHighlight = lineHighlight,
            selection = selection,
            lineNumber = lineNumber,
            gutter = gutter,
            cursor = cursor,
            whitespace = whitespace,
            indentGuide = indentGuide,
            bracketMatch = bracketMatch,
            tc = tc,
        )
        commands.setTheme(theme)
    }
}

private fun Long.colorOrDefault(fallback: Color): Color =
    if (this != 0L) Color(this) else fallback

private fun buildMonacoTheme(
    background: Color,
    foreground: Color,
    lineHighlight: Color,
    selection: Color,
    lineNumber: Color,
    gutter: Color,
    cursor: Color,
    whitespace: Color,
    indentGuide: Color,
    bracketMatch: Color,
    tc: EditorThemeColors,
): String {
    fun Color.hex(): String {
        val argb = toArgb()
        return String.format("#%06X", 0xFFFFFF and argb)
    }

    // Determine base theme from background luminance
    val bgLuminance = background.red * 0.299f + background.green * 0.587f + background.blue * 0.114f
    val base = if (bgLuminance < 0.5f) "vs-dark" else "vs"

    // Build syntax token rules (only include non-zero entries)
    val rules = buildList {
        fun addRule(token: String, color: Long, bold: Boolean = false, italic: Boolean = false) {
            if (color == 0L) return
            val hex = Color(color).hex().removePrefix("#")
            val extra = buildString {
                if (bold) append(""","fontStyle":"bold"""")
                if (italic) append(""","fontStyle":"italic"""")
            }
            add("""{"token":"$token","foreground":"$hex"$extra}""")
        }

        addRule("keyword", tc.syntaxKeyword)
        addRule("keyword.control", tc.syntaxKeyword)
        addRule("keyword.operator", tc.syntaxOperator)
        addRule("string", tc.syntaxString)
        addRule("string.escape", tc.syntaxString)
        addRule("comment", tc.syntaxComment, italic = true)
        addRule("comment.line", tc.syntaxComment, italic = true)
        addRule("comment.block", tc.syntaxComment, italic = true)
        addRule("identifier", tc.syntaxVariable)
        addRule("variable", tc.syntaxVariable)
        addRule("variable.predefined", tc.syntaxConstant)
        addRule("number", tc.syntaxNumber)
        addRule("number.float", tc.syntaxNumber)
        addRule("number.hex", tc.syntaxNumber)
        addRule("type", tc.syntaxType)
        addRule("type.identifier", tc.syntaxType)
        addRule("class", tc.syntaxType)
        addRule("interface", tc.syntaxType)
        addRule("struct", tc.syntaxType)
        addRule("enum", tc.syntaxType)
        addRule("function", tc.syntaxFunction)
        addRule("function.declaration", tc.syntaxFunction)
        addRule("method", tc.syntaxFunction)
        addRule("operator", tc.syntaxOperator)
        addRule("constant", tc.syntaxConstant)
        addRule("constant.language", tc.syntaxConstant)
        addRule("tag", tc.syntaxTag)
        addRule("tag.id", tc.syntaxTag)
        addRule("tag.class", tc.syntaxTag)
        addRule("attribute.name", tc.syntaxAttribute)
        addRule("attribute.value", tc.syntaxString)
        addRule("property", tc.syntaxProperty)
        addRule("regexp", tc.syntaxRegex)
        addRule("delimiter", tc.syntaxPunctuation)
        addRule("delimiter.bracket", tc.syntaxPunctuation)
        addRule("delimiter.parenthesis", tc.syntaxPunctuation)
        addRule("delimiter.curly", tc.syntaxPunctuation)
        addRule("delimiter.square", tc.syntaxPunctuation)
        addRule("delimiter.angle", tc.syntaxPunctuation)
    }

    val rulesJson = rules.joinToString(",", "[", "]")

    return buildString {
        append("""{"base":"$base","inherit":true,"rules":$rulesJson,"colors":{""")
        append(""""editor.background":"${background.hex()}"""")
        append(""","editor.foreground":"${foreground.hex()}"""")
        append(""","editor.lineHighlightBackground":"${lineHighlight.hex()}"""")
        append(""","editor.selectionBackground":"${selection.hex()}"""")
        append(""","editorLineNumber.foreground":"${lineNumber.hex()}"""")
        append(""","editorGutter.background":"${gutter.hex()}"""")
        append(""","editorCursor.foreground":"${cursor.hex()}"""")
        append(""","editorWhitespace.foreground":"${whitespace.hex()}"""")
        append(""","editorIndentGuide.background":"${indentGuide.hex()}"""")
        append(""","editorBracketMatch.background":"${bracketMatch.hex()}"""")
        append("}}")
    }
}
