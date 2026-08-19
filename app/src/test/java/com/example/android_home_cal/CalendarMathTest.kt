package com.example.android_home_cal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarMathTest {

    @Test
    fun `month starting Monday has no leading blanks`() {
        // September 2025 starts on a Monday.
        val month = YearMonth.of(2025, 9)
        assertEquals(0, CalendarMath.mondayBasedOffset(month))
        val cells = CalendarMath.gridCells(month)
        assertEquals(month.atDay(1), cells[0])
    }

    @Test
    fun `month starting Sunday has six leading blanks`() {
        // June 2025 starts on a Sunday, which is the last Monday-first column.
        val month = YearMonth.of(2025, 6)
        assertEquals(6, CalendarMath.mondayBasedOffset(month))
        val cells = CalendarMath.gridCells(month)
        (0 until 6).forEach { assertNull(cells[it]) }
        assertEquals(month.atDay(1), cells[6])
    }

    @Test
    fun `28-day February lays out correctly`() {
        val month = YearMonth.of(2026, 2)
        val cells = CalendarMath.gridCells(month)
        assertEquals(28, cells.filterNotNull().size)
        assertEquals(LocalDate.of(2026, 2, 28), cells.filterNotNull().last())
    }

    @Test
    fun `29-day leap year February lays out correctly`() {
        val month = YearMonth.of(2028, 2)
        assertEquals(29, month.lengthOfMonth())
        val cells = CalendarMath.gridCells(month)
        assertEquals(29, cells.filterNotNull().size)
        assertEquals(LocalDate.of(2028, 2, 29), cells.filterNotNull().last())
    }

    @Test
    fun `30-day month lays out correctly`() {
        val month = YearMonth.of(2026, 4)
        val cells = CalendarMath.gridCells(month)
        assertEquals(30, cells.filterNotNull().size)
    }

    @Test
    fun `31-day month lays out correctly`() {
        val month = YearMonth.of(2026, 8)
        val cells = CalendarMath.gridCells(month)
        assertEquals(31, cells.filterNotNull().size)
        assertEquals(LocalDate.of(2026, 8, 18), cells[cells.indexOfFirst { it?.dayOfMonth == 18 }])
    }

    @Test
    fun `previous month crosses year boundary`() {
        val month = YearMonth.of(2027, 1)
        assertEquals(YearMonth.of(2026, 12), CalendarMath.previousMonth(month))
    }

    @Test
    fun `next month crosses year boundary`() {
        val month = YearMonth.of(2026, 12)
        assertEquals(YearMonth.of(2027, 1), CalendarMath.nextMonth(month))
    }
}
