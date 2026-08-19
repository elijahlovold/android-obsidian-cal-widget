package com.example.android_home_cal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

const val CALENDAR_GRID_SIZE = 42

object CalendarMath {

    fun previousMonth(month: YearMonth): YearMonth = month.minusMonths(1)

    fun nextMonth(month: YearMonth): YearMonth = month.plusMonths(1)

    fun mondayBasedOffset(month: YearMonth): Int {
        val firstDayOfWeek = month.atDay(1).dayOfWeek
        return firstDayOfWeek.value - DayOfWeek.MONDAY.value
    }

    /**
     * 42-cell Monday-first grid. Cells outside [month] are null.
     */
    fun gridCells(month: YearMonth): List<LocalDate?> {
        val offset = mondayBasedOffset(month)
        val daysInMonth = month.lengthOfMonth()
        return (0 until CALENDAR_GRID_SIZE).map { index ->
            val dayOfMonth = index - offset + 1
            if (dayOfMonth in 1..daysInMonth) month.atDay(dayOfMonth) else null
        }
    }
}
