package com.serverdash.app.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
data class ThemeColors(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val secondary: Long,
    val onSecondary: Long,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,
    val tertiary: Long,
    val onTertiary: Long,
    val tertiaryContainer: Long,
    val onTertiaryContainer: Long,
    val background: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val onBackground: Long,
    val onSurface: Long,
    val onSurfaceVariant: Long,
    val outline: Long,
    val outlineVariant: Long,
    val error: Long,
    val onError: Long,
    val errorContainer: Long,
    val onErrorContainer: Long,
    val inverseSurface: Long,
    val inverseOnSurface: Long,
    val inversePrimary: Long,

    // Editor chrome
    val editorBackground: Long = 0,
    val editorForeground: Long = 0,
    val editorLineHighlight: Long = 0,
    val editorSelection: Long = 0,
    val editorLineNumber: Long = 0,
    val editorGutter: Long = 0,
    val editorCursor: Long = 0,
    val editorWhitespace: Long = 0,
    val editorIndentGuide: Long = 0,
    val editorBracketMatch: Long = 0,

    // Syntax highlighting
    val syntaxKeyword: Long = 0,
    val syntaxString: Long = 0,
    val syntaxComment: Long = 0,
    val syntaxFunction: Long = 0,
    val syntaxNumber: Long = 0,
    val syntaxType: Long = 0,
    val syntaxOperator: Long = 0,
    val syntaxVariable: Long = 0,
    val syntaxConstant: Long = 0,
    val syntaxTag: Long = 0,
    val syntaxAttribute: Long = 0,
    val syntaxProperty: Long = 0,
    val syntaxRegex: Long = 0,
    val syntaxPunctuation: Long = 0
) {
    fun toColorScheme(isDark: Boolean): ColorScheme {
        val colors = arrayOf(
            Color(primary), Color(onPrimary), Color(primaryContainer), Color(onPrimaryContainer),
            Color(secondary), Color(onSecondary), Color(secondaryContainer), Color(onSecondaryContainer),
            Color(tertiary), Color(onTertiary), Color(tertiaryContainer), Color(onTertiaryContainer),
            Color(background), Color(surface), Color(surfaceVariant),
            Color(onBackground), Color(onSurface), Color(onSurfaceVariant),
            Color(outline), Color(outlineVariant),
            Color(error), Color(onError), Color(errorContainer), Color(onErrorContainer),
            Color(inverseSurface), Color(inverseOnSurface), Color(inversePrimary)
        )
        return if (isDark) darkColorScheme(
            primary = colors[0], onPrimary = colors[1], primaryContainer = colors[2], onPrimaryContainer = colors[3],
            secondary = colors[4], onSecondary = colors[5], secondaryContainer = colors[6], onSecondaryContainer = colors[7],
            tertiary = colors[8], onTertiary = colors[9], tertiaryContainer = colors[10], onTertiaryContainer = colors[11],
            background = colors[12], surface = colors[13], surfaceVariant = colors[14],
            onBackground = colors[15], onSurface = colors[16], onSurfaceVariant = colors[17],
            outline = colors[18], outlineVariant = colors[19],
            error = colors[20], onError = colors[21], errorContainer = colors[22], onErrorContainer = colors[23],
            inverseSurface = colors[24], inverseOnSurface = colors[25], inversePrimary = colors[26]
        ) else lightColorScheme(
            primary = colors[0], onPrimary = colors[1], primaryContainer = colors[2], onPrimaryContainer = colors[3],
            secondary = colors[4], onSecondary = colors[5], secondaryContainer = colors[6], onSecondaryContainer = colors[7],
            tertiary = colors[8], onTertiary = colors[9], tertiaryContainer = colors[10], onTertiaryContainer = colors[11],
            background = colors[12], surface = colors[13], surfaceVariant = colors[14],
            onBackground = colors[15], onSurface = colors[16], onSurfaceVariant = colors[17],
            outline = colors[18], outlineVariant = colors[19],
            error = colors[20], onError = colors[21], errorContainer = colors[22], onErrorContainer = colors[23],
            inverseSurface = colors[24], inverseOnSurface = colors[25], inversePrimary = colors[26]
        )
    }

    companion object {
        fun fromColorScheme(scheme: ColorScheme) = ThemeColors(
            primary = scheme.primary.value.toLong(),
            onPrimary = scheme.onPrimary.value.toLong(),
            primaryContainer = scheme.primaryContainer.value.toLong(),
            onPrimaryContainer = scheme.onPrimaryContainer.value.toLong(),
            secondary = scheme.secondary.value.toLong(),
            onSecondary = scheme.onSecondary.value.toLong(),
            secondaryContainer = scheme.secondaryContainer.value.toLong(),
            onSecondaryContainer = scheme.onSecondaryContainer.value.toLong(),
            tertiary = scheme.tertiary.value.toLong(),
            onTertiary = scheme.onTertiary.value.toLong(),
            tertiaryContainer = scheme.tertiaryContainer.value.toLong(),
            onTertiaryContainer = scheme.onTertiaryContainer.value.toLong(),
            background = scheme.background.value.toLong(),
            surface = scheme.surface.value.toLong(),
            surfaceVariant = scheme.surfaceVariant.value.toLong(),
            onBackground = scheme.onBackground.value.toLong(),
            onSurface = scheme.onSurface.value.toLong(),
            onSurfaceVariant = scheme.onSurfaceVariant.value.toLong(),
            outline = scheme.outline.value.toLong(),
            outlineVariant = scheme.outlineVariant.value.toLong(),
            error = scheme.error.value.toLong(),
            onError = scheme.onError.value.toLong(),
            errorContainer = scheme.errorContainer.value.toLong(),
            onErrorContainer = scheme.onErrorContainer.value.toLong(),
            inverseSurface = scheme.inverseSurface.value.toLong(),
            inverseOnSurface = scheme.inverseOnSurface.value.toLong(),
            inversePrimary = scheme.inversePrimary.value.toLong()
        )
    }
}

@Serializable
data class AppTheme(
    val id: String,
    val name: String,
    val category: String,
    val isDark: Boolean,
    val colors: ThemeColors,
    val isBuiltIn: Boolean = true
)

enum class ThemeCategory(val displayName: String) {
    DEFAULT("Default"),
    POPULAR("Popular"),
    MINIMAL("Minimal"),
    VIBRANT("Vibrant"),
    RETRO("Retro"),
    NATURE("Nature"),
    CUSTOM("Custom")
}

// ── Built-in themes ─────────────────────────────────────────────────

object BuiltInThemes {

    val default = AppTheme(
        id = "default_dark", name = "ServerDash Dark", category = "Default", isDark = true,
        colors = ThemeColors(
            primary = 0xFF5CCFE6, onPrimary = 0xFF001F24,
            primaryContainer = 0xFF0E3640, onPrimaryContainer = 0xFFB8EBF5,
            secondary = 0xFFF0B866, onSecondary = 0xFF261A00,
            secondaryContainer = 0xFF3B2E0A, onSecondaryContainer = 0xFFFADDA0,
            tertiary = 0xFFCBB2F0, onTertiary = 0xFF1E0F38,
            tertiaryContainer = 0xFF2E1F50, onTertiaryContainer = 0xFFE8DAF8,
            background = 0xFF0D0D0D, surface = 0xFF0D0D0D, surfaceVariant = 0xFF1A1A1A,
            onBackground = 0xFFECECEC, onSurface = 0xFFECECEC, onSurfaceVariant = 0xFFA0A0A0,
            outline = 0xFF3A3A3A, outlineVariant = 0xFF2A2A2A,
            error = 0xFFFF6B6B, onError = 0xFF2D0000, errorContainer = 0xFF3D1010, onErrorContainer = 0xFFFFB3B3,
            inverseSurface = 0xFFECECEC, inverseOnSurface = 0xFF1A1A1A, inversePrimary = 0xFF006B7A,
            editorBackground = 0xFF0D0D0D, editorForeground = 0xFFECECEC, editorLineHighlight = 0xFF1A1A1A,
            editorSelection = 0xFF0E3640, editorLineNumber = 0xFFA0A0A0, editorGutter = 0xFF0D0D0D,
            editorCursor = 0xFF5CCFE6, editorWhitespace = 0xFF3A3A3A, editorIndentGuide = 0xFF2A2A2A,
            editorBracketMatch = 0xFF3A5A5A, syntaxKeyword = 0xFF5CCFE6, syntaxString = 0xFFF0B866,
            syntaxComment = 0xFF5C6370, syntaxFunction = 0xFFCBB2F0, syntaxNumber = 0xFFF0B866,
            syntaxType = 0xFF5CCFE6, syntaxOperator = 0xFFECECEC, syntaxVariable = 0xFFECECEC,
            syntaxConstant = 0xFFF0B866, syntaxTag = 0xFF5CCFE6, syntaxAttribute = 0xFFF0B866,
            syntaxProperty = 0xFFCBB2F0, syntaxRegex = 0xFF5CCFE6, syntaxPunctuation = 0xFFA0A0A0
        )
    )

    val defaultLight = AppTheme(
        id = "default_light", name = "ServerDash Light", category = "Default", isDark = false,
        colors = ThemeColors(
            primary = 0xFF006B7A, onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFD4F1F6, onPrimaryContainer = 0xFF003F49,
            secondary = 0xFF8B6914, onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFFFF0D0, onSecondaryContainer = 0xFF4A3600,
            tertiary = 0xFF5C3D8F, onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFEDE0FA, onTertiaryContainer = 0xFF361F5E,
            background = 0xFFF8F8F8, surface = 0xFFF8F8F8, surfaceVariant = 0xFFEEEEEE,
            onBackground = 0xFF111111, onSurface = 0xFF111111, onSurfaceVariant = 0xFF555555,
            outline = 0xFFCCCCCC, outlineVariant = 0xFFDDDDDD,
            error = 0xFFC62828, onError = 0xFFFFFFFF, errorContainer = 0xFFFFDAD4, onErrorContainer = 0xFF6B1111,
            inverseSurface = 0xFF1A1A1A, inverseOnSurface = 0xFFF0F0F0, inversePrimary = 0xFF5CCFE6,
            editorBackground = 0xFFF8F8F8, editorForeground = 0xFF111111, editorLineHighlight = 0xFFEEEEEE,
            editorSelection = 0xFFD4F1F6, editorLineNumber = 0xFF555555, editorGutter = 0xFFF8F8F8,
            editorCursor = 0xFF006B7A, editorWhitespace = 0xFFCCCCCC, editorIndentGuide = 0xFFDDDDDD,
            editorBracketMatch = 0xFFB0D8E0, syntaxKeyword = 0xFF006B7A, syntaxString = 0xFF8B6914,
            syntaxComment = 0xFF999999, syntaxFunction = 0xFF5C3D8F, syntaxNumber = 0xFF8B6914,
            syntaxType = 0xFF006B7A, syntaxOperator = 0xFF111111, syntaxVariable = 0xFF111111,
            syntaxConstant = 0xFF8B6914, syntaxTag = 0xFF006B7A, syntaxAttribute = 0xFF8B6914,
            syntaxProperty = 0xFF5C3D8F, syntaxRegex = 0xFF006B7A, syntaxPunctuation = 0xFF555555
        )
    )

    val trueBlack = AppTheme(
        id = "true_black", name = "True Black", category = "Default", isDark = true,
        colors = ThemeColors(
            primary = 0xFF5CCFE6, onPrimary = 0xFF001F24,
            primaryContainer = 0xFF0E3640, onPrimaryContainer = 0xFFB8EBF5,
            secondary = 0xFFF0B866, onSecondary = 0xFF261A00,
            secondaryContainer = 0xFF3B2E0A, onSecondaryContainer = 0xFFFADDA0,
            tertiary = 0xFFCBB2F0, onTertiary = 0xFF1E0F38,
            tertiaryContainer = 0xFF2E1F50, onTertiaryContainer = 0xFFE8DAF8,
            background = 0xFF000000, surface = 0xFF000000, surfaceVariant = 0xFF0D0D0D,
            onBackground = 0xFFF0F0F0, onSurface = 0xFFF0F0F0, onSurfaceVariant = 0xFFA8A8A8,
            outline = 0xFF333333, outlineVariant = 0xFF1A1A1A,
            error = 0xFFFF6B6B, onError = 0xFF2D0000, errorContainer = 0xFF3D1010, onErrorContainer = 0xFFFFB3B3,
            inverseSurface = 0xFFF0F0F0, inverseOnSurface = 0xFF1A1A1A, inversePrimary = 0xFF006B7A,
            editorBackground = 0xFF000000, editorForeground = 0xFFF0F0F0, editorLineHighlight = 0xFF0D0D0D,
            editorSelection = 0xFF0E3640, editorLineNumber = 0xFFA8A8A8, editorGutter = 0xFF000000,
            editorCursor = 0xFF5CCFE6, editorWhitespace = 0xFF333333, editorIndentGuide = 0xFF1A1A1A,
            editorBracketMatch = 0xFF2A4A4A, syntaxKeyword = 0xFF5CCFE6, syntaxString = 0xFFF0B866,
            syntaxComment = 0xFF5C6370, syntaxFunction = 0xFFCBB2F0, syntaxNumber = 0xFFF0B866,
            syntaxType = 0xFF5CCFE6, syntaxOperator = 0xFFF0F0F0, syntaxVariable = 0xFFF0F0F0,
            syntaxConstant = 0xFFF0B866, syntaxTag = 0xFF5CCFE6, syntaxAttribute = 0xFFF0B866,
            syntaxProperty = 0xFFCBB2F0, syntaxRegex = 0xFF5CCFE6, syntaxPunctuation = 0xFFA8A8A8
        )
    )

    // ── Popular ─────────────────────────────────────────────────────

    val dracula = AppTheme(
        id = "dracula", name = "Dracula", category = "Popular", isDark = true,
        colors = ThemeColors(
            primary = 0xFFBD93F9, onPrimary = 0xFF1A0E2E,
            primaryContainer = 0xFF3D2A6E, onPrimaryContainer = 0xFFE2D4FC,
            secondary = 0xFFFF79C6, onSecondary = 0xFF2E0A1E,
            secondaryContainer = 0xFF5A1E40, onSecondaryContainer = 0xFFFFB8E0,
            tertiary = 0xFF8BE9FD, onTertiary = 0xFF002B35,
            tertiaryContainer = 0xFF0E4B58, onTertiaryContainer = 0xFFC5F4FE,
            background = 0xFF282A36, surface = 0xFF282A36, surfaceVariant = 0xFF343746,
            onBackground = 0xFFF8F8F2, onSurface = 0xFFF8F8F2, onSurfaceVariant = 0xFFBFBFB6,
            outline = 0xFF44475A, outlineVariant = 0xFF383A4D,
            error = 0xFFFF5555, onError = 0xFF2D0000, errorContainer = 0xFF5A1A1A, onErrorContainer = 0xFFFFAAAA,
            inverseSurface = 0xFFF8F8F2, inverseOnSurface = 0xFF282A36, inversePrimary = 0xFF6D28D9,
            editorBackground = 0xFF282A36, editorForeground = 0xFFF8F8F2, editorLineHighlight = 0xFF343746,
            editorSelection = 0xFF44475A, editorLineNumber = 0xFF6272A4, editorGutter = 0xFF282A36,
            editorCursor = 0xFFF8F8F2, editorWhitespace = 0xFF44475A, editorIndentGuide = 0xFF383A4D,
            editorBracketMatch = 0xFF555872, syntaxKeyword = 0xFFFF79C6, syntaxString = 0xFFF1FA8C,
            syntaxComment = 0xFF6272A4, syntaxFunction = 0xFF50FA7B, syntaxNumber = 0xFFBD93F9,
            syntaxType = 0xFF8BE9FD, syntaxOperator = 0xFFFF79C6, syntaxVariable = 0xFFF8F8F2,
            syntaxConstant = 0xFFBD93F9, syntaxTag = 0xFFFF79C6, syntaxAttribute = 0xFF50FA7B,
            syntaxProperty = 0xFF66D9EF, syntaxRegex = 0xFFFFB86C, syntaxPunctuation = 0xFFF8F8F2
        )
    )

    val nord = AppTheme(
        id = "nord", name = "Nord", category = "Popular", isDark = true,
        colors = ThemeColors(
            primary = 0xFF88C0D0, onPrimary = 0xFF0D2B35,
            primaryContainer = 0xFF2E5260, onPrimaryContainer = 0xFFC4E0E8,
            secondary = 0xFFA3BE8C, onSecondary = 0xFF1A2E14,
            secondaryContainer = 0xFF3B5230, onSecondaryContainer = 0xFFD1DFC6,
            tertiary = 0xFFB48EAD, onTertiary = 0xFF2A1628,
            tertiaryContainer = 0xFF4D2E48, onTertiaryContainer = 0xFFDCC7D6,
            background = 0xFF2E3440, surface = 0xFF2E3440, surfaceVariant = 0xFF3B4252,
            onBackground = 0xFFECEFF4, onSurface = 0xFFECEFF4, onSurfaceVariant = 0xFFD8DEE9,
            outline = 0xFF4C566A, outlineVariant = 0xFF434C5E,
            error = 0xFFBF616A, onError = 0xFF2D0A0E, errorContainer = 0xFF5A2028, onErrorContainer = 0xFFE0B0B5,
            inverseSurface = 0xFFECEFF4, inverseOnSurface = 0xFF2E3440, inversePrimary = 0xFF3B7A8C,
            editorBackground = 0xFF2E3440, editorForeground = 0xFFD8DEE9, editorLineHighlight = 0xFF3B4252,
            editorSelection = 0xFF434C5E, editorLineNumber = 0xFF4C566A, editorGutter = 0xFF2E3440,
            editorCursor = 0xFFD8DEE9, editorWhitespace = 0xFF4C566A, editorIndentGuide = 0xFF434C5E,
            editorBracketMatch = 0xFF5E6A82, syntaxKeyword = 0xFF81A1C1, syntaxString = 0xFFA3BE8C,
            syntaxComment = 0xFF616E88, syntaxFunction = 0xFF88C0D0, syntaxNumber = 0xFFB48EAD,
            syntaxType = 0xFF8FBCBB, syntaxOperator = 0xFF81A1C1, syntaxVariable = 0xFFD8DEE9,
            syntaxConstant = 0xFFB48EAD, syntaxTag = 0xFF81A1C1, syntaxAttribute = 0xFF8FBCBB,
            syntaxProperty = 0xFF88C0D0, syntaxRegex = 0xFFEBCB8B, syntaxPunctuation = 0xFFECEFF4
        )
    )

    val solarizedDark = AppTheme(
        id = "solarized_dark", name = "Solarized Dark", category = "Popular", isDark = true,
        colors = ThemeColors(
            primary = 0xFF268BD2, onPrimary = 0xFF001E33,
            primaryContainer = 0xFF0A3D5C, onPrimaryContainer = 0xFF93C5E9,
            secondary = 0xFF2AA198, onSecondary = 0xFF002420,
            secondaryContainer = 0xFF0A4540, onSecondaryContainer = 0xFF95D0CC,
            tertiary = 0xFFD33682, onTertiary = 0xFF2D0A1A,
            tertiaryContainer = 0xFF5A1A38, onTertiaryContainer = 0xFFE99BC1,
            background = 0xFF002B36, surface = 0xFF002B36, surfaceVariant = 0xFF073642,
            onBackground = 0xFF839496, onSurface = 0xFF839496, onSurfaceVariant = 0xFF657B83,
            outline = 0xFF586E75, outlineVariant = 0xFF073642,
            error = 0xFFDC322F, onError = 0xFF2D0000, errorContainer = 0xFF5A1010, onErrorContainer = 0xFFEE9998,
            inverseSurface = 0xFFFDF6E3, inverseOnSurface = 0xFF002B36, inversePrimary = 0xFF0D5A8C,
            editorBackground = 0xFF002B36, editorForeground = 0xFF839496, editorLineHighlight = 0xFF073642,
            editorSelection = 0xFF0A4A58, editorLineNumber = 0xFF586E75, editorGutter = 0xFF002B36,
            editorCursor = 0xFF839496, editorWhitespace = 0xFF073642, editorIndentGuide = 0xFF073642,
            editorBracketMatch = 0xFF0A5A5A, syntaxKeyword = 0xFF859900, syntaxString = 0xFF2AA198,
            syntaxComment = 0xFF586E75, syntaxFunction = 0xFF268BD2, syntaxNumber = 0xFFD33682,
            syntaxType = 0xFFB58900, syntaxOperator = 0xFF859900, syntaxVariable = 0xFF839496,
            syntaxConstant = 0xFFCB4B16, syntaxTag = 0xFF268BD2, syntaxAttribute = 0xFF93A1A1,
            syntaxProperty = 0xFF268BD2, syntaxRegex = 0xFFDC322F, syntaxPunctuation = 0xFF839496
        )
    )

    val solarizedLight = AppTheme(
        id = "solarized_light", name = "Solarized Light", category = "Popular", isDark = false,
        colors = ThemeColors(
            primary = 0xFF268BD2, onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFD4ECFA, onPrimaryContainer = 0xFF0A3D5C,
            secondary = 0xFF2AA198, onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFD0F0ED, onSecondaryContainer = 0xFF0A4540,
            tertiary = 0xFFD33682, onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFFAD4E6, onTertiaryContainer = 0xFF5A1A38,
            background = 0xFFFDF6E3, surface = 0xFFFDF6E3, surfaceVariant = 0xFFEEE8D5,
            onBackground = 0xFF586E75, onSurface = 0xFF586E75, onSurfaceVariant = 0xFF657B83,
            outline = 0xFF93A1A1, outlineVariant = 0xFFEEE8D5,
            error = 0xFFDC322F, onError = 0xFFFFFFFF, errorContainer = 0xFFFAD4D3, onErrorContainer = 0xFF6B1111,
            inverseSurface = 0xFF002B36, inverseOnSurface = 0xFFFDF6E3, inversePrimary = 0xFF93C5E9,
            editorBackground = 0xFFFDF6E3, editorForeground = 0xFF586E75, editorLineHighlight = 0xFFEEE8D5,
            editorSelection = 0xFFD4E8E0, editorLineNumber = 0xFF93A1A1, editorGutter = 0xFFFDF6E3,
            editorCursor = 0xFF586E75, editorWhitespace = 0xFFEEE8D5, editorIndentGuide = 0xFFEEE8D5,
            editorBracketMatch = 0xFFCCDDD5, syntaxKeyword = 0xFF859900, syntaxString = 0xFF2AA198,
            syntaxComment = 0xFF93A1A1, syntaxFunction = 0xFF268BD2, syntaxNumber = 0xFFD33682,
            syntaxType = 0xFFB58900, syntaxOperator = 0xFF859900, syntaxVariable = 0xFF586E75,
            syntaxConstant = 0xFFCB4B16, syntaxTag = 0xFF268BD2, syntaxAttribute = 0xFF657B83,
            syntaxProperty = 0xFF268BD2, syntaxRegex = 0xFFDC322F, syntaxPunctuation = 0xFF586E75
        )
    )

    val monokai = AppTheme(
        id = "monokai", name = "Monokai", category = "Popular", isDark = true,
        colors = ThemeColors(
            primary = 0xFFA6E22E, onPrimary = 0xFF1A2E06,
            primaryContainer = 0xFF3B520F, onPrimaryContainer = 0xFFD3F197,
            secondary = 0xFFF92672, onSecondary = 0xFF2E0614,
            secondaryContainer = 0xFF5A1430, onSecondaryContainer = 0xFFFC93B9,
            tertiary = 0xFF66D9EF, onTertiary = 0xFF002B35,
            tertiaryContainer = 0xFF0E4B58, onTertiaryContainer = 0xFFB3ECF7,
            background = 0xFF272822, surface = 0xFF272822, surfaceVariant = 0xFF3E3D32,
            onBackground = 0xFFF8F8F2, onSurface = 0xFFF8F8F2, onSurfaceVariant = 0xFFA6A69C,
            outline = 0xFF49483E, outlineVariant = 0xFF3E3D32,
            error = 0xFFF92672, onError = 0xFF2E0614, errorContainer = 0xFF5A1430, onErrorContainer = 0xFFFC93B9,
            inverseSurface = 0xFFF8F8F2, inverseOnSurface = 0xFF272822, inversePrimary = 0xFF5A8C0E,
            editorBackground = 0xFF272822, editorForeground = 0xFFF8F8F2, editorLineHighlight = 0xFF3E3D32,
            editorSelection = 0xFF49483E, editorLineNumber = 0xFF90908A, editorGutter = 0xFF272822,
            editorCursor = 0xFFF8F8F0, editorWhitespace = 0xFF49483E, editorIndentGuide = 0xFF3E3D32,
            editorBracketMatch = 0xFF5A5950, syntaxKeyword = 0xFFF92672, syntaxString = 0xFFE6DB74,
            syntaxComment = 0xFF75715E, syntaxFunction = 0xFFA6E22E, syntaxNumber = 0xFFAE81FF,
            syntaxType = 0xFF66D9EF, syntaxOperator = 0xFFF92672, syntaxVariable = 0xFFF8F8F2,
            syntaxConstant = 0xFFAE81FF, syntaxTag = 0xFFF92672, syntaxAttribute = 0xFFA6E22E,
            syntaxProperty = 0xFF66D9EF, syntaxRegex = 0xFFE6DB74, syntaxPunctuation = 0xFFF8F8F2
        )
    )

    val oneDark = AppTheme(
        id = "one_dark", name = "One Dark", category = "Popular", isDark = true,
        colors = ThemeColors(
            primary = 0xFF61AFEF, onPrimary = 0xFF0D2234,
            primaryContainer = 0xFF1E4A6E, onPrimaryContainer = 0xFFB0D7F7,
            secondary = 0xFF98C379, onSecondary = 0xFF1A2E14,
            secondaryContainer = 0xFF355228, onSecondaryContainer = 0xFFCCE1BC,
            tertiary = 0xFFC678DD, onTertiary = 0xFF28102E,
            tertiaryContainer = 0xFF4D2058, onTertiaryContainer = 0xFFE3BCEE,
            background = 0xFF282C34, surface = 0xFF282C34, surfaceVariant = 0xFF2C313A,
            onBackground = 0xFFABB2BF, onSurface = 0xFFABB2BF, onSurfaceVariant = 0xFF848B98,
            outline = 0xFF3E4451, outlineVariant = 0xFF353B45,
            error = 0xFFE06C75, onError = 0xFF2D0E11, errorContainer = 0xFF5A2028, onErrorContainer = 0xFFF0B6BA,
            inverseSurface = 0xFFABB2BF, inverseOnSurface = 0xFF282C34, inversePrimary = 0xFF2B6CA3,
            editorBackground = 0xFF282C34, editorForeground = 0xFFABB2BF, editorLineHighlight = 0xFF2C313A,
            editorSelection = 0xFF3E4451, editorLineNumber = 0xFF636D83, editorGutter = 0xFF282C34,
            editorCursor = 0xFF528BFF, editorWhitespace = 0xFF3E4451, editorIndentGuide = 0xFF353B45,
            editorBracketMatch = 0xFF515A6B, syntaxKeyword = 0xFFC678DD, syntaxString = 0xFF98C379,
            syntaxComment = 0xFF5C6370, syntaxFunction = 0xFF61AFEF, syntaxNumber = 0xFFD19A66,
            syntaxType = 0xFFE5C07B, syntaxOperator = 0xFF56B6C2, syntaxVariable = 0xFFE06C75,
            syntaxConstant = 0xFFD19A66, syntaxTag = 0xFFE06C75, syntaxAttribute = 0xFFD19A66,
            syntaxProperty = 0xFF61AFEF, syntaxRegex = 0xFF98C379, syntaxPunctuation = 0xFFABB2BF
        )
    )

    val gruvbox = AppTheme(
        id = "gruvbox", name = "Gruvbox", category = "Popular", isDark = true,
        colors = ThemeColors(
            primary = 0xFFFABD2F, onPrimary = 0xFF2E2406,
            primaryContainer = 0xFF5A4810, onPrimaryContainer = 0xFFFCDE97,
            secondary = 0xFF8EC07C, onSecondary = 0xFF142E10,
            secondaryContainer = 0xFF325228, onSecondaryContainer = 0xFFC7E0BE,
            tertiary = 0xFFD3869B, onTertiary = 0xFF2E1420,
            tertiaryContainer = 0xFF5A2840, onTertiaryContainer = 0xFFE9C3CE,
            background = 0xFF282828, surface = 0xFF282828, surfaceVariant = 0xFF3C3836,
            onBackground = 0xFFEBDBB2, onSurface = 0xFFEBDBB2, onSurfaceVariant = 0xFFA89984,
            outline = 0xFF504945, outlineVariant = 0xFF3C3836,
            error = 0xFFFB4934, onError = 0xFF2D0000, errorContainer = 0xFF5A1010, onErrorContainer = 0xFFFDA49A,
            inverseSurface = 0xFFEBDBB2, inverseOnSurface = 0xFF282828, inversePrimary = 0xFF8C6A10,
            editorBackground = 0xFF282828, editorForeground = 0xFFEBDBB2, editorLineHighlight = 0xFF3C3836,
            editorSelection = 0xFF504945, editorLineNumber = 0xFF7C6F64, editorGutter = 0xFF282828,
            editorCursor = 0xFFEBDBB2, editorWhitespace = 0xFF504945, editorIndentGuide = 0xFF3C3836,
            editorBracketMatch = 0xFF665C54, syntaxKeyword = 0xFFFB4934, syntaxString = 0xFFB8BB26,
            syntaxComment = 0xFF928374, syntaxFunction = 0xFFFABD2F, syntaxNumber = 0xFFD3869B,
            syntaxType = 0xFF83A598, syntaxOperator = 0xFFFE8019, syntaxVariable = 0xFFEBDBB2,
            syntaxConstant = 0xFFD3869B, syntaxTag = 0xFFFB4934, syntaxAttribute = 0xFF8EC07C,
            syntaxProperty = 0xFF83A598, syntaxRegex = 0xFFB8BB26, syntaxPunctuation = 0xFFA89984
        )
    )

    val gruvboxLight = AppTheme(
        id = "gruvbox_light", name = "Gruvbox Light", category = "Popular", isDark = false,
        colors = ThemeColors(
            primary = 0xFFB57614, onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFFBE8C4, onPrimaryContainer = 0xFF5A3B0A,
            secondary = 0xFF427B58, onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFD0E8DA, onSecondaryContainer = 0xFF1A3D2C,
            tertiary = 0xFF8F3F71, onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFF0D0E4, onTertiaryContainer = 0xFF481F38,
            background = 0xFFFBF1C7, surface = 0xFFFBF1C7, surfaceVariant = 0xFFF2E5BC,
            onBackground = 0xFF3C3836, onSurface = 0xFF3C3836, onSurfaceVariant = 0xFF665C54,
            outline = 0xFFBDAE93, outlineVariant = 0xFFD5C4A1,
            error = 0xFFCC241D, onError = 0xFFFFFFFF, errorContainer = 0xFFFBD4D2, onErrorContainer = 0xFF6B1210,
            inverseSurface = 0xFF3C3836, inverseOnSurface = 0xFFFBF1C7, inversePrimary = 0xFFFABD2F,
            editorBackground = 0xFFFBF1C7, editorForeground = 0xFF3C3836, editorLineHighlight = 0xFFF2E5BC,
            editorSelection = 0xFFD5C4A1, editorLineNumber = 0xFF928374, editorGutter = 0xFFFBF1C7,
            editorCursor = 0xFF3C3836, editorWhitespace = 0xFFD5C4A1, editorIndentGuide = 0xFFD5C4A1,
            editorBracketMatch = 0xFFBDAE93, syntaxKeyword = 0xFFCC241D, syntaxString = 0xFF79740E,
            syntaxComment = 0xFF928374, syntaxFunction = 0xFFB57614, syntaxNumber = 0xFF8F3F71,
            syntaxType = 0xFF427B58, syntaxOperator = 0xFFAF3A03, syntaxVariable = 0xFF3C3836,
            syntaxConstant = 0xFF8F3F71, syntaxTag = 0xFFCC241D, syntaxAttribute = 0xFF427B58,
            syntaxProperty = 0xFF427B58, syntaxRegex = 0xFF79740E, syntaxPunctuation = 0xFF665C54
        )
    )

    val tokyoNight = AppTheme(
        id = "tokyo_night", name = "Tokyo Night", category = "Popular", isDark = true,
        colors = ThemeColors(
            primary = 0xFF7AA2F7, onPrimary = 0xFF0D1B38,
            primaryContainer = 0xFF1E3A6E, onPrimaryContainer = 0xFFBDD1FB,
            secondary = 0xFF9ECE6A, onSecondary = 0xFF1A2E0E,
            secondaryContainer = 0xFF355220, onSecondaryContainer = 0xFFCFE7B5,
            tertiary = 0xFFBB9AF7, onTertiary = 0xFF1E0F38,
            tertiaryContainer = 0xFF3D2070, onTertiaryContainer = 0xFFDDCDFB,
            background = 0xFF1A1B26, surface = 0xFF1A1B26, surfaceVariant = 0xFF24252F,
            onBackground = 0xFFC0CAF5, onSurface = 0xFFC0CAF5, onSurfaceVariant = 0xFF787C9E,
            outline = 0xFF3B3D57, outlineVariant = 0xFF2F3146,
            error = 0xFFF7768E, onError = 0xFF2D0E14, errorContainer = 0xFF5A2030, onErrorContainer = 0xFFFBBBC7,
            inverseSurface = 0xFFC0CAF5, inverseOnSurface = 0xFF1A1B26, inversePrimary = 0xFF3460C0,
            editorBackground = 0xFF1A1B26, editorForeground = 0xFFC0CAF5, editorLineHighlight = 0xFF24252F,
            editorSelection = 0xFF33354A, editorLineNumber = 0xFF3B3D57, editorGutter = 0xFF1A1B26,
            editorCursor = 0xFFC0CAF5, editorWhitespace = 0xFF3B3D57, editorIndentGuide = 0xFF2F3146,
            editorBracketMatch = 0xFF4A4D6A, syntaxKeyword = 0xFF9D7CD8, syntaxString = 0xFF9ECE6A,
            syntaxComment = 0xFF565F89, syntaxFunction = 0xFF7AA2F7, syntaxNumber = 0xFFFF9E64,
            syntaxType = 0xFF2AC3DE, syntaxOperator = 0xFF89DDFF, syntaxVariable = 0xFFC0CAF5,
            syntaxConstant = 0xFFFF9E64, syntaxTag = 0xFFF7768E, syntaxAttribute = 0xFF73DACA,
            syntaxProperty = 0xFF7AA2F7, syntaxRegex = 0xFFB4F9F8, syntaxPunctuation = 0xFF89DDFF
        )
    )

    val catppuccinMocha = AppTheme(
        id = "catppuccin_mocha", name = "Catppuccin Mocha", category = "Popular", isDark = true,
        colors = ThemeColors(
            primary = 0xFFCBA6F7, onPrimary = 0xFF1E0E38,
            primaryContainer = 0xFF3E2070, onPrimaryContainer = 0xFFE5D3FB,
            secondary = 0xFFA6E3A1, onSecondary = 0xFF142E12,
            secondaryContainer = 0xFF2C5228, onSecondaryContainer = 0xFFD3F1D0,
            tertiary = 0xFFF5C2E7, onTertiary = 0xFF2E1428,
            tertiaryContainer = 0xFF5A2850, onTertiaryContainer = 0xFFFAE1F3,
            background = 0xFF1E1E2E, surface = 0xFF1E1E2E, surfaceVariant = 0xFF313244,
            onBackground = 0xFFCDD6F4, onSurface = 0xFFCDD6F4, onSurfaceVariant = 0xFFA6ADC8,
            outline = 0xFF45475A, outlineVariant = 0xFF313244,
            error = 0xFFF38BA8, onError = 0xFF2D0E18, errorContainer = 0xFF5A2038, onErrorContainer = 0xFFF9C5D4,
            inverseSurface = 0xFFCDD6F4, inverseOnSurface = 0xFF1E1E2E, inversePrimary = 0xFF7B50C0,
            editorBackground = 0xFF1E1E2E, editorForeground = 0xFFCDD6F4, editorLineHighlight = 0xFF313244,
            editorSelection = 0xFF45475A, editorLineNumber = 0xFF585B70, editorGutter = 0xFF1E1E2E,
            editorCursor = 0xFFF5E0DC, editorWhitespace = 0xFF45475A, editorIndentGuide = 0xFF313244,
            editorBracketMatch = 0xFF585B70, syntaxKeyword = 0xFFCBA6F7, syntaxString = 0xFFA6E3A1,
            syntaxComment = 0xFF6C7086, syntaxFunction = 0xFF89B4FA, syntaxNumber = 0xFFFAB387,
            syntaxType = 0xFF94E2D5, syntaxOperator = 0xFF89DCEB, syntaxVariable = 0xFFCDD6F4,
            syntaxConstant = 0xFFFAB387, syntaxTag = 0xFFCBA6F7, syntaxAttribute = 0xFFF9E2AF,
            syntaxProperty = 0xFF89B4FA, syntaxRegex = 0xFFF5C2E7, syntaxPunctuation = 0xFF9399B2
        )
    )

    val catppuccinLatte = AppTheme(
        id = "catppuccin_latte", name = "Catppuccin Latte", category = "Popular", isDark = false,
        colors = ThemeColors(
            primary = 0xFF8839EF, onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFE8D4FC, onPrimaryContainer = 0xFF441C78,
            secondary = 0xFF40A02B, onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFD0F0CA, onSecondaryContainer = 0xFF1A4A10,
            tertiary = 0xFFEA76CB, onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFFAD4F0, onTertiaryContainer = 0xFF782860,
            background = 0xFFEFF1F5, surface = 0xFFEFF1F5, surfaceVariant = 0xFFE6E9EF,
            onBackground = 0xFF4C4F69, onSurface = 0xFF4C4F69, onSurfaceVariant = 0xFF6C6F85,
            outline = 0xFF9CA0B0, outlineVariant = 0xFFCCD0DA,
            error = 0xFFD20F39, onError = 0xFFFFFFFF, errorContainer = 0xFFFAD2DB, onErrorContainer = 0xFF6B0818,
            inverseSurface = 0xFF4C4F69, inverseOnSurface = 0xFFEFF1F5, inversePrimary = 0xFFCBA6F7,
            editorBackground = 0xFFEFF1F5, editorForeground = 0xFF4C4F69, editorLineHighlight = 0xFFE6E9EF,
            editorSelection = 0xFFCCD0DA, editorLineNumber = 0xFF8C8FA1, editorGutter = 0xFFEFF1F5,
            editorCursor = 0xFFDC8A78, editorWhitespace = 0xFFCCD0DA, editorIndentGuide = 0xFFCCD0DA,
            editorBracketMatch = 0xFFACB0BE, syntaxKeyword = 0xFF8839EF, syntaxString = 0xFF40A02B,
            syntaxComment = 0xFF9CA0B0, syntaxFunction = 0xFF1E66F5, syntaxNumber = 0xFFFE640B,
            syntaxType = 0xFF179299, syntaxOperator = 0xFF04A5E5, syntaxVariable = 0xFF4C4F69,
            syntaxConstant = 0xFFFE640B, syntaxTag = 0xFF8839EF, syntaxAttribute = 0xFFDF8E1D,
            syntaxProperty = 0xFF1E66F5, syntaxRegex = 0xFFEA76CB, syntaxPunctuation = 0xFF6C6F85
        )
    )

    // ── Minimal ─────────────────────────────────────────────────────

    val monochrome = AppTheme(
        id = "monochrome", name = "Monochrome", category = "Minimal", isDark = true,
        colors = ThemeColors(
            primary = 0xFFFFFFFF, onPrimary = 0xFF000000,
            primaryContainer = 0xFF333333, onPrimaryContainer = 0xFFE0E0E0,
            secondary = 0xFFBBBBBB, onSecondary = 0xFF1A1A1A,
            secondaryContainer = 0xFF2A2A2A, onSecondaryContainer = 0xFFD5D5D5,
            tertiary = 0xFF888888, onTertiary = 0xFF000000,
            tertiaryContainer = 0xFF222222, onTertiaryContainer = 0xFFBBBBBB,
            background = 0xFF111111, surface = 0xFF111111, surfaceVariant = 0xFF1A1A1A,
            onBackground = 0xFFDDDDDD, onSurface = 0xFFDDDDDD, onSurfaceVariant = 0xFF999999,
            outline = 0xFF3A3A3A, outlineVariant = 0xFF2A2A2A,
            error = 0xFFFF4444, onError = 0xFF1A0000, errorContainer = 0xFF3D1010, onErrorContainer = 0xFFFF9999,
            inverseSurface = 0xFFE0E0E0, inverseOnSurface = 0xFF1A1A1A, inversePrimary = 0xFF555555,
            editorBackground = 0xFF111111, editorForeground = 0xFFDDDDDD, editorLineHighlight = 0xFF1A1A1A,
            editorSelection = 0xFF333333, editorLineNumber = 0xFF999999, editorGutter = 0xFF111111,
            editorCursor = 0xFFFFFFFF, editorWhitespace = 0xFF3A3A3A, editorIndentGuide = 0xFF2A2A2A,
            editorBracketMatch = 0xFF444444, syntaxKeyword = 0xFFFFFFFF, syntaxString = 0xFFBBBBBB,
            syntaxComment = 0xFF999999, syntaxFunction = 0xFF888888, syntaxNumber = 0xFFBBBBBB,
            syntaxType = 0xFFFFFFFF, syntaxOperator = 0xFFDDDDDD, syntaxVariable = 0xFFDDDDDD,
            syntaxConstant = 0xFFBBBBBB, syntaxTag = 0xFFFFFFFF, syntaxAttribute = 0xFFBBBBBB,
            syntaxProperty = 0xFF888888, syntaxRegex = 0xFFFFFFFF, syntaxPunctuation = 0xFF999999
        )
    )

    val paper = AppTheme(
        id = "paper", name = "Paper", category = "Minimal", isDark = false,
        colors = ThemeColors(
            primary = 0xFF333333, onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFE8E8E8, onPrimaryContainer = 0xFF1A1A1A,
            secondary = 0xFF666666, onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFF0F0F0, onSecondaryContainer = 0xFF333333,
            tertiary = 0xFF999999, onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFF5F5F5, onTertiaryContainer = 0xFF444444,
            background = 0xFFFAFAFA, surface = 0xFFFAFAFA, surfaceVariant = 0xFFF0F0F0,
            onBackground = 0xFF1A1A1A, onSurface = 0xFF1A1A1A, onSurfaceVariant = 0xFF666666,
            outline = 0xFFD0D0D0, outlineVariant = 0xFFE0E0E0,
            error = 0xFFCC0000, onError = 0xFFFFFFFF, errorContainer = 0xFFFFE0E0, onErrorContainer = 0xFF660000,
            inverseSurface = 0xFF1A1A1A, inverseOnSurface = 0xFFF0F0F0, inversePrimary = 0xFFBBBBBB,
            editorBackground = 0xFFFAFAFA, editorForeground = 0xFF1A1A1A, editorLineHighlight = 0xFFF0F0F0,
            editorSelection = 0xFFE8E8E8, editorLineNumber = 0xFF666666, editorGutter = 0xFFFAFAFA,
            editorCursor = 0xFF333333, editorWhitespace = 0xFFD0D0D0, editorIndentGuide = 0xFFE0E0E0,
            editorBracketMatch = 0xFFD8D8D8, syntaxKeyword = 0xFF333333, syntaxString = 0xFF666666,
            syntaxComment = 0xFF666666, syntaxFunction = 0xFF999999, syntaxNumber = 0xFF666666,
            syntaxType = 0xFF333333, syntaxOperator = 0xFF1A1A1A, syntaxVariable = 0xFF1A1A1A,
            syntaxConstant = 0xFF666666, syntaxTag = 0xFF333333, syntaxAttribute = 0xFF666666,
            syntaxProperty = 0xFF999999, syntaxRegex = 0xFF333333, syntaxPunctuation = 0xFF666666
        )
    )

    val ink = AppTheme(
        id = "ink", name = "Ink", category = "Minimal", isDark = true,
        colors = ThemeColors(
            primary = 0xFF90CAF9, onPrimary = 0xFF0A1928,
            primaryContainer = 0xFF1A3350, onPrimaryContainer = 0xFFC8E5FC,
            secondary = 0xFFA5D6A7, onSecondary = 0xFF0E2810,
            secondaryContainer = 0xFF1E4420, onSecondaryContainer = 0xFFD2EBD3,
            tertiary = 0xFFCE93D8, onTertiary = 0xFF1E0E22,
            tertiaryContainer = 0xFF3A1E42, onTertiaryContainer = 0xFFE7C9EC,
            background = 0xFF0A0E14, surface = 0xFF0A0E14, surfaceVariant = 0xFF141A22,
            onBackground = 0xFFB0BEC5, onSurface = 0xFFB0BEC5, onSurfaceVariant = 0xFF78909C,
            outline = 0xFF2A3540, outlineVariant = 0xFF1E2830,
            error = 0xFFEF9A9A, onError = 0xFF2D0A0A, errorContainer = 0xFF3D1818, onErrorContainer = 0xFFF7CDCD,
            inverseSurface = 0xFFB0BEC5, inverseOnSurface = 0xFF0A0E14, inversePrimary = 0xFF2A6090,
            editorBackground = 0xFF0A0E14, editorForeground = 0xFFB0BEC5, editorLineHighlight = 0xFF141A22,
            editorSelection = 0xFF1A3350, editorLineNumber = 0xFF78909C, editorGutter = 0xFF0A0E14,
            editorCursor = 0xFF90CAF9, editorWhitespace = 0xFF2A3540, editorIndentGuide = 0xFF1E2830,
            editorBracketMatch = 0xFF253A50, syntaxKeyword = 0xFF90CAF9, syntaxString = 0xFFA5D6A7,
            syntaxComment = 0xFF78909C, syntaxFunction = 0xFFCE93D8, syntaxNumber = 0xFFA5D6A7,
            syntaxType = 0xFF90CAF9, syntaxOperator = 0xFFB0BEC5, syntaxVariable = 0xFFB0BEC5,
            syntaxConstant = 0xFFA5D6A7, syntaxTag = 0xFF90CAF9, syntaxAttribute = 0xFFA5D6A7,
            syntaxProperty = 0xFFCE93D8, syntaxRegex = 0xFF90CAF9, syntaxPunctuation = 0xFF78909C
        )
    )

    // ── Vibrant ─────────────────────────────────────────────────────

    val synthwave = AppTheme(
        id = "synthwave", name = "Synthwave", category = "Vibrant", isDark = true,
        colors = ThemeColors(
            primary = 0xFFFF7EDB, onPrimary = 0xFF2E0A28,
            primaryContainer = 0xFF5A1A4E, onPrimaryContainer = 0xFFFFBFED,
            secondary = 0xFF72F1B8, onSecondary = 0xFF0A2E1E,
            secondaryContainer = 0xFF1A5A3C, onSecondaryContainer = 0xFFB9F8DC,
            tertiary = 0xFFFEDE00, onTertiary = 0xFF2E2A00,
            tertiaryContainer = 0xFF5A5200, onTertiaryContainer = 0xFFFEEE80,
            background = 0xFF1A1038, surface = 0xFF1A1038, surfaceVariant = 0xFF261848,
            onBackground = 0xFFF0E0FF, onSurface = 0xFFF0E0FF, onSurfaceVariant = 0xFFB0A0C8,
            outline = 0xFF3D2860, outlineVariant = 0xFF302050,
            error = 0xFFFF5555, onError = 0xFF2D0000, errorContainer = 0xFF5A1010, onErrorContainer = 0xFFFFAAAA,
            inverseSurface = 0xFFF0E0FF, inverseOnSurface = 0xFF1A1038, inversePrimary = 0xFFA0308C,
            editorBackground = 0xFF1A1038, editorForeground = 0xFFF0E0FF, editorLineHighlight = 0xFF261848,
            editorSelection = 0xFF5A1A4E, editorLineNumber = 0xFFB0A0C8, editorGutter = 0xFF1A1038,
            editorCursor = 0xFFFF7EDB, editorWhitespace = 0xFF3D2860, editorIndentGuide = 0xFF302050,
            editorBracketMatch = 0xFF4A2868, syntaxKeyword = 0xFFFF7EDB, syntaxString = 0xFF72F1B8,
            syntaxComment = 0xFFB0A0C8, syntaxFunction = 0xFFFEDE00, syntaxNumber = 0xFF72F1B8,
            syntaxType = 0xFFFF7EDB, syntaxOperator = 0xFFF0E0FF, syntaxVariable = 0xFFF0E0FF,
            syntaxConstant = 0xFF72F1B8, syntaxTag = 0xFFFF7EDB, syntaxAttribute = 0xFF72F1B8,
            syntaxProperty = 0xFFFEDE00, syntaxRegex = 0xFFFF7EDB, syntaxPunctuation = 0xFFB0A0C8
        )
    )

    val cyberpunk = AppTheme(
        id = "cyberpunk", name = "Cyberpunk", category = "Vibrant", isDark = true,
        colors = ThemeColors(
            primary = 0xFF00F0FF, onPrimary = 0xFF002A2E,
            primaryContainer = 0xFF004A50, onPrimaryContainer = 0xFF80F8FF,
            secondary = 0xFFFF003C, onSecondary = 0xFF2E0008,
            secondaryContainer = 0xFF5A0018, onSecondaryContainer = 0xFFFF809E,
            tertiary = 0xFFFFD600, onTertiary = 0xFF2E2A00,
            tertiaryContainer = 0xFF5A5200, onTertiaryContainer = 0xFFFFEB80,
            background = 0xFF0A0A1A, surface = 0xFF0A0A1A, surfaceVariant = 0xFF141428,
            onBackground = 0xFFE0E0F0, onSurface = 0xFFE0E0F0, onSurfaceVariant = 0xFF8888A8,
            outline = 0xFF2A2A48, outlineVariant = 0xFF1E1E38,
            error = 0xFFFF003C, onError = 0xFF2E0008, errorContainer = 0xFF5A0018, onErrorContainer = 0xFFFF809E,
            inverseSurface = 0xFFE0E0F0, inverseOnSurface = 0xFF0A0A1A, inversePrimary = 0xFF008088,
            editorBackground = 0xFF0A0A1A, editorForeground = 0xFFE0E0F0, editorLineHighlight = 0xFF141428,
            editorSelection = 0xFF004A50, editorLineNumber = 0xFF8888A8, editorGutter = 0xFF0A0A1A,
            editorCursor = 0xFF00F0FF, editorWhitespace = 0xFF2A2A48, editorIndentGuide = 0xFF1E1E38,
            editorBracketMatch = 0xFF1A3A40, syntaxKeyword = 0xFF00F0FF, syntaxString = 0xFFFF003C,
            syntaxComment = 0xFF8888A8, syntaxFunction = 0xFFFFD600, syntaxNumber = 0xFFFF003C,
            syntaxType = 0xFF00F0FF, syntaxOperator = 0xFFE0E0F0, syntaxVariable = 0xFFE0E0F0,
            syntaxConstant = 0xFFFF003C, syntaxTag = 0xFF00F0FF, syntaxAttribute = 0xFFFF003C,
            syntaxProperty = 0xFFFFD600, syntaxRegex = 0xFF00F0FF, syntaxPunctuation = 0xFF8888A8
        )
    )

    val aurora = AppTheme(
        id = "aurora", name = "Aurora", category = "Vibrant", isDark = true,
        colors = ThemeColors(
            primary = 0xFF82AAFF, onPrimary = 0xFF0D1E38,
            primaryContainer = 0xFF1E3E70, onPrimaryContainer = 0xFFC1D5FF,
            secondary = 0xFFC3E88D, onSecondary = 0xFF1A2E0E,
            secondaryContainer = 0xFF355220, onSecondaryContainer = 0xFFE1F4C6,
            tertiary = 0xFFFF5370, onTertiary = 0xFF2E0A12,
            tertiaryContainer = 0xFF5A1A28, onTertiaryContainer = 0xFFFFAAB8,
            background = 0xFF0F111A, surface = 0xFF0F111A, surfaceVariant = 0xFF1A1C28,
            onBackground = 0xFFB4C0E0, onSurface = 0xFFB4C0E0, onSurfaceVariant = 0xFF808AA8,
            outline = 0xFF2A2E42, outlineVariant = 0xFF1E2235,
            error = 0xFFFF5370, onError = 0xFF2E0A12, errorContainer = 0xFF5A1A28, onErrorContainer = 0xFFFFAAB8,
            inverseSurface = 0xFFB4C0E0, inverseOnSurface = 0xFF0F111A, inversePrimary = 0xFF3460C0,
            editorBackground = 0xFF0F111A, editorForeground = 0xFFB4C0E0, editorLineHighlight = 0xFF1A1C28,
            editorSelection = 0xFF1E3E70, editorLineNumber = 0xFF808AA8, editorGutter = 0xFF0F111A,
            editorCursor = 0xFF82AAFF, editorWhitespace = 0xFF2A2E42, editorIndentGuide = 0xFF1E2235,
            editorBracketMatch = 0xFF2A3858, syntaxKeyword = 0xFF82AAFF, syntaxString = 0xFFC3E88D,
            syntaxComment = 0xFF808AA8, syntaxFunction = 0xFFFF5370, syntaxNumber = 0xFFC3E88D,
            syntaxType = 0xFF82AAFF, syntaxOperator = 0xFFB4C0E0, syntaxVariable = 0xFFB4C0E0,
            syntaxConstant = 0xFFC3E88D, syntaxTag = 0xFF82AAFF, syntaxAttribute = 0xFFC3E88D,
            syntaxProperty = 0xFFFF5370, syntaxRegex = 0xFF82AAFF, syntaxPunctuation = 0xFF808AA8
        )
    )

    // ── Retro ───────────────────────────────────────────────────────

    val retroGreen = AppTheme(
        id = "retro_green", name = "Retro Terminal", category = "Retro", isDark = true,
        colors = ThemeColors(
            primary = 0xFF33FF33, onPrimary = 0xFF002E00,
            primaryContainer = 0xFF0A4A0A, onPrimaryContainer = 0xFF99FF99,
            secondary = 0xFF00CC00, onSecondary = 0xFF002400,
            secondaryContainer = 0xFF084008, onSecondaryContainer = 0xFF80E680,
            tertiary = 0xFF66FF66, onTertiary = 0xFF003A00,
            tertiaryContainer = 0xFF0E5A0E, onTertiaryContainer = 0xFFB3FFB3,
            background = 0xFF0A0A0A, surface = 0xFF0A0A0A, surfaceVariant = 0xFF141414,
            onBackground = 0xFF33FF33, onSurface = 0xFF33FF33, onSurfaceVariant = 0xFF1EAA1E,
            outline = 0xFF1A3A1A, outlineVariant = 0xFF142A14,
            error = 0xFFFF3333, onError = 0xFF2D0000, errorContainer = 0xFF3D1010, onErrorContainer = 0xFFFF9999,
            inverseSurface = 0xFF33FF33, inverseOnSurface = 0xFF0A0A0A, inversePrimary = 0xFF008800,
            editorBackground = 0xFF0A0A0A, editorForeground = 0xFF33FF33, editorLineHighlight = 0xFF141414,
            editorSelection = 0xFF0A4A0A, editorLineNumber = 0xFF1EAA1E, editorGutter = 0xFF0A0A0A,
            editorCursor = 0xFF33FF33, editorWhitespace = 0xFF1A3A1A, editorIndentGuide = 0xFF142A14,
            editorBracketMatch = 0xFF1A4A1A, syntaxKeyword = 0xFF33FF33, syntaxString = 0xFF00CC00,
            syntaxComment = 0xFF1EAA1E, syntaxFunction = 0xFF66FF66, syntaxNumber = 0xFF00CC00,
            syntaxType = 0xFF33FF33, syntaxOperator = 0xFF33FF33, syntaxVariable = 0xFF33FF33,
            syntaxConstant = 0xFF00CC00, syntaxTag = 0xFF33FF33, syntaxAttribute = 0xFF00CC00,
            syntaxProperty = 0xFF66FF66, syntaxRegex = 0xFF33FF33, syntaxPunctuation = 0xFF1EAA1E
        )
    )

    val retroAmber = AppTheme(
        id = "retro_amber", name = "Amber Terminal", category = "Retro", isDark = true,
        colors = ThemeColors(
            primary = 0xFFFFB000, onPrimary = 0xFF2E2000,
            primaryContainer = 0xFF5A4000, onPrimaryContainer = 0xFFFFD880,
            secondary = 0xFFCC8800, onSecondary = 0xFF241800,
            secondaryContainer = 0xFF483000, onSecondaryContainer = 0xFFE6C480,
            tertiary = 0xFFFFCC44, onTertiary = 0xFF2E2600,
            tertiaryContainer = 0xFF5A4C0A, onTertiaryContainer = 0xFFFFE6AA,
            background = 0xFF0A0800, surface = 0xFF0A0800, surfaceVariant = 0xFF141000,
            onBackground = 0xFFFFB000, onSurface = 0xFFFFB000, onSurfaceVariant = 0xFFAA7700,
            outline = 0xFF3A2800, outlineVariant = 0xFF2A1E00,
            error = 0xFFFF4444, onError = 0xFF2D0000, errorContainer = 0xFF3D1010, onErrorContainer = 0xFFFF9999,
            inverseSurface = 0xFFFFB000, inverseOnSurface = 0xFF0A0800, inversePrimary = 0xFF886000,
            editorBackground = 0xFF0A0800, editorForeground = 0xFFFFB000, editorLineHighlight = 0xFF141000,
            editorSelection = 0xFF5A4000, editorLineNumber = 0xFFAA7700, editorGutter = 0xFF0A0800,
            editorCursor = 0xFFFFB000, editorWhitespace = 0xFF3A2800, editorIndentGuide = 0xFF2A1E00,
            editorBracketMatch = 0xFF4A3800, syntaxKeyword = 0xFFFFB000, syntaxString = 0xFFCC8800,
            syntaxComment = 0xFFAA7700, syntaxFunction = 0xFFFFCC44, syntaxNumber = 0xFFCC8800,
            syntaxType = 0xFFFFB000, syntaxOperator = 0xFFFFB000, syntaxVariable = 0xFFFFB000,
            syntaxConstant = 0xFFCC8800, syntaxTag = 0xFFFFB000, syntaxAttribute = 0xFFCC8800,
            syntaxProperty = 0xFFFFCC44, syntaxRegex = 0xFFFFB000, syntaxPunctuation = 0xFFAA7700
        )
    )

    val eighties = AppTheme(
        id = "eighties", name = "Eighties", category = "Retro", isDark = true,
        colors = ThemeColors(
            primary = 0xFF99CC99, onPrimary = 0xFF142414,
            primaryContainer = 0xFF2E4A2E, onPrimaryContainer = 0xFFCCE6CC,
            secondary = 0xFFFFCC66, onSecondary = 0xFF2E2400,
            secondaryContainer = 0xFF5A480A, onSecondaryContainer = 0xFFFFE6B3,
            tertiary = 0xFFCC99CC, onTertiary = 0xFF241424,
            tertiaryContainer = 0xFF4A2E4A, onTertiaryContainer = 0xFFE6CCE6,
            background = 0xFF2D2D2D, surface = 0xFF2D2D2D, surfaceVariant = 0xFF393939,
            onBackground = 0xFFD3D0C8, onSurface = 0xFFD3D0C8, onSurfaceVariant = 0xFF999690,
            outline = 0xFF515151, outlineVariant = 0xFF424242,
            error = 0xFFF2777A, onError = 0xFF2D0E10, errorContainer = 0xFF5A2024, onErrorContainer = 0xFFF8BCBD,
            inverseSurface = 0xFFD3D0C8, inverseOnSurface = 0xFF2D2D2D, inversePrimary = 0xFF4D664D,
            editorBackground = 0xFF2D2D2D, editorForeground = 0xFFD3D0C8, editorLineHighlight = 0xFF393939,
            editorSelection = 0xFF2E4A2E, editorLineNumber = 0xFF999690, editorGutter = 0xFF2D2D2D,
            editorCursor = 0xFF99CC99, editorWhitespace = 0xFF515151, editorIndentGuide = 0xFF424242,
            editorBracketMatch = 0xFF4A5A4A, syntaxKeyword = 0xFF99CC99, syntaxString = 0xFFFFCC66,
            syntaxComment = 0xFF999690, syntaxFunction = 0xFFCC99CC, syntaxNumber = 0xFFFFCC66,
            syntaxType = 0xFF99CC99, syntaxOperator = 0xFFD3D0C8, syntaxVariable = 0xFFD3D0C8,
            syntaxConstant = 0xFFFFCC66, syntaxTag = 0xFF99CC99, syntaxAttribute = 0xFFFFCC66,
            syntaxProperty = 0xFFCC99CC, syntaxRegex = 0xFF99CC99, syntaxPunctuation = 0xFF999690
        )
    )

    // ── Nature ──────────────────────────────────────────────────────

    val forest = AppTheme(
        id = "forest", name = "Forest", category = "Nature", isDark = true,
        colors = ThemeColors(
            primary = 0xFF81C784, onPrimary = 0xFF0E2410,
            primaryContainer = 0xFF1E4A22, onPrimaryContainer = 0xFFC0E3C2,
            secondary = 0xFFA1887F, onSecondary = 0xFF1A1410,
            secondaryContainer = 0xFF3E322C, onSecondaryContainer = 0xFFD0C4BF,
            tertiary = 0xFF4DB6AC, onTertiary = 0xFF002420,
            tertiaryContainer = 0xFF0A4540, onTertiaryContainer = 0xFFA6DCD6,
            background = 0xFF1B2820, surface = 0xFF1B2820, surfaceVariant = 0xFF253530,
            onBackground = 0xFFD8E8D8, onSurface = 0xFFD8E8D8, onSurfaceVariant = 0xFF90A890,
            outline = 0xFF3A5040, outlineVariant = 0xFF2E4235,
            error = 0xFFE57373, onError = 0xFF2D0E0E, errorContainer = 0xFF4A1E1E, onErrorContainer = 0xFFF2B9B9,
            inverseSurface = 0xFFD8E8D8, inverseOnSurface = 0xFF1B2820, inversePrimary = 0xFF388E3C,
            editorBackground = 0xFF1B2820, editorForeground = 0xFFD8E8D8, editorLineHighlight = 0xFF253530,
            editorSelection = 0xFF1E4A22, editorLineNumber = 0xFF90A890, editorGutter = 0xFF1B2820,
            editorCursor = 0xFF81C784, editorWhitespace = 0xFF3A5040, editorIndentGuide = 0xFF2E4235,
            editorBracketMatch = 0xFF2E5A35, syntaxKeyword = 0xFF81C784, syntaxString = 0xFFA1887F,
            syntaxComment = 0xFF90A890, syntaxFunction = 0xFF4DB6AC, syntaxNumber = 0xFFA1887F,
            syntaxType = 0xFF81C784, syntaxOperator = 0xFFD8E8D8, syntaxVariable = 0xFFD8E8D8,
            syntaxConstant = 0xFFA1887F, syntaxTag = 0xFF81C784, syntaxAttribute = 0xFFA1887F,
            syntaxProperty = 0xFF4DB6AC, syntaxRegex = 0xFF81C784, syntaxPunctuation = 0xFF90A890
        )
    )

    val ocean = AppTheme(
        id = "ocean", name = "Ocean", category = "Nature", isDark = true,
        colors = ThemeColors(
            primary = 0xFF4FC3F7, onPrimary = 0xFF002838,
            primaryContainer = 0xFF0A4A68, onPrimaryContainer = 0xFFA8E1FB,
            secondary = 0xFF80DEEA, onSecondary = 0xFF002A30,
            secondaryContainer = 0xFF0A4A52, onSecondaryContainer = 0xFFC0EFF5,
            tertiary = 0xFF80CBC4, onTertiary = 0xFF002420,
            tertiaryContainer = 0xFF0A4540, onTertiaryContainer = 0xFFC0E5E2,
            background = 0xFF0D1B2A, surface = 0xFF0D1B2A, surfaceVariant = 0xFF162838,
            onBackground = 0xFFBDD8E8, onSurface = 0xFFBDD8E8, onSurfaceVariant = 0xFF7898AA,
            outline = 0xFF2A4055, outlineVariant = 0xFF1E3345,
            error = 0xFFFF8A80, onError = 0xFF2D0A06, errorContainer = 0xFF4A1810, onErrorContainer = 0xFFFFC5C0,
            inverseSurface = 0xFFBDD8E8, inverseOnSurface = 0xFF0D1B2A, inversePrimary = 0xFF0277BD,
            editorBackground = 0xFF0D1B2A, editorForeground = 0xFFBDD8E8, editorLineHighlight = 0xFF162838,
            editorSelection = 0xFF0A4A68, editorLineNumber = 0xFF7898AA, editorGutter = 0xFF0D1B2A,
            editorCursor = 0xFF4FC3F7, editorWhitespace = 0xFF2A4055, editorIndentGuide = 0xFF1E3345,
            editorBracketMatch = 0xFF1A4A60, syntaxKeyword = 0xFF4FC3F7, syntaxString = 0xFF80DEEA,
            syntaxComment = 0xFF7898AA, syntaxFunction = 0xFF80CBC4, syntaxNumber = 0xFF80DEEA,
            syntaxType = 0xFF4FC3F7, syntaxOperator = 0xFFBDD8E8, syntaxVariable = 0xFFBDD8E8,
            syntaxConstant = 0xFF80DEEA, syntaxTag = 0xFF4FC3F7, syntaxAttribute = 0xFF80DEEA,
            syntaxProperty = 0xFF80CBC4, syntaxRegex = 0xFF4FC3F7, syntaxPunctuation = 0xFF7898AA
        )
    )

    val sunset = AppTheme(
        id = "sunset", name = "Sunset", category = "Nature", isDark = true,
        colors = ThemeColors(
            primary = 0xFFFFAB40, onPrimary = 0xFF2E2000,
            primaryContainer = 0xFF5A4000, onPrimaryContainer = 0xFFFFD5A0,
            secondary = 0xFFFF7043, onSecondary = 0xFF2E1208,
            secondaryContainer = 0xFF5A2818, onSecondaryContainer = 0xFFFFB8A1,
            tertiary = 0xFFFFEA00, onTertiary = 0xFF2E2A00,
            tertiaryContainer = 0xFF5A5200, onTertiaryContainer = 0xFFFFF580,
            background = 0xFF1A1218, surface = 0xFF1A1218, surfaceVariant = 0xFF281C25,
            onBackground = 0xFFECDCE0, onSurface = 0xFFECDCE0, onSurfaceVariant = 0xFFA898A0,
            outline = 0xFF403038, outlineVariant = 0xFF35282E,
            error = 0xFFFF5252, onError = 0xFF2D0000, errorContainer = 0xFF5A1010, onErrorContainer = 0xFFFFAAAA,
            inverseSurface = 0xFFECDCE0, inverseOnSurface = 0xFF1A1218, inversePrimary = 0xFF8C5A10,
            editorBackground = 0xFF1A1218, editorForeground = 0xFFECDCE0, editorLineHighlight = 0xFF281C25,
            editorSelection = 0xFF5A4000, editorLineNumber = 0xFFA898A0, editorGutter = 0xFF1A1218,
            editorCursor = 0xFFFFAB40, editorWhitespace = 0xFF403038, editorIndentGuide = 0xFF35282E,
            editorBracketMatch = 0xFF4A3030, syntaxKeyword = 0xFFFFAB40, syntaxString = 0xFFFF7043,
            syntaxComment = 0xFFA898A0, syntaxFunction = 0xFFFFEA00, syntaxNumber = 0xFFFF7043,
            syntaxType = 0xFFFFAB40, syntaxOperator = 0xFFECDCE0, syntaxVariable = 0xFFECDCE0,
            syntaxConstant = 0xFFFF7043, syntaxTag = 0xFFFFAB40, syntaxAttribute = 0xFFFF7043,
            syntaxProperty = 0xFFFFEA00, syntaxRegex = 0xFFFFAB40, syntaxPunctuation = 0xFFA898A0
        )
    )

    val arctic = AppTheme(
        id = "arctic", name = "Arctic", category = "Nature", isDark = false,
        colors = ThemeColors(
            primary = 0xFF0288D1, onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFD4EEFA, onPrimaryContainer = 0xFF01466C,
            secondary = 0xFF00897B, onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFD0F0EC, onSecondaryContainer = 0xFF004A42,
            tertiary = 0xFF5C6BC0, onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFDDE0F6, onTertiaryContainer = 0xFF2E3560,
            background = 0xFFF0F4F8, surface = 0xFFF0F4F8, surfaceVariant = 0xFFE4EAF0,
            onBackground = 0xFF1A2530, onSurface = 0xFF1A2530, onSurfaceVariant = 0xFF556070,
            outline = 0xFFBCC5D0, outlineVariant = 0xFFD5DCE5,
            error = 0xFFC62828, onError = 0xFFFFFFFF, errorContainer = 0xFFFFDAD4, onErrorContainer = 0xFF6B1111,
            inverseSurface = 0xFF1A2530, inverseOnSurface = 0xFFF0F4F8, inversePrimary = 0xFF81D4FA,
            editorBackground = 0xFFF0F4F8, editorForeground = 0xFF1A2530, editorLineHighlight = 0xFFE4EAF0,
            editorSelection = 0xFFD4EEFA, editorLineNumber = 0xFF556070, editorGutter = 0xFFF0F4F8,
            editorCursor = 0xFF0288D1, editorWhitespace = 0xFFBCC5D0, editorIndentGuide = 0xFFD5DCE5,
            editorBracketMatch = 0xFFC0D8E8, syntaxKeyword = 0xFF0288D1, syntaxString = 0xFF00897B,
            syntaxComment = 0xFF556070, syntaxFunction = 0xFF5C6BC0, syntaxNumber = 0xFF00897B,
            syntaxType = 0xFF0288D1, syntaxOperator = 0xFF1A2530, syntaxVariable = 0xFF1A2530,
            syntaxConstant = 0xFF00897B, syntaxTag = 0xFF0288D1, syntaxAttribute = 0xFF00897B,
            syntaxProperty = 0xFF5C6BC0, syntaxRegex = 0xFF0288D1, syntaxPunctuation = 0xFF556070
        )
    )

    // ── All themes list ─────────────────────────────────────────────

    val all: List<AppTheme> = listOf(
        default, defaultLight, trueBlack,
        dracula, nord, solarizedDark, solarizedLight, monokai, oneDark, gruvbox, gruvboxLight, tokyoNight, catppuccinMocha, catppuccinLatte,
        monochrome, paper, ink,
        synthwave, cyberpunk, aurora,
        retroGreen, retroAmber, eighties,
        forest, ocean, sunset, arctic
    )

    fun findById(id: String): AppTheme? = all.find { it.id == id }

    val categories: List<String> = all.map { it.category }.distinct()
}
