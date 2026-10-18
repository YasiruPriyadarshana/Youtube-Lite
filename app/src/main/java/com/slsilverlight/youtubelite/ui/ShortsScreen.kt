package com.slsilverlight.youtubelite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.slsilverlight.youtubelite.data.Video

/**
 * Full-screen Shorts you swipe up through, like the YouTube app. Only the Short on screen has
 * a live player; the others show their thumbnail, so nothing plays in the background.
 */
@Composable
fun ShortsScreen(
    shorts: List<Video>,
    startIndex: Int,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    onWatched: (Video) -> Unit,
) {
    if (shorts.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, shorts.lastIndex)) { shorts.size }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { shorts[it].id }) { page ->
            val video = shorts[page]
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (page == pager.settledPage) {
                    LaunchedEffect(video.id) { onWatched(video) }
                    val webView = rememberPlayerWebView(video.id, loop = true)
                    AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f))
                } else {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f),
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
                ) {
                    Text(
                        "@${video.channelTitle}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = if (video.channelId.isNotEmpty()) {
                            Modifier.clickable { onChannelClick(video.channelId) }
                        } else Modifier,
                    )
                    Text(
                        video.title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}
