package com.slsilverlight.youtubelite.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Estimated dislike counts from Return YouTube Dislike (returnyoutubedislike.com).
 * YouTube stopped publishing dislike counts in 2021; this community service estimates them
 * from archived data and its own users' votes. If it's unreachable the app just shows no number.
 */
class DislikeCounts {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, Long>()

    suspend fun estimate(videoId: String): Long? {
        cache[videoId]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://returnyoutubedislikeapi.com/votes".toHttpUrl().newBuilder()
                    .addQueryParameter("videoId", videoId)
                    .build()
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()?.let { json.decodeFromString<Votes>(it).dislikes }
                }
            }.getOrNull()
        }?.also { cache[videoId] = it }
    }

    @Serializable
    private data class Votes(val dislikes: Long? = null)
}
