package com.slsilverlight.youtubelite.data

import java.time.Duration
import java.time.Instant

/** A video as the UI shows it. */
data class Video(
    val id: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String?,
    val publishedAt: Instant,
    val duration: Duration?,
    val viewCount: Long?,
    val isLive: Boolean,
) {
    companion object {
        fun from(item: VideoItem) = Video(
            id = item.id,
            title = item.snippet.title,
            channelTitle = item.snippet.channelTitle,
            thumbnailUrl = item.snippet.thumbnails.high?.url ?: item.snippet.thumbnails.medium?.url,
            publishedAt = runCatching { Instant.parse(item.snippet.publishedAt) }.getOrDefault(Instant.EPOCH),
            duration = item.contentDetails?.duration?.let { runCatching { Duration.parse(it) }.getOrNull() },
            viewCount = item.statistics?.viewCount?.toLongOrNull(),
            isLive = item.snippet.liveBroadcastContent == "live",
        )
    }
}

data class Comment(
    val id: String,
    val author: String,
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
