package com.slsilverlight.youtubelite.ui

import java.time.Duration
import java.time.Instant

fun formatDuration(d: Duration): String {
    val total = d.seconds
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 1234 -> "1.2K", 5_600_000 -> "5.6M". */
fun formatCompact(count: Long): String = when {
    count >= 1_000_000_000 -> "%.1fB".format(count / 1e9)
    count >= 1_000_000 -> "%.1fM".format(count / 1e6)
    count >= 10_000 -> "%.0fK".format(count / 1e3)
    count >= 1_000 -> "%.1fK".format(count / 1e3)
    else -> count.toString()
}.replace(".0", "")

fun formatViews(count: Long): String = if (count == 1L) "1 view" else "${formatCompact(count)} views"

fun formatAge(publishedAt: Instant, now: Instant = Instant.now()): String {
    val secs = Duration.between(publishedAt, now).seconds.coerceAtLeast(0)
    fun plural(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
    return when {
        secs < 60 -> "just now"
        secs < 3_600 -> plural(secs / 60, "minute")
        secs < 86_400 -> plural(secs / 3_600, "hour")
        secs < 604_800 -> plural(secs / 86_400, "day")
        secs < 2_592_000 -> plural(secs / 604_800, "week")
        secs < 31_536_000 -> plural(secs / 2_592_000, "month")
        else -> plural(secs / 31_536_000, "year")
    }
}
