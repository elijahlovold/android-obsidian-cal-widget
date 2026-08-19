package com.example.android_home_cal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AgendaPreviewExtractorTest {

    @Test
    fun `note path mirrors todays-notes' own layout`() {
        assertEquals(
            "/vault/Calendar/2026/2026-08-18.md",
            AgendaPreviewExtractor.notePath("/vault", LocalDate.of(2026, 8, 18))
        )
    }

    @Test
    fun `extracts content between the Agenda heading and the next top-level heading`() {
        val note = """
            ---
            type: daily
            ---

            # Agenda

            - morning agenda ish
            - work 8-4

            # Scratchpad

            Thoughts on morning walk:
        """.trimIndent()

        assertEquals(
            "- morning agenda ish\n- work 8-4",
            AgendaPreviewExtractor.extractAgendaSection(note)
        )
    }

    @Test
    fun `extracts to end of file when Agenda is the last section`() {
        val note = "# Agenda\n\n- only item\n"
        assertEquals("- only item", AgendaPreviewExtractor.extractAgendaSection(note))
    }

    @Test
    fun `returns empty string when there is no Agenda heading`() {
        val note = "# Scratchpad\n\nsome notes\n"
        assertEquals("", AgendaPreviewExtractor.extractAgendaSection(note))
    }

    @Test
    fun `returns empty string for an empty Agenda section`() {
        val note = "# Agenda\n\n# Scratchpad\n\nnotes\n"
        assertEquals("", AgendaPreviewExtractor.extractAgendaSection(note))
    }

    @Test
    fun `a sub-heading like Agenda Details does not match the top-level heading`() {
        val note = "# Agenda Details\n\n- not the real section\n\n# Agenda\n\n- the real item\n"
        assertEquals("- the real item", AgendaPreviewExtractor.extractAgendaSection(note))
    }
}
