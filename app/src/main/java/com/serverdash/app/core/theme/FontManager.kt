package com.serverdash.app.core.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import com.serverdash.app.R

// Provider for Google Fonts
val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Font categories for the picker UI
enum class FontCategory(val displayName: String) {
    SANS_SERIF("Sans Serif"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
    DISPLAY("Display"),
    HANDWRITING("Handwriting")
}

data class FontOption(
    val name: String,
    val family: String,  // Google Font family name
    val category: FontCategory,
    val isBuiltIn: Boolean = false
)

// Curated list of popular Google Fonts
val availableFonts = listOf(
    // Built-in
    FontOption("JetBrains Mono", "JetBrains Mono", FontCategory.MONOSPACE, isBuiltIn = true),
    FontOption("System Default", "system", FontCategory.SANS_SERIF, isBuiltIn = true),
    // Sans Serif
    FontOption("Inter", "Inter", FontCategory.SANS_SERIF),
    FontOption("Roboto", "Roboto", FontCategory.SANS_SERIF),
    FontOption("Open Sans", "Open Sans", FontCategory.SANS_SERIF),
    FontOption("Lato", "Lato", FontCategory.SANS_SERIF),
    FontOption("Nunito", "Nunito", FontCategory.SANS_SERIF),
    FontOption("Poppins", "Poppins", FontCategory.SANS_SERIF),
    FontOption("Montserrat", "Montserrat", FontCategory.SANS_SERIF),
    FontOption("Raleway", "Raleway", FontCategory.SANS_SERIF),
    FontOption("Work Sans", "Work Sans", FontCategory.SANS_SERIF),
    FontOption("DM Sans", "DM Sans", FontCategory.SANS_SERIF),
    FontOption("Plus Jakarta Sans", "Plus Jakarta Sans", FontCategory.SANS_SERIF),
    FontOption("Manrope", "Manrope", FontCategory.SANS_SERIF),
    FontOption("Outfit", "Outfit", FontCategory.SANS_SERIF),
    FontOption("Space Grotesk", "Space Grotesk", FontCategory.SANS_SERIF),
    // Serif
    FontOption("Merriweather", "Merriweather", FontCategory.SERIF),
    FontOption("Playfair Display", "Playfair Display", FontCategory.SERIF),
    FontOption("Lora", "Lora", FontCategory.SERIF),
    FontOption("PT Serif", "PT Serif", FontCategory.SERIF),
    FontOption("Source Serif 4", "Source Serif 4", FontCategory.SERIF),
    FontOption("Bitter", "Bitter", FontCategory.SERIF),
    FontOption("Crimson Text", "Crimson Text", FontCategory.SERIF),
    // Monospace
    FontOption("Fira Code", "Fira Code", FontCategory.MONOSPACE),
    FontOption("Source Code Pro", "Source Code Pro", FontCategory.MONOSPACE),
    FontOption("IBM Plex Mono", "IBM Plex Mono", FontCategory.MONOSPACE),
    FontOption("Roboto Mono", "Roboto Mono", FontCategory.MONOSPACE),
    FontOption("Ubuntu Mono", "Ubuntu Mono", FontCategory.MONOSPACE),
    FontOption("Space Mono", "Space Mono", FontCategory.MONOSPACE),
    FontOption("Inconsolata", "Inconsolata", FontCategory.MONOSPACE),
    // Display
    FontOption("Righteous", "Righteous", FontCategory.DISPLAY),
    FontOption("Fredoka", "Fredoka", FontCategory.DISPLAY),
    FontOption("Comfortaa", "Comfortaa", FontCategory.DISPLAY),
    FontOption("Audiowide", "Audiowide", FontCategory.DISPLAY),
    FontOption("Orbitron", "Orbitron", FontCategory.DISPLAY),
    // Handwriting
    FontOption("Caveat", "Caveat", FontCategory.HANDWRITING),
    FontOption("Dancing Script", "Dancing Script", FontCategory.HANDWRITING),
    FontOption("Pacifico", "Pacifico", FontCategory.HANDWRITING),
    FontOption("Satisfy", "Satisfy", FontCategory.HANDWRITING),
)

fun loadGoogleFont(fontName: String): FontFamily {
    if (fontName == "JetBrains Mono") return JetBrainsMono
    if (fontName == "system") return FontFamily.Default

    return try {
        val googleFont = GoogleFont(fontName)
        FontFamily(
            Font(googleFont = googleFont, fontProvider = googleFontProvider),
            Font(googleFont = googleFont, fontProvider = googleFontProvider, weight = androidx.compose.ui.text.font.FontWeight.Medium),
            Font(googleFont = googleFont, fontProvider = googleFontProvider, weight = androidx.compose.ui.text.font.FontWeight.Bold),
            Font(googleFont = googleFont, fontProvider = googleFontProvider, weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            Font(googleFont = googleFont, fontProvider = googleFontProvider, weight = androidx.compose.ui.text.font.FontWeight.Light),
        )
    } catch (e: Exception) {
        JetBrainsMono // fallback
    }
}
