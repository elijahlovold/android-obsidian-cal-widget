package com.example.android_home_cal

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.ViewConfiguration
import android.widget.RemoteViews
import com.example.android_home_cal.reminders.ReminderScheduler
import com.example.android_home_cal.reminders.TaskActions
import com.example.android_home_cal.reminders.TaskRefExtras
import com.example.android_home_cal.termux.TermuxLauncher
import com.example.android_home_cal.vault.FileParseCache
import com.example.android_home_cal.vault.VaultFiles
import com.example.android_home_cal.widget.CalendarMath
import com.example.android_home_cal.widget.CalendarWidgetRenderer
import com.example.android_home_cal.widget.WidgetActions
import com.example.android_home_cal.widget.WidgetPreferences
import com.example.android_home_cal.widget.Widgets
import java.time.LocalDate
import java.time.YearMonth
import kotlin.concurrent.thread

class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val preferences = WidgetPreferences(context)
        when (intent.action) {
            WidgetActions.ACTION_WIDGET_TAP -> handleWidgetTap(context, preferences, intent)
            WidgetActions.ACTION_DESELECT_EXPIRED -> handleDeselectExpired(context, preferences, intent)
            WidgetActions.ACTION_TASK_TOGGLE -> handleTaskToggle(context, intent)
        }
    }

    /**
     * Manual rescan: forget every parsed file (in case a sync tool kept the old mtime), then
     * replan reminders and redraw every widget. Off the main thread.
     */
    private fun rescan(context: Context) {
        val app = context.applicationContext
        val pending = goAsync()
        thread(name = "rescan") {
            try {
                FileParseCache.clear()
                ReminderScheduler.scan(app, forceRedraw = true)
            } catch (e: Exception) {
                Log.e(TAG, "Manual rescan failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * A list checkbox changed: write it back off the main thread, then rescan. The rescan sees
     * the changed file and redraws; an explicit redraw is only needed when it can't (the write
     * failed and the checkbox must revert, or this widget uses a different vault than the
     * primary one the rescan watches).
     */
    private fun handleTaskToggle(context: Context, intent: Intent) {
        val ref = TaskRefExtras.get(intent) ?: return
        val checked = intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false)
        val settings = WidgetActions.parse(intent)?.let { WidgetPreferences(context).loadSettings(it.appWidgetId) } ?: return
        val app = context.applicationContext
        val pending = goAsync()
        thread(name = "task-toggle") {
            try {
                val vault = VaultFiles(settings.agendaVaultPath)
                val outcome = TaskActions.setDone(vault, ref, checked)
                val watched = Widgets.primaryVault(app)?.root == vault.root
                if (outcome != TaskActions.Outcome.OK || !watched) CalendarWidgetRenderer.redrawAll(app)
                ReminderScheduler.scan(app)
            } catch (e: Exception) {
                Log.e(TAG, "Task toggle failed for $ref", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleWidgetTap(context: Context, preferences: WidgetPreferences, intent: Intent) {
        val action = WidgetActions.parse(intent) ?: return
        Log.d(TAG, "handleWidgetTap action=$action")

        when (action) {
            is WidgetActions.WidgetAction.PrevMonth -> {
                val month = CalendarMath.previousMonth(preferences.loadDisplayedMonth(action.appWidgetId))
                preferences.saveDisplayedMonth(action.appWidgetId, month)
                redraw(context, action.appWidgetId)
            }
            is WidgetActions.WidgetAction.NextMonth -> {
                val month = CalendarMath.nextMonth(preferences.loadDisplayedMonth(action.appWidgetId))
                preferences.saveDisplayedMonth(action.appWidgetId, month)
                redraw(context, action.appWidgetId)
            }
            is WidgetActions.WidgetAction.GoToToday -> {
                // Jump back to the current month with today selected. Opening the note stays
                // on double-tap, same as any other date.
                val today = LocalDate.now()
                preferences.saveDisplayedMonth(action.appWidgetId, YearMonth.from(today))
                preferences.clearLastTap(action.appWidgetId)
                select(context, preferences, action.appWidgetId, today, fullRedraw = true)
            }
            is WidgetActions.WidgetAction.TogglePaneMode -> {
                preferences.saveShowUpcoming(action.appWidgetId, !preferences.loadShowUpcoming(action.appWidgetId))
                redraw(context, action.appWidgetId)
            }
            is WidgetActions.WidgetAction.Rescan -> rescan(context)
            is WidgetActions.WidgetAction.DateTap -> handleDateTap(context, preferences, action)
            else -> Unit
        }
    }

    private fun handleDateTap(
        context: Context,
        preferences: WidgetPreferences,
        action: WidgetActions.WidgetAction.DateTap
    ) {
        val now = System.currentTimeMillis()
        val lastTap = preferences.loadLastTap(action.appWidgetId)
        val elapsedMillis = lastTap?.let { now - it.second }
        val doubleTapWindowMillis = ViewConfiguration.getDoubleTapTimeout().toLong()
        val isDoubleTap = lastTap != null &&
            lastTap.first == action.date &&
            elapsedMillis != null &&
            elapsedMillis <= doubleTapWindowMillis

        Log.d(
            TAG,
            "handleDateTap date=${action.date} elapsedSinceLastTap=${elapsedMillis}ms " +
                "-> ${if (isDoubleTap) "OPEN" else "SELECT/DESELECT"}"
        )

        if (isDoubleTap) {
            preferences.clearLastTap(action.appWidgetId)
            cancelDeselectExpiry(context, action.appWidgetId, action.date)
            preferences.saveSelectedDate(action.appWidgetId, action.date)
            TermuxLauncher.openNote(context, action.date, preferences.loadSettings(action.appWidgetId))
            redraw(context, action.appWidgetId)
            return
        }

        val currentlySelected = preferences.loadSelectedDate(action.appWidgetId)
        preferences.saveLastTap(action.appWidgetId, action.date, now)

        if (currentlySelected == action.date) {
            deselect(context, preferences, action.appWidgetId, action.date)
        } else {
            select(context, preferences, action.appWidgetId, action.date)
        }
    }

    private fun select(
        context: Context,
        preferences: WidgetPreferences,
        appWidgetId: Int,
        date: LocalDate,
        fullRedraw: Boolean = false
    ) {
        preferences.loadSelectedDate(appWidgetId)?.let { previousDate ->
            cancelDeselectExpiry(context, appWidgetId, previousDate)
        }
        preferences.saveSelectedDate(appWidgetId, date)
        scheduleDeselectExpiry(context, appWidgetId, date)
        if (fullRedraw) redraw(context, appWidgetId) else partialRedraw(context, appWidgetId)
    }

    private fun deselect(context: Context, preferences: WidgetPreferences, appWidgetId: Int, date: LocalDate) {
        cancelDeselectExpiry(context, appWidgetId, date)
        preferences.clearSelectedDate(appWidgetId)
        partialRedraw(context, appWidgetId)
    }

    private fun handleDeselectExpired(context: Context, preferences: WidgetPreferences, intent: Intent) {
        val action = WidgetActions.parse(intent) as? WidgetActions.WidgetAction.DeselectExpired ?: return
        if (preferences.loadSelectedDate(action.appWidgetId) != action.date) {
            // Superseded by a newer selection already - that selection has its own expiry timer.
            return
        }
        deselect(context, preferences, action.appWidgetId, action.date)
    }

    private fun scheduleDeselectExpiry(context: Context, appWidgetId: Int, date: LocalDate) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + DESELECT_TIMEOUT_MILLIS
        // Non-wakeup: no need to rouse the device early just to clear a selection nobody is
        // looking at; it fires whenever the device is next awake at or after triggerAt.
        alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, deselectExpiryPendingIntent(context, appWidgetId, date))
    }

    private fun cancelDeselectExpiry(context: Context, appWidgetId: Int, date: LocalDate) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(deselectExpiryPendingIntent(context, appWidgetId, date))
    }

    private fun deselectExpiryPendingIntent(context: Context, appWidgetId: Int, date: LocalDate): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActions.ACTION_DESELECT_EXPIRED
            data = WidgetActions.deselectExpiredUri(appWidgetId, date)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun redraw(context: Context, appWidgetId: Int) {
        CalendarWidgetRenderer.updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    // Partial update: a full updateAppWidget() re-inflates all cells over Binder, slow enough
    // to itself swallow the second tap of a real double-tap. This fires on every select/deselect
    // and every agenda-preview result, so it must stay cheap.
    private fun partialRedraw(context: Context, appWidgetId: Int) {
        CalendarWidgetRenderer.updateWidgetSelectionOnly(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    companion object {
        private const val TAG = "WidgetActionReceiver"

        // "Deselect when you close the phone" can't be done via true screen-off detection
        // without a persistent foreground service (ACTION_SCREEN_OFF can't reach a
        // manifest-registered receiver on Android 8+). An idle auto-expire is the practical
        // equivalent here: you can't interact with a locked screen anyway, so by the time you
        // come back the stale selection is already gone.
        private const val DESELECT_TIMEOUT_MILLIS = 1 * 60 * 1000L
    }
}
