package it.tornado.multiprotocolclient.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UiSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = UiSettingsStore(application.applicationContext)

    val settings: StateFlow<UiSettings> = store.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiSettings()
    )

    fun updateThemeMode(value: ThemeMode) = viewModelScope.launch { store.updateThemeMode(value) }
    fun updateDynamicColor(value: Boolean) = viewModelScope.launch { store.updateDynamicColor(value) }
    fun updateUiTextSize(value: UiTextSize) = viewModelScope.launch { store.updateUiTextSize(value) }
    fun updateConsoleAutoScroll(value: Boolean) = viewModelScope.launch { store.updateConsoleAutoScroll(value) }
    fun updateConsoleShowTimestamps(value: Boolean) = viewModelScope.launch { store.updateConsoleShowTimestamps(value) }
    fun updateConsoleFontSize(value: ConsoleFontSize) = viewModelScope.launch { store.updateConsoleFontSize(value) }
    fun updateConsoleBufferLimit(value: Int) = viewModelScope.launch { store.updateConsoleBufferLimit(value) }
}
