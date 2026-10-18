package com.slsilverlight.youtubelite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.slsilverlight.youtubelite.data.AppSettings
import com.slsilverlight.youtubelite.data.Reminder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val BREAK_CHOICES = listOf(5, 10, 15, 30, 45, 60, 90, 120, 180)

/** The "Time management" part of Settings, like YouTube's "Time watched" page. */
@Composable
fun TimeManagementSection(
    settings: AppSettings,
    onOpenTimeWatched: () -> Unit,
    onBreakReminderChange: (enabled: Boolean, minutes: Int) -> Unit,
    onBedtimeChange: (enabled: Boolean, start: Int, end: Int, waitForVideo: Boolean) -> Unit,
) {
    Text(
        "Time management",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
    ListItem(
        headlineContent = { Text("Time watched") },
        supportingContent = { Text("How long you've watched videos in this app") },
        modifier = Modifier.clickable(onClick = onOpenTimeWatched),
    )
    ListItem(
        headlineContent = { Text("Remind me to take a break") },
        supportingContent = {
            Text(if (settings.breakReminder) "Every ${formatMinutes(settings.breakMinutes)} of watching" else "Off")
        },
        trailingContent = {
            Switch(
                checked = settings.breakReminder,
                onCheckedChange = { onBreakReminderChange(it, settings.breakMinutes) },
            )
        },
    )
    if (settings.breakReminder) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BREAK_CHOICES.forEach { minutes ->
                FilterChip(
                    selected = minutes == settings.breakMinutes,
                    onClick = { onBreakReminderChange(true, minutes) },
                    label = { Text(formatMinutes(minutes)) },
                )
            }
        }
    }

    ListItem(
        headlineContent = { Text("Remind me when it's bedtime") },
        supportingContent = {
            Text(
                if (settings.bedtimeReminder) "${formatClock(settings.bedtimeStart)} to ${formatClock(settings.bedtimeEnd)}"
                else "Off"
            )
        },
        trailingContent = {
            Switch(
                checked = settings.bedtimeReminder,
                onCheckedChange = {
                    onBedtimeChange(it, settings.bedtimeStart, settings.bedtimeEnd, settings.bedtimeWaitForVideo)
                },
            )
        },
    )
    if (settings.bedtimeReminder) {
        var editing by remember { mutableStateOf<String?>(null) }
        ListItem(
            headlineContent = { Text("Start time") },
            trailingContent = { Text(formatClock(settings.bedtimeStart)) },
            modifier = Modifier.clickable { editing = "start" }.padding(start = 16.dp),
        )
        ListItem(
            headlineContent = { Text("End time") },
            trailingContent = { Text(formatClock(settings.bedtimeEnd)) },
            modifier = Modifier.clickable { editing = "end" }.padding(start = 16.dp),
        )
        ListItem(
            headlineContent = { Text("Wait until I finish my video") },
            supportingContent = { Text("Show the reminder when the video ends") },
            trailingContent = {
                Switch(
                    checked = settings.bedtimeWaitForVideo,
                    onCheckedChange = { onBedtimeChange(true, settings.bedtimeStart, settings.bedtimeEnd, it) },
                )
            },
            modifier = Modifier.padding(start = 16.dp),
        )
        editing?.let { which ->
            TimeDialog(
                title = if (which == "start") "Start time" else "End time",
                minutes = if (which == "start") settings.bedtimeStart else settings.bedtimeEnd,
                onDismiss = { editing = null },
                onConfirm = { picked ->
                    editing = null
                    if (which == "start") onBedtimeChange(true, picked, settings.bedtimeEnd, settings.bedtimeWaitForVideo)
                    else onBedtimeChange(true, settings.bedtimeStart, picked, settings.bedtimeWaitForVideo)
                },
            )
        }
    }
    HorizontalDivider(Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, minutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Today, yesterday, the last 7 days and a daily average, with a bar for each day. */
@Composable
fun TimeWatchedScreen(days: Map<LocalDate, Long>, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekTotal = week.sumOf { days[it] ?: 0L }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Daily average", style = MaterialTheme.typography.labelLarge)
        Text(formatWatched(weekTotal / 7), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Daily average over the last 7 days",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        val max = week.maxOf { days[it] ?: 0L }.coerceAtLeast(1)
        Row(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEach { day ->
                val seconds = days[day] ?: 0L
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((seconds.toFloat() / max * 0.85f).coerceAtLeast(0.01f))
                            .background(
                                if (day == today) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            ),
                    )
                    Text(dayLabel(day.dayOfWeek), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        StatRow("Today", days[today] ?: 0L)
        StatRow("Yesterday", days[today.minusDays(1)] ?: 0L)
        StatRow("Last 7 days", weekTotal)
        Spacer(Modifier.height(16.dp))
        Text(
            "Counts only videos played in this app, on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatRow(label: String, seconds: Long) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Text(formatWatched(seconds), style = MaterialTheme.typography.bodyLarge) },
    )
}

/** The pop-up YouTube shows when it's time for a break or for bed. */
@Composable
fun ReminderDialog(reminder: Reminder, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    val (title, text) = when (reminder) {
        is Reminder.TakeBreak -> "Time to take a break?" to
            "You've been watching for ${formatMinutes(reminder.minutes)}."
        is Reminder.Bedtime -> "It's time for bed" to
            "Your bedtime reminder is set for ${formatClock(reminder.start)} to ${formatClock(reminder.end)}."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
        dismissButton = { TextButton(onClick = onOpenSettings) { Text("Settings") } },
    )
}

private fun dayLabel(day: DayOfWeek) = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> if (minutes == 60) "1 hour" else "${minutes / 60} hours"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private fun formatClock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun formatWatched(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "$h h $m min"
        m > 0 -> "$m min"
        seconds > 0 -> "< 1 min"
        else -> "0 min"
    }
}
