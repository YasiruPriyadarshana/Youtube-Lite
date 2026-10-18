package com.slsilverlight.youtubelite.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.slsilverlight.youtubelite.data.RepliesPage
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.slsilverlight.youtubelite.data.Channel
import com.slsilverlight.youtubelite.data.Comment
import com.slsilverlight.youtubelite.data.PlaybackMonitor
import com.slsilverlight.youtubelite.data.Rating
import com.slsilverlight.youtubelite.data.Video
import kotlinx.coroutines.launch

/**
 * Plays a video in YouTube's official embedded player inside a WebView, with the channel and
 * a comments panel below, laid out like the YouTube app.
 *
 * YouTube requires embeds to identify the app through the HTTP Referer, otherwise the player
 * shows error 152/153. Loading the page with a base URL of https://<package name> sets it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    videoId: String,
    video: Video?,
    loadComments: suspend (String) -> Result<List<Comment>?>,
    loadReplies: suspend (commentId: String, pageToken: String?) -> Result<RepliesPage>,
    loadChannel: suspend (String) -> Channel?,
    onChannelClick: (String) -> Unit,
    isSignedIn: Boolean,
    loadRating: suspend (String) -> Rating?,
    rate: suspend (String, Rating) -> Result<Unit>,
    loadDislikes: suspend (String) -> Long?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var playerError by remember { mutableStateOf<Int?>(null) }
    var showComments by remember { mutableStateOf(false) }

    val webView = rememberPlayerWebView(videoId, onPlayerError = { playerError = it })

    val comments by produceState<CommentsState>(CommentsState.Loading, videoId) {
        value = loadComments(videoId).fold(
            onSuccess = { if (it == null) CommentsState.Disabled else CommentsState.Loaded(it) },
            onFailure = { CommentsState.Failed(it.message ?: "Couldn't load comments.") },
        )
    }
    val channelId = video?.channelId.orEmpty()
    val channel by produceState<Channel?>(null, channelId) {
        if (channelId.isNotEmpty()) value = loadChannel(channelId)
    }

    Column(modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(12.dp)) {
                    if (video != null) {
                        Text(video.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(video.viewCount?.let(::formatViews), formatAge(video.publishedAt))
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    playerError?.let { code ->
                        Text(
                            "This video can't play here (error $code). The channel may have turned off playback in other apps.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            if (video != null && channelId.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onChannelClick(channelId) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = channel?.avatarUrl,
                            contentDescription = "Open channel",
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.DarkGray),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                channel?.title ?: video.channelTitle,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            channel?.subscriberCount?.let {
                                Text(
                                    "${formatCompact(it)} subscribers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item {
                ActionRow(
                    videoId = videoId,
                    title = video?.title,
                    likeCount = video?.likeCount,
                    isSignedIn = isSignedIn,
                    loadRating = loadRating,
                    rate = rate,
                    loadDislikes = loadDislikes,
                )
            }
            item {
                CommentsCard(
                    state = comments,
                    count = video?.commentCount,
                    onClick = { showComments = true },
                )
            }
        }
    }

    if (showComments) {
        ModalBottomSheet(
            onDismissRequest = { showComments = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            CommentsSheet(
                state = comments,
                count = video?.commentCount,
                loadReplies = loadReplies,
                onAuthorClick = {
                    showComments = false
                    onChannelClick(it)
                },
            )
        }
    }
}

/** Like / dislike / share / open-in-YouTube buttons, styled like the YouTube app's pills. */
@Composable
private fun ActionRow(
    videoId: String,
    title: String?,
    likeCount: Long?,
    isSignedIn: Boolean,
    loadRating: suspend (String) -> Rating?,
    rate: suspend (String, Rating) -> Result<Unit>,
    loadDislikes: suspend (String) -> Long?,
) {
    val context = LocalContext.current
    val dislikeEstimate by produceState<Long?>(null, videoId) { value = loadDislikes(videoId) }
    val scope = rememberCoroutineScope()
    // The rating YouTube had when the screen opened, and the one shown now.
    var original by remember(videoId) { mutableStateOf(Rating.None) }
    var current by remember(videoId) { mutableStateOf(Rating.None) }
    LaunchedEffect(videoId, isSignedIn) {
        loadRating(videoId)?.let { original = it; current = it }
    }

    fun toggle(target: Rating) {
        if (!isSignedIn) {
            Toast.makeText(context, "Sign in on the Subscriptions tab to like videos", Toast.LENGTH_SHORT).show()
            return
        }
        val previous = current
        val next = if (current == target) Rating.None else target
        current = next
        scope.launch {
            rate(videoId, next).onFailure {
                current = previous
                Toast.makeText(context, it.message ?: "Couldn't save your rating", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val shownLikes = likeCount?.let {
        it - (if (original == Rating.Like) 1 else 0) + (if (current == Rating.Like) 1 else 0)
    }
    // A zero usually means the estimate service has no data for the video, so it's hidden too.
    val shownDislikes = dislikeEstimate?.takeIf { it > 0 }?.let {
        (it - (if (original == Rating.Dislike) 1 else 0) + (if (current == Rating.Dislike) 1 else 0)).coerceAtLeast(0)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clickable { toggle(Rating.Like) }
                        .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (current == Rating.Like) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        modifier = Modifier.size(20.dp),
                    )
                    if (shownLikes != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(formatCompact(shownLikes), style = MaterialTheme.typography.labelLarge)
                    }
                }
                VerticalDivider(Modifier.height(20.dp))
                Row(
                    Modifier
                        .clickable { toggle(Rating.Dislike) }
                        .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (current == Rating.Dislike) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Dislike",
                        modifier = Modifier.size(20.dp),
                    )
                    if (shownDislikes != null) {
                        Spacer(Modifier.width(6.dp))
                        // "~" marks it as an estimate; YouTube doesn't publish real dislike counts.
                        Text("~${formatCompact(shownDislikes)}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        PillButton(Icons.Outlined.Share, "Share") {
            val url = "https://youtu.be/$videoId"
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
                title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            }
            context.startActivity(Intent.createChooser(send, "Share video"))
        }
        PillButton(Icons.AutoMirrored.Outlined.OpenInNew, "YouTube") {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")))
        }
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private sealed interface CommentsState {
    data object Loading : CommentsState
    data object Disabled : CommentsState
    data class Failed(val message: String) : CommentsState
    data class Loaded(val comments: List<Comment>) : CommentsState
}

/** The collapsed "Comments" box under the video, showing the top comment like YouTube does. */
@Composable
private fun CommentsCard(state: CommentsState, count: Long?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = state is CommentsState.Loaded, onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                listOfNotNull("Comments", count?.let(::formatCompact)).joinToString("  "),
                style = MaterialTheme.typography.titleSmall,
            )
            val preview = when (state) {
                CommentsState.Loading -> "Loading…"
                CommentsState.Disabled -> "Comments are turned off."
                is CommentsState.Failed -> state.message
                is CommentsState.Loaded -> state.comments.firstOrNull()?.text ?: "No comments yet."
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val first = (state as? CommentsState.Loaded)?.comments?.firstOrNull()
                if (first != null) {
                    AsyncImage(
                        model = first.authorImageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.DarkGray),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(preview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The full comments panel, with replies that open under each comment. */
@Composable
private fun CommentsSheet(
    state: CommentsState,
    count: Long?,
    loadReplies: suspend (commentId: String, pageToken: String?) -> Result<RepliesPage>,
    onAuthorClick: (String) -> Unit,
) {
    // Comment ids whose replies are open. Hiding replies forgets them; opening again reloads.
    val open = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
        item {
            Text(
                listOfNotNull("Comments", count?.let(::formatCompact)).joinToString("  "),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val list = (state as? CommentsState.Loaded)?.comments.orEmpty()
        items(list, key = { it.id }) { comment ->
            CommentRow(comment, onAuthorClick)
            if (comment.replyCount > 0) {
                val isOpen = open[comment.id] == true
                TextButton(
                    onClick = {
                        if (isOpen) {
                            open.remove(comment.id)
                        } else {
                            open[comment.id] = true
                        }
                    },
                    modifier = Modifier.padding(start = 52.dp),
                ) {
                    Text(if (isOpen) "Hide replies" else "${comment.replyCount} ${if (comment.replyCount == 1) "reply" else "replies"}")
                }
                if (isOpen) RepliesThread(comment.id, loadReplies, onAuthorClick)
            }
        }
    }
}

private const val REPLIES_STEP = 10

/**
 * A comment's replies, like YouTube: the first few, then "Show more replies" reveals more,
 * fetching the next page from YouTube once the loaded ones run out.
 */
@Composable
private fun RepliesThread(
    commentId: String,
    loadReplies: suspend (commentId: String, pageToken: String?) -> Result<RepliesPage>,
    onAuthorClick: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val loaded = remember(commentId) { mutableStateListOf<Comment>() }
    var nextPage by remember(commentId) { mutableStateOf<String?>(null) }
    var shown by remember(commentId) { mutableIntStateOf(REPLIES_STEP) }
    var loading by remember(commentId) { mutableStateOf(true) }
    var error by remember(commentId) { mutableStateOf<String?>(null) }

    fun fetch(token: String?) {
        loading = true
        error = null
        scope.launch {
            loadReplies(commentId, token)
                .onSuccess { page ->
                    loaded += page.replies.filter { r -> loaded.none { it.id == r.id } }
                    nextPage = page.nextPageToken
                }
                .onFailure { error = it.message ?: "Couldn't load replies." }
            loading = false
        }
    }
    LaunchedEffect(commentId) { fetch(null) }

    Column(Modifier.padding(start = 40.dp)) {
        loaded.take(shown).forEach { CommentRow(it, onAuthorClick, small = true) }
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
            error != null -> TextButton(onClick = { fetch(nextPage.takeIf { loaded.isNotEmpty() }) }) {
                Text("${error}  Tap to retry.", style = MaterialTheme.typography.bodySmall)
            }
            shown < loaded.size || nextPage != null -> TextButton(
                onClick = {
                    shown += REPLIES_STEP
                    // Fetch the next page once we're about to run out of loaded replies.
                    if (shown >= loaded.size && nextPage != null) fetch(nextPage)
                },
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text("Show more replies")
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, onAuthorClick: (String) -> Unit, small: Boolean = false) {
    val authorModifier = comment.authorChannelId?.let { id -> Modifier.clickable { onAuthorClick(id) } } ?: Modifier
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        AsyncImage(
            model = comment.authorImageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(if (small) 24.dp else 32.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
                .then(authorModifier),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${comment.author} · ${formatAge(comment.publishedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = authorModifier,
            )
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            if (comment.likeCount > 0) {
                Text(
                    "👍 ${formatCompact(comment.likeCount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * A WebView running YouTube's official embedded player for [videoId]. It's destroyed when it
 * leaves the screen, which also stops playback.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun rememberPlayerWebView(videoId: String, loop: Boolean = false, onPlayerError: (Int) -> Unit = {}): WebView {
    val context = LocalContext.current
    val webView = remember(videoId) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // Video frames are drawn on the GPU; without a hardware layer some phones play sound over a black picture.
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = FullscreenChromeClient(context.findActivity())
            val main = Handler(Looper.getMainLooper())
            val playerId = System.identityHashCode(this)
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onError(code: Int) {
                    main.post { onPlayerError(code) }
                }

                // YouTube's states: 0 ended, 1 playing, 2 paused, 3 buffering.
                @JavascriptInterface
                fun onState(state: Int) {
                    if (state == 0) PlaybackMonitor.onFinished(playerId)
                    else if (state != 3) PlaybackMonitor.setPlaying(playerId, state == 1)
                }
            }, "AndroidPlayer")
            val origin = "https://${context.packageName}"
            loadDataWithBaseURL(origin, playerHtml(videoId, origin, loop), "text/html", "utf-8", null)
        }
    }
    // Break and bedtime reminders pause whatever is playing.
    LaunchedEffect(webView) {
        PlaybackMonitor.pauseRequests.collect {
            webView.evaluateJavascript("window.player && player.pauseVideo && player.pauseVideo();", null)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            PlaybackMonitor.onFinished(System.identityHashCode(webView))
            webView.destroy()
        }
    }
    return webView
}

/** Lets the player's full-screen button cover the whole screen in landscape. */
private class FullscreenChromeClient(private val activity: Activity?) : WebChromeClient() {
    private var customView: View? = null
    private var callback: CustomViewCallback? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val decor = activity?.window?.decorView as? FrameLayout ?: return callback.onCustomViewHidden()
        decor.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        customView = view
        this.callback = callback
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onHideCustomView() {
        (activity?.window?.decorView as? FrameLayout)?.removeView(customView)
        customView = null
        callback?.onCustomViewHidden()
        callback = null
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun playerHtml(videoId: String, origin: String, loop: Boolean) = """
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="referrer" content="strict-origin-when-cross-origin">
  <style>html, body { margin: 0; padding: 0; height: 100%; background: #000; overflow: hidden; } #player { width: 100%; height: 100%; }</style>
</head>
<body>
  <div id="player"></div>
  <script src="https://www.youtube.com/iframe_api"></script>
  <script>
    function onYouTubeIframeAPIReady() {
      window.player = new YT.Player('player', {
        width: '100%',
        height: '100%',
        videoId: '$videoId',
        host: 'https://www.youtube.com',
        playerVars: { autoplay: 1, playsinline: 1, rel: 0, origin: '$origin'${if (loop) ", loop: 1, playlist: '$videoId'" else ""} },
        events: {
          onReady: function (e) { e.target.playVideo(); },
          onError: function (e) { AndroidPlayer.onError(e.data); },
          onStateChange: function (e) { AndroidPlayer.onState(e.data); }
        }
      });
    }
  </script>
</body>
</html>
""".trimIndent()
