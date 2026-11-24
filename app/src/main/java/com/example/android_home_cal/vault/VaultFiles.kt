package com.example.android_home_cal.vault

import android.os.Environment
import android.util.Log
import com.example.android_home_cal.cron.CronFile
import com.example.android_home_cal.cron.CronFileParser
import com.example.android_home_cal.cron.CronHistoryEntry
import com.example.android_home_cal.cron.CronHistoryFormat
import com.example.android_home_cal.notes.NoteParser
import com.example.android_home_cal.notes.NotePaths
import com.example.android_home_cal.notes.NoteTask
import com.example.android_home_cal.notes.NoteView
import com.example.android_home_cal.notes.ParsedNote
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** The vault files the app reads and writes on shared storage, parsed through [FileParseCache]. */
class VaultFiles(val root: String) {

    fun note(date: LocalDate) = File(NotePaths.notePath(root, date))
    val cron get() = File(root, CRON_PATH)
    val history get() = File(root, HISTORY_PATH)

    /**
     * Dates of every daily note on or after [start], from directory listings alone. Future
     * notes only exist when something was planned for that day, so this stays small.
     */
    fun noteDatesFrom(start: LocalDate): List<LocalDate> {
        val calendar = File(root, "Calendar")
        val years = calendar.list()?.mapNotNull { name ->
            name.takeIf { it.length == 4 }?.toIntOrNull()?.takeIf { it >= start.year }
        } ?: return emptyList()
        return years.flatMap { year ->
            File(calendar, year.toString()).list().orEmpty().mapNotNull { name ->
                NotePaths.noteDateFromFileName(name)?.takeIf { it.year == year && !it.isBefore(start) }
            }
        }.sorted()
    }

    fun parsedNote(date: LocalDate): ParsedNote =
        FileParseCache.get("note", note(date), NoteParser::parse) ?: ParsedNote.EMPTY

    fun tasks(date: LocalDate): List<NoteTask> = parsedNote(date).tasks

    fun noteView(date: LocalDate): NoteView = parsedNote(date).view

    /** Open (unchecked) timed tasks in notes from [start] on, soonest first. */
    fun upcomingTasks(start: LocalDate): List<Pair<LocalDate, NoteTask>> =
        noteDatesFrom(start)
            .flatMap { date -> tasks(date).filter { it.time != null && !it.checked }.map { date to it } }
            .sortedWith(compareBy({ it.first }, { it.second.time }))

    /** Days from [start] on whose note has at least one unchecked checkbox in `# Agenda`. */
    fun datesWithOpenTasks(start: LocalDate): Set<LocalDate> =
        noteDatesFrom(start).filterTo(HashSet()) { date -> tasks(date).any { !it.checked } }

    fun cronFile(): CronFile = FileParseCache.get("cron", cron, CronFileParser::parse) ?: CronFile.EMPTY

    fun cronHistory(): List<CronHistoryEntry> =
        FileParseCache.get("history", history, CronHistoryFormat::parse) ?: emptyList()

    companion object {
        private const val TAG = "VaultFiles"
        const val CRON_PATH = "Calendar/Cal-Cron.md"
        const val HISTORY_PATH = "Calendar/Cal-Cron-History.md"

        fun hasStorageAccess(): Boolean = Environment.isExternalStorageManager()

        fun readText(file: File): String? = try {
            if (file.isFile) file.readText() else null
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read ${file.path}", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied reading ${file.path}", e)
            null
        }

        /** Temp file + rename, so a sync tool or nvim never sees a half-written file. */
        fun writeAtomic(file: File, text: String): Boolean = try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, ".${file.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
            FileParseCache.invalidate(file)
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write ${file.path}", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied writing ${file.path}", e)
            false
        }
    }
}
