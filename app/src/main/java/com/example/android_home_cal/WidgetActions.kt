package com.example.android_home_cal

import android.content.Intent
import android.net.Uri
import java.time.LocalDate

/**
 * Encodes widget tap targets and system callbacks as intent data URIs
 * (scheme dailynotescal://<appWidgetId>/<op>[/<date>]). Distinct data per intent is what
 * makes each cell's PendingIntent unique with FLAG_UPDATE_CURRENT.
 */
object WidgetActions {

    const val ACTION_WIDGET_TAP = "com.example.android_home_cal.action.WIDGET_TAP"
    const val ACTION_DESELECT_EXPIRED = "com.example.android_home_cal.action.DESELECT_EXPIRED"

    private const val SCHEME = "dailynotescal"
    private const val OP_PREV = "prev"
    private const val OP_NEXT = "next"
    private const val OP_DATE = "date"
    private const val OP_TODAY = "today"
    private const val OP_DESELECT_EXPIRED = "deselect-expired"

    sealed class WidgetAction {
        abstract val appWidgetId: Int

        data class PrevMonth(override val appWidgetId: Int) : WidgetAction()
        data class NextMonth(override val appWidgetId: Int) : WidgetAction()
        data class DateTap(override val appWidgetId: Int, val date: LocalDate) : WidgetAction()
        data class OpenToday(override val appWidgetId: Int) : WidgetAction()
        data class DeselectExpired(override val appWidgetId: Int, val date: LocalDate) : WidgetAction()
    }

    fun prevMonthUri(appWidgetId: Int): Uri =
        Uri.parse("$SCHEME://$appWidgetId/$OP_PREV")

    fun nextMonthUri(appWidgetId: Int): Uri =
        Uri.parse("$SCHEME://$appWidgetId/$OP_NEXT")

    fun dateUri(appWidgetId: Int, date: LocalDate): Uri =
        Uri.parse("$SCHEME://$appWidgetId/$OP_DATE/$date")

    fun todayUri(appWidgetId: Int): Uri =
        Uri.parse("$SCHEME://$appWidgetId/$OP_TODAY")

    fun deselectExpiredUri(appWidgetId: Int, date: LocalDate): Uri =
        Uri.parse("$SCHEME://$appWidgetId/$OP_DESELECT_EXPIRED/$date")

    fun parse(intent: Intent): WidgetAction? {
        val data = intent.data ?: return null
        val appWidgetId = data.host?.toIntOrNull() ?: return null
        val segments = data.pathSegments
        val op = segments.getOrNull(0) ?: return null
        fun dateSegment() = segments.getOrNull(1)?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        return when (op) {
            OP_PREV -> WidgetAction.PrevMonth(appWidgetId)
            OP_NEXT -> WidgetAction.NextMonth(appWidgetId)
            OP_TODAY -> WidgetAction.OpenToday(appWidgetId)
            OP_DATE -> dateSegment()?.let { WidgetAction.DateTap(appWidgetId, it) }
            OP_DESELECT_EXPIRED -> dateSegment()?.let { WidgetAction.DeselectExpired(appWidgetId, it) }
            else -> null
        }
    }
}
