package com.slsilverlight.youtubelite.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * The parts of the YouTube Data API v3 this app uses. Auth is added by an OkHttp interceptor:
 * calls marked [PUBLIC] use the API key and work without signing in; the rest use the
 * signed-in user's access token.
 */
interface YouTubeApi {

    @GET("subscriptions")
    suspend fun mySubscriptions(
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null,
    ): ListResponse<SubscriptionItem>

    @GET("videos")
    suspend fun likedVideos(
        @Query("myRating") myRating: String = "like",
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("maxResults") maxResults: Int = 50,
    ): ListResponse<VideoItem>

    /** The signed-in user's rating of a video: "like", "dislike" or "none". */
    @GET("videos/getRating")
    suspend fun getRating(@Query("id") id: String): RatingResponse

    /** Likes, dislikes or clears the rating. Costs 50 quota units. */
    @POST("videos/rate")
    suspend fun rate(@Query("id") id: String, @Query("rating") rating: String): Response<Unit>

    @GET("playlists")
    suspend fun myPlaylists(
        @Query("mine") mine: Boolean = true,
        @Query("part") part: String = "snippet,contentDetails",
        @Query("maxResults") maxResults: Int = 50,
    ): ListResponse<PlaylistResource>

    @GET("playlistItems")
    suspend fun playlistItems(
        @Query("playlistId") playlistId: String,
        @Query("part") part: String = "contentDetails",
        @Query("maxResults") maxResults: Int,
    ): ListResponse<PlaylistItem>

    @GET("videos")
    suspend fun videosById(
        @Query("id") ids: String,
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("maxResults") maxResults: Int = 50,
    ): ListResponse<VideoItem>

    @Headers(PUBLIC)
    @GET("videos")
    suspend fun mostPopular(
        @Query("regionCode") regionCode: String,
        @Query("chart") chart: String = "mostPopular",
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("maxResults") maxResults: Int = 50,
    ): ListResponse<VideoItem>

    /** Costs 100 quota units per call, so searches are the most expensive thing the app does. */
    @Headers(PUBLIC)
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("videoEmbeddable") videoEmbeddable: Boolean = true,
        @Query("maxResults") maxResults: Int = 25,
    ): ListResponse<SearchItem>

    @Headers(PUBLIC)
    @GET("videos")
    suspend fun publicVideosById(
        @Query("id") ids: String,
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("maxResults") maxResults: Int = 50,
    ): ListResponse<VideoItem>

    @Headers(PUBLIC)
    @GET("commentThreads")
    suspend fun comments(
        @Query("videoId") videoId: String,
        @Query("part") part: String = "snippet",
        @Query("order") order: String = "relevance",
        @Query("textFormat") textFormat: String = "plainText",
        @Query("maxResults") maxResults: Int = 30,
    ): ListResponse<CommentThread>

    @Headers(PUBLIC)
    @GET("comments")
    suspend fun replies(
        @Query("parentId") parentId: String,
        @Query("part") part: String = "snippet",
        @Query("textFormat") textFormat: String = "plainText",
        @Query("maxResults") maxResults: Int = 50,
    ): ListResponse<ReplyItem>

    @Headers(PUBLIC)
    @GET("channels")
    suspend fun channels(
        @Query("id") ids: String,
        @Query("part") part: String = "snippet,statistics",
    ): ListResponse<ChannelItem>

    @Headers(PUBLIC)
    @GET("playlistItems")
    suspend fun publicPlaylistItems(
        @Query("playlistId") playlistId: String,
        @Query("part") part: String = "contentDetails",
        @Query("maxResults") maxResults: Int,
    ): ListResponse<PlaylistItem>

    companion object {
        const val PUBLIC_HEADER = "X-Public-Request"
        const val PUBLIC = "X-Public-Request: true"
    }
}

@Serializable
data class ListResponse<T>(
    val items: List<T> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class SubscriptionItem(val snippet: SubscriptionSnippet)

@Serializable
data class SubscriptionSnippet(val title: String = "", val resourceId: ResourceId)

@Serializable
data class ResourceId(val channelId: String = "")

@Serializable
data class PlaylistItem(val contentDetails: PlaylistItemDetails)

@Serializable
data class PlaylistItemDetails(val videoId: String, val videoPublishedAt: String? = null)

@Serializable
data class VideoItem(
    val id: String,
    val snippet: VideoSnippet,
    val contentDetails: VideoContentDetails? = null,
    val statistics: VideoStatistics? = null,
)

@Serializable
data class VideoSnippet(
    val title: String = "",
    val channelTitle: String = "",
    val channelId: String = "",
    val publishedAt: String = "",
    val thumbnails: Thumbnails = Thumbnails(),
    val liveBroadcastContent: String = "none",
)

@Serializable
data class Thumbnails(
    val medium: Thumbnail? = null,
    val high: Thumbnail? = null,
    val maxres: Thumbnail? = null,
)

@Serializable
data class Thumbnail(val url: String)

@Serializable
data class VideoContentDetails(val duration: String = "")

@Serializable
data class VideoStatistics(
    val viewCount: String? = null,
    val commentCount: String? = null,
    val likeCount: String? = null,
)

@Serializable
data class CommentThread(val id: String, val snippet: CommentThreadSnippet)

@Serializable
data class CommentThreadSnippet(val topLevelComment: TopLevelComment, val totalReplyCount: Int = 0)

@Serializable
data class TopLevelComment(val snippet: CommentSnippet)

@Serializable
data class CommentSnippet(
    val authorDisplayName: String = "",
    val authorProfileImageUrl: String? = null,
    val authorChannelId: AuthorChannelId? = null,
    val textDisplay: String = "",
    val likeCount: Long = 0,
    val publishedAt: String = "",
)

@Serializable
data class SearchItem(val id: SearchId)

@Serializable
data class SearchId(val videoId: String? = null)

@Serializable
data class PlaylistResource(
    val id: String,
    val snippet: PlaylistSnippet,
    val contentDetails: PlaylistContentDetails? = null,
)

@Serializable
data class PlaylistSnippet(val title: String = "", val thumbnails: Thumbnails = Thumbnails())

@Serializable
data class PlaylistContentDetails(val itemCount: Int = 0)

@Serializable
data class AuthorChannelId(val value: String = "")

@Serializable
data class ReplyItem(val id: String, val snippet: CommentSnippet)

@Serializable
data class ChannelItem(
    val id: String,
    val snippet: ChannelSnippet,
    val statistics: ChannelStatistics? = null,
)

@Serializable
data class ChannelSnippet(
    val title: String = "",
    val description: String = "",
    val customUrl: String? = null,
    val thumbnails: Thumbnails = Thumbnails(),
)

@Serializable
data class ChannelStatistics(
    val subscriberCount: String? = null,
    val hiddenSubscriberCount: Boolean = false,
    val videoCount: String? = null,
)

@Serializable
data class RatingResponse(val items: List<VideoRating> = emptyList())

@Serializable
data class VideoRating(val videoId: String, val rating: String = "none")
