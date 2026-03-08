package com.serverdash.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel
import com.serverdash.app.core.theme.BuiltInThemes
import com.serverdash.app.core.theme.ServerDashTheme
import com.serverdash.app.core.theme.loadGoogleFont
import com.serverdash.app.core.theme.resolveColorScheme
import com.serverdash.app.data.preferences.PreferencesManager
import com.serverdash.app.domain.model.ThemeMode
import com.serverdash.app.presentation.navigation.ServerDashNavHost
import com.serverdash.app.presentation.screens.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.json.Json
import com.serverdash.app.core.theme.AppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val preferences by settingsViewModel.preferences.collectAsState()
            val customThemesJson by preferencesManager.customThemesJson.collectAsState(initial = "[]")
            val isDarkSystem = isSystemInDarkTheme()

            val json = remember { Json { ignoreUnknownKeys = true } }
            val customThemes = remember(customThemesJson) {
                try { json.decodeFromString<List<AppTheme>>(customThemesJson) } catch (_: Exception) { emptyList() }
            }

            val (colorScheme, isDark) = remember(preferences.themeMode, preferences.selectedThemeId, customThemes, isDarkSystem) {
                resolveColorScheme(preferences.themeMode, preferences.selectedThemeId, customThemes, isDarkSystem)
            }

            // Apply display settings
            if (preferences.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            if (preferences.brightnessOverride >= 0f) {
                val lp = window.attributes
                lp.screenBrightness = preferences.brightnessOverride
                window.attributes = lp
            }

            val headerFontFamily = remember(preferences.headerFont) { loadGoogleFont(preferences.headerFont) }
            val bodyFontFamily = remember(preferences.bodyFont) { loadGoogleFont(preferences.bodyFont) }

            ServerDashTheme(
                themeMode = preferences.themeMode,
                customColorScheme = colorScheme,
                customIsDark = isDark,
                headerFontFamily = headerFontFamily,
                bodyFontFamily = bodyFontFamily
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ServerDashNavHost()
                }
            }
        }
    }
}
