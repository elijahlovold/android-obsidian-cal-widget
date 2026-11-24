package com.example.android_home_cal.notes

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotePathsTest {

    @Test
    fun `note path mirrors todays-notes' own layout`() {
        assertEquals("/vault/Calendar/2026/2026-08-18.md", NotePaths.notePath("/vault", LocalDate.of(2026, 8, 18)))
    }

    @Test
    fun `parses daily note file names only`() {
        assertEquals(LocalDate.of(2026, 11, 23), NotePaths.noteDateFromFileName("2026-11-23.md"))
        assertNull(NotePaths.noteDateFromFileName("2026-11-23.md.tmp"))
        assertNull(NotePaths.noteDateFromFileName(".2026-11-23.md.tmp"))
        assertNull(NotePaths.noteDateFromFileName("2026-13-40.md"))
        assertNull(NotePaths.noteDateFromFileName("Cal-Cron.md"))
    }
}
