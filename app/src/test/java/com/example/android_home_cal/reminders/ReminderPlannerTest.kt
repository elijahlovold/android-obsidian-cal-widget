package com.example.android_home_cal.reminders

import com.example.android_home_cal.cron.CronFileParser
import com.example.android_home_cal.cron.CronHistoryEntry
import com.example.android_home_cal.cron.CronOutcome
import com.example.android_home_cal.notes.NoteTask
import com.example.android_home_cal.notes.ReminderLineParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {

    private val today = LocalDate.of(2026, 11, 23)
    private fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, day, h, m)
    private fun task(line: String) = ReminderLineParser.parse(line)!!

    private fun input(
        now: LocalDateTime,
        notes: Map<LocalDate, List<NoteTask>> = emptyMap(),
        cron: String = "",
        history: List<CronHistoryEntry> = emptyList(),
        handled: Set<String> = emptySet(),
        snoozes: Map<String, LocalDateTime> = emptyMap()
    ) = PlannerInput(now, notes, CronFileParser.parse(cron).entries, history, handled, snoozes, ZoneOffset.UTC)

    @Test
    fun `before anything is due, schedules the earliest fire time`() {
        val plan = ReminderPlanner.plan(input(at(23, 13, 0), mapOf(today to listOf(task("- [ ] 14:30 -30m dentist")))))
        assertTrue(plan.due.isEmpty())
        assertEquals(at(23, 14, 0), plan.nextFireAt)
    }

    @Test
    fun `fires when due and schedules the next lead`() {
        val plan = ReminderPlanner.plan(input(at(23, 14, 0), mapOf(today to listOf(task("- [ ] 14:30 -30m dentist")))))
        assertEquals(listOf(at(23, 14, 0)), plan.due.map { it.fireAt })
        assertEquals(at(23, 14, 30), plan.nextFireAt)
        assertEquals("14:30 dentist", plan.due.single().title)
    }

    @Test
    fun `catch-up posts only the latest missed reminder and marks the rest handled`() {
        val t = task("- [ ] 14:30 -1h -30m dentist")
        val plan = ReminderPlanner.plan(input(at(23, 14, 10), mapOf(today to listOf(t))))
        assertEquals(listOf(at(23, 14, 0)), plan.due.map { it.fireAt })
        assertEquals(2, plan.markHandled.size)
        assertEquals(at(23, 14, 30), plan.nextFireAt)
    }

    @Test
    fun `stale reminders from a previous day are handled but not posted`() {
        val tomorrow = today.plusDays(1)
        val t = task("- [ ] 9am -1d8pm flight")
        // The 8pm-yesterday lead was missed; it's now the next morning.
        val plan = ReminderPlanner.plan(input(LocalDateTime.of(tomorrow, java.time.LocalTime.of(7, 0)), mapOf(tomorrow to listOf(t))))
        assertTrue(plan.due.isEmpty())
        assertEquals(1, plan.markHandled.size)
        assertEquals(LocalDateTime.of(tomorrow, java.time.LocalTime.of(9, 0)), plan.nextFireAt)
    }

    @Test
    fun `handled keys don't refire, checked tasks complete their group`() {
        val open = task("- [ ] 14:30 dentist")
        val key = ReminderPlanner.keyOf(TaskRef.Note(today, open.rawLine, 0), at(23, 14, 30))
        val plan = ReminderPlanner.plan(
            input(at(23, 15, 0), mapOf(today to listOf(open, task("- [x] 10:00 done thing"))), handled = setOf(key))
        )
        assertTrue(plan.due.isEmpty())
        assertNull(plan.nextFireAt)
        assertEquals(1, plan.completedGroups.size)
    }

    @Test
    fun `snooze overrides handled and fires at the snooze time`() {
        val t = task("- [ ] 14:30 dentist")
        val key = ReminderPlanner.keyOf(TaskRef.Note(today, t.rawLine, 0), at(23, 14, 30))
        val waiting = ReminderPlanner.plan(
            input(at(23, 14, 35), mapOf(today to listOf(t)), handled = setOf(key), snoozes = mapOf(key to at(23, 14, 40)))
        )
        assertEquals(at(23, 14, 40), waiting.nextFireAt)
        val firing = ReminderPlanner.plan(
            input(at(23, 14, 40), mapOf(today to listOf(t)), handled = setOf(key), snoozes = mapOf(key to at(23, 14, 40)))
        )
        assertEquals(listOf(key), firing.due.map { it.key })
        assertTrue(key in firing.markHandled)
    }

    @Test
    fun `cron occurrences in the window, history suppresses them`() {
        val cron = "# Daily\n0 9 * * * take meds"
        val plan = ReminderPlanner.plan(input(at(23, 8, 0), cron = cron))
        assertEquals(at(23, 9, 0), plan.nextFireAt)

        val done = CronHistoryEntry(at(23, 9, 0), CronOutcome.DONE, "Daily", "take meds")
        val after = ReminderPlanner.plan(input(at(23, 9, 5), cron = cron, history = listOf(done)))
        assertTrue(after.due.isEmpty())
        assertEquals(at(24, 9, 0), after.nextFireAt)
        assertEquals(1, after.completedGroups.size)

        val firing = ReminderPlanner.plan(input(at(23, 9, 0), cron = cron))
        assertEquals("Daily", firing.due.single().section)
    }

    @Test
    fun `relative wording`() {
        val now = at(23, 14, 0)
        assertEquals("now", ReminderText.relative(at(23, 14, 0), now))
        assertEquals("in 30 min", ReminderText.relative(at(23, 14, 30), now))
        assertEquals("in 1h 30m", ReminderText.relative(at(23, 15, 30), now))
        assertEquals("in 2h", ReminderText.relative(at(23, 16, 0), now))
        assertEquals("tomorrow 09:00", ReminderText.relative(at(24, 9, 0), now))
        assertEquals("13:00, 1h ago", ReminderText.relative(at(23, 13, 0), now))
    }
}
