package com.slsilverlight.youtubelite.data

import java.time.Duration
import java.time.Instant

/** A video as the UI shows it. */
data class Video(
    val id: String,
    val title: String,
    val channelTitle: String,
    val channelId: String = "",
    val categoryId: String = "",
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
            categoryId = item.snippet.categoryId,
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

/** A channel the user subscribes to, for the row along the top of Subscriptions. */
data class SubscribedChannel(val id: String, val title: String, val avatarUrl: String?)

/** The Subscriptions tab: newest videos, plus the channels with the newest uploads first. */
data class SubscriptionFeed(val videos: List<Video>, val channels: List<SubscribedChannel>)

enum class Rating(val apiValue: String) {
    Like("like"), Dislike("dislike"), None("none");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.apiValue == value } ?: None
    }
}

/** Names for YouTube's video category ids (they're the same in every country). */
val VIDEO_CATEGORY_NAMES = mapOf(
    "1" to "Film & Animation",
    "2" to "Cars",
    "10" to "Music",
    "15" to "Pets & Animals",
    "17" to "Sports",
    "19" to "Travel",
    "20" to "Gaming",
    "22" to "Vlogs",
    "23" to "Comedy",
    "24" to "Entertainment",
    "25" to "News",
    "26" to "How-to & Style",
    "27" to "Education",
    "28" to "Science & Tech",
)
