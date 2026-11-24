package com.example.android_home_cal.cron

data class CronEntry(val lineNumber: Int, val section: String?, val expression: CronExpression, val text: String)

data class CronFileError(val lineNumber: Int, val line: String, val message: String)

data class CronFile(val entries: List<CronEntry>, val errors: List<CronFileError>) {
    companion object {
        val EMPTY = CronFile(emptyList(), emptyList())
    }
}

/** Parses `Calendar/Cal-Cron.md` (docs/reminders.md §2). */
object CronFileParser {

    private val heading = Regex("""^#{1,6}\s+(.*)$""")
    private val bullet = Regex("""^-\s+""")
    private val whitespace = Regex("\\s+")

    fun parse(content: String): CronFile {
        val entries = mutableListOf<CronEntry>()
        val errors = mutableListOf<CronFileError>()
        var section: String? = null
        content.lines().forEachIndexed { index, raw ->
            val lineNumber = index + 1
            val line = raw.trim()
            val h = heading.matchEntire(line)
            if (h != null) {
                section = h.groupValues[1].trim().trimEnd('#').trim().ifEmpty { null }
                return@forEachIndexed
            }
            val body = line.replaceFirst(bullet, "")
            if (body.isEmpty() || !(body[0].isDigit() || body[0] == '*')) return@forEachIndexed
            val parts = body.split(whitespace, limit = 6)
            if (parts.size < 6 || parts[5].isBlank()) {
                errors += CronFileError(lineNumber, line, "expected 5 cron fields followed by text")
                return@forEachIndexed
            }
            try {
                entries += CronEntry(lineNumber, section, CronExpression.parse(parts.take(5)), parts[5].trim())
            } catch (e: CronParseException) {
                errors += CronFileError(lineNumber, line, e.message ?: "invalid")
            }
        }
        return CronFile(entries, errors)
    }
}

