package com.example.android_home_cal.vault

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun vault() = VaultFiles(tmp.root.path)

    private fun file(rel: String, text: String = "") =
        File(tmp.root, rel).apply { parentFile!!.mkdirs(); writeText(text) }

    @Test
    fun `future note dates come from file names in year folders`() {
        listOf(
            "Calendar/2025/2025-12-31.md",      // past year
            "Calendar/2026/2026-11-20.md",      // past day
            "Calendar/2026/2026-11-23.md",      // today
            "Calendar/2026/2026-12-01.md",
            "Calendar/2026/.2026-11-24.md.tmp", // write-back temp file
            "Calendar/2026/notes.md",
            "Calendar/2027/2027-01-05.md",
            "Calendar/2027/2026-12-30.md",      // in the wrong year folder
            "Calendar/Cal-Cron.md",
            "Calendar/templates/2026-12-02.md"  // not a year folder
        ).forEach { file(it) }
        assertEquals(
            listOf(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 5)),
            vault().noteDatesFrom(LocalDate.of(2026, 11, 23))
        )
    }

    @Test
    fun `no Calendar folder means no notes`() {
        assertTrue(vault().noteDatesFrom(LocalDate.of(2026, 11, 23)).isEmpty())
    }

    @Test
    fun `upcoming tasks are open timed tasks, soonest first`() {
        file("Calendar/2026/2026-11-25.md", "# Agenda\n- [ ] 15:00 later\n- [ ] 9am earlier\n- [x] 10:00 done\n- [ ] untimed\n")
        file("Calendar/2026/2026-11-24.md", "# Agenda\n- [ ] 20:00 tomorrow\n")
        file("Calendar/2026/2026-11-22.md", "# Agenda\n- [ ] 9am yesterday\n")
        val upcoming = vault().upcomingTasks(LocalDate.of(2026, 11, 23))
        assertEquals(listOf("20:00 tomorrow", "9am earlier", "15:00 later"), upcoming.map { it.second.displayText })
        assertEquals(LocalTime.of(9, 0), upcoming[1].second.time)
    }

    @Test
    fun `dots go on future days with at least one open checkbox`() {
        file("Calendar/2026/2026-11-22.md", "# Agenda\n- [ ] past, never scanned\n")
        file("Calendar/2026/2026-11-23.md", "# Agenda\n- [ ] untimed counts too\n")
        file("Calendar/2026/2026-11-24.md", "# Agenda\n- [x] 9am all done\n")
        file("Calendar/2026/2026-11-25.md", "# Agenda\n- just a bullet\n# Scratchpad\n- [ ] not agenda\n")
        file("Calendar/2026/2026-12-01.md", "# Agenda\n- [x] done\n  - [ ] 14:00 open sub-item\n")
        assertEquals(
            setOf(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 12, 1)),
            vault().datesWithOpenTasks(LocalDate.of(2026, 11, 23))
        )
    }

    @Test
    fun `parse cache notices external edits and our own writes`() {
        val date = LocalDate.of(2026, 11, 23)
        val note = file("Calendar/2026/2026-11-23.md", "# Agenda\n- [ ] 9am a\n")
        val v = vault()
        assertEquals(listOf("9am a"), v.tasks(date).map { it.displayText })

        // External edit (different length) is picked up without invalidation.
        note.writeText("# Agenda\n- [ ] 9am a\n- [ ] 10:00 b\n")
        assertEquals(2, v.tasks(date).size)

        // Our own atomic write invalidates explicitly and leaves no temp file behind.
        assertTrue(VaultFiles.writeAtomic(note, "# Agenda\n- [x] 9am a\n"))
        assertEquals(listOf(true), v.tasks(date).map { it.checked })
        assertEquals(listOf("2026-11-23.md"), note.parentFile!!.list()!!.toList())
    }
}
