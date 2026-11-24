package com.example.android_home_cal.reminders

import com.example.android_home_cal.notes.NoteEditor
import java.time.LocalDate
import java.time.LocalDateTime

/** Identifies a task for write-back, from a notification action or a widget checkbox. */
sealed class TaskRef {
    /** One notification per task: later lead reminders replace earlier ones. */
    abstract val groupId: String

    data class Note(val date: LocalDate, val rawLine: String, val occurrence: Int) : TaskRef() {
        override val groupId: String get() = "note|$date|${NoteEditor.normalize(rawLine).trim()}|$occurrence"
    }

    data class Cron(val occurrence: LocalDateTime, val section: String?, val text: String) : TaskRef() {
        override val groupId: String get() = "cron|$occurrence|${section.orEmpty()}|$text"
    }
}

