package com.example.android_home_cal.reminders

import com.example.android_home_cal.reminders.TaskActions.Outcome
import com.example.android_home_cal.vault.VaultFiles
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TaskActionsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val date = LocalDate.of(2026, 11, 23)
    private val at = LocalDateTime.of(2026, 11, 23, 9, 0)
    private fun vault() = VaultFiles(tmp.root.path)
    private fun note() = File(tmp.root, "Calendar/2026/2026-11-23.md")
    private fun history() = File(tmp.root, "Calendar/Cal-Cron-History.md")

    private fun writeNote(text: String) = note().apply { parentFile!!.mkdirs(); writeText(text) }

    @Test
    fun `ticks and unticks the matching note line`() {
        writeNote("# Agenda\n- [ ] 14:30 -30m dentist\n- [ ] lunch\n")
        val ref = TaskRef.Note(date, "- [ ] 14:30 -30m dentist", 0)

        assertEquals(Outcome.OK, TaskActions.setDone(vault(), ref, true))
        assertEquals("# Agenda\n- [x] 14:30 -30m dentist\n- [ ] lunch\n", note().readText())

        // Already in that state: no write, still OK.
        assertEquals(Outcome.OK, TaskActions.setDone(vault(), ref, true))

        // The ref's raw line still says "[ ]"; matching ignores checkbox state.
        assertEquals(Outcome.OK, TaskActions.setDone(vault(), ref, false))
        assertEquals("# Agenda\n- [ ] 14:30 -30m dentist\n- [ ] lunch\n", note().readText())
    }

    @Test
    fun `missing line or missing note is NOT_FOUND and changes nothing`() {
        writeNote("# Agenda\n- [ ] 14:30 dentist moved\n")
        assertEquals(Outcome.NOT_FOUND, TaskActions.setDone(vault(), TaskRef.Note(date, "- [ ] 14:30 dentist", 0), true))
        assertEquals("# Agenda\n- [ ] 14:30 dentist moved\n", note().readText())
        assertEquals(
            Outcome.NOT_FOUND,
            TaskActions.setDone(vault(), TaskRef.Note(date.plusDays(1), "- [ ] 14:30 dentist", 0), true)
        )
    }

    @Test
    fun `cron done creates the history file, is idempotent, and can be undone`() {
        val ref = TaskRef.Cron(at, "Daily", "take meds")
        assertFalse(history().exists())

        assertEquals(Outcome.OK, TaskActions.setDone(vault(), ref, true))
        assertEquals(Outcome.OK, TaskActions.setDone(vault(), ref, true))
        assertEquals("- 2026-11-23 09:00 done    [Daily] take meds\n", history().readText())

        assertEquals(Outcome.OK, TaskActions.setDone(vault(), ref, false))
        assertEquals("", history().readText())
    }

    @Test
    fun `dismiss logs cron as skipped and leaves notes alone`() {
        assertEquals(Outcome.OK, TaskActions.skip(vault(), TaskRef.Cron(at, null, "plan tomorrow")))
        assertEquals("- 2026-11-23 09:00 skipped plan tomorrow\n", history().readText())

        writeNote("# Agenda\n- [ ] 9am x\n")
        assertEquals(Outcome.OK, TaskActions.skip(vault(), TaskRef.Note(date, "- [ ] 9am x", 0)))
        assertEquals("# Agenda\n- [ ] 9am x\n", note().readText())
    }

    @Test
    fun `history appends keep existing lines`() {
        history().apply { parentFile!!.mkdirs(); writeText("- 2026-11-22 09:00 done    [Daily] take meds") }
        TaskActions.setDone(vault(), TaskRef.Cron(at, "Daily", "take meds"), true)
        assertEquals(
            "- 2026-11-22 09:00 done    [Daily] take meds\n- 2026-11-23 09:00 done    [Daily] take meds\n",
            history().readText()
        )
    }
}
