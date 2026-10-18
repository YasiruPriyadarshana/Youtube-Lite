package com.slsilverlight.youtubelite.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val showShorts: Boolean = false,
    val showHome: Boolean = true,
    val showDislikes: Boolean = false,
    val breakReminder: Boolean = false,
    val breakMinutes: Int = 60,
    val bedtimeReminder: Boolean = false,
    /** Minutes after midnight. */
    val bedtimeStart: Int = 23 * 60,
    val bedtimeEnd: Int = 7 * 60,
    val bedtimeWaitForVideo: Boolean = false,
)

class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            showShorts = prefs[SHOW_SHORTS] ?: false,
            showHome = prefs[SHOW_HOME] ?: true,
            showDislikes = prefs[SHOW_DISLIKES] ?: false,
            breakReminder = prefs[BREAK_REMINDER] ?: false,
            breakMinutes = prefs[BREAK_MINUTES] ?: 60,
            bedtimeReminder = prefs[BEDTIME_REMINDER] ?: false,
            bedtimeStart = prefs[BEDTIME_START] ?: (23 * 60),
            bedtimeEnd = prefs[BEDTIME_END] ?: (7 * 60),
            bedtimeWaitForVideo = prefs[BEDTIME_WAIT] ?: false,
        )
    }

    suspend fun setShowShorts(value: Boolean) {
        context.dataStore.edit { it[SHOW_SHORTS] = value }
    }

    suspend fun setShowHome(value: Boolean) {
        context.dataStore.edit { it[SHOW_HOME] = value }
    }

    suspend fun setShowDislikes(value: Boolean) {
        context.dataStore.edit { it[SHOW_DISLIKES] = value }
    }

    suspend fun setBreakReminder(enabled: Boolean, minutes: Int) {
        context.dataStore.edit { it[BREAK_REMINDER] = enabled; it[BREAK_MINUTES] = minutes }
    }

    suspend fun setBedtime(enabled: Boolean, start: Int, end: Int, waitForVideo: Boolean) {
        context.dataStore.edit {
            it[BEDTIME_REMINDER] = enabled
            it[BEDTIME_START] = start
            it[BEDTIME_END] = end
            it[BEDTIME_WAIT] = waitForVideo
        }
    }

    private companion object {
        val SHOW_SHORTS = booleanPreferencesKey("show_shorts")
        val SHOW_HOME = booleanPreferencesKey("show_home")
        val SHOW_DISLIKES = booleanPreferencesKey("show_dislikes")
        val BREAK_REMINDER = booleanPreferencesKey("break_reminder")
        val BREAK_MINUTES = intPreferencesKey("break_minutes")
        val BEDTIME_REMINDER = booleanPreferencesKey("bedtime_reminder")
        val BEDTIME_START = intPreferencesKey("bedtime_start")
        val BEDTIME_END = intPreferencesKey("bedtime_end")
        val BEDTIME_WAIT = booleanPreferencesKey("bedtime_wait")
    }
}
