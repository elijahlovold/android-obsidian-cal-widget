package com.example.android_home_cal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.android_home_cal.R
import com.example.android_home_cal.WidgetActionReceiver
import com.example.android_home_cal.vault.VaultFiles
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object CalendarWidgetRenderer {

    // Widget's own FrameLayout padding (widget_calendar.xml): 4dp each side, both axes.
    private const val WIDGET_PADDING_DP = 0
    // calendar_widget_info.xml's declared minWidth, used only as a fallback for the rare case
    // a host hasn't reported real options yet (e.g. the very first draw before any resize).
    private const val FALLBACK_MIN_WIDTH_DP = 250
    private const val FALLBACK_MIN_HEIGHT_DP = 375

    // Header row is deterministic: btn_prev/btn_next are fixed 36dp with 6dp paddingBottom.
    private const val HEADER_ROW_HEIGHT_DP = 42
    // Weekday label row isn't pinned to a fixed-size view, so this is an estimate (11sp text
    // plus 4dp paddingBottom).
    private const val WEEKDAY_ROW_HEIGHT_ESTIMATE_DP = 21
    // Launchers snap resize to whole grid rows, which can land well above the calendar's own
    // computed minimum (observed ~80dp of slack on this device) - this buffer errs toward
    // treating "close to the smallest the launcher will actually allow" as "hide the agenda",
    // rather than trying to guess the exact grid row size of every launcher.
    private const val AGENDA_VISIBILITY_BUFFER_DP = 90

    private val weekRowIds = intArrayOf(
        R.id.week_row_0,
        R.id.week_row_1,
        R.id.week_row_2,
        R.id.week_row_3,
        R.id.week_row_4,
        R.id.week_row_5
    )

    /** Day cell containers: background, click target. */
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

    /** The number inside each day cell. */
    private val dayTextIds = intArrayOf(
        R.id.day_00_text,
        R.id.day_01_text,
        R.id.day_02_text,
        R.id.day_03_text,
        R.id.day_04_text,
        R.id.day_05_text,
        R.id.day_06_text,
        R.id.day_07_text,
        R.id.day_08_text,
        R.id.day_09_text,
        R.id.day_10_text,
        R.id.day_11_text,
        R.id.day_12_text,
        R.id.day_13_text,
        R.id.day_14_text,
        R.id.day_15_text,
        R.id.day_16_text,
        R.id.day_17_text,
        R.id.day_18_text,
        R.id.day_19_text,
        R.id.day_20_text,
        R.id.day_21_text,
        R.id.day_22_text,
        R.id.day_23_text,
        R.id.day_24_text,
        R.id.day_25_text,
        R.id.day_26_text,
        R.id.day_27_text,
        R.id.day_28_text,
        R.id.day_29_text,
        R.id.day_30_text,
        R.id.day_31_text,
        R.id.day_32_text,
        R.id.day_33_text,
        R.id.day_34_text,
        R.id.day_35_text,
        R.id.day_36_text,
        R.id.day_37_text,
        R.id.day_38_text,
        R.id.day_39_text,
        R.id.day_40_text,
        R.id.day_41_text
    )

    /** The open-tasks dot under each day number. */
    private val dayDotIds = intArrayOf(
        R.id.day_00_dot,
        R.id.day_01_dot,
        R.id.day_02_dot,
        R.id.day_03_dot,
        R.id.day_04_dot,
        R.id.day_05_dot,
        R.id.day_06_dot,
        R.id.day_07_dot,
        R.id.day_08_dot,
        R.id.day_09_dot,
        R.id.day_10_dot,
        R.id.day_11_dot,
        R.id.day_12_dot,
        R.id.day_13_dot,
        R.id.day_14_dot,
        R.id.day_15_dot,
        R.id.day_16_dot,
        R.id.day_17_dot,
        R.id.day_18_dot,
        R.id.day_19_dot,
        R.id.day_20_dot,
        R.id.day_21_dot,
        R.id.day_22_dot,
        R.id.day_23_dot,
        R.id.day_24_dot,
        R.id.day_25_dot,
        R.id.day_26_dot,
        R.id.day_27_dot,
        R.id.day_28_dot,
        R.id.day_29_dot,
        R.id.day_30_dot,
        R.id.day_31_dot,
        R.id.day_32_dot,
        R.id.day_33_dot,
        R.id.day_34_dot,
        R.id.day_35_dot,
        R.id.day_36_dot,
        R.id.day_37_dot,
        R.id.day_38_dot,
        R.id.day_39_dot,
        R.id.day_40_dot,
        R.id.day_41_dot
    )

    private val headerFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetManager, appWidgetId))
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
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetManager, appWidgetId))
    }

    private fun buildRemoteViews(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): RemoteViews {
        val preferences = WidgetPreferences(context)
        val displayedMonth = preferences.loadDisplayedMonth(appWidgetId)
        val today = LocalDate.now()
        // No explicit user selection defaults to today, so the widget always opens with
        // today's agenda visible instead of a blank preview pane.
        val selectedDate = preferences.loadSelectedDate(appWidgetId) ?: today

        val views = RemoteViews(context.packageName, R.layout.widget_calendar)

        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val cellSizeDp = squareCellSizeDp(options)
        val density = context.resources.displayMetrics.density
        val cellHeightPx = (cellSizeDp * density).roundToInt()
        weekRowIds.forEach { rowId -> views.setInt(rowId, "setMinimumHeight", cellHeightPx) }

        views.setViewVisibility(R.id.agenda_container, agendaVisibility(options, cellSizeDp))
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
        val taskDays = datesWithOpenTasks(preferences, appWidgetId, today)
        cells.forEachIndexed { index, date ->
            val cellId = dayCellIds[index]
            val textId = dayTextIds[index]
            val dotId = dayDotIds[index]
            if (date == null) {
                views.setTextViewText(textId, "")
                views.setInt(cellId, "setBackgroundResource", 0)
                views.setViewVisibility(dotId, View.INVISIBLE)
            } else {
                views.setTextViewText(textId, date.dayOfMonth.toString())
                views.setOnClickPendingIntent(
                    cellId,
                    actionPendingIntent(context, WidgetActions.dateUri(appWidgetId, date))
                )
                // INVISIBLE rather than GONE keeps every number at the same height.
                views.setViewVisibility(dotId, if (date in taskDays) View.VISIBLE else View.INVISIBLE)
                views.setImageViewResource(dotId, if (date == today) R.drawable.ic_day_dot_on_accent else R.drawable.ic_day_dot)
                when {
                    date == today -> {
                        views.setInt(cellId, "setBackgroundResource", R.drawable.bg_cell_today)
                        views.setTextColor(textId, colorInt(context, R.color.widget_on_accent))
                    }
                    date == selectedDate -> {
                        views.setInt(cellId, "setBackgroundResource", R.drawable.bg_cell_selected)
                        views.setTextColor(textId, colorInt(context, R.color.widget_on_surface))
                    }
                    else -> {
                        views.setInt(cellId, "setBackgroundResource", 0)
                        views.setTextColor(textId, colorInt(context, R.color.widget_on_surface))
                    }
                }
            }
        }

        bindPane(context, views, preferences, appWidgetId, today, selectedDate)

        return views
    }

    /**
     * The agenda pane under the divider: the rescan button, the Day/Upcoming pill, and the
     * list for the current mode. Lists read the notes directly (mtime-cached), since checkbox
     * rows need each line's identity. text_pane_empty stands in when a list would be empty.
     */
    private fun bindPane(
        context: Context,
        views: RemoteViews,
        preferences: WidgetPreferences,
        appWidgetId: Int,
        today: LocalDate,
        selectedDate: LocalDate
    ) {
        val settings = preferences.loadSettings(appWidgetId)
        val showUpcoming = preferences.loadShowUpcoming(appWidgetId)
        views.setOnClickPendingIntent(R.id.btn_rescan, actionPendingIntent(context, WidgetActions.rescanUri(appWidgetId)))
        views.setTextViewText(
            R.id.btn_pane_mode,
            context.getString(if (showUpcoming) R.string.pane_mode_upcoming else R.string.pane_mode_day)
        )
        views.setOnClickPendingIntent(R.id.btn_pane_mode, actionPendingIntent(context, WidgetActions.toggleModeUri(appWidgetId)))
        val hasRows = if (showUpcoming) {
            PaneListRenderer.bindUpcoming(context, views, appWidgetId, today, settings)
        } else {
            PaneListRenderer.bindDay(context, views, appWidgetId, selectedDate, settings)
        }
        views.setTextViewText(R.id.text_pane_empty, if (showUpcoming) context.getString(R.string.upcoming_empty) else "")
        views.setViewVisibility(R.id.text_pane_empty, if (hasRows) View.GONE else View.VISIBLE)
    }

    /**
     * Today-and-later days with open tasks, for the dots under day numbers. Past days are
     * never scanned. Directory listing plus mtime-cached parses, so cheap on every redraw.
     */
    private fun datesWithOpenTasks(preferences: WidgetPreferences, appWidgetId: Int, today: LocalDate): Set<LocalDate> =
        if (VaultFiles.hasStorageAccess()) {
            VaultFiles(preferences.loadSettings(appWidgetId).agendaVaultPath).datesWithOpenTasks(today)
        } else {
            emptySet()
        }

    /** Full redraw of every placed widget, e.g. after vault files changed on disk. */
    fun redrawAll(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        Widgets.ids(context).forEach { updateWidget(context, appWidgetManager, it) }
    }

    /**
     * Day cells are square: RemoteViews has no declarative aspect-ratio (no ConstraintLayout
     * support), and a build-time guess can't know what pixel width a given launcher actually
     * hands a "4 columns wide" widget - that varies by device/launcher. So the real available
     * width is read live via getAppWidgetOptions() (kept current by onAppWidgetOptionsChanged),
     * divided by 7 columns the same way layout_weight already divides width. The result is
     * pushed onto each row's minimumHeight via the same reflective setInt() RemoteViews already
     * uses for backgrounds - this works on any API level, unlike RemoteViews.setViewLayoutHeight
     * (31+).
     */
    private fun squareCellSizeDp(options: android.os.Bundle): Float {
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            .takeIf { it > 0 } ?: FALLBACK_MIN_WIDTH_DP
        return (minWidthDp - WIDGET_PADDING_DP) / 7f
    }

    /**
     * Hides the agenda pane once the widget is resized down near the calendar's own natural
     * minimum height, computed the same way squareCellSizeDp derives cell height - from the
     * live-queried actual width, not a static guess. A plain layout_weight="0" outcome isn't
     * reliable here because launchers snap resize to whole grid rows, which can land well
     * above the calendar's true minimum (see AGENDA_VISIBILITY_BUFFER_DP).
     */
    private fun agendaVisibility(options: android.os.Bundle, cellSizeDp: Float): Int {
        val currentHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            .takeIf { it > 0 } ?: FALLBACK_MIN_HEIGHT_DP

        val calendarNaturalHeightDp = WIDGET_PADDING_DP + HEADER_ROW_HEIGHT_DP +
            WEEKDAY_ROW_HEIGHT_ESTIMATE_DP + 6 * cellSizeDp

        return if (currentHeightDp <= calendarNaturalHeightDp + AGENDA_VISIBILITY_BUFFER_DP) {
            View.GONE
        } else {
            View.VISIBLE
        }
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
