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
    val inversePrimary: Long
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
            inverseSurface = 0xFFECECEC, inverseOnSurface = 0xFF1A1A1A, inversePrimary = 0xFF006B7A
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
            inverseSurface = 0xFF1A1A1A, inverseOnSurface = 0xFFF0F0F0, inversePrimary = 0xFF5CCFE6
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
            inverseSurface = 0xFFF0F0F0, inverseOnSurface = 0xFF1A1A1A, inversePrimary = 0xFF006B7A
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
            inverseSurface = 0xFFF8F8F2, inverseOnSurface = 0xFF282A36, inversePrimary = 0xFF6D28D9
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
            inverseSurface = 0xFFECEFF4, inverseOnSurface = 0xFF2E3440, inversePrimary = 0xFF3B7A8C
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
            inverseSurface = 0xFFFDF6E3, inverseOnSurface = 0xFF002B36, inversePrimary = 0xFF0D5A8C
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
            inverseSurface = 0xFF002B36, inverseOnSurface = 0xFFFDF6E3, inversePrimary = 0xFF93C5E9
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
            inverseSurface = 0xFFF8F8F2, inverseOnSurface = 0xFF272822, inversePrimary = 0xFF5A8C0E
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
            inverseSurface = 0xFFABB2BF, inverseOnSurface = 0xFF282C34, inversePrimary = 0xFF2B6CA3
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
            inverseSurface = 0xFFEBDBB2, inverseOnSurface = 0xFF282828, inversePrimary = 0xFF8C6A10
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
            inverseSurface = 0xFF3C3836, inverseOnSurface = 0xFFFBF1C7, inversePrimary = 0xFFFABD2F
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
            inverseSurface = 0xFFC0CAF5, inverseOnSurface = 0xFF1A1B26, inversePrimary = 0xFF3460C0
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
            inverseSurface = 0xFFCDD6F4, inverseOnSurface = 0xFF1E1E2E, inversePrimary = 0xFF7B50C0
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
            inverseSurface = 0xFF4C4F69, inverseOnSurface = 0xFFEFF1F5, inversePrimary = 0xFFCBA6F7
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
            inverseSurface = 0xFFE0E0E0, inverseOnSurface = 0xFF1A1A1A, inversePrimary = 0xFF555555
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
            inverseSurface = 0xFF1A1A1A, inverseOnSurface = 0xFFF0F0F0, inversePrimary = 0xFFBBBBBB
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
            inverseSurface = 0xFFB0BEC5, inverseOnSurface = 0xFF0A0E14, inversePrimary = 0xFF2A6090
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
            inverseSurface = 0xFFF0E0FF, inverseOnSurface = 0xFF1A1038, inversePrimary = 0xFFA0308C
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
            inverseSurface = 0xFFE0E0F0, inverseOnSurface = 0xFF0A0A1A, inversePrimary = 0xFF008088
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
            inverseSurface = 0xFFB4C0E0, inverseOnSurface = 0xFF0F111A, inversePrimary = 0xFF3460C0
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
            inverseSurface = 0xFF33FF33, inverseOnSurface = 0xFF0A0A0A, inversePrimary = 0xFF008800
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
            inverseSurface = 0xFFFFB000, inverseOnSurface = 0xFF0A0800, inversePrimary = 0xFF886000
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
            inverseSurface = 0xFFD3D0C8, inverseOnSurface = 0xFF2D2D2D, inversePrimary = 0xFF4D664D
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
            inverseSurface = 0xFFD8E8D8, inverseOnSurface = 0xFF1B2820, inversePrimary = 0xFF388E3C
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
            inverseSurface = 0xFFBDD8E8, inverseOnSurface = 0xFF0D1B2A, inversePrimary = 0xFF0277BD
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
            inverseSurface = 0xFFECDCE0, inverseOnSurface = 0xFF1A1218, inversePrimary = 0xFF8C5A10
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
            inverseSurface = 0xFF1A2530, inverseOnSurface = 0xFFF0F4F8, inversePrimary = 0xFF81D4FA
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
