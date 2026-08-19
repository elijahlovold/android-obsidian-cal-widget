package com.example.android_home_cal

import java.time.LocalDate

object AgendaPreviewExtractor {

    /** Mirrors todays-notes' own layout: <vault>/Calendar/<year>/<yyyy-MM-dd>.md */
    fun notePath(agendaVaultPath: String, date: LocalDate): String =
        "$agendaVaultPath/Calendar/${date.year}/$date.md"

    /**
     * Everything under the first top-level "# Agenda" heading, up to (not including) the next
     * top-level heading or end of file. Returns an empty string if there's no such section.
     */
    fun extractAgendaSection(noteContent: String): String {
        val lines = noteContent.lineSequence()
        val body = mutableListOf<String>()
        var inAgendaSection = false
        for (line in lines) {
            if (line.startsWith("# ") || line == "# Agenda") {
                inAgendaSection = line.trimEnd() == "# Agenda"
                continue
            }
            if (inAgendaSection) {
                body.add(line)
            }
        }
        return body.joinToString("\n").trim()
    }
}
