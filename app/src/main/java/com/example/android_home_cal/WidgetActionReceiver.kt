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
import java.time.LocalDate

class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val preferences = WidgetPreferences(context)
        when (intent.action) {
            WidgetActions.ACTION_WIDGET_TAP -> handleWidgetTap(context, preferences, intent)
            WidgetActions.ACTION_DESELECT_EXPIRED -> handleDeselectExpired(context, preferences, intent)
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
            is WidgetActions.WidgetAction.OpenToday -> {
                val today = LocalDate.now()
                cancelDeselectExpiry(context, action.appWidgetId, today)
                preferences.saveSelectedDate(action.appWidgetId, today)
                preferences.clearLastTap(action.appWidgetId)
                TermuxLauncher.openNote(context, today, preferences.loadSettings(action.appWidgetId))
                redraw(context, action.appWidgetId)
            }
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

    private fun select(context: Context, preferences: WidgetPreferences, appWidgetId: Int, date: LocalDate) {
        preferences.loadSelectedDate(appWidgetId)?.let { previousDate ->
            cancelDeselectExpiry(context, appWidgetId, previousDate)
        }
        preferences.saveSelectedDate(appWidgetId, date)
        val agendaText = AgendaPreviewReader.read(date, preferences.loadSettings(appWidgetId))
        preferences.saveAgendaPreview(appWidgetId, date, agendaText)
        scheduleDeselectExpiry(context, appWidgetId, date)
        partialRedraw(context, appWidgetId)
    }

    private fun deselect(context: Context, preferences: WidgetPreferences, appWidgetId: Int, date: LocalDate) {
        cancelDeselectExpiry(context, appWidgetId, date)
        preferences.clearSelectedDate(appWidgetId)
        preferences.clearAgendaPreview(appWidgetId)
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
