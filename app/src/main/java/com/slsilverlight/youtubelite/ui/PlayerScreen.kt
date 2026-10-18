package com.slsilverlight.youtubelite.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import com.slsilverlight.youtubelite.data.Comment
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.slsilverlight.youtubelite.data.Video

/**
 * Plays a video in YouTube's official embedded player inside a WebView, with comments below.
 *
 * YouTube requires embeds to identify the app through the HTTP Referer, otherwise the player
 * shows error 152/153. Loading the page with a base URL of https://<package name> sets it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    videoId: String,
    video: Video?,
    loadComments: suspend (String) -> Result<List<Comment>?>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var playerError by remember { mutableStateOf<Int?>(null) }

    val webView = remember {
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
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onError(code: Int) {
                    main.post { playerError = code }
                }
            }, "AndroidPlayer")
            val origin = "https://${context.packageName}"
            loadDataWithBaseURL(origin, playerHtml(videoId, origin), "text/html", "utf-8", null)
        }
    }
    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    val comments by produceState<CommentsState>(CommentsState.Loading, videoId) {
        value = loadComments(videoId).fold(
            onSuccess = { if (it == null) CommentsState.Disabled else CommentsState.Loaded(it) },
            onFailure = { CommentsState.Failed(it.message ?: "Couldn't load comments.") },
        )
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
                            listOfNotNull(video.channelTitle, video.viewCount?.let(::formatViews), formatAge(video.publishedAt))
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
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                            )
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Open in YouTube") }
                }
                HorizontalDivider()
                Text(
                    "Comments",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            when (val state = comments) {
                CommentsState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                CommentsState.Disabled -> item { CommentsMessage("Comments are turned off for this video.") }
                is CommentsState.Failed -> item { CommentsMessage(state.message) }
                is CommentsState.Loaded -> {
                    if (state.comments.isEmpty()) item { CommentsMessage("No comments yet.") }
                    items(state.comments, key = { it.id }) { CommentRow(it) }
                }
            }
        }
    }
}

private sealed interface CommentsState {
    data object Loading : CommentsState
    data object Disabled : CommentsState
    data class Failed(val message: String) : CommentsState
    data class Loaded(val comments: List<Comment>) : CommentsState
}

@Composable
private fun CommentsMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(12.dp),
    )
}

@Composable
private fun CommentRow(comment: Comment) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        AsyncImage(
            model = comment.authorImageUrl,
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.DarkGray),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${comment.author} · ${formatAge(comment.publishedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            val extras = listOfNotNull(
                comment.likeCount.takeIf { it > 0 }?.let { "$it likes" },
                comment.replyCount.takeIf { it > 0 }?.let { "$it replies" },
            )
            if (extras.isNotEmpty()) {
                Text(
                    extras.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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

private fun playerHtml(videoId: String, origin: String) = """
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
      new YT.Player('player', {
        width: '100%',
        height: '100%',
        videoId: '$videoId',
        host: 'https://www.youtube.com',
        playerVars: { autoplay: 1, playsinline: 1, rel: 0, origin: '$origin' },
        events: {
          onReady: function (e) { e.target.playVideo(); },
          onError: function (e) { AndroidPlayer.onError(e.data); }
        }
      });
    }
  </script>
</body>
</html>
""".trimIndent()
