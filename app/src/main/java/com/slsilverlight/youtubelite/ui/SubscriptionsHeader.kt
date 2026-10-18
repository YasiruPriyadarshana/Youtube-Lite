package com.slsilverlight.youtubelite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.slsilverlight.youtubelite.data.SubscribedChannel

/**
 * The row of subscribed channels along the top of Subscriptions, like the YouTube app.
 * Tapping a channel shows only its videos; "All" goes back to every subscription.
 */
@Composable
fun SubscriptionsHeader(
    channels: List<SubscribedChannel>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    if (channels.isEmpty()) return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(channels, key = { it.id }) { channel ->
                    ChannelBubble(
                        channel = channel,
                        isSelected = channel.id == selected,
                        dimmed = selected != null && channel.id != selected,
                        onClick = { onSelect(if (channel.id == selected) null else channel.id) },
                    )
                }
            }
            TextButton(onClick = { onSelect(null) }, enabled = selected != null) {
                Text("All", fontWeight = FontWeight.SemiBold)
            }
        }
        val current = channels.firstOrNull { it.id == selected }
        if (current != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    current.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onOpenChannel(current.id) }) { Text("View channel") }
            }
        }
    }
}

@Composable
private fun ChannelBubble(
    channel: SubscribedChannel,
    isSelected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .alpha(if (dimmed) 0.45f else 1f)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ring = if (isSelected) {
            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        } else Modifier
        AsyncImage(
            model = channel.avatarUrl,
            contentDescription = channel.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .then(ring)
                .padding(if (isSelected) 3.dp else 0.dp)
                .clip(CircleShape)
                .background(Color.DarkGray),
        )
        Text(
            channel.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
