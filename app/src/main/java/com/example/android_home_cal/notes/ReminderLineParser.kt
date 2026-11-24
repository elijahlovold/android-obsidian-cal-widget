package com.example.android_home_cal.notes

import java.time.LocalTime

/**
 * Parses the daily-note reminder format (docs/reminders.md §1):
 *
 *     - [ ] <time> [<lead> ...] <text>
 */
object ReminderLineParser {

    private val checkboxLine = Regex("""^(\s*)[-*+]\s+\[([ xX])\]\s*(.*)$""")
    private val timeToken = Regex("""^(\d{1,2})(?::([0-5]\d))?(am|pm)?$""", RegexOption.IGNORE_CASE)
    private val leadToken = Regex("""^-(\d{1,3})([mhd])(.*)$""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("\\s+")

    /** `14:30`, `9:05`, `2pm`, `2:30pm`. A bare number is not a time. */
    fun parseTime(token: String): LocalTime? {
        val m = timeToken.matchEntire(token) ?: return null
        val minuteText = m.groups[2]?.value
        val meridiem = m.groups[3]?.value?.lowercase()
        if (minuteText == null && meridiem == null) return null
        var hour = m.groupValues[1].toInt()
        val minute = minuteText?.toInt() ?: 0
        if (meridiem != null) {
            if (hour !in 1..12) return null
            hour = hour % 12 + if (meridiem == "pm") 12 else 0
        } else if (hour !in 0..23) {
            return null
        }
        return LocalTime.of(hour, minute)
    }

    /** Null if [line] isn't a checkbox item. */
    fun parse(line: String, lineIndex: Int = 0, occurrence: Int = 0): NoteTask? {
        val bare = line.trimEnd('\r')
        val m = checkboxLine.matchEntire(bare) ?: return null
        val checked = m.groupValues[2] != " "
        val body = m.groupValues[3].trim()
        val indentLevel = (indentWidth(m.groupValues[1]) / 2).coerceAtMost(3)
        val tokens = body.split(whitespace).filter { it.isNotEmpty() }
        val time = tokens.firstOrNull()?.let(::parseTime)

        if (time == null) {
            return NoteTask(lineIndex, bare, occurrence, checked, indentLevel, body, null, emptyList())
        }

        val leads = mutableListOf<ReminderLead>()
        val warnings = mutableListOf<String>()
        var i = 1
        while (i < tokens.size) {
            val lm = leadToken.matchEntire(tokens[i]) ?: break
            val n = lm.groupValues[1].toLong()
            val rest = lm.groupValues[3]
            val lead: ReminderLead? = when (lm.groupValues[2].lowercase()) {
                "m" -> if (rest.isEmpty()) ReminderLead.Minutes(n) else null
                "h" -> if (rest.isEmpty()) ReminderLead.Hours(n) else null
                else -> if (rest.isEmpty()) ReminderLead.Days(n, null) else parseTime(rest)?.let { ReminderLead.Days(n, it) }
            }
            if (lead == null) {
                // Not a lead after all - it starts the task text.
                warnings += "unrecognized reminder lead '${tokens[i]}'"
                break
            }
            leads += lead
            i++
        }

        val displayText = (listOf(tokens[0]) + tokens.drop(i)).joinToString(" ")
        return NoteTask(lineIndex, bare, occurrence, checked, indentLevel, displayText, time, leads, warnings)
    }

    private fun indentWidth(ws: String): Int = ws.length + 3 * ws.count { it == '\t' }
}

