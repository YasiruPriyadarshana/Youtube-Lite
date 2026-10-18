package com.slsilverlight.youtubelite.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val showShorts: Boolean = false,
    val showHome: Boolean = true,
)

class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            showShorts = prefs[SHOW_SHORTS] ?: false,
            showHome = prefs[SHOW_HOME] ?: true,
        )
    }

    suspend fun setShowShorts(value: Boolean) {
        context.dataStore.edit { it[SHOW_SHORTS] = value }
    }

    suspend fun setShowHome(value: Boolean) {
        context.dataStore.edit { it[SHOW_HOME] = value }
    }

    private companion object {
        val SHOW_SHORTS = booleanPreferencesKey("show_shorts")
        val SHOW_HOME = booleanPreferencesKey("show_home")
    }
}
