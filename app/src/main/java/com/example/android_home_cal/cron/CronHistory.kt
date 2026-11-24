package com.example.android_home_cal.cron

import java.time.LocalDateTime

enum class CronOutcome(val word: String) { DONE("done"), SKIPPED("skipped") }

/** Identifies one occurrence of a cron entry; the key history lines are matched on. */
data class CronOccurrence(val at: LocalDateTime, val section: String?, val text: String)

data class CronHistoryEntry(val at: LocalDateTime, val outcome: CronOutcome, val section: String?, val text: String) {
    val occurrence: CronOccurrence get() = CronOccurrence(at, section, text)

    fun matches(at: LocalDateTime, section: String?, text: String): Boolean =
        this.at == at && this.section == section && this.text == text
}

/** Lines of `Calendar/Cal-Cron-History.md` (docs/reminders.md §3). */
object CronHistoryFormat {

    private val line = Regex(
        """^-\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s+(done|skipped)\s+(?:\[([^\]]*)]\s+)?(.*?)\s*$"""
    )

    fun format(entry: CronHistoryEntry): String {
        val section = entry.section?.let { "[$it] " }.orEmpty()
        return "- ${entry.at.toLocalDate()} ${entry.at.toLocalTime()} ${entry.outcome.word.padEnd(7)} $section${entry.text}"
    }

    fun parseLine(text: String): CronHistoryEntry? {
        val m = line.matchEntire(text.trimEnd('\r')) ?: return null
        return try {
            CronHistoryEntry(
                at = LocalDateTime.parse("${m.groupValues[1]}T${m.groupValues[2]}"),
                outcome = if (m.groupValues[3] == "done") CronOutcome.DONE else CronOutcome.SKIPPED,
                section = m.groups[4]?.value,
                text = m.groupValues[5]
            )
        } catch (e: java.time.format.DateTimeParseException) {
            null
        }
    }

    fun parse(content: String): List<CronHistoryEntry> = content.lines().mapNotNull(::parseLine)

    fun append(content: String, entry: CronHistoryEntry): String {
        val prefix = if (content.isEmpty() || content.endsWith("\n")) content else "$content\n"
        return prefix + format(entry) + "\n"
    }

    /** Removes the last `done` line for that occurrence, or returns null if there is none. */
    fun removeDone(content: String, at: LocalDateTime, section: String?, text: String): String? {
        val lines = content.split("\n").toMutableList()
        val index = lines.indexOfLast { l ->
            parseLine(l)?.let { it.outcome == CronOutcome.DONE && it.matches(at, section, text) } == true
        }
        if (index < 0) return null
        lines.removeAt(index)
        return lines.joinToString("\n")
    }
}
