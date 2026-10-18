package com.slsilverlight.youtubelite.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the app's players are doing, so watch time can be counted and reminders can pause them.
 * Every player WebView reports here under its own id.
 */
object PlaybackMonitor {
    private val playingIds = mutableSetOf<Int>()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Fires when a video ends or its player is closed. */
    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    private val _pauseRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pauseRequests: SharedFlow<Unit> = _pauseRequests.asSharedFlow()

    @Synchronized
    fun setPlaying(playerId: Int, playing: Boolean) {
        if (playing) playingIds += playerId else playingIds -= playerId
        _isPlaying.value = playingIds.isNotEmpty()
    }

    fun onFinished(playerId: Int) {
        setPlaying(playerId, false)
        _finished.tryEmit(Unit)
    }

    fun pauseAll() {
        _pauseRequests.tryEmit(Unit)
    }
}
