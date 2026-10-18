package com.slsilverlight.youtubelite.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.slsilverlight.youtubelite.data.AppSettings
import com.slsilverlight.youtubelite.data.VIDEO_CATEGORY_NAMES

private const val SEARCH_ROUTE = "search"
private const val HISTORY_ROUTE = "library/history"
private const val LIKED_ROUTE = "library/liked"
private const val PLAYLIST_ROUTE = "library/playlist/{id}/{title}"
private const val CHANNEL_ROUTE = "channel/{id}"
private const val SHORTS_ROUTE = "shorts"

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Subscriptions("subscriptions", "Subscriptions", Icons.Filled.Subscriptions),
    Library("library", "Library", Icons.Filled.VideoLibrary),
    Settings("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun App(viewModel: MainViewModel) {
    val loadedSettings by viewModel.settings.collectAsStateWithLifecycle()
    val settings = loadedSettings
    if (settings == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        MainScaffold(viewModel, settings)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(viewModel: MainViewModel, settings: AppSettings) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val home by viewModel.home.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val libraryList by viewModel.libraryList.collectAsStateWithLifecycle()
    val channelPage by viewModel.channelPage.collectAsStateWithLifecycle()
    val shorts by viewModel.shorts.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    // Open on Home when it's switched on, otherwise on Subscriptions.
    val startRoute = remember { if (settings.showHome) Tab.Home.route else Tab.Subscriptions.route }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val tabs = Tab.entries.filter { it != Tab.Home || settings.showHome }

    // If Home is switched off while it's open, go to Subscriptions.
    LaunchedEffect(settings.showHome, currentRoute) {
        if (!settings.showHome && currentRoute == Tab.Home.route) navController.navigateToTab(Tab.Subscriptions)
    }

    // The player and Shorts are full-screen, without the top and bottom bars.
    val onPlayerScreen = currentRoute?.startsWith("player") == true || currentRoute == SHORTS_ROUTE
    val shortsShelf = ShortsShelfConfig(
        enabled = settings.showShorts,
        onOpen = { list, start ->
            viewModel.openShorts(list, start)
            navController.navigate(SHORTS_ROUTE)
        },
    )
    Scaffold(
        topBar = {
            if (!onPlayerScreen) {
                TopAppBar(
                    navigationIcon = {
                        // Screens that aren't tabs (search, history, liked, a playlist) get a back arrow.
                        if (currentRoute != null && Tab.entries.none { it.route == currentRoute }) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    title = {
                        val title = when (currentRoute) {
                            SEARCH_ROUTE -> "Search"
                            HISTORY_ROUTE -> "History"
                            LIKED_ROUTE -> "Liked videos"
                            PLAYLIST_ROUTE -> backStack?.arguments?.getString("title")
                            CHANNEL_ROUTE -> channelPage.channel?.title ?: "Channel"
                            else -> tabs.firstOrNull { it.route == currentRoute }?.label
                        }
                        Text(title ?: "YouTube Lite")
                    },
                    actions = {
                        if (currentRoute != SEARCH_ROUTE) {
                            IconButton(onClick = { navController.navigate(SEARCH_ROUTE) { launchSingleTop = true } }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!onPlayerScreen) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route || currentRoute?.startsWith("${tab.route}/") == true,
                            onClick = { navController.navigateToTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalShortsShelf provides shortsShelf) {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Home.route) {
                LaunchedEffect(Unit) { viewModel.loadHomeIfNeeded() }
                val homeFilter by viewModel.homeFilter.collectAsStateWithLifecycle()
                val favourites by viewModel.favouriteCategories.collectAsStateWithLifecycle()
                FeedScreen(
                    state = home,
                    emptyMessage = "No trending videos right now.",
                    onRefresh = viewModel::refreshHome,
                    onVideoClick = { navController.navigate("player/${it.id}") },
                    header = {
                        HomeChips(homeFilter, favourites, onSelect = { viewModel.selectHomeFilter(it) })
                    },
                )
            }
            composable(Tab.Subscriptions.route) {
                when (val state = authState) {
                    AuthState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is AuthState.SignedOut -> SignInPanel(state, viewModel, "Sign in with your Google account to see videos from your subscriptions.")
                    AuthState.SignedIn -> FeedScreen(
                        state = subscriptions,
                        emptyMessage = "No recent videos from your subscriptions.",
                        onRefresh = viewModel::refreshSubscriptions,
                        onVideoClick = { navController.navigate("player/${it.id}") },
                    )
                }
            }
            composable(Tab.Library.route) {
                when (val state = authState) {
                    AuthState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is AuthState.SignedOut -> SignInPanel(state, viewModel, "Sign in with your Google account to see your history, liked videos and playlists.")
                    AuthState.SignedIn -> {
                        LaunchedEffect(Unit) { viewModel.loadPlaylistsIfNeeded() }
                        LibraryScreen(
                            history = history,
                            playlists = playlists,
                            onVideoClick = { navController.navigate("player/${it.id}") },
                            onSeeAllHistory = { navController.navigate(HISTORY_ROUTE) },
                            onLikedClick = {
                                viewModel.openLikedVideos()
                                navController.navigate(LIKED_ROUTE)
                            },
                            onPlaylistClick = {
                                viewModel.openPlaylist(it.id)
                                navController.navigate("library/playlist/${it.id}/${Uri.encode(it.title)}")
                            },
                        )
                    }
                }
            }
            composable(HISTORY_ROUTE) {
                HistoryScreen(
                    history = history,
                    onVideoClick = { navController.navigate("player/${it.id}") },
                    onClear = { viewModel.clearHistory() },
                )
            }
            composable(LIKED_ROUTE) {
                FeedScreen(
                    state = libraryList,
                    emptyMessage = "You haven't liked any videos yet.",
                    onRefresh = viewModel::refreshLibraryList,
                    onVideoClick = { navController.navigate("player/${it.id}") },
                )
            }
            composable(PLAYLIST_ROUTE) {
                FeedScreen(
                    state = libraryList,
                    emptyMessage = "This playlist is empty.",
                    onRefresh = viewModel::refreshLibraryList,
                    onVideoClick = { navController.navigate("player/${it.id}") },
                )
            }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    settings = settings,
                    signedIn = authState == AuthState.SignedIn,
                    onShowShortsChange = { viewModel.setShowShorts(it) },
                    onShowHomeChange = { viewModel.setShowHome(it) },
                    onShowDislikesChange = { viewModel.setShowDislikes(it) },
                    onSignOut = viewModel::signOut,
                )
            }
            composable(SEARCH_ROUTE) {
                val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
                SearchScreen(
                    state = search,
                    recentSearches = recentSearches,
                    loadSuggestions = { viewModel.suggestionsFor(it) },
                    onSearch = { viewModel.search(it) },
                    onRemoveRecent = { viewModel.removeRecentSearch(it) },
                    onRefresh = { viewModel.repeatSearch() },
                    onVideoClick = { navController.navigate("player/${it.id}") },
                )
            }
            composable("player/{videoId}") { entry ->
                val videoId = entry.arguments?.getString("videoId").orEmpty()
                val video = (subscriptions.videos + home.videos + search.videos + libraryList.videos + channelPage.videos.videos + history)
                    .firstOrNull { it.id == videoId }
                LaunchedEffect(videoId) { video?.let { viewModel.addToHistory(it) } }
                PlayerScreen(
                    videoId = videoId,
                    video = video,
                    loadComments = { viewModel.comments(it) },
                    loadReplies = { viewModel.replies(it) },
                    loadChannel = { viewModel.channelInfo(it) },
                    onChannelClick = { id ->
                        viewModel.openChannel(id)
                        navController.navigate("channel/$id")
                    },
                    isSignedIn = authState == AuthState.SignedIn,
                    loadRating = { viewModel.rating(it) },
                    rate = { id, rating -> viewModel.rate(id, rating) },
                    // With the setting off the service isn't asked at all.
                    loadDislikes = { if (settings.showDislikes) viewModel.dislikeEstimate(it) else null },
                )
            }
            composable(CHANNEL_ROUTE) {
                ChannelScreen(
                    state = channelPage,
                    onRefresh = viewModel::refreshChannelVideos,
                    onVideoClick = { navController.navigate("player/${it.id}") },
                )
            }
            composable(SHORTS_ROUTE) {
                ShortsScreen(
                    shorts = shorts.first,
                    startIndex = shorts.second,
                    onBack = { navController.popBackStack() },
                    onChannelClick = { id ->
                        viewModel.openChannel(id)
                        navController.navigate("channel/$id")
                    },
                    onWatched = { viewModel.addToHistory(it) },
                )
            }
        }
        }
    }
}

/** Shown on the Subscriptions and Library tabs until the user signs in. */
@Composable
private fun SignInPanel(state: AuthState.SignedOut, viewModel: MainViewModel, message: String) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onConsentResult(it.data)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center)
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Button(onClick = {
            val consent = state.consent
            if (consent != null) {
                launcher.launch(IntentSenderRequest.Builder(consent.intentSender).build())
            } else {
                viewModel.signOut() // Re-runs authorization to get a consent screen.
            }
        }) { Text("Sign in with Google") }
    }
}

private fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun HomeChips(selected: String, favourites: List<String>, onSelect: (String) -> Unit) {
    // Until History knows your favourites, "For you" is just Trending, so offer popular categories instead.
    val categories = favourites.ifEmpty { listOf("10", "20", "17", "24", "25", "28") }
    val first = if (favourites.isEmpty()) listOf(HOME_FOR_YOU to "Trending")
        else listOf(HOME_FOR_YOU to "For you", HOME_TRENDING to "Trending")
    val chips = first + categories.map { it to (VIDEO_CATEGORY_NAMES[it] ?: it) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.first }) { (id, label) ->
            FilterChip(
                selected = id == selected,
                onClick = { onSelect(id) },
                label = { Text(label) },
            )
        }
    }
}
