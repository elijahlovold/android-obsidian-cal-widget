package com.example.android_home_cal.reminders

import android.util.Log
import com.example.android_home_cal.cron.CronHistoryEntry
import com.example.android_home_cal.cron.CronHistoryFormat
import com.example.android_home_cal.cron.CronOutcome
import com.example.android_home_cal.notes.NoteEditor
import com.example.android_home_cal.vault.VaultFiles

/**
 * Write-back shared by notification actions and widget checkboxes (docs/reminders.md §5-6).
 * Doesn't redraw anything: the rescan that callers run afterwards sees the changed file and
 * redraws the widgets.
 */
object TaskActions {

    private const val TAG = "TaskActions"

    enum class Outcome { OK, NOT_FOUND, FAILED }

    /** Done/undone: ticks the note line, or adds/removes the cron history `done` line. */
    fun setDone(vault: VaultFiles, ref: TaskRef, done: Boolean): Outcome {
        val outcome = when (ref) {
            is TaskRef.Note -> setNoteChecked(vault, ref, done)
            is TaskRef.Cron -> if (done) logCron(vault, ref, CronOutcome.DONE) else unlogCronDone(vault, ref)
        }
        Log.d(TAG, "setDone ref=$ref done=$done -> $outcome")
        return outcome
    }

    /** Dismiss: cron occurrences are logged as skipped; note tasks need no write. */
    fun skip(vault: VaultFiles, ref: TaskRef): Outcome =
        if (ref is TaskRef.Cron) logCron(vault, ref, CronOutcome.SKIPPED) else Outcome.OK

    private fun setNoteChecked(vault: VaultFiles, ref: TaskRef.Note, checked: Boolean): Outcome {
        val file = vault.note(ref.date)
        val content = VaultFiles.readText(file) ?: return Outcome.NOT_FOUND
        return when (val result = NoteEditor.setChecked(content, ref.rawLine, ref.occurrence, checked)) {
            is NoteEditor.Result.Updated -> if (VaultFiles.writeAtomic(file, result.content)) Outcome.OK else Outcome.FAILED
            NoteEditor.Result.Unchanged -> Outcome.OK
            NoteEditor.Result.NotFound -> Outcome.NOT_FOUND
        }
    }

    private fun logCron(vault: VaultFiles, ref: TaskRef.Cron, outcome: CronOutcome): Outcome {
        val file = vault.history
        val content = VaultFiles.readText(file).orEmpty()
        val already = CronHistoryFormat.parse(content).any { it.outcome == outcome && it.matches(ref.occurrence, ref.section, ref.text) }
        if (already) return Outcome.OK
        val updated = CronHistoryFormat.append(content, CronHistoryEntry(ref.occurrence, outcome, ref.section, ref.text))
        return if (VaultFiles.writeAtomic(file, updated)) Outcome.OK else Outcome.FAILED
    }

    private fun unlogCronDone(vault: VaultFiles, ref: TaskRef.Cron): Outcome {
        val file = vault.history
        val content = VaultFiles.readText(file) ?: return Outcome.OK
        val updated = CronHistoryFormat.removeDone(content, ref.occurrence, ref.section, ref.text) ?: return Outcome.OK
        return if (VaultFiles.writeAtomic(file, updated)) Outcome.OK else Outcome.FAILED
    }
}
