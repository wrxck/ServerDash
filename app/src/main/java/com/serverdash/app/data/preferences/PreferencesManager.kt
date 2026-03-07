package com.serverdash.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.serverdash.app.domain.model.AppPreferences
import com.serverdash.app.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val POLLING_INTERVAL = intPreferencesKey("polling_interval")
        val BRIGHTNESS_OVERRIDE = floatPreferencesKey("brightness_override")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KIOSK_MODE = booleanPreferencesKey("kiosk_mode")
        val PIXEL_SHIFT = booleanPreferencesKey("pixel_shift")
        val AUTO_START_BOOT = booleanPreferencesKey("auto_start_boot")
        val BG_CHECK_INTERVAL = intPreferencesKey("bg_check_interval")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            themeMode = ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.AUTO.name),
            pollingIntervalSeconds = prefs[Keys.POLLING_INTERVAL] ?: 10,
            brightnessOverride = prefs[Keys.BRIGHTNESS_OVERRIDE] ?: -1f,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            kioskMode = prefs[Keys.KIOSK_MODE] ?: false,
            pixelShiftEnabled = prefs[Keys.PIXEL_SHIFT] ?: false,
            autoStartOnBoot = prefs[Keys.AUTO_START_BOOT] ?: false,
            backgroundCheckIntervalMinutes = prefs[Keys.BG_CHECK_INTERVAL] ?: 15
        )
    }

    suspend fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        context.dataStore.edit { prefs ->
            val current = AppPreferences(
                themeMode = ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.AUTO.name),
                pollingIntervalSeconds = prefs[Keys.POLLING_INTERVAL] ?: 10,
                brightnessOverride = prefs[Keys.BRIGHTNESS_OVERRIDE] ?: -1f,
                keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
                kioskMode = prefs[Keys.KIOSK_MODE] ?: false,
                pixelShiftEnabled = prefs[Keys.PIXEL_SHIFT] ?: false,
                autoStartOnBoot = prefs[Keys.AUTO_START_BOOT] ?: false,
                backgroundCheckIntervalMinutes = prefs[Keys.BG_CHECK_INTERVAL] ?: 15
            )
            val updated = transform(current)
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.POLLING_INTERVAL] = updated.pollingIntervalSeconds
            prefs[Keys.BRIGHTNESS_OVERRIDE] = updated.brightnessOverride
            prefs[Keys.KEEP_SCREEN_ON] = updated.keepScreenOn
            prefs[Keys.KIOSK_MODE] = updated.kioskMode
            prefs[Keys.PIXEL_SHIFT] = updated.pixelShiftEnabled
            prefs[Keys.AUTO_START_BOOT] = updated.autoStartOnBoot
            prefs[Keys.BG_CHECK_INTERVAL] = updated.backgroundCheckIntervalMinutes
        }
    }
}
