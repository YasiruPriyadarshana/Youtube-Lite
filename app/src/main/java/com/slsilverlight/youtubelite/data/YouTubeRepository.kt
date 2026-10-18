package com.slsilverlight.youtubelite.data

import android.content.Context
import com.slsilverlight.youtubelite.BuildConfig
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * Builds the feeds from the YouTube Data API.
 *
 * Quota note: the API gives each Google Cloud project 10,000 units a day, shared by everyone
 * using the app. A Subscriptions refresh costs about one unit per subscribed channel.
 */
class YouTubeRepository(context: Context, tokenProvider: () -> String?) {

    private val packageName = context.packageName
    private val certSha1 = signingCertSha1(context)

    private val api: YouTubeApi = run {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val isPublic = original.header(YouTubeApi.PUBLIC_HEADER) != null
                val request = original.newBuilder().removeHeader(YouTubeApi.PUBLIC_HEADER).apply {
                    if (isPublic) {
                        url(original.url.newBuilder().addQueryParameter("key", BuildConfig.YOUTUBE_API_KEY).build())
                        // Lets an API key restricted to this Android app accept the request.
                        header("X-Android-Package", packageName)
                        certSha1?.let { header("X-Android-Cert", it) }
                    } else {
                        tokenProvider()?.let { header("Authorization", "Bearer $it") }
                    }
                }.build()
                chain.proceed(request)
            }
            .build()
        val json = Json { ignoreUnknownKeys = true }
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/youtube/v3/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YouTubeApi::class.java)
    }

    /** Latest uploads from every channel the signed-in user subscribes to, newest first. */
    suspend fun subscriptionFeed(showShorts: Boolean): List<Video> = coroutineScope {
        val channelIds = allSubscribedChannelIds()
        val limit = Semaphore(8)

        // Each channel's uploads playlist is its id with "UC" swapped for "UU". The "UULF"
        // playlist holds only long-form uploads, which is how Shorts are left out precisely.
        val uploads = channelIds.map { channelId ->
            async {
                limit.withPermit { latestUploads(channelId.removePrefix("UC"), showShorts) }
            }
        }.awaitAll().flatten()

        val newest = uploads
            .sortedByDescending { it.publishedAt }
            .take(MAX_FEED_VIDEOS)
        val needsDurationCheck = newest.filter { it.needsShortsCheck }.map { it.videoId }.toSet()

        videoDetails(newest.map { it.videoId })
            .distinctBy { it.id }
            .filterNot { it.id in needsDurationCheck && it.isShort }
            .sortedByDescending { it.publishedAt }
    }

    val hasApiKey: Boolean get() = BuildConfig.YOUTUBE_API_KEY.isNotBlank()

    /** Trending videos for the phone's region. The API has no personalised Home feed. */
    suspend fun homeFeed(showShorts: Boolean): List<Video> {
        check(hasApiKey) { "No API key" }
        val region = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
        return api.mostPopular(regionCode = region).items
            .map(Video::from)
            .filter { showShorts || !it.isShort }
    }

    /** Videos the signed-in user has liked, most recent first. */
    suspend fun likedVideos(): List<Video> = api.likedVideos().items.map(Video::from)

    suspend fun myPlaylists(): List<Playlist> = api.myPlaylists().items.map {
        Playlist(
            id = it.id,
            title = it.snippet.title,
            thumbnailUrl = it.snippet.thumbnails.medium?.url ?: it.snippet.thumbnails.high?.url,
            itemCount = it.contentDetails?.itemCount ?: 0,
        )
    }

    /** The first 50 videos of a playlist, in playlist order. Deleted or private videos are skipped. */
    suspend fun playlistVideos(playlistId: String): List<Video> {
        val ids = api.playlistItems(playlistId = playlistId, maxResults = 50).items.map { it.contentDetails.videoId }
        if (ids.isEmpty()) return emptyList()
        val byId = videoDetails(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /** Videos matching [query], in YouTube's relevance order. */
    suspend fun search(query: String, showShorts: Boolean): List<Video> {
        check(hasApiKey) { "No API key" }
        val ids = api.search(query = query).items.mapNotNull { it.id.videoId }.distinct()
        if (ids.isEmpty()) return emptyList()
        val byId = api.publicVideosById(ids = ids.joinToString(",")).items.map(Video::from).associateBy { it.id }
        return ids.mapNotNull { byId[it] }.filter { showShorts || !it.isShort }
    }

    suspend fun rating(videoId: String): Rating =
        Rating.from(api.getRating(videoId).items.firstOrNull()?.rating ?: "none")

    suspend fun rate(videoId: String, rating: Rating) {
        val response = api.rate(videoId, rating.apiValue)
        if (!response.isSuccessful) throw HttpException(response)
    }

    /** Replies to a top-level comment, oldest first like YouTube shows them. */
    suspend fun replies(commentId: String): List<Comment> {
        check(hasApiKey) { "No API key" }
        return api.replies(parentId = commentId).items.map { reply ->
            val c = reply.snippet
            Comment(
                id = reply.id,
                author = c.authorDisplayName,
                authorChannelId = c.authorChannelId?.value?.takeIf { it.isNotEmpty() },
                authorImageUrl = c.authorProfileImageUrl,
                text = c.textDisplay,
                likeCount = c.likeCount,
                replyCount = 0,
                publishedAt = runCatching { Instant.parse(c.publishedAt) }.getOrDefault(Instant.EPOCH),
            )
        }.sortedBy { it.publishedAt }
    }

    suspend fun channel(channelId: String): Channel? {
        check(hasApiKey) { "No API key" }
        val item = api.channels(ids = channelId).items.firstOrNull() ?: return null
        return Channel(
            id = item.id,
            title = item.snippet.title,
            handle = item.snippet.customUrl,
            description = item.snippet.description,
            avatarUrl = item.snippet.thumbnails.medium?.url ?: item.snippet.thumbnails.high?.url,
            subscriberCount = item.statistics?.takeUnless { it.hiddenSubscriberCount }?.subscriberCount?.toLongOrNull(),
            videoCount = item.statistics?.videoCount?.toLongOrNull(),
        )
    }

    /** A channel's latest uploads, newest first. Works without signing in. */
    suspend fun channelVideos(channelId: String, showShorts: Boolean): List<Video> {
        check(hasApiKey) { "No API key" }
        val suffix = channelId.removePrefix("UC")
        // Same trick as the Subscriptions feed: "UULF" is the long-form-only uploads list.
        val longForm = if (showShorts) null else publicUploadsOrNull("UULF$suffix")
        val needsShortsCheck = !showShorts && longForm == null
        val items = longForm ?: publicUploadsOrNull("UU$suffix").orEmpty()
        val ids = items.map { it.contentDetails.videoId }
        if (ids.isEmpty()) return emptyList()
        val byId = api.publicVideosById(ids = ids.joinToString(",")).items.map(Video::from).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
            .filterNot { needsShortsCheck && it.isShort }
            .sortedByDescending { it.publishedAt }
    }

    /** A playlist's items, or null when YouTube says it doesn't exist (404). */
    private suspend fun publicUploadsOrNull(playlistId: String): List<PlaylistItem>? = try {
        api.publicPlaylistItems(playlistId = playlistId, maxResults = 30).items
    } catch (e: HttpException) {
        if (e.code() == 404) null else throw e
    }

    /** Top comments for a video. Returns null when the channel has turned comments off. */
    suspend fun comments(videoId: String): List<Comment>? {
        check(hasApiKey) { "No API key" }
        return try {
            api.comments(videoId = videoId).items.map { thread ->
                val c = thread.snippet.topLevelComment.snippet
                Comment(
                    id = thread.id,
                    author = c.authorDisplayName,
                    authorChannelId = c.authorChannelId?.value?.takeIf { it.isNotEmpty() },
                    authorImageUrl = c.authorProfileImageUrl,
                    text = c.textDisplay,
                    likeCount = c.likeCount,
                    replyCount = thread.snippet.totalReplyCount,
                    publishedAt = runCatching { Instant.parse(c.publishedAt) }.getOrDefault(Instant.EPOCH),
                )
            }
        } catch (e: HttpException) {
            if (e.code() == 403 && e.response()?.errorBody()?.string()?.contains("commentsDisabled") == true) null
            else throw e
        }
    }

    private suspend fun allSubscribedChannelIds(): List<String> {
        val ids = mutableListOf<String>()
        var pageToken: String? = null
        do {
            val page = api.mySubscriptions(pageToken = pageToken)
            ids += page.items.map { it.snippet.resourceId.channelId }.filter { it.isNotEmpty() }
            pageToken = page.nextPageToken
        } while (pageToken != null && ids.size < MAX_CHANNELS)
        return ids
    }

    private suspend fun latestUploads(channelSuffix: String, showShorts: Boolean): List<Upload> {
        if (!showShorts) {
            try {
                return fetchUploads("UULF$channelSuffix", needsShortsCheck = false)
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 403) throw e
                // Fall through: some channels have no long-form playlist.
            }
        }
        return try {
            fetchUploads("UU$channelSuffix", needsShortsCheck = !showShorts)
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) throw e
            emptyList() // A channel with no uploads returns 404; skip it.
        }
    }

    private suspend fun fetchUploads(playlistId: String, needsShortsCheck: Boolean) =
        api.playlistItems(playlistId = playlistId, maxResults = UPLOADS_PER_CHANNEL).items.map {
            Upload(
                videoId = it.contentDetails.videoId,
                publishedAt = it.contentDetails.videoPublishedAt
                    ?.let { t -> runCatching { Instant.parse(t) }.getOrNull() } ?: Instant.EPOCH,
                needsShortsCheck = needsShortsCheck,
            )
        }

    private suspend fun videoDetails(ids: List<String>): List<Video> =
        ids.chunked(50).flatMap { chunk ->
            api.videosById(ids = chunk.joinToString(",")).items.map(Video::from)
        }

    private data class Upload(val videoId: String, val publishedAt: Instant, val needsShortsCheck: Boolean)

    private companion object {
        const val MAX_CHANNELS = 500
        const val UPLOADS_PER_CHANNEL = 5
        const val MAX_FEED_VIDEOS = 150
    }
}
