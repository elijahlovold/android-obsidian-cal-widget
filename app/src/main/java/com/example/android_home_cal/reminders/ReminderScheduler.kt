package com.example.android_home_cal.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.android_home_cal.ReminderReceiver
import com.example.android_home_cal.cron.CronFile
import com.example.android_home_cal.vault.VaultFiles
import com.example.android_home_cal.widget.CalendarWidgetRenderer
import com.example.android_home_cal.widget.WidgetPreferences
import com.example.android_home_cal.widget.Widgets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Scan → plan → post due notifications → arm alarms (docs/reminders.md §4). Two alarms:
 * an exact, allow-while-idle one for the next reminder, and an inexact 10-minute poll that
 * picks up edits. The poll deliberately doesn't use allow-while-idle, so it never eats into
 * the Doze quota the real reminders need.
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    private const val POLL_INTERVAL_MILLIS = 10 * 60 * 1000L
    /** Cron occurrences are planned this many days ahead; daily notes have no limit. */
    private const val CRON_WINDOW_DAYS = 3
    private const val REQUEST_FIRE = 1
    private const val REQUEST_POLL = 2

    /** [forceRedraw]: redraw the widgets even if no file changed (manual rescan). */
    @Synchronized
    fun scan(context: Context, forceRedraw: Boolean = false) {
        val app = context.applicationContext
        val vault = Widgets.primaryVault(app)
        if (vault == null) {
            Log.d(TAG, "No widgets placed; reminders off")
            cancelAlarms(app)
            return
        }
        if (!VaultFiles.hasStorageAccess()) {
            Log.w(TAG, "No storage access; can't scan for reminders")
            schedulePoll(app)
            return
        }

        val zone = ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()
        val now = LocalDateTime.now(zone)
        val today = now.toLocalDate()
        val store = ReminderStore(app)
        store.prune(nowMillis)

        val noteDates = vault.noteDatesFrom(today)
        redrawWidgetsIfVaultChanged(app, vault, noteDates, today, store, forceRedraw)

        val cronFile = vault.cronFile()
        reportCronErrors(app, vault, cronFile, store)

        val plan = ReminderPlanner.plan(
            PlannerInput(
                now = now,
                notes = noteDates.associateWith { vault.tasks(it) },
                cronEntries = cronFile.entries,
                cronHistory = vault.cronHistory(),
                handledKeys = store.handledKeys(),
                snoozes = store.snoozes(zone),
                zone = zone,
                windowDays = CRON_WINDOW_DAYS
            )
        )
        Log.d(TAG, "scan: due=${plan.due.size} next=${plan.nextFireAt} completed=${plan.completedGroups.size}")

        plan.due.forEach { ReminderNotifier.post(app, it, now) }
        store.markHandled(plan.markHandled, nowMillis)
        store.removeSnoozes(plan.markHandled)
        if (plan.completedGroups.isNotEmpty()) ReminderNotifier.cancelCompleted(app, plan.completedGroups)

        scheduleNext(app, plan.nextFireAt, zone)
        schedulePoll(app)
    }

    /**
     * Keeps the widget at most one poll behind edits made outside it (sync, nvim). Compares
     * (path, mtime, length) of everything the widget can show: future notes, each widget's
     * displayed date, and the cron files. Stats only; redraws only when something changed
     * (or [force]). This is also how the widget reflects the app's own writes (checkbox ticks,
     * notification actions): they change a file, then rescan.
     */
    private fun redrawWidgetsIfVaultChanged(
        context: Context,
        vault: VaultFiles,
        noteDates: List<LocalDate>,
        today: LocalDate,
        store: ReminderStore,
        force: Boolean
    ) {
        val preferences = WidgetPreferences(context)
        val shownDates = Widgets.ids(context).map { preferences.loadSelectedDate(it) ?: today }
        val files = (noteDates + shownDates).distinct().map(vault::note) + vault.cron + vault.history
        val fingerprint = files.joinToString("|") { "${it.path}:${it.lastModified()}:${it.length()}" }
            .let { java.security.MessageDigest.getInstance("SHA-1").digest(it.toByteArray()) }
            .joinToString("") { "%02x".format(it) }
        val previous = store.lastVaultFingerprint
        if (fingerprint != previous) store.lastVaultFingerprint = fingerprint
        // First scan ever has nothing to compare against; the widget was just drawn anyway.
        val changed = previous != null && fingerprint != previous
        if (changed || force) {
            Log.d(TAG, "Redrawing widgets (changed=$changed force=$force)")
            CalendarWidgetRenderer.redrawAll(context)
        }
    }

    fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context, ReminderReceiver.ACTION_FIRE, REQUEST_FIRE))
        alarmManager.cancel(pendingIntent(context, ReminderReceiver.ACTION_POLL, REQUEST_POLL))
    }

    private fun reportCronErrors(context: Context, vault: VaultFiles, cronFile: CronFile, store: ReminderStore) {
        if (cronFile.errors.isEmpty()) {
            if (store.reportedCronErrorStamp != null) {
                store.reportedCronErrorStamp = null
                ReminderNotifier.clearCronErrors(context)
            }
            return
        }
        val stamp = "${vault.cron.lastModified()}:${vault.cron.length()}"
        if (stamp == store.reportedCronErrorStamp) return
        ReminderNotifier.postCronErrors(context, cronFile.errors)
        store.reportedCronErrorStamp = stamp
    }

    private fun scheduleNext(context: Context, next: LocalDateTime?, zone: ZoneId) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, ReminderReceiver.ACTION_FIRE, REQUEST_FIRE)
        if (next == null) {
            alarmManager.cancel(pi)
            return
        }
        val triggerAt = ZonedDateTime.of(next, zone).toInstant().toEpochMilli()
        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                Log.w(TAG, "Exact alarms not permitted; reminder may be late")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm refused; falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun schedulePoll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.set(
            AlarmManager.RTC,
            System.currentTimeMillis() + POLL_INTERVAL_MILLIS,
            pendingIntent(context, ReminderReceiver.ACTION_POLL, REQUEST_POLL)
        )
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
