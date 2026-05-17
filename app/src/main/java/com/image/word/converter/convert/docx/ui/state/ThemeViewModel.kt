package com.image.word.converter.convert.docx.ui.state

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val themeKey = stringPreferencesKey("theme_mode")

    val themeMode: StateFlow<ThemeMode> = application.themeDataStore.data
        .map { prefs ->
            val themeName = prefs[themeKey] ?: ThemeMode.SYSTEM.name
            ThemeMode.valueOf(themeName)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            getApplication<Application>().themeDataStore.edit { prefs ->
                prefs[themeKey] = mode.name
            }
        }
    }
}
