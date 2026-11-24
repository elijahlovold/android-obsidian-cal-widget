package com.example.android_home_cal.notes

enum class PreviewMarker { NONE, BULLET, UNCHECKED, CHECKED }

enum class PreviewKind { TEXT, HEADING, BLANK }

/**
 * One row of the widget's note list. Items are drawn flush left; [level] (from indentation)
 * only picks the marker color. [task] is set for `# Agenda` checkbox items, which render as
 * real, tickable checkboxes.
 */
data class PreviewLine(
    val kind: PreviewKind,
    val marker: PreviewMarker,
    val level: Int,
    val text: StyledText,
    val task: NoteTask? = null
)

/** The widget's two note-derived sections, ready to render. */
data class NoteView(val agenda: List<PreviewLine>, val scratchpad: List<PreviewLine>) {
    companion object {
        val EMPTY = NoteView(emptyList(), emptyList())
    }
}

/** Everything the app needs from one daily note, from a single parse. */
data class ParsedNote(val tasks: List<NoteTask>, val view: NoteView) {
    companion object {
        val EMPTY = ParsedNote(emptyList(), NoteView.EMPTY)
    }
}

/**
 * Parses a daily note in one pass: checkbox tasks in `# Agenda` (for reminders and write-back)
 * and the display rows for `# Agenda` and `# Scratchpad`. A section runs from its top-level
 * `# ` heading to the next one; `## ` sub-headings stay inside it.
 */
object NoteParser {

    private const val AGENDA = "Agenda"
    private const val SCRATCHPAD = "Scratchpad"

    private val checkbox = Regex("""^(\s*)[-*+]\s+\[([ xX])\]\s*(.*)$""")
    private val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val numbered = Regex("""^(\s*)(\d+[.)]\s+.*)$""")
    private val heading = Regex("""^#{1,6}\s+(.*)$""")

    fun parse(content: String): ParsedNote {
        val lines = content.split("\n").map { it.trimEnd('\r') }
        val tasks = mutableListOf<NoteTask>()
        val agenda = mutableListOf<Int>()
        val scratchpad = mutableListOf<Int>()
        // Occurrence = identical lines (ignoring checkbox state) earlier in the whole file.
        val seen = HashMap<String, Int>()
        var section: String? = null
        lines.forEachIndexed { index, line ->
            val normalized = NoteEditor.normalize(line)
            val occurrence = seen.getOrDefault(normalized, 0)
            seen[normalized] = occurrence + 1
            if (line.startsWith("# ")) {
                section = line.removePrefix("# ").trimEnd()
                return@forEachIndexed
            }
            when (section) {
                AGENDA -> {
                    agenda += index
                    ReminderLineParser.parse(line, index, occurrence)?.let(tasks::add)
                }
                SCRATCHPAD -> scratchpad += index
            }
        }
        val taskByLine = tasks.associateBy { it.lineIndex }
        return ParsedNote(
            tasks = tasks,
            view = NoteView(
                agenda = fromLines(agenda.map { lines[it] }) { local -> taskByLine[agenda[local]] },
                scratchpad = fromLines(scratchpad.map { lines[it] }) { null }
            )
        )
    }

    /** Display rows for a bare section body (no task linking). */
    fun lines(section: String): List<PreviewLine> = fromLines(section.lines()) { null }

    fun levelOf(indent: String): Int = (indent.length + 3 * indent.count { it == '\t' }) / 2

    private fun fromLines(raw: List<String>, taskAt: (Int) -> NoteTask?): List<PreviewLine> {
        val out = mutableListOf<PreviewLine>()
        for (i in raw.indices) {
            val line = raw[i].trimEnd()
            if (line.isBlank()) {
                // Runs of blank lines collapse to one spacer; none at the start or end.
                if (out.isNotEmpty() && out.last().kind != PreviewKind.BLANK) out += blank()
                continue
            }
            val parsed = parseLine(line)
            val task = taskAt(i)
            out += if (task == null) parsed else parsed.copy(task = task, text = InlineMarkdown.parse(task.displayText))
        }
        while (out.lastOrNull()?.kind == PreviewKind.BLANK) out.removeAt(out.size - 1)
        return out
    }

    private fun parseLine(line: String): PreviewLine {
        checkbox.matchEntire(line)?.let { m ->
            val marker = if (m.groupValues[2] == " ") PreviewMarker.UNCHECKED else PreviewMarker.CHECKED
            return item(marker, levelOf(m.groupValues[1]), m.groupValues[3])
        }
        bullet.matchEntire(line)?.let { m -> return item(PreviewMarker.BULLET, levelOf(m.groupValues[1]), m.groupValues[2]) }
        numbered.matchEntire(line)?.let { m -> return item(PreviewMarker.NONE, levelOf(m.groupValues[1]), m.groupValues[2]) }
        heading.matchEntire(line)?.let { m ->
            return PreviewLine(PreviewKind.HEADING, PreviewMarker.NONE, 0, InlineMarkdown.parse(m.groupValues[1].trim()))
        }
        return item(PreviewMarker.NONE, 0, line.trim())
    }

    private fun item(marker: PreviewMarker, level: Int, text: String) =
        PreviewLine(PreviewKind.TEXT, marker, level, InlineMarkdown.parse(text.trim()))

    private fun blank() = PreviewLine(PreviewKind.BLANK, PreviewMarker.NONE, 0, StyledText("", emptyList()))
}
