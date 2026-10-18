package com.slsilverlight.youtubelite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.slsilverlight.youtubelite.data.Playlist
import com.slsilverlight.youtubelite.data.Video

@Composable
fun LibraryScreen(
    history: List<Video>,
    playlists: PlaylistsState,
    onVideoClick: (Video) -> Unit,
    onSeeAllHistory: () -> Unit,
    onLikedClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("History", style = MaterialTheme.typography.titleMedium)
                if (history.isNotEmpty()) TextButton(onClick = onSeeAllHistory) { Text("See all") }
            }
            if (history.isEmpty()) {
                Text(
                    "Videos you watch in this app will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(history.take(20), key = { it.id }) { video ->
                        Column(Modifier.width(160.dp).clickable { onVideoClick(video) }) {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray),
                            )
                            Text(
                                video.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp))
            ListItem(
                headlineContent = { Text("Liked videos") },
                leadingContent = { Icon(Icons.Filled.ThumbUp, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onLikedClick),
            )
            HorizontalDivider()
            Text(
                "Playlists",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        when {
            playlists.loading && playlists.playlists.isEmpty() -> item {
                CircularProgressIndicator(Modifier.padding(24.dp))
            }
            playlists.error != null -> item { LibraryMessage(playlists.error) }
            playlists.loaded && playlists.playlists.isEmpty() -> item { LibraryMessage("You don't have any playlists yet.") }
        }
        items(playlists.playlists, key = { it.id }) { playlist ->
            ListItem(
                headlineContent = { Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${playlist.itemCount} videos") },
                leadingContent = {
                    if (playlist.thumbnailUrl != null) {
                        AsyncImage(
                            model = playlist.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)),
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                    }
                },
                modifier = Modifier.clickable { onPlaylistClick(playlist) },
            )
        }
    }
}

@Composable
private fun LibraryMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(12.dp),
    )
}

/** Every video watched in the app, with a button to clear the list. */
@Composable
fun HistoryScreen(
    history: List<Video>,
    onVideoClick: (Video) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (history.isNotEmpty()) {
            TextButton(onClick = onClear, modifier = Modifier.padding(horizontal = 4.dp)) { Text("Clear history") }
        }
        FeedScreen(
            state = FeedState(videos = history, loaded = true),
            emptyMessage = "No history yet.",
            onRefresh = {},
            onVideoClick = onVideoClick,
        )
    }
}
