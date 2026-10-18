package com.slsilverlight.youtubelite.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

private val Context.historyStore by preferencesDataStore(name = "history")

/**
 * Videos watched in this app, newest first, kept on the phone.
 * YouTube doesn't let apps read your YouTube watch history, so this is the app's own.
 */
class HistoryStore(private val context: Context) {

    val history: Flow<List<Video>> = context.historyStore.data.map { prefs ->
        decode(prefs[KEY]).map { it.toVideo() }
    }

    suspend fun add(video: Video) {
        context.historyStore.edit { prefs ->
            val entries = decode(prefs[KEY]).filterNot { it.id == video.id }
            prefs[KEY] = json.encodeToString((listOf(Entry.from(video)) + entries).take(MAX_ENTRIES))
        }
    }

    suspend fun clear() {
        context.historyStore.edit { it.remove(KEY) }
    }

    private fun decode(raw: String?): List<Entry> =
        raw?.let { runCatching { json.decodeFromString<List<Entry>>(it) }.getOrNull() } ?: emptyList()

    @Serializable
    private data class Entry(
        val id: String,
        val title: String,
        val channelTitle: String,
        val channelId: String = "",
        val thumbnailUrl: String? = null,
        val publishedAtMillis: Long,
        val durationSeconds: Long? = null,
        val viewCount: Long? = null,
    ) {
        fun toVideo() = Video(
            id = id,
            title = title,
            channelTitle = channelTitle,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            publishedAt = Instant.ofEpochMilli(publishedAtMillis),
            duration = durationSeconds?.let(Duration::ofSeconds),
            viewCount = viewCount,
            isLive = false,
        )

        companion object {
            fun from(v: Video) = Entry(
                id = v.id,
                title = v.title,
                channelTitle = v.channelTitle,
                channelId = v.channelId,
                thumbnailUrl = v.thumbnailUrl,
                publishedAtMillis = v.publishedAt.toEpochMilli(),
                durationSeconds = v.duration?.seconds,
                viewCount = v.viewCount,
            )
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("videos")
        const val MAX_ENTRIES = 200
        val json = Json { ignoreUnknownKeys = true }
    }
}
