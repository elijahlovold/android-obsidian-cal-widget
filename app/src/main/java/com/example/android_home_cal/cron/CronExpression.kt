package com.example.android_home_cal.cron

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CronParseException(message: String) : IllegalArgumentException(message)

/**
 * A standard 5-field cron expression (`min hour dom month dow`): `*`, numbers, `a-b`, `a,b`,
 * `*\/n`, `a-b/n`, `a/n`, month and weekday names, and 0 or 7 for Sunday. When both
 * day-of-month and day-of-week are restricted, a day matching either fires (Vixie cron).
 */
class CronExpression private constructor(
    private val minutes: BooleanArray,
    private val hours: BooleanArray,
    private val daysOfMonth: BooleanArray,
    private val months: BooleanArray,
    private val daysOfWeek: BooleanArray,
    private val domUnrestricted: Boolean,
    private val dowUnrestricted: Boolean
) {

    fun matchesDate(date: LocalDate): Boolean {
        if (!months[date.monthValue]) return false
        val dom = daysOfMonth[date.dayOfMonth]
        val dow = daysOfWeek[date.dayOfWeek.value % 7]
        return when {
            domUnrestricted || dowUnrestricted -> dom && dow
            else -> dom || dow
        }
    }

    /**
     * Occurrences in [from, until), local time. Times that don't exist in [zone] (DST
     * spring-forward gap) are skipped. Stops after [limit] results.
     */
    fun occurrences(from: LocalDateTime, until: LocalDateTime, zone: ZoneId, limit: Int = 500): List<LocalDateTime> {
        val result = mutableListOf<LocalDateTime>()
        var date = from.toLocalDate()
        val lastDate = until.toLocalDate()
        while (!date.isAfter(lastDate)) {
            if (matchesDate(date)) {
                for (h in 0..23) {
                    if (!hours[h]) continue
                    for (m in 0..59) {
                        if (!minutes[m]) continue
                        val t = date.atTime(h, m)
                        if (t.isBefore(from) || !t.isBefore(until)) continue
                        if (zone.rules.getValidOffsets(t).isEmpty()) continue
                        result += t
                        if (result.size >= limit) return result
                    }
                }
            }
            date = date.plusDays(1)
        }
        return result
    }

    companion object {
        private val monthNames = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            .withIndex().associate { it.value to it.index + 1 }
        private val dayNames = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
            .withIndex().associate { it.value to it.index }

        fun parse(fields: List<String>): CronExpression {
            if (fields.size != 5) throw CronParseException("expected 5 fields, got ${fields.size}")
            val dow = parseField(fields[4], 0, 7, dayNames, "day-of-week")
            if (dow[7]) dow[0] = true
            return CronExpression(
                minutes = parseField(fields[0], 0, 59, emptyMap(), "minute"),
                hours = parseField(fields[1], 0, 23, emptyMap(), "hour"),
                daysOfMonth = parseField(fields[2], 1, 31, emptyMap(), "day-of-month"),
                months = parseField(fields[3], 1, 12, monthNames, "month"),
                daysOfWeek = dow.copyOf(7),
                domUnrestricted = fields[2].startsWith("*"),
                dowUnrestricted = fields[4].startsWith("*")
            )
        }

        private fun parseField(text: String, min: Int, max: Int, names: Map<String, Int>, label: String): BooleanArray {
            val out = BooleanArray(max + 1)
            fun fail(why: String): Nothing = throw CronParseException("$label '$text': $why")
            fun value(token: String): Int {
                val v = token.toIntOrNull() ?: names[token.lowercase()] ?: fail("'$token' is not a number")
                if (v !in min..max) fail("$v is outside $min-$max")
                return v
            }
            for (part in text.split(',')) {
                if (part.isEmpty()) fail("empty list item")
                val pieces = part.split('/')
                if (pieces.size > 2) fail("more than one '/'")
                val range = pieces[0]
                val step = pieces.getOrNull(1)?.let { s -> s.toIntOrNull()?.takeIf { it > 0 } ?: fail("bad step '$s'") } ?: 1
                val (lo, hi) = when {
                    range == "*" -> min to max
                    '-' in range -> {
                        val ends = range.split('-')
                        if (ends.size != 2) fail("bad range '$range'")
                        value(ends[0]) to value(ends[1])
                    }
                    else -> value(range).let { v -> if (pieces.size == 2) v to max else v to v }
                }
                if (lo > hi) fail("range $lo-$hi is backwards")
                for (v in lo..hi step step) out[v] = true
            }
            return out
        }
    }
}

