package com.example.android_home_cal.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteParserLinesTest {

    private fun summary(lines: List<PreviewLine>) = lines.map { "${it.kind}/${it.marker}/${it.level}/${it.text.text}" }

    @Test
    fun `checkboxes, bullets and levels`() {
        val lines = NoteParser.lines(
            """
            - [ ] 9am call
              - [x] sub done
                - deep bullet
            * star bullet
            1. numbered
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "TEXT/UNCHECKED/0/9am call",
                "TEXT/CHECKED/1/sub done",
                "TEXT/BULLET/2/deep bullet",
                "TEXT/BULLET/0/star bullet",
                "TEXT/NONE/0/1. numbered"
            ),
            summary(lines)
        )
    }

    @Test
    fun `blank runs collapse to one spacer, none at the ends, headings and markup`() {
        val lines = NoteParser.lines("\n\nThoughts on **walk**:\n\n\n## Ideas\n- [[note|alias]]\n\n")
        assertEquals(
            listOf("TEXT/NONE/0/Thoughts on walk:", "BLANK/NONE/0/", "HEADING/NONE/0/Ideas", "TEXT/BULLET/0/alias"),
            summary(lines)
        )
        assertEquals(1, lines[0].text.spans.size)
    }

    @Test
    fun `work sub-items are kept`() {
        assertEquals(3, NoteParser.lines("- work 8-4\n  - standup\n- gym").size)
    }

    @Test
    fun `tab indentation counts as two levels`() {
        assertEquals(2, NoteParser.lines("\t- x").single().level)
    }
}

class NoteParserTest {

    private fun agendaTexts(note: String) = NoteParser.parse(note).view.agenda.map { it.text.text }

    @Test
    fun `agenda runs from its heading to the next top-level heading`() {
        val note = "---\ntype: daily\n---\n\n# Agenda\n\n- morning agenda ish\n- work 8-4\n\n# Scratchpad\n\nThoughts on morning walk:"
        assertEquals(listOf("morning agenda ish", "work 8-4"), agendaTexts(note))
        assertEquals(listOf("Thoughts on morning walk:"), NoteParser.parse(note).view.scratchpad.map { it.text.text })
    }

    @Test
    fun `section edge cases`() {
        assertEquals(listOf("only item"), agendaTexts("# Agenda\n\n- only item\n"))
        assertEquals(emptyList<String>(), agendaTexts("# Scratchpad\n\nsome notes\n"))
        assertEquals(emptyList<String>(), agendaTexts("# Agenda\n\n# Scratchpad\n\nnotes\n"))
        assertEquals(listOf("the real item"), agendaTexts("# Agenda Details\n\n- not the real section\n\n# Agenda\n\n- the real item\n"))
        // ## sub-headings stay inside the section.
        assertEquals(listOf("a", "Later", "b"), agendaTexts("# Agenda\n- a\n## Later\n- b\n# Scratchpad\nx"))
    }

    @Test
    fun `tasks come only from the Agenda, with whole-file occurrence counts`() {
        val note = """
            # Agenda
            - [ ] 9am call
            - work 8-4
              - [x] 10:00 review
            - [x] 9am call

            # Scratchpad
            - [ ] 11:00 not a task here
        """.trimIndent()
        val tasks = NoteParser.parse(note).tasks
        assertEquals(listOf("9am call", "10:00 review", "9am call"), tasks.map { it.displayText })
        assertEquals(listOf(0, 0, 1), tasks.map { it.occurrence })
        assertEquals(listOf(1, 3, 4), tasks.map { it.lineIndex })
    }

    @Test
    fun `agenda checkboxes carry their task, bullets don't, scratchpad is display only`() {
        val note = """
            ---
            type: daily
            ---
            # Agenda

            - [ ] 14:30 -30m dentist
            - work 8-4
              - [ ] standup under work
            	- [x] tabbed done
            - gym

            # Scratchpad
            - [ ] idea
            Some text
        """.trimIndent()
        val view = NoteParser.parse(note).view
        assertEquals(
            listOf(
                "TEXT/UNCHECKED/0/14:30 dentist",
                "TEXT/BULLET/0/work 8-4",
                "TEXT/UNCHECKED/1/standup under work",
                "TEXT/CHECKED/2/tabbed done",
                "TEXT/BULLET/0/gym"
            ),
            view.agenda.map { "${it.kind}/${it.marker}/${it.level}/${it.text.text}" }
        )
        val task = view.agenda[0].task!!
        assertEquals(5, task.lineIndex)
        assertEquals("- [ ] 14:30 -30m dentist", task.rawLine)
        assertEquals(null, view.agenda[1].task)
        assertEquals(listOf("idea", "Some text"), view.scratchpad.map { it.text.text })
        assertEquals(null, view.scratchpad[0].task)
    }

    @Test
    fun `missing sections are empty`() {
        assertEquals(NoteView.EMPTY, NoteParser.parse("# Other\n- x").view)
    }
}
