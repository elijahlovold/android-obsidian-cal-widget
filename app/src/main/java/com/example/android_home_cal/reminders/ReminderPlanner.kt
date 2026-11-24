package com.example.android_home_cal.reminders

import com.example.android_home_cal.cron.CronEntry
import com.example.android_home_cal.cron.CronHistoryEntry
import com.example.android_home_cal.cron.CronOccurrence
import com.example.android_home_cal.notes.NoteTask
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Reminder(
    val key: String,
    val fireAt: LocalDateTime,
    val eventAt: LocalDateTime,
    val ref: TaskRef,
    val title: String,
    val section: String?
)

data class PlannerInput(
    val now: LocalDateTime,
    val notes: Map<LocalDate, List<NoteTask>>,
    val cronEntries: List<CronEntry>,
    val cronHistory: List<CronHistoryEntry>,
    val handledKeys: Set<String>,
    val snoozes: Map<String, LocalDateTime>,
    val zone: ZoneId,
    /** Days ahead to expand cron entries; [notes] is taken as given. */
    val windowDays: Int = 3
)

/**
 * [due]: post these now. [markHandled]: record as handled, including stale reminders that are
 * skipped rather than posted. [nextFireAt]: when to wake for the next one. [completedGroups]:
 * tasks now checked or logged, whose notifications should be cleared.
 */
data class ReminderPlan(
    val due: List<Reminder>,
    val markHandled: Set<String>,
    val nextFireAt: LocalDateTime?,
    val completedGroups: Set<String>
)

object ReminderPlanner {

    fun plan(input: PlannerInput): ReminderPlan {
        val now = input.now
        val today = now.toLocalDate()
        val completed = mutableSetOf<String>()
        val groups = mutableListOf<List<Reminder>>()

        for ((date, tasks) in input.notes) {
            for (task in tasks) {
                val time = task.time ?: continue
                val ref = TaskRef.Note(date, task.rawLine, task.occurrence)
                if (task.checked) {
                    completed += ref.groupId
                    continue
                }
                val event = date.atTime(time)
                groups += task.fireTimes(date).map { fireAt ->
                    Reminder(keyOf(ref, fireAt), fireAt, event, ref, task.displayText, null)
                }
            }
        }

        val windowStart = today.atStartOfDay()
        val windowEnd = today.plusDays(input.windowDays + 1L).atStartOfDay()
        val logged = input.cronHistory.mapTo(HashSet()) { it.occurrence }
        for (entry in input.cronEntries) {
            for (occurrence in entry.expression.occurrences(windowStart, windowEnd, input.zone)) {
                val ref = TaskRef.Cron(occurrence, entry.section, entry.text)
                if (CronOccurrence(occurrence, entry.section, entry.text) in logged) {
                    completed += ref.groupId
                    continue
                }
                groups += listOf(Reminder(keyOf(ref, occurrence), occurrence, occurrence, ref, entry.text, entry.section))
            }
        }

        val due = mutableListOf<Reminder>()
        val markHandled = mutableSetOf<String>()
        var nextFireAt: LocalDateTime? = null
        for (reminders in groups) {
            val pending = reminders.mapNotNull { r ->
                val snoozedUntil = input.snoozes[r.key]
                when {
                    snoozedUntil != null -> r.copy(fireAt = snoozedUntil)
                    r.key in input.handledKeys -> null
                    else -> r
                }
            }
            val (past, future) = pending.partition { !it.fireAt.isAfter(now) }
            val soonest = future.minOfOrNull { it.fireAt }
            val currentNext = nextFireAt
            if (soonest != null && (currentNext == null || soonest < currentNext)) nextFireAt = soonest
            // Catch-up: of everything missed, post only the latest, and only if it's from today.
            val latest = past.maxByOrNull { it.fireAt } ?: continue
            if (latest.fireAt.toLocalDate() == today) due += latest
            markHandled += past.map { it.key }
        }

        return ReminderPlan(due.sortedBy { it.fireAt }, markHandled, nextFireAt, completed)
    }

    fun keyOf(ref: TaskRef, fireAt: LocalDateTime): String = "${ref.groupId}@$fireAt"
}

