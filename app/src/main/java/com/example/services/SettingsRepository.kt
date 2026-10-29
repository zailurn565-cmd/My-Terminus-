package com.example.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.models.AppSettings
import com.example.models.AppThemeMode
import com.example.models.CursorStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.forgeSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "forgeshell_settings"
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TERMINAL_FONT_SIZE = floatPreferencesKey("terminal_font_size")
        val TERMINAL_FONT_FAMILY = stringPreferencesKey("terminal_font_family")
        val DEFAULT_PORT = intPreferencesKey("default_port")
        val DEFAULT_USERNAME = stringPreferencesKey("default_username")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val CURSOR_STYLE = stringPreferencesKey("cursor_style")
    }

    val settingsFlow: Flow<AppSettings> = context.forgeSettingsDataStore.data.map { prefs ->
        val themeMode = prefs[Keys.THEME_MODE]
            ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
            ?: AppThemeMode.DARK
        val cursorStyle = prefs[Keys.CURSOR_STYLE]
            ?.let { runCatching { CursorStyle.valueOf(it) }.getOrNull() }
            ?: CursorStyle.BLOCK
        val fontSize = (prefs[Keys.TERMINAL_FONT_SIZE] ?: 14f).coerceIn(10f, 24f)
        val fontFamily = prefs[Keys.TERMINAL_FONT_FAMILY]
            ?.takeIf { it in AppSettings.AvailableFonts }
            ?: "JetBrains Mono"
        val defaultPort = (prefs[Keys.DEFAULT_PORT] ?: 22).coerceIn(1, 65535)
        val defaultUsername = prefs[Keys.DEFAULT_USERNAME]?.ifBlank { "root" } ?: "root"
        val autoReconnect = prefs[Keys.AUTO_RECONNECT] ?: false

        AppSettings(
            themeMode = themeMode,
            terminalFontSize = fontSize,
            terminalFontFamily = fontFamily,
            defaultPort = defaultPort,
            defaultUsername = defaultUsername,
            autoReconnect = autoReconnect,
            cursorStyle = cursorStyle
        )
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        context.forgeSettingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateTerminalFontSize(size: Float) {
        context.forgeSettingsDataStore.edit {
            it[Keys.TERMINAL_FONT_SIZE] = size.coerceIn(10f, 24f)
        }
    }

    suspend fun updateTerminalFontFamily(family: String) {
        context.forgeSettingsDataStore.edit { it[Keys.TERMINAL_FONT_FAMILY] = family }
    }

    suspend fun updateCursorStyle(style: CursorStyle) {
        context.forgeSettingsDataStore.edit { it[Keys.CURSOR_STYLE] = style.name }
    }

    suspend fun updateDefaultPort(port: Int) {
        if (port in 1..65535) {
            context.forgeSettingsDataStore.edit { it[Keys.DEFAULT_PORT] = port }
        }
    }

    suspend fun updateDefaultUsername(username: String) {
        val clean = username.trim()
        if (clean.isNotEmpty()) {
            context.forgeSettingsDataStore.edit { it[Keys.DEFAULT_USERNAME] = clean }
        }
    }

    suspend fun updateAutoReconnect(enabled: Boolean) {
        context.forgeSettingsDataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }
}
