package com.slsilverlight.youtubelite.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** A reminder dialog to show over the app. */
sealed interface Reminder {
    data class TakeBreak(val minutes: Int) : Reminder
    data class Bedtime(val start: Int, val end: Int) : Reminder
}

/**
 * YouTube's time management tools: counts watch time per day, and reminds you to take a
 * break or that it's bedtime. Reminders pause the video, like the YouTube app.
 */
class TimeManager(
    private val scope: CoroutineScope,
    private val settings: StateFlow<AppSettings?>,
    private val store: WatchTimeStore,
) {
    private val _reminder = MutableStateFlow<Reminder?>(null)
    val reminder: StateFlow<Reminder?> = _reminder.asStateFlow()

    private var secondsSinceBreak = 0
    private var unsavedSeconds = 0L
    /** The day the current bedtime window started, so each night reminds only once. */
    private var bedtimeShownFor: LocalDate? = null
    private var bedtimePending = false

    init {
        scope.launch {
            while (true) {
                delay(1_000)
                tick()
            }
        }
        scope.launch {
            PlaybackMonitor.finished.collect {
                if (bedtimePending) {
                    bedtimePending = false
                    settings.value?.takeIf { it.bedtimeReminder }?.let { show(Reminder.Bedtime(it.bedtimeStart, it.bedtimeEnd)) }
                }
            }
        }
    }

    fun dismiss() {
        _reminder.value = null
    }

    /** Saves the seconds counted so far, e.g. when the app goes to the background. */
    fun flush() {
        val seconds = unsavedSeconds
        if (seconds == 0L) return
        unsavedSeconds = 0
        scope.launch { store.add(seconds) }
    }

    private fun tick() {
        val s = settings.value ?: return
        if (!PlaybackMonitor.isPlaying.value || _reminder.value != null) return

        unsavedSeconds++
        if (unsavedSeconds >= 15) flush()

        secondsSinceBreak++
        if (s.breakReminder && secondsSinceBreak >= s.breakMinutes * 60) {
            secondsSinceBreak = 0
            show(Reminder.TakeBreak(s.breakMinutes))
            return
        }

        val window = bedtimeWindowStart(s)
        if (s.bedtimeReminder && window != null && window != bedtimeShownFor) {
            bedtimeShownFor = window
            if (s.bedtimeWaitForVideo) bedtimePending = true
            else show(Reminder.Bedtime(s.bedtimeStart, s.bedtimeEnd))
        }
    }

    private fun show(reminder: Reminder) {
        flush()
        _reminder.value = reminder
        PlaybackMonitor.pauseAll()
    }

    /** The day tonight's bedtime began, or null when it isn't bedtime right now. */
    private fun bedtimeWindowStart(s: AppSettings): LocalDate? {
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        val today = LocalDate.now()
        return when {
            s.bedtimeStart == s.bedtimeEnd -> null
            s.bedtimeStart < s.bedtimeEnd -> today.takeIf { now >= s.bedtimeStart && now < s.bedtimeEnd }
            now >= s.bedtimeStart -> today
            now < s.bedtimeEnd -> today.minusDays(1)
            else -> null
        }
    }
}
