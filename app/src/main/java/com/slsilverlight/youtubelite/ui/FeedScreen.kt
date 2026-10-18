package com.slsilverlight.youtubelite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImage
import com.slsilverlight.youtubelite.data.Video

/** Whether feeds show a Shorts row, and what tapping a Short does. Provided once by the app. */
data class ShortsShelfConfig(
    val enabled: Boolean = false,
    val onOpen: (shorts: List<Video>, start: Video) -> Unit = { _, _ -> },
)

val LocalShortsShelf = staticCompositionLocalOf { ShortsShelfConfig() }

private const val SHELF_POSITION = 3

@Composable
private fun ShortsShelf(shorts: List<Video>, onClick: (Video) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("Shorts", style = MaterialTheme.typography.titleMedium)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shorts, key = { it.id }) { video ->
                Box(
                    Modifier
                        .width(150.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.DarkGray)
                        .clickable { onClick(video) },
                ) {
                    // YouTube's Shorts thumbnails are landscape crops; centre-cropping keeps the subject.
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                            .padding(8.dp),
                    ) {
                        Text(
                            video.title,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        video.viewCount?.let {
                            Text(formatViews(it), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: FeedState,
    emptyMessage: String,
    onRefresh: () -> Unit,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    // Read here: the LazyColumn's content block below isn't composable, so it can't read locals.
    val shelf = LocalShortsShelf.current
    val shorts = if (shelf.enabled) state.videos.filter { it.isShort } else emptyList()
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (header != null) item { header() }
            state.error?.let { error ->
                item { Message(error) }
            }
            if (state.loaded && state.error == null && state.videos.isEmpty()) {
                item { Message(emptyMessage) }
            }
            if (shorts.isEmpty()) {
                items(state.videos, key = { it.id }) { video ->
                    VideoRow(video, onClick = { onVideoClick(video) })
                }
            } else {
                // Like the YouTube app: a few regular videos, then a Shorts row, then the rest.
                val regular = state.videos.filterNot { it.isShort }
                items(regular.take(SHELF_POSITION), key = { it.id }) { video ->
                    VideoRow(video, onClick = { onVideoClick(video) })
                }
                item(key = "shorts-shelf") { ShortsShelf(shorts, onClick = { shelf.onOpen(shorts, it) }) }
                items(regular.drop(SHELF_POSITION), key = { it.id }) { video ->
                    VideoRow(video, onClick = { onVideoClick(video) })
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

@Composable
private fun VideoRow(video: Video, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.DarkGray)) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val badge = when {
                video.isLive -> "LIVE"
                else -> video.duration?.takeIf { !it.isZero }?.let(::formatDuration)
            }
            if (badge != null) {
                Text(
                    text = badge,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (video.isLive) Color(0xFFCC0000) else Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                video.channelTitle,
                video.viewCount?.let(::formatViews),
                formatAge(video.publishedAt),
            ).joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
