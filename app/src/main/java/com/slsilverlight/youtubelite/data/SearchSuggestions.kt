package com.slsilverlight.youtubelite.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private val Context.searchStore by preferencesDataStore(name = "search_history")

/**
 * Search suggestions as you type, from the same endpoint the YouTube website uses. It isn't
 * part of the official API, so it costs no quota, but Google could change it without notice;
 * if it stops working the app just shows no suggestions.
 */
class SearchSuggestions(private val context: Context) {

    private val client = OkHttpClient()

    val recentSearches: Flow<List<String>> = context.searchStore.data.map { prefs ->
        prefs[RECENT]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun addRecent(query: String) {
        context.searchStore.edit { prefs ->
            val current = prefs[RECENT]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(query) + current.filterNot { it.equals(query, ignoreCase = true) }).take(MAX_RECENT)
            prefs[RECENT] = updated.joinToString(SEPARATOR)
        }
    }

    suspend fun removeRecent(query: String) {
        context.searchStore.edit { prefs ->
            val current = prefs[RECENT]?.split(SEPARATOR) ?: return@edit
            prefs[RECENT] = current.filterNot { it == query }.joinToString(SEPARATOR)
        }
    }

    suspend fun suggestionsFor(query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://suggestqueries.google.com/complete/search".toHttpUrl().newBuilder()
                .addQueryParameter("client", "firefox")
                .addQueryParameter("ds", "yt")
                .addQueryParameter("q", query)
                .build()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body?.string() ?: return@use emptyList()
                // Response shape: ["query", ["suggestion 1", "suggestion 2", ...]]
                val root = Json.parseToJsonElement(body).jsonArray
                (root.getOrNull(1) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        val RECENT = stringPreferencesKey("recent")
        const val SEPARATOR = "\u001F"
        const val MAX_RECENT = 15
    }
}
