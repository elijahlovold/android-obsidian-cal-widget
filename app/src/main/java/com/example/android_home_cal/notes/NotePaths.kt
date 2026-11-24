package com.example.android_home_cal.notes

import java.time.LocalDate

/** Daily-note layout, mirroring todays-notes: `<vault>/Calendar/<year>/<yyyy-MM-dd>.md`. */
object NotePaths {

    private val noteFileName = Regex("""^\d{4}-\d{2}-\d{2}\.md$""")

    fun notePath(vaultRoot: String, date: LocalDate): String = "$vaultRoot/Calendar/${date.year}/$date.md"

    /** The date a daily note's file name encodes (`2026-11-23.md`), or null if it isn't one. */
    fun noteDateFromFileName(name: String): LocalDate? =
        if (noteFileName.matches(name)) runCatching { LocalDate.parse(name.removeSuffix(".md")) }.getOrNull() else null
}
