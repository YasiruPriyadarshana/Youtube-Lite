package com.slsilverlight.youtubelite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.slsilverlight.youtubelite.data.Video

/** A channel's page: its avatar, name and subscriber count, then its latest videos. */
@Composable
fun ChannelScreen(
    state: ChannelPageState,
    onRefresh: () -> Unit,
    onVideoClick: (Video) -> Unit,
) {
    FeedScreen(
        state = state.videos,
        emptyMessage = "This channel hasn't uploaded any videos.",
        onRefresh = onRefresh,
        onVideoClick = onVideoClick,
        header = {
            val channel = state.channel
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = channel?.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.DarkGray),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(channel?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        val details = listOfNotNull(
                            channel?.handle,
                            channel?.subscriberCount?.let { "${formatCompact(it)} subscribers" },
                            channel?.videoCount?.let { "${formatCompact(it)} videos" },
                        ).joinToString(" · ")
                        if (details.isNotEmpty()) {
                            Text(
                                details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!channel?.description.isNullOrBlank()) {
                    Text(
                        channel?.description.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
    )
}
