package com.example.android_home_cal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.android_home_cal.reminders.ReminderNotifier
import com.example.android_home_cal.reminders.ReminderScheduler
import com.example.android_home_cal.reminders.ReminderStore
import com.example.android_home_cal.reminders.TaskActions
import com.example.android_home_cal.reminders.TaskRef
import com.example.android_home_cal.reminders.TaskRefExtras
import com.example.android_home_cal.termux.TermuxLauncher
import com.example.android_home_cal.vault.VaultFiles
import com.example.android_home_cal.widget.Widgets
import kotlin.concurrent.thread

/** Alarm, system and notification-action entry point for reminders. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "onReceive $action")
        if (action == ACTION_OPEN) {
            // Toasts from TermuxLauncher need the main thread; this is quick anyway.
            open(context, intent)
            return
        }
        // File IO off the main thread; goAsync keeps the process alive until it finishes.
        val pending = goAsync()
        thread(name = "reminders") {
            try {
                handle(context.applicationContext, action, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling $action", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handle(context: Context, action: String, intent: Intent) {
        when (action) {
            ACTION_DONE -> {
                val ref = TaskRefExtras.get(intent) ?: return
                val vault = Widgets.primaryVault(context) ?: return
                ReminderNotifier.cancel(context, ref)
                when (TaskActions.setDone(vault, ref, done = true)) {
                    TaskActions.Outcome.NOT_FOUND -> ReminderNotifier.postProblem(
                        context,
                        ref.groupId.hashCode(),
                        context.getString(R.string.reminder_not_found_title),
                        context.getString(R.string.reminder_not_found_text, describe(ref))
                    )
                    TaskActions.Outcome.FAILED -> ReminderNotifier.postProblem(
                        context,
                        ref.groupId.hashCode(),
                        context.getString(R.string.reminder_write_failed_title),
                        describe(ref)
                    )
                    TaskActions.Outcome.OK -> Unit
                }
            }
            ACTION_SNOOZE -> {
                val ref = TaskRefExtras.get(intent) ?: return
                val key = intent.getStringExtra(ReminderNotifier.EXTRA_KEY) ?: return
                ReminderStore(context).snooze(key, System.currentTimeMillis() + SNOOZE_MILLIS)
                ReminderNotifier.cancel(context, ref)
            }
            ACTION_DISMISS -> {
                val ref = TaskRefExtras.get(intent) ?: return
                val vault = Widgets.primaryVault(context) ?: return
                ReminderNotifier.cancel(context, ref)
                TaskActions.skip(vault, ref)
            }
        }
        // Everything (alarms, boot, time changes, and the actions above) ends in a rescan,
        // which also redraws the widgets if an action changed a file.
        ReminderScheduler.scan(context)
    }

    private fun open(context: Context, intent: Intent) {
        val settings = Widgets.primarySettings(context) ?: return
        when (val ref = TaskRefExtras.get(intent)) {
            is TaskRef.Note -> TermuxLauncher.openNote(context, ref.date, settings)
            is TaskRef.Cron -> TermuxLauncher.openFile(context, VaultFiles.CRON_PATH, settings)
            null -> Unit
        }
    }

    private fun describe(ref: TaskRef): String = when (ref) {
        is TaskRef.Note -> "${ref.rawLine.trim()} (${ref.date}.md)"
        is TaskRef.Cron -> "${ref.text} (${VaultFiles.HISTORY_PATH})"
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val SNOOZE_MILLIS = 10 * 60 * 1000L

        const val ACTION_POLL = "com.example.android_home_cal.reminder.POLL"
        const val ACTION_FIRE = "com.example.android_home_cal.reminder.FIRE"
        const val ACTION_SCAN = "com.example.android_home_cal.reminder.SCAN"
        const val ACTION_DONE = "com.example.android_home_cal.reminder.DONE"
        const val ACTION_SNOOZE = "com.example.android_home_cal.reminder.SNOOZE"
        const val ACTION_DISMISS = "com.example.android_home_cal.reminder.DISMISS"
        const val ACTION_OPEN = "com.example.android_home_cal.reminder.OPEN"

        /** Fire-and-forget rescan from anywhere (widget updates, config save). */
        fun requestScan(context: Context) {
            context.sendBroadcast(Intent(context, ReminderReceiver::class.java).setAction(ACTION_SCAN))
        }
    }
}

/**
 * Exported receiver for system broadcasts (boot, app update, clock/zone change, exact-alarm
 * permission change). It ignores the intent's content and only requests a rescan, so another
 * app sending it something is harmless.
 */
class ReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ReminderSystemReceiver", "onReceive ${intent.action}")
        ReminderReceiver.requestScan(context)
    }
}
