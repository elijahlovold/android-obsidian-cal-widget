package com.example.android_home_cal

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.example.android_home_cal.reminders.ReminderScheduler
import com.example.android_home_cal.widget.CalendarWidgetRenderer
import com.example.android_home_cal.widget.WidgetPreferences

class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            CalendarWidgetRenderer.updateWidget(context, appWidgetManager, appWidgetId)
        }
        ReminderReceiver.requestScan(context)
    }

    override fun onDisabled(context: Context) {
        // Last widget removed: reminders use the widget's vault path, so they stop too.
        ReminderScheduler.cancelAlarms(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Fires on every resize - the day cells' square size depends on the widget's actual
        // current width, so a resize has to trigger a full recompute/redraw.
        CalendarWidgetRenderer.updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val preferences = WidgetPreferences(context)
        appWidgetIds.forEach { appWidgetId -> preferences.remove(appWidgetId) }
    }
}
