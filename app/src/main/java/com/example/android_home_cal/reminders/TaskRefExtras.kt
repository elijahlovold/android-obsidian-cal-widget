package com.example.android_home_cal.reminders

import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime

/** Carries a [TaskRef] through Intent extras (notification actions, widget fill-in intents). */
object TaskRefExtras {
    private const val KIND = "task.kind"
    private const val DATE = "task.date"
    private const val RAW_LINE = "task.rawLine"
    private const val OCCURRENCE_INDEX = "task.occurrenceIndex"
    private const val AT = "task.at"
    private const val SECTION = "task.section"
    private const val TEXT = "task.text"

    fun put(intent: Intent, ref: TaskRef): Intent = intent.apply {
        when (ref) {
            is TaskRef.Note -> {
                putExtra(KIND, "note")
                putExtra(DATE, ref.date.toString())
                putExtra(RAW_LINE, ref.rawLine)
                putExtra(OCCURRENCE_INDEX, ref.occurrence)
            }
            is TaskRef.Cron -> {
                putExtra(KIND, "cron")
                putExtra(AT, ref.occurrence.toString())
                putExtra(SECTION, ref.section)
                putExtra(TEXT, ref.text)
            }
        }
    }

    fun get(intent: Intent): TaskRef? = runCatching {
        when (intent.getStringExtra(KIND)) {
            "note" -> TaskRef.Note(
                LocalDate.parse(intent.getStringExtra(DATE)),
                intent.getStringExtra(RAW_LINE) ?: return null,
                intent.getIntExtra(OCCURRENCE_INDEX, 0)
            )
            "cron" -> TaskRef.Cron(
                LocalDateTime.parse(intent.getStringExtra(AT)),
                intent.getStringExtra(SECTION),
                intent.getStringExtra(TEXT) ?: return null
            )
            else -> null
        }
    }.getOrNull()
}

