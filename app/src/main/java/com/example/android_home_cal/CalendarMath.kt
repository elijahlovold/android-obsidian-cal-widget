package com.example.android_home_cal

import java.time.LocalDate
import java.time.YearMonth

const val CALENDAR_GRID_SIZE = 42

object CalendarMath {

    fun previousMonth(month: YearMonth): YearMonth = month.minusMonths(1)

    fun nextMonth(month: YearMonth): YearMonth = month.plusMonths(1)

    fun sundayBasedOffset(month: YearMonth): Int {
        val firstDayOfWeek = month.atDay(1).dayOfWeek
        return firstDayOfWeek.value % 7
    }

    /**
     * 42-cell Sunday-first grid. Cells outside [month] are null.
     */
    fun gridCells(month: YearMonth): List<LocalDate?> {
        val offset = sundayBasedOffset(month)
        val daysInMonth = month.lengthOfMonth()
        return (0 until CALENDAR_GRID_SIZE).map { index ->
            val dayOfMonth = index - offset + 1
            if (dayOfMonth in 1..daysInMonth) month.atDay(dayOfMonth) else null
        }
    }
}
