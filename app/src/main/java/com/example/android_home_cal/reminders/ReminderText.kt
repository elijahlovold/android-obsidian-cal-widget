package com.example.android_home_cal.reminders

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Notification wording. */
object ReminderText {

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun body(reminder: Reminder, now: LocalDateTime): String =
        listOfNotNull(reminder.section, relative(reminder.eventAt, now)).joinToString(" · ")

    fun relative(event: LocalDateTime, now: LocalDateTime): String {
        val minutes = Duration.between(now, event).toMinutes()
        val time = event.format(timeFormat)
        val sameDay = event.toLocalDate() == now.toLocalDate()
        return when {
            minutes in -1..1 -> "now"
            minutes < 0 -> "$time, ${duration(-minutes)} ago"
            sameDay -> "in ${duration(minutes)}"
            event.toLocalDate() == now.toLocalDate().plusDays(1) -> "tomorrow $time"
            else -> event.format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault()))
        }
    }

    fun duration(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
