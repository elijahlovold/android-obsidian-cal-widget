package com.example.android_home_cal.cron

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CronExpressionTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private fun expr(s: String) = CronExpression.parse(s.split(" "))
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    @Test
    fun `weekday ranges and names`() {
        val e = expr("0 9 * * mon-fri")
        assertTrue(e.matchesDate(day(2026, 11, 23))) // Monday
        assertFalse(e.matchesDate(day(2026, 11, 22))) // Sunday
        assertTrue(expr("0 9 * * 7").matchesDate(day(2026, 11, 22)))
        assertTrue(expr("0 9 * * 0").matchesDate(day(2026, 11, 22)))
    }

    @Test
    fun `day-of-month and day-of-week restricted together match either`() {
        val e = expr("0 9 1 * mon")
        assertTrue(e.matchesDate(day(2026, 11, 23))) // a Monday
        assertTrue(e.matchesDate(day(2026, 12, 1))) // the 1st, a Tuesday
        assertFalse(e.matchesDate(day(2026, 12, 2)))
    }

    @Test
    fun `star-step on one day field keeps AND semantics`() {
        val e = expr("0 9 */2 * mon")
        assertFalse(e.matchesDate(day(2026, 11, 24))) // Tue the 24th: dom no, dow no
        assertFalse(e.matchesDate(day(2026, 11, 25))) // Wed the 25th: dom yes, dow no
        assertTrue(e.matchesDate(day(2026, 11, 23))) // Mon the 23rd: dom yes, dow yes
    }

    @Test
    fun `steps, lists, month names, end of month`() {
        val e = expr("*/15 8-9 * * *")
        val occ = e.occurrences(LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 2, 0, 0), utc)
        assertEquals(8, occ.size)
        assertEquals(LocalDateTime.of(2026, 1, 1, 9, 45), occ.last())

        val birthday = expr("0 9 14 mar *")
        assertTrue(birthday.matchesDate(day(2027, 3, 14)))
        assertFalse(birthday.matchesDate(day(2027, 4, 14)))

        val thirtyFirst = expr("0 9 31 * *")
        assertFalse(thirtyFirst.matchesDate(day(2026, 11, 30)))
        assertTrue(thirtyFirst.matchesDate(day(2026, 12, 31)))

        assertTrue(expr("0 9 5/10 * *").matchesDate(day(2026, 1, 25)))
        assertTrue(expr("0,30 9 1,15 * *").matchesDate(day(2026, 1, 15)))
    }

    @Test
    fun `occurrences are half-open`() {
        val e = expr("0 9 * * *")
        val occ = e.occurrences(LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 3, 9, 0), utc)
        assertEquals(listOf(LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 2, 9, 0)), occ)
    }

    @Test
    fun `skips times in a DST gap, fires once in an overlap`() {
        val ny = ZoneId.of("America/New_York")
        val gap = expr("30 2 * * *").occurrences(
            LocalDateTime.of(2026, 3, 8, 0, 0), LocalDateTime.of(2026, 3, 9, 0, 0), ny
        )
        assertTrue(gap.isEmpty())
        val overlap = expr("30 1 * * *").occurrences(
            LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 2, 0, 0), ny
        )
        assertEquals(1, overlap.size)
    }

    @Test(expected = CronParseException::class)
    fun `rejects out of range`() { expr("60 9 * * *") }

    @Test(expected = CronParseException::class)
    fun `rejects backwards range`() { expr("0 9 * * fri-mon") }

    @Test(expected = CronParseException::class)
    fun `rejects garbage`() { expr("0 9 * * someday") }
}

class CronFileParserTest {

    @Test
    fun `sections, optional bullets, prose ignored, errors reported`() {
        val file = CronFileParser.parse(
            """
            Notes about this file.

            # Daily
            - 0 9 * * 1-5 take meds
            30 21 * * * plan tomorrow

            ## Birthdays ##
            0 9 14 3 * Mom's birthday
            2026 plans are below
            0 9 * * *
            * * * * * every minute
            """.trimIndent()
        )
        assertEquals(
            listOf("Daily" to "take meds", "Daily" to "plan tomorrow", "Birthdays" to "Mom's birthday", "Birthdays" to "every minute"),
            file.entries.map { it.section to it.text }
        )
        assertEquals(listOf(9, 10), file.errors.map { it.lineNumber })
        assertEquals(4, file.entries[0].lineNumber)
    }

    @Test
    fun `entries before any heading have no section`() {
        assertNull(CronFileParser.parse("0 9 * * * x").entries.single().section)
    }
}

class CronHistoryFormatTest {

    private val at = LocalDateTime.of(2026, 11, 23, 9, 0)

    @Test
    fun `format and parse round trip`() {
        val entry = CronHistoryEntry(at, CronOutcome.DONE, "Daily", "take meds")
        val line = CronHistoryFormat.format(entry)
        assertEquals("- 2026-11-23 09:00 done    [Daily] take meds", line)
        assertEquals(entry, CronHistoryFormat.parseLine(line))

        val noSection = CronHistoryEntry(at, CronOutcome.SKIPPED, null, "plan")
        assertEquals(noSection, CronHistoryFormat.parseLine(CronHistoryFormat.format(noSection)))
    }

    @Test
    fun `append adds a missing newline first`() {
        val entry = CronHistoryEntry(at, CronOutcome.DONE, null, "x")
        assertEquals("a\n- 2026-11-23 09:00 done    x\n", CronHistoryFormat.append("a", entry))
        assertEquals("- 2026-11-23 09:00 done    x\n", CronHistoryFormat.append("", entry))
    }

    @Test
    fun `removeDone removes only the matching done line`() {
        val content = "- 2026-11-23 09:00 skipped [D] x\n- 2026-11-23 09:00 done    [D] x\n- 2026-11-24 09:00 done    [D] x\n"
        assertEquals(
            "- 2026-11-23 09:00 skipped [D] x\n- 2026-11-24 09:00 done    [D] x\n",
            CronHistoryFormat.removeDone(content, at, "D", "x")
        )
        assertNull(CronHistoryFormat.removeDone(content, at, "Other", "x"))
    }
}
