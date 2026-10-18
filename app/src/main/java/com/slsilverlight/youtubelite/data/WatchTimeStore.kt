package com.slsilverlight.youtubelite.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.watchTimeStore by preferencesDataStore(name = "watch_time")

/** Seconds of video watched in this app per day, for the last [DAYS_KEPT] days. */
class WatchTimeStore(private val context: Context) {

    val days: Flow<Map<LocalDate, Long>> = context.watchTimeStore.data.map { decode(it[DAYS]) }

    suspend fun add(seconds: Long, day: LocalDate = LocalDate.now()) {
        if (seconds <= 0) return
        context.watchTimeStore.edit { prefs ->
            val days = decode(prefs[DAYS]).toMutableMap()
            days[day] = (days[day] ?: 0) + seconds
            val oldest = day.minusDays(DAYS_KEPT - 1L)
            prefs[DAYS] = days.filterKeys { !it.isBefore(oldest) }
                .entries.joinToString(";") { "${it.key}=${it.value}" }
        }
    }

    private fun decode(raw: String?): Map<LocalDate, Long> =
        raw.orEmpty().split(";").mapNotNull { entry ->
            val (date, secs) = entry.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@mapNotNull null
            secs.toLongOrNull()?.let { day to it }
        }.toMap()

    private companion object {
        const val DAYS_KEPT = 8
        val DAYS = stringPreferencesKey("days")
    }
}
