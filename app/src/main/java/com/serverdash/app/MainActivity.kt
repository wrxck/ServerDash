package com.serverdash.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import javax.inject.Inject
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.serverdash.app.core.privacy.LocalPrivacyFilter
import com.serverdash.app.core.privacy.PrivacyFilter
import com.serverdash.app.core.security.AppLockManager
import com.serverdash.app.core.theme.BuiltInThemes
import com.serverdash.app.core.theme.ServerDashTheme
import com.serverdash.app.core.theme.loadGoogleFont
import com.serverdash.app.core.theme.resolveColorScheme
import com.serverdash.app.data.preferences.PreferencesManager
import com.serverdash.app.domain.model.ThemeMode
import com.serverdash.app.presentation.navigation.ServerDashNavHost
import com.serverdash.app.presentation.screens.lock.LockScreen
import com.serverdash.app.presentation.screens.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.serverdash.app.core.theme.AppTheme

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var privacyFilter: PrivacyFilter
    @Inject lateinit var appLockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle lock state on lifecycle changes
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appLockManager.onAppResumed()
            }
        }

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val preferences by settingsViewModel.preferences.collectAsState()
            val customThemesJson by preferencesManager.customThemesJson.collectAsState(initial = "[]")
            val isDarkSystem = isSystemInDarkTheme()
            val isLocked by appLockManager.isLocked.collectAsState()

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

            CompositionLocalProvider(LocalPrivacyFilter provides privacyFilter) {
                ServerDashTheme(
                    themeMode = preferences.themeMode,
                    customColorScheme = colorScheme,
                    customIsDark = isDark,
                    headerFontFamily = headerFontFamily,
                    bodyFontFamily = bodyFontFamily
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            val widgetDestination = remember {
                                intent?.data?.host?.let { host ->
                                    when (host) {
                                        "terminal" -> "terminal"
                                        "claude_code" -> "claude_code"
                                        "dashboard" -> "dashboard"
                                        else -> null
                                    }
                                }
                            }
                            ServerDashNavHost(widgetDeepLink = widgetDestination)
                        }

                        // Lock screen overlay
                        AnimatedVisibility(
                            visible = isLocked && preferences.appLockEnabled,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            LockScreen(
                                appLockManager = appLockManager,
                                authMethod = preferences.appLockAuthMethod
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        appLockManager.onAppPaused()
    }
}
