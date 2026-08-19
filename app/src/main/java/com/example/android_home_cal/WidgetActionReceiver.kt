package com.example.android_home_cal

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDate

class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetActions.ACTION_WIDGET_TAP) return
        val action = WidgetActions.parse(intent) ?: return
        Log.d(TAG, "onReceive action=$action")
        val preferences = WidgetPreferences(context)

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
                preferences.saveSelectedDate(action.appWidgetId, today)
                preferences.clearLastTap(action.appWidgetId)
                TermuxLauncher.openNote(context, today, preferences.loadSettings(action.appWidgetId))
                redraw(context, action.appWidgetId)
            }
            is WidgetActions.WidgetAction.DateTap -> handleDateTap(context, preferences, action)
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
        val isDoubleTap = lastTap != null &&
            lastTap.first == action.date &&
            elapsedMillis != null &&
            elapsedMillis <= DOUBLE_TAP_WINDOW_MILLIS

        Log.d(
            TAG,
            "handleDateTap date=${action.date} elapsedSinceLastTap=${elapsedMillis}ms " +
                "-> ${if (isDoubleTap) "OPEN" else "SELECT"}"
        )

        if (isDoubleTap) {
            preferences.clearLastTap(action.appWidgetId)
            preferences.saveSelectedDate(action.appWidgetId, action.date)
            TermuxLauncher.openNote(context, action.date, preferences.loadSettings(action.appWidgetId))
            redraw(context, action.appWidgetId)
        } else {
            preferences.saveLastTap(action.appWidgetId, action.date, now)
            preferences.saveSelectedDate(action.appWidgetId, action.date)
            // Partial update: a full updateAppWidget() re-inflates all 42 cells over Binder,
            // slow enough to itself swallow the second tap of a real double-tap. This redraw
            // fires on every single tap (to show the selection highlight), so it must stay cheap.
            CalendarWidgetRenderer.updateWidgetSelectionOnly(
                context,
                AppWidgetManager.getInstance(context),
                action.appWidgetId
            )
        }
    }

    private fun redraw(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        CalendarWidgetRenderer.updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        private const val TAG = "WidgetActionReceiver"

        // A widget double-tap round-trips through two separate broadcasts (touch -> launcher
        // -> PendingIntent -> onReceive), plus whatever the launcher takes to re-inflate the
        // host view after the first tap's redraw - on-device measurement showed ~700-950ms
        // between deliveries even for a fast physical double-tap, well above
        // ViewConfiguration.getDoubleTapTimeout() (~300ms, tuned for a single View's raw touch
        // gesture, not this round-trip).
        private const val DOUBLE_TAP_WINDOW_MILLIS = 1200L
    }
}
