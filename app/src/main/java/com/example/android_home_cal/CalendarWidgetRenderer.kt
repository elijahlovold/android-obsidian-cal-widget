package com.example.android_home_cal

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object CalendarWidgetRenderer {

    private val dayCellIds = intArrayOf(
        R.id.day_00,
        R.id.day_01,
        R.id.day_02,
        R.id.day_03,
        R.id.day_04,
        R.id.day_05,
        R.id.day_06,
        R.id.day_07,
        R.id.day_08,
        R.id.day_09,
        R.id.day_10,
        R.id.day_11,
        R.id.day_12,
        R.id.day_13,
        R.id.day_14,
        R.id.day_15,
        R.id.day_16,
        R.id.day_17,
        R.id.day_18,
        R.id.day_19,
        R.id.day_20,
        R.id.day_21,
        R.id.day_22,
        R.id.day_23,
        R.id.day_24,
        R.id.day_25,
        R.id.day_26,
        R.id.day_27,
        R.id.day_28,
        R.id.day_29,
        R.id.day_30,
        R.id.day_31,
        R.id.day_32,
        R.id.day_33,
        R.id.day_34,
        R.id.day_35,
        R.id.day_36,
        R.id.day_37,
        R.id.day_38,
        R.id.day_39,
        R.id.day_40,
        R.id.day_41
    )

    private val headerFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetId))
    }

    /**
     * Applies the same content as [updateWidget] but as an incremental patch against the
     * already-inflated host view instead of a full layout replace. A full updateAppWidget()
     * tears down and re-inflates all 42 cells over Binder, which is slow enough to itself
     * create a dead zone that swallows the second tap of a real double-tap. Selecting a date
     * (a single tap, fired on every tap regardless of whether it turns out to be part of a
     * double-tap) must stay cheap so double-tap detection isn't sabotaged by its own redraw.
     */
    fun updateWidgetSelectionOnly(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetId))
    }

    private fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val preferences = WidgetPreferences(context)
        val displayedMonth = preferences.loadDisplayedMonth(appWidgetId)
        val selectedDate = preferences.loadSelectedDate(appWidgetId)
        val today = LocalDate.now()

        val views = RemoteViews(context.packageName, R.layout.widget_calendar)
        views.setTextViewText(
            R.id.text_month_year,
            displayedMonth.atDay(1).format(headerFormatter).replaceFirstChar {
                it.titlecase(Locale.getDefault())
            }
        )

        views.setOnClickPendingIntent(
            R.id.btn_prev,
            actionPendingIntent(context, WidgetActions.prevMonthUri(appWidgetId))
        )
        views.setOnClickPendingIntent(
            R.id.btn_next,
            actionPendingIntent(context, WidgetActions.nextMonthUri(appWidgetId))
        )
        views.setOnClickPendingIntent(
            R.id.text_month_year,
            actionPendingIntent(context, WidgetActions.todayUri(appWidgetId))
        )

        val cells = CalendarMath.gridCells(displayedMonth)
        cells.forEachIndexed { index, date ->
            val cellId = dayCellIds[index]
            if (date == null) {
                views.setTextViewText(cellId, "")
                views.setInt(cellId, "setBackgroundResource", 0)
            } else {
                views.setTextViewText(cellId, date.dayOfMonth.toString())
                views.setOnClickPendingIntent(
                    cellId,
                    actionPendingIntent(context, WidgetActions.dateUri(appWidgetId, date))
                )
                when {
                    date == today -> {
                        views.setInt(cellId, "setBackgroundResource", R.drawable.bg_cell_today)
                        views.setTextColor(cellId, colorInt(context, R.color.widget_on_accent))
                    }
                    date == selectedDate -> {
                        views.setInt(cellId, "setBackgroundResource", R.drawable.bg_cell_selected)
                        views.setTextColor(cellId, colorInt(context, R.color.widget_on_surface))
                    }
                    else -> {
                        views.setInt(cellId, "setBackgroundResource", 0)
                        views.setTextColor(cellId, colorInt(context, R.color.widget_on_surface))
                    }
                }
            }
        }

        views.setTextViewText(R.id.text_agenda, agendaPreviewText(preferences, appWidgetId, selectedDate))

        return views
    }

    private fun agendaPreviewText(preferences: WidgetPreferences, appWidgetId: Int, selectedDate: LocalDate?): String {
        if (selectedDate == null) return ""
        if (preferences.loadAgendaPreviewDate(appWidgetId) != selectedDate) return ""
        return preferences.loadAgendaPreviewText(appWidgetId).orEmpty()
    }

    private fun colorInt(context: Context, colorRes: Int): Int =
        context.getColor(colorRes)

    private fun actionPendingIntent(context: Context, uri: android.net.Uri): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActions.ACTION_WIDGET_TAP
            data = uri
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
