package com.example.android_home_cal.notes

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderLineParserTest {

    private val date = LocalDate.of(2026, 11, 23)
    private fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, day, h, m)
    private fun fires(line: String) = ReminderLineParser.parse(line)!!.fireTimes(date)

    @Test
    fun `parses 24h and 12h times, rejects bare numbers`() {
        assertEquals(LocalTime.of(14, 30), ReminderLineParser.parseTime("14:30"))
        assertEquals(LocalTime.of(9, 5), ReminderLineParser.parseTime("9:05"))
        assertEquals(LocalTime.of(14, 0), ReminderLineParser.parseTime("2pm"))
        assertEquals(LocalTime.of(14, 30), ReminderLineParser.parseTime("2:30PM"))
        assertEquals(LocalTime.of(0, 0), ReminderLineParser.parseTime("12am"))
        assertEquals(LocalTime.of(12, 0), ReminderLineParser.parseTime("12pm"))
        assertNull(ReminderLineParser.parseTime("2026"))
        assertNull(ReminderLineParser.parseTime("14"))
        assertNull(ReminderLineParser.parseTime("25:00"))
        assertNull(ReminderLineParser.parseTime("13pm"))
        assertNull(ReminderLineParser.parseTime("9:5"))
    }

    @Test
    fun `plain time reminds at the time`() {
        assertEquals(listOf(at(23, 14, 30)), fires("- [ ] 14:30 dentist"))
    }

    @Test
    fun `minute and hour leads add earlier reminders`() {
        assertEquals(listOf(at(23, 14, 0), at(23, 14, 30)), fires("- [ ] 14:30 -30m dentist"))
        assertEquals(listOf(at(23, 8, 0), at(23, 8, 50), at(23, 9, 0)), fires("- [ ] 9am -1h -10m flight"))
    }

    @Test
    fun `day leads, with and without a clock time`() {
        assertEquals(listOf(at(22, 14, 30), at(23, 14, 30)), fires("- [ ] 14:30 -1d dentist"))
        assertEquals(
            listOf(at(22, 20, 0), at(23, 8, 0), at(23, 14, 15), at(23, 14, 30)),
            fires("- [ ] 14:30 -1d8pm -0d8am -15m dentist")
        )
    }

    @Test
    fun `display text keeps the time and drops lead tokens`() {
        assertEquals("14:30 dentist appt", ReminderLineParser.parse("- [ ] 14:30 -1d8pm -30m dentist appt")!!.displayText)
    }

    @Test
    fun `untimed, plain bullets and checked items`() {
        val untimed = ReminderLineParser.parse("- [ ] buy milk")!!
        assertNull(untimed.time)
        assertTrue(untimed.fireTimes(date).isEmpty())
        assertNull(ReminderLineParser.parse("- 10:00 standup"))
        val checked = ReminderLineParser.parse("- [x] 14:30 dentist")!!
        assertTrue(checked.checked)
    }

    @Test
    fun `long day leads are allowed, since every future note is scanned`() {
        val task = ReminderLineParser.parse("- [ ] 14:30 -14d9am -30m passport")!!
        assertEquals(listOf(ReminderLead.Days(14, LocalTime.of(9, 0)), ReminderLead.Minutes(30)), task.leads)
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 0), task.fireTimes(date).first())
    }

    @Test
    fun `a malformed lead ends the lead list and becomes text`() {
        val task = ReminderLineParser.parse("- [ ] 14:30 -30x dentist")!!
        assertTrue(task.leads.isEmpty())
        assertEquals("14:30 -30x dentist", task.displayText)
    }

    @Test
    fun `negative-looking text after the time is not a lead unless it parses`() {
        val task = ReminderLineParser.parse("- [ ] 7pm -2d9am -1d9am dinner w/ Sam")!!
        assertEquals(3, task.fireTimes(date).size)
        assertEquals("7pm dinner w/ Sam", task.displayText)
    }

    @Test
    fun `indentation, alternate bullets, CRLF`() {
        val task = ReminderLineParser.parse("    * [ ] 9am pack\r")!!
        assertEquals(2, task.indentLevel)
        assertEquals("    * [ ] 9am pack", task.rawLine)
    }
}

class NoteEditorTest {

    @Test
    fun `checks the matching line and preserves everything else`() {
        val note = "# Agenda\r\n- [ ] 9am call\r\n- [ ] lunch\r\n"
        val result = NoteEditor.setChecked(note, "- [ ] 9am call", 0, true)
        assertEquals(NoteEditor.Result.Updated("# Agenda\r\n- [x] 9am call\r\n- [ ] lunch\r\n"), result)
    }

    @Test
    fun `targets the right duplicate`() {
        val note = "- [ ] call\n- [ ] call\n"
        assertEquals(
            NoteEditor.Result.Updated("- [ ] call\n- [x] call\n"),
            NoteEditor.setChecked(note, "- [ ] call", 1, true)
        )
    }

    @Test
    fun `matches regardless of current checkbox state`() {
        assertEquals(NoteEditor.Result.Unchanged, NoteEditor.setChecked("- [x] call\n", "- [ ] call", 0, true))
        assertEquals(
            NoteEditor.Result.Updated("- [ ] call\n"),
            NoteEditor.setChecked("- [X] call\n", "- [x] call", 0, false)
        )
    }

    @Test
    fun `reports a missing line`() {
        assertEquals(NoteEditor.Result.NotFound, NoteEditor.setChecked("- [ ] call mom\n", "- [ ] call", 0, true))
        assertEquals(NoteEditor.Result.NotFound, NoteEditor.setChecked("- [ ] call\n", "- [ ] call", 1, true))
    }

    @Test
    fun `no trailing newline stays that way`() {
        assertEquals(NoteEditor.Result.Updated("a\n- [x] b"), NoteEditor.setChecked("a\n- [ ] b", "- [ ] b", 0, true))
    }

    @Test
    fun `normalize ignores checkbox state only`() {
        assertEquals(NoteEditor.normalize("- [x] a"), NoteEditor.normalize("- [ ] a"))
        assertFalse(NoteEditor.normalize("- [ ] a") == NoteEditor.normalize("- [ ] b"))
    }
}
