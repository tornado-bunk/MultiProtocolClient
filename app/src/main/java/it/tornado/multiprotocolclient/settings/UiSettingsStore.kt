package it.tornado.multiprotocolclient.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiSettingsDataStore by preferencesDataStore(name = "ui_settings")

class UiSettingsStore(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val uiTextSize = stringPreferencesKey("ui_text_size")
        val consoleAutoScroll = booleanPreferencesKey("console_auto_scroll")
        val consoleShowTimestamps = booleanPreferencesKey("console_show_timestamps")
        val consoleFontSize = stringPreferencesKey("console_font_size")
        val consoleBufferLimit = intPreferencesKey("console_buffer_limit")
        val confirmBeforeReset = booleanPreferencesKey("confirm_before_reset")
        val keepLastFieldsPerProtocol = booleanPreferencesKey("keep_last_fields_per_protocol")
        val defaultTimeoutSeconds = intPreferencesKey("default_timeout_seconds")
        val showLocalNetworkWarnings = booleanPreferencesKey("show_local_network_warnings")
        val maskSensitiveOutput = booleanPreferencesKey("mask_sensitive_output")
        val clearOutputOnExit = booleanPreferencesKey("clear_output_on_exit")
    }

    val settingsFlow: Flow<UiSettings> = context.uiSettingsDataStore.data.map { prefs ->
        UiSettings(
            themeMode = prefs[Keys.themeMode].toThemeMode(),
            dynamicColor = prefs[Keys.dynamicColor] ?: false,
            uiTextSize = prefs[Keys.uiTextSize].toUiTextSize(),
            consoleAutoScroll = prefs[Keys.consoleAutoScroll] ?: true,
            consoleShowTimestamps = prefs[Keys.consoleShowTimestamps] ?: false,
            consoleFontSize = prefs[Keys.consoleFontSize].toConsoleFontSize(),
            consoleBufferLimit = (prefs[Keys.consoleBufferLimit] ?: 1000).coerceIn(100, 20000),
            confirmBeforeReset = prefs[Keys.confirmBeforeReset] ?: true,
            keepLastFieldsPerProtocol = prefs[Keys.keepLastFieldsPerProtocol] ?: false,
            defaultTimeoutSeconds = (prefs[Keys.defaultTimeoutSeconds] ?: 8).coerceIn(1, 120),
            showLocalNetworkWarnings = prefs[Keys.showLocalNetworkWarnings] ?: true,
            maskSensitiveOutput = prefs[Keys.maskSensitiveOutput] ?: true,
            clearOutputOnExit = prefs[Keys.clearOutputOnExit] ?: false
        )
    }

    suspend fun updateThemeMode(value: ThemeMode) = updateString(Keys.themeMode, value.name)
    suspend fun updateDynamicColor(value: Boolean) = updateBoolean(Keys.dynamicColor, value)
    suspend fun updateUiTextSize(value: UiTextSize) = updateString(Keys.uiTextSize, value.name)
    suspend fun updateConsoleAutoScroll(value: Boolean) = updateBoolean(Keys.consoleAutoScroll, value)
    suspend fun updateConsoleShowTimestamps(value: Boolean) = updateBoolean(Keys.consoleShowTimestamps, value)
    suspend fun updateConsoleFontSize(value: ConsoleFontSize) = updateString(Keys.consoleFontSize, value.name)
    suspend fun updateConsoleBufferLimit(value: Int) = updateInt(Keys.consoleBufferLimit, value.coerceIn(100, 20000))
    suspend fun updateConfirmBeforeReset(value: Boolean) = updateBoolean(Keys.confirmBeforeReset, value)
    suspend fun updateKeepLastFieldsPerProtocol(value: Boolean) = updateBoolean(Keys.keepLastFieldsPerProtocol, value)
    suspend fun updateDefaultTimeoutSeconds(value: Int) = updateInt(Keys.defaultTimeoutSeconds, value.coerceIn(1, 120))
    suspend fun updateShowLocalNetworkWarnings(value: Boolean) = updateBoolean(Keys.showLocalNetworkWarnings, value)
    suspend fun updateMaskSensitiveOutput(value: Boolean) = updateBoolean(Keys.maskSensitiveOutput, value)
    suspend fun updateClearOutputOnExit(value: Boolean) = updateBoolean(Keys.clearOutputOnExit, value)

    suspend fun resetAll() {
        context.uiSettingsDataStore.edit { prefs -> prefs.clear() }
    }

    private suspend fun updateBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.uiSettingsDataStore.edit { prefs -> prefs[key] = value }
    }

    private suspend fun updateInt(key: Preferences.Key<Int>, value: Int) {
        context.uiSettingsDataStore.edit { prefs -> prefs[key] = value }
    }

    private suspend fun updateString(key: Preferences.Key<String>, value: String) {
        context.uiSettingsDataStore.edit { prefs -> prefs[key] = value }
    }
}

private fun String?.toThemeMode(): ThemeMode = runCatching { ThemeMode.valueOf(this ?: "") }.getOrDefault(ThemeMode.SYSTEM)
private fun String?.toUiTextSize(): UiTextSize = runCatching { UiTextSize.valueOf(this ?: "") }.getOrDefault(UiTextSize.MEDIUM)
private fun String?.toConsoleFontSize(): ConsoleFontSize =
    runCatching { ConsoleFontSize.valueOf(this ?: "") }.getOrDefault(ConsoleFontSize.MEDIUM)
