package com.slsilverlight.youtubelite.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slsilverlight.youtubelite.auth.GoogleAuth
import com.slsilverlight.youtubelite.data.AppSettings
import com.slsilverlight.youtubelite.data.Channel
import com.slsilverlight.youtubelite.data.Comment
import com.slsilverlight.youtubelite.data.DislikeCounts
import com.slsilverlight.youtubelite.data.HistoryStore
import com.slsilverlight.youtubelite.data.Playlist
import com.slsilverlight.youtubelite.data.Rating
import com.slsilverlight.youtubelite.data.Reminder
import com.slsilverlight.youtubelite.data.RepliesPage
import com.slsilverlight.youtubelite.data.SearchSuggestions
import com.slsilverlight.youtubelite.data.SettingsStore
import com.slsilverlight.youtubelite.data.SubscribedChannel
import com.slsilverlight.youtubelite.data.TimeManager
import com.slsilverlight.youtubelite.data.VIDEO_CATEGORY_NAMES
import com.slsilverlight.youtubelite.data.Video
import com.slsilverlight.youtubelite.data.WatchTimeStore
import com.slsilverlight.youtubelite.data.YouTubeLink
import com.slsilverlight.youtubelite.data.YouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import retrofit2.HttpException

sealed interface AuthState {
    data object Checking : AuthState
    data class SignedOut(val consent: PendingIntent? = null, val error: String? = null) : AuthState
    data object SignedIn : AuthState
}

const val HOME_FOR_YOU = "for_you"
const val HOME_TRENDING = "trending"

data class ChannelPageState(
    val channelId: String? = null,
    val channel: Channel? = null,
    val videos: FeedState = FeedState(),
)

data class PlaylistsState(
    val playlists: List<Playlist> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

data class FeedState(
    val videos: List<Video> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = GoogleAuth(app)
    private val settingsStore = SettingsStore(app)
    private val historyStore = HistoryStore(app)
    private val searchSuggestions = SearchSuggestions(app)
    private val dislikeCounts = DislikeCounts()

    @Volatile private var accessToken: String? = null
    private val repo = YouTubeRepository(app) { accessToken }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _subscriptions = MutableStateFlow(FeedState())
    val subscriptions: StateFlow<FeedState> = _subscriptions.asStateFlow()

    /** The channel row on Subscriptions, newest uploader first. */
    private val _subscribedChannels = MutableStateFlow<List<SubscribedChannel>>(emptyList())
    val subscribedChannels: StateFlow<List<SubscribedChannel>> = _subscribedChannels.asStateFlow()

    /** The channel picked in that row, or null for All. */
    private val _selectedSubscription = MutableStateFlow<String?>(null)
    val selectedSubscription: StateFlow<String?> = _selectedSubscription.asStateFlow()

    private val _subscriptionChannelFeed = MutableStateFlow(FeedState())
    val subscriptionChannelFeed: StateFlow<FeedState> = _subscriptionChannelFeed.asStateFlow()

    private val _home = MutableStateFlow(FeedState())
    val home: StateFlow<FeedState> = _home.asStateFlow()

    /** Null until the saved settings have been read. */
    val history: StateFlow<List<Video>> =
        historyStore.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _playlists = MutableStateFlow(PlaylistsState())
    val playlists: StateFlow<PlaylistsState> = _playlists.asStateFlow()

    /** The list opened from Library: liked videos or one playlist. */
    private val _libraryList = MutableStateFlow(FeedState())
    val libraryList: StateFlow<FeedState> = _libraryList.asStateFlow()
    private var libraryListSource: (suspend () -> List<Video>)? = null

    private val _search = MutableStateFlow(FeedState())
    val search: StateFlow<FeedState> = _search.asStateFlow()
    private var lastQuery = ""

    val recentSearches: StateFlow<List<String>> =
        searchSuggestions.recentSearches.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<AppSettings?> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val watchTimeStore = WatchTimeStore(app)
    private val timeManager = TimeManager(viewModelScope, settings, watchTimeStore)
    val reminder: StateFlow<Reminder?> = timeManager.reminder

    /** Seconds watched per day, for the Time watched page. */
    val watchTime: StateFlow<Map<LocalDate, Long>> =
        watchTimeStore.days.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun dismissReminder() = timeManager.dismiss()

    fun saveWatchTime() = timeManager.flush()

    fun setBreakReminder(enabled: Boolean, minutes: Int) =
        viewModelScope.launch { settingsStore.setBreakReminder(enabled, minutes) }

    fun setBedtime(enabled: Boolean, start: Int, end: Int, waitForVideo: Boolean) =
        viewModelScope.launch { settingsStore.setBedtime(enabled, start, end, waitForVideo) }

    init {
        viewModelScope.launch { applyOutcome(auth.authorize()) }

        // Changing the Shorts switch changes what the feeds contain, so reload them.
        viewModelScope.launch {
            settingsStore.settings.map { it.showShorts }.distinctUntilChanged().drop(1).collect {
                if (_home.value.loaded) refreshHome()
                if (_authState.value == AuthState.SignedIn) refreshSubscriptions()
            }
        }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch { applyOutcome(auth.handleConsentResult(data)) }
    }

    fun signOut() {
        val token = accessToken
        accessToken = null
        _subscriptions.value = FeedState()
        _subscribedChannels.value = emptyList()
        _selectedSubscription.value = null
        _subscriptionChannelFeed.value = FeedState()
        _playlists.value = PlaylistsState()
        _libraryList.value = FeedState()
        viewModelScope.launch {
            if (token != null) auth.signOut(token)
            // Ask again so the sign-in button has a fresh consent screen ready.
            applyOutcome(auth.authorize())
        }
    }

    fun refreshSubscriptions() {
        load(_subscriptions) {
            val feed = repo.subscriptionFeed(currentShowShorts())
            _subscribedChannels.value = feed.channels
            feed.videos
        }
        if (_selectedSubscription.value != null) refreshSubscriptionChannel()
    }

    fun selectSubscription(channelId: String?) {
        if (channelId == _selectedSubscription.value) return
        _selectedSubscription.value = channelId
        _subscriptionChannelFeed.value = FeedState(loading = channelId != null)
        if (channelId != null) refreshSubscriptionChannel()
    }

    private fun refreshSubscriptionChannel() {
        val channelId = _selectedSubscription.value ?: return
        load(_subscriptionChannelFeed) { repo.channelVideos(channelId, currentShowShorts()) }
    }

    /** The chip selected on Home: [HOME_FOR_YOU], [HOME_TRENDING] or a category id. */
    private val _homeFilter = MutableStateFlow(HOME_FOR_YOU)
    val homeFilter: StateFlow<String> = _homeFilter.asStateFlow()

    /** Up to three categories the user watches most in this app, most watched first. */
    private val topCategories = historyStore.history
        .map { videos ->
            videos.map { it.categoryId }
                .filter { it in VIDEO_CATEGORY_NAMES }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
                .take(3).map { it.key }
        }
        .distinctUntilChanged()

    /** Channels you watch most in this app; For you mixes in their latest uploads. */
    private val topChannels = historyStore.history
        .map { videos ->
            videos.map { it.channelId }.filter { it.isNotEmpty() }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
                .take(8).map { it.key }
        }
        .distinctUntilChanged()

    val favouriteCategories: StateFlow<List<String>> = topCategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectHomeFilter(filter: String) {
        if (filter == _homeFilter.value) return
        _homeFilter.value = filter
        _home.value = FeedState(loading = true)
        refreshHome()
    }

    fun refreshHome() = load(_home) {
        val showShorts = currentShowShorts()
        when (val filter = _homeFilter.value) {
            HOME_FOR_YOU -> repo.homeFeed(showShorts, topCategories.first(), topChannels.first())
            HOME_TRENDING -> repo.homeFeed(showShorts)
            else -> repo.categoryFeed(filter, showShorts)
        }
    }

    /** Comments for the player screen; null means comments are turned off for the video. */
    suspend fun comments(videoId: String): Result<List<Comment>?> = friendly { repo.comments(videoId) }

    val isSignedIn: Boolean get() = _authState.value == AuthState.SignedIn

    /** Your like/dislike on a video, or null when signed out, or it couldn't be read. */
    suspend fun rating(videoId: String): Rating? =
        if (!isSignedIn) null else friendly { withFreshTokenOn401 { repo.rating(videoId) } }.getOrNull()

    /** Estimated dislikes from Return YouTube Dislike, or null if unavailable. */
    suspend fun dislikeEstimate(videoId: String): Long? = dislikeCounts.estimate(videoId)

    suspend fun rate(videoId: String, rating: Rating): Result<Unit> =
        friendly { withFreshTokenOn401 { repo.rate(videoId, rating) } }

    suspend fun replies(commentId: String, pageToken: String?): Result<RepliesPage> =
        friendly { repo.replies(commentId, pageToken) }

    private val channelCache = mutableMapOf<String, Channel>()

    /** Channel name, avatar and subscriber count, cached for the session. */
    suspend fun channelInfo(channelId: String): Channel? =
        channelCache[channelId] ?: friendly { repo.channel(channelId) }.getOrNull()?.also { channelCache[channelId] = it }

    private val _channelPage = MutableStateFlow(ChannelPageState())
    val channelPage: StateFlow<ChannelPageState> = _channelPage.asStateFlow()

    /** A YouTube link opened from another app, waiting for the screen to open it. */
    private val _pendingLink = MutableStateFlow<YouTubeLink?>(null)
    val pendingLink: StateFlow<YouTubeLink?> = _pendingLink.asStateFlow()

    /** Details for videos opened from links, which aren't in any feed. */
    private val _linkedVideos = MutableStateFlow<Map<String, Video>>(emptyMap())
    val linkedVideos: StateFlow<Map<String, Video>> = _linkedVideos.asStateFlow()

    fun handleLink(link: YouTubeLink) {
        _pendingLink.value = link
        if (link is YouTubeLink.Video && link.id !in _linkedVideos.value) {
            viewModelScope.launch {
                runCatching { repo.video(link.id) }.getOrNull()?.let { video ->
                    _linkedVideos.update { it + (video.id to video) }
                    addToHistory(video)
                }
            }
        }
    }

    fun consumeLink() {
        _pendingLink.value = null
    }

    fun openChannel(channelId: String) {
        if (_channelPage.value.channelId == channelId && _channelPage.value.videos.loaded) return
        _channelPage.value = ChannelPageState(channelId = channelId, videos = FeedState(loading = true))
        viewModelScope.launch {
            val channel = channelInfo(channelId)
            _channelPage.update { if (it.channelId == channelId) it.copy(channel = channel) else it }
        }
        refreshChannelVideos()
    }

    fun refreshChannelVideos() {
        val channelId = _channelPage.value.channelId ?: return
        viewModelScope.launch {
            _channelPage.update { it.copy(videos = it.videos.copy(loading = true, error = null)) }
            val result = runCatching { repo.channelVideos(channelId, currentShowShorts()) }
            val feed = result.fold(
                onSuccess = { FeedState(videos = it, loaded = true) },
                onFailure = { FeedState(error = friendlyError(it), loaded = true) },
            )
            _channelPage.update { if (it.channelId == channelId) it.copy(videos = feed) else it }
        }
    }

    private suspend fun <T> friendly(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(Exception(friendlyError(e)))
    }

    fun search(query: String) {
        lastQuery = query.trim()
        if (lastQuery.isEmpty()) return
        val saved = lastQuery
        viewModelScope.launch { searchSuggestions.addRecent(saved) }
        load(_search) { repo.search(lastQuery, currentShowShorts()) }
    }

    fun repeatSearch() = search(lastQuery)

    suspend fun suggestionsFor(query: String): List<String> = searchSuggestions.suggestionsFor(query)

    fun removeRecentSearch(query: String) = viewModelScope.launch { searchSuggestions.removeRecent(query) }

    /** The Shorts player swipes through, and where it starts. */
    private val _shorts = MutableStateFlow(emptyList<Video>() to 0)
    val shorts: StateFlow<Pair<List<Video>, Int>> = _shorts.asStateFlow()

    fun openShorts(shorts: List<Video>, start: Video) {
        _shorts.value = shorts to shorts.indexOfFirst { it.id == start.id }.coerceAtLeast(0)
    }

    fun addToHistory(video: Video) = viewModelScope.launch { historyStore.add(video) }

    fun clearHistory() = viewModelScope.launch { historyStore.clear() }

    fun loadPlaylistsIfNeeded() {
        if (!_playlists.value.loaded && !_playlists.value.loading) refreshPlaylists()
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            _playlists.update { it.copy(loading = true, error = null) }
            val result = runCatching { withFreshTokenOn401 { repo.myPlaylists() } }
            _playlists.value = result.fold(
                onSuccess = { PlaylistsState(playlists = it, loaded = true) },
                onFailure = { PlaylistsState(error = friendlyError(it), loaded = true) },
            )
        }
    }

    fun openLikedVideos() = openLibraryList { repo.likedVideos() }

    fun openPlaylist(id: String) = openLibraryList { repo.playlistVideos(id) }

    fun refreshLibraryList() {
        libraryListSource?.let { load(_libraryList, it) }
    }

    private fun openLibraryList(source: suspend () -> List<Video>) {
        libraryListSource = source
        _libraryList.value = FeedState(loading = true)
        load(_libraryList, source)
    }

    fun loadHomeIfNeeded() {
        if (!_home.value.loaded && !_home.value.loading) refreshHome()
    }

    fun setShowShorts(value: Boolean) = viewModelScope.launch { settingsStore.setShowShorts(value) }

    fun setShowHome(value: Boolean) = viewModelScope.launch { settingsStore.setShowHome(value) }

    fun setShowDislikes(value: Boolean) = viewModelScope.launch { settingsStore.setShowDislikes(value) }

    private suspend fun currentShowShorts() = settingsStore.settings.first().showShorts

    private fun applyOutcome(outcome: GoogleAuth.Outcome) {
        when (outcome) {
            is GoogleAuth.Outcome.SignedIn -> {
                accessToken = outcome.accessToken
                _authState.value = AuthState.SignedIn
                if (!_subscriptions.value.loaded) refreshSubscriptions()
            }
            is GoogleAuth.Outcome.NeedsConsent -> _authState.value = AuthState.SignedOut(consent = outcome.intent)
            is GoogleAuth.Outcome.Failed -> _authState.value = AuthState.SignedOut(error = outcome.message)
        }
    }

    private fun load(target: MutableStateFlow<FeedState>, fetch: suspend () -> List<Video>) {
        viewModelScope.launch {
            target.update { it.copy(loading = true, error = null) }
            val result = runCatching { withFreshTokenOn401(fetch) }
            target.update { state ->
                result.fold(
                    onSuccess = { FeedState(videos = it, loaded = true) },
                    onFailure = { state.copy(loading = false, error = friendlyError(it), loaded = true) },
                )
            }
        }
    }

    /** Access tokens last about an hour. On a 401, get a new one silently and try once more. */
    private suspend fun <T> withFreshTokenOn401(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        if (e.code() != 401) throw e
        when (val outcome = auth.authorize()) {
            is GoogleAuth.Outcome.SignedIn -> {
                accessToken = outcome.accessToken
                block()
            }
            else -> {
                applyOutcome(outcome)
                throw e
            }
        }
    }

    private fun friendlyError(t: Throwable): String = when {
        t is IllegalStateException && !repo.hasApiKey ->
            "Home, search and comments need a YouTube API key. Add it to secrets.properties (see the README) and rebuild."
        t is HttpException && t.code() == 400 ->
            "YouTube rejected the request. Check that the API key in secrets.properties is correct."
        t is HttpException && t.code() == 403 ->
            "YouTube refused the request. The daily API quota may be used up; try again tomorrow."
        t is HttpException -> "YouTube returned an error (${t.code()})."
        else -> "Couldn't load videos. Check your connection and pull to refresh."
    }
}
