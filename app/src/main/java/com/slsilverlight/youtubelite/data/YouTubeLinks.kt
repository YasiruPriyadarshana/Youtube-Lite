package com.slsilverlight.youtubelite.data

import android.net.Uri

/** What a YouTube link points to, as far as this app can open it. */
sealed interface YouTubeLink {
    data class Video(val id: String) : YouTubeLink
    data class Channel(val id: String) : YouTubeLink
    data class Search(val query: String) : YouTubeLink
}

object YouTubeLinks {
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val URL_IN_TEXT = Regex("""https?://\S+""")

    /** Finds the first link in shared text, e.g. "Check this out https://youtu.be/abc". */
    fun fromSharedText(text: String): YouTubeLink? =
        URL_IN_TEXT.findAll(text).firstNotNullOfOrNull { parse(Uri.parse(it.value)) }

    fun parse(uri: Uri): YouTubeLink? {
        val segments = uri.pathSegments.orEmpty()
        return when (uri.scheme?.lowercase()) {
            // vnd.youtube:VIDEO_ID or vnd.youtube://VIDEO_ID
            "vnd.youtube" -> (uri.schemeSpecificPart ?: "").trimStart('/').substringBefore('?').video()
            "http", "https" -> when (uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")) {
                "youtu.be" -> segments.firstOrNull()?.video()
                "youtube.com", "music.youtube.com" -> when (segments.firstOrNull()) {
                    "watch" -> uri.getQueryParameter("v")?.video()
                    "shorts", "live", "embed", "v" -> segments.getOrNull(1)?.video()
                    "channel" -> segments.getOrNull(1)?.takeIf { it.startsWith("UC") }?.let { YouTubeLink.Channel(it) }
                    "results" -> uri.getQueryParameter("search_query")?.takeIf { it.isNotBlank() }?.let { YouTubeLink.Search(it) }
                    else -> null
                }
                else -> null
            }
            else -> null
        }
    }

    private fun String.video(): YouTubeLink? = takeIf { VIDEO_ID.matches(it) }?.let { YouTubeLink.Video(it) }
}
