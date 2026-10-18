package com.slsilverlight.youtubelite.data

import java.time.Duration
import java.time.Instant

/** A video as the UI shows it. */
data class Video(
    val id: String,
    val title: String,
    val channelTitle: String,
    val channelId: String = "",
    val thumbnailUrl: String?,
    val publishedAt: Instant,
    val duration: Duration?,
    val viewCount: Long?,
    val isLive: Boolean,
    val commentCount: Long? = null,
    val likeCount: Long? = null,
) {
    /**
     * Whether this is probably a Short: 3 minutes or less and not live. The API doesn't flag
     * Shorts, so this is a duration check; a short regular video can be counted as one.
     */
    val isShort: Boolean
        get() {
            val d = duration ?: return false
            return !isLive && d > Duration.ZERO && d <= Duration.ofMinutes(3)
        }

    companion object {
        fun from(item: VideoItem) = Video(
            id = item.id,
            title = item.snippet.title,
            channelTitle = item.snippet.channelTitle,
            channelId = item.snippet.channelId,
            thumbnailUrl = item.snippet.thumbnails.high?.url ?: item.snippet.thumbnails.medium?.url,
            publishedAt = runCatching { Instant.parse(item.snippet.publishedAt) }.getOrDefault(Instant.EPOCH),
            duration = item.contentDetails?.duration?.let { runCatching { Duration.parse(it) }.getOrNull() },
            viewCount = item.statistics?.viewCount?.toLongOrNull(),
            isLive = item.snippet.liveBroadcastContent == "live",
            commentCount = item.statistics?.commentCount?.toLongOrNull(),
            likeCount = item.statistics?.likeCount?.toLongOrNull(),
        )
    }
}

data class Comment(
    val id: String,
    val author: String,
    val authorChannelId: String?,
    val authorImageUrl: String?,
    val text: String,
    val likeCount: Long,
    val replyCount: Int,
    val publishedAt: Instant,
)

data class Playlist(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val itemCount: Int,
)

data class Channel(
    val id: String,
    val title: String,
    val handle: String?,
    val description: String,
    val avatarUrl: String?,
    val subscriberCount: Long?,
    val videoCount: Long?,
)

enum class Rating(val apiValue: String) {
    Like("like"), Dislike("dislike"), None("none");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.apiValue == value } ?: None
    }
}
