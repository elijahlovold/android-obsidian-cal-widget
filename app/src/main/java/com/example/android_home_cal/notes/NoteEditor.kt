package com.example.android_home_cal.notes

/** Flips a checkbox in note content, locating the line by content rather than line number. */
object NoteEditor {

    sealed class Result {
        data class Updated(val content: String) : Result()
        object Unchanged : Result()
        object NotFound : Result()
    }

    private val checkbox = Regex("""^(\s*[-*+]\s+\[)([ xX])(\])""")

    /** [line] with its checkbox state blanked, so checked and unchecked copies compare equal. */
    fun normalize(line: String): String =
        checkbox.replace(line.trimEnd('\r')) { "${it.groupValues[1]} ${it.groupValues[3]}" }

    /**
     * Sets the [occurrence]-th line matching [rawLine] (ignoring checkbox state) to [checked].
     * Keeps CRLF line endings and the trailing newline as they were.
     */
    fun setChecked(content: String, rawLine: String, occurrence: Int, checked: Boolean): Result {
        val target = normalize(rawLine)
        val lines = content.split("\n").toMutableList()
        var seen = 0
        for (i in lines.indices) {
            val line = lines[i]
            val bare = line.trimEnd('\r')
            if (normalize(bare) != target) continue
            if (seen++ != occurrence) continue
            val m = checkbox.find(bare) ?: return Result.NotFound
            val isChecked = m.groupValues[2] != " "
            if (isChecked == checked) return Result.Unchanged
            val updated = bare.replaceRange(m.groups[2]!!.range, if (checked) "x" else " ")
            lines[i] = updated + line.substring(bare.length)
            return Result.Updated(lines.joinToString("\n"))
        }
        return Result.NotFound
    }
}
