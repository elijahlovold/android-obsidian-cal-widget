package com.example.android_home_cal.notes

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One `-30m` / `-2h` / `-1d` / `-1d8pm` token: an extra, earlier reminder for a timed task. */
sealed class ReminderLead {
    abstract fun fireAt(noteDate: LocalDate, event: LocalDateTime): LocalDateTime

    data class Minutes(val minutes: Long) : ReminderLead() {
        override fun fireAt(noteDate: LocalDate, event: LocalDateTime): LocalDateTime = event.minusMinutes(minutes)
    }

    data class Hours(val hours: Long) : ReminderLead() {
        override fun fireAt(noteDate: LocalDate, event: LocalDateTime): LocalDateTime = event.minusHours(hours)
    }

    /** [days] before the event: at the event's own clock time, or at [at] if given. */
    data class Days(val days: Long, val at: LocalTime?) : ReminderLead() {
        override fun fireAt(noteDate: LocalDate, event: LocalDateTime): LocalDateTime =
            if (at == null) event.minusDays(days) else noteDate.minusDays(days).atTime(at)
    }
}

/**
 * A checkbox line from a daily note. [time] is non-null only when the item starts with a time
 * token, which is what makes it a reminder. [occurrence] counts identical lines (ignoring the
 * checkbox state) earlier in the same file, so write-back can find the right one.
 */
data class NoteTask(
    val lineIndex: Int,
    val rawLine: String,
    val occurrence: Int,
    val checked: Boolean,
    val indentLevel: Int,
    val displayText: String,
    val time: LocalTime?,
    val leads: List<ReminderLead>,
    val warnings: List<String> = emptyList()
) {
    /** Every reminder instant for this task on [noteDate], earliest first. Empty if untimed. */
    fun fireTimes(noteDate: LocalDate): List<LocalDateTime> {
        val t = time ?: return emptyList()
        val event = noteDate.atTime(t)
        return (leads.map { it.fireAt(noteDate, event) } + event).distinct().sorted()
    }
}

