package com.example.android_home_cal

import android.content.Context
import android.util.Log
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

class WidgetPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(appWidgetId: Int): WidgetSettings = WidgetSettings(
        vaultPath = getString(appWidgetId, KEY_VAULT_PATH, WidgetSettings.Defaults.VAULT_PATH),
        todaysNotesCommand = getString(
            appWidgetId,
            KEY_TODAYS_NOTES_COMMAND,
            WidgetSettings.Defaults.TODAYS_NOTES_COMMAND
        ),
        nvimPath = getString(appWidgetId, KEY_NVIM_PATH, WidgetSettings.Defaults.NVIM_PATH),
        shellPath = getString(appWidgetId, KEY_SHELL_PATH, WidgetSettings.Defaults.SHELL_PATH),
        agendaVaultPath = getString(
            appWidgetId,
            KEY_AGENDA_VAULT_PATH,
            WidgetSettings.Defaults.AGENDA_VAULT_PATH
        )
    )

    fun saveSettings(appWidgetId: Int, settings: WidgetSettings) {
        prefs.edit()
            .putString(key(appWidgetId, KEY_VAULT_PATH), settings.vaultPath)
            .putString(key(appWidgetId, KEY_TODAYS_NOTES_COMMAND), settings.todaysNotesCommand)
            .putString(key(appWidgetId, KEY_NVIM_PATH), settings.nvimPath)
            .putString(key(appWidgetId, KEY_SHELL_PATH), settings.shellPath)
            .putString(key(appWidgetId, KEY_AGENDA_VAULT_PATH), settings.agendaVaultPath)
            .apply()
    }

    fun loadDisplayedMonth(appWidgetId: Int): YearMonth {
        val stored = prefs.getString(key(appWidgetId, KEY_DISPLAYED_MONTH), null)
            ?: return YearMonth.now()
        return try {
            YearMonth.parse(stored)
        } catch (e: DateTimeParseException) {
            Log.e(TAG, "Corrupted displayed month '$stored' for widget $appWidgetId", e)
            YearMonth.now()
        }
    }

    fun saveDisplayedMonth(appWidgetId: Int, month: YearMonth) {
        prefs.edit().putString(key(appWidgetId, KEY_DISPLAYED_MONTH), month.toString()).apply()
    }

    fun loadSelectedDate(appWidgetId: Int): LocalDate? {
        val stored = prefs.getString(key(appWidgetId, KEY_SELECTED_DATE), null) ?: return null
        return try {
            LocalDate.parse(stored)
        } catch (e: DateTimeParseException) {
            Log.e(TAG, "Corrupted selected date '$stored' for widget $appWidgetId", e)
            null
        }
    }

    fun saveSelectedDate(appWidgetId: Int, date: LocalDate) {
        prefs.edit().putString(key(appWidgetId, KEY_SELECTED_DATE), date.toString()).apply()
    }

    fun clearSelectedDate(appWidgetId: Int) {
        prefs.edit().remove(key(appWidgetId, KEY_SELECTED_DATE)).apply()
    }

    /** The date the cached agenda preview text was fetched for, so a redraw can tell a
     * stale in-flight result (for a date the user has since navigated away from) apart
     * from a current one. */
    fun loadAgendaPreviewDate(appWidgetId: Int): LocalDate? {
        val stored = prefs.getString(key(appWidgetId, KEY_AGENDA_PREVIEW_DATE), null) ?: return null
        return try {
            LocalDate.parse(stored)
        } catch (e: DateTimeParseException) {
            Log.e(TAG, "Corrupted agenda preview date '$stored' for widget $appWidgetId", e)
            null
        }
    }

    fun loadAgendaPreviewText(appWidgetId: Int): String? =
        prefs.getString(key(appWidgetId, KEY_AGENDA_PREVIEW_TEXT), null)

    fun saveAgendaPreview(appWidgetId: Int, date: LocalDate, text: String) {
        prefs.edit()
            .putString(key(appWidgetId, KEY_AGENDA_PREVIEW_DATE), date.toString())
            .putString(key(appWidgetId, KEY_AGENDA_PREVIEW_TEXT), text)
            .apply()
    }

    fun clearAgendaPreview(appWidgetId: Int) {
        prefs.edit()
            .remove(key(appWidgetId, KEY_AGENDA_PREVIEW_DATE))
            .remove(key(appWidgetId, KEY_AGENDA_PREVIEW_TEXT))
            .apply()
    }

    fun loadLastTap(appWidgetId: Int): Pair<LocalDate, Long>? {
        val storedDate = prefs.getString(key(appWidgetId, KEY_LAST_TAP_DATE), null) ?: return null
        val storedAt = prefs.getLong(key(appWidgetId, KEY_LAST_TAP_AT), -1L)
        if (storedAt < 0) return null
        return try {
            LocalDate.parse(storedDate) to storedAt
        } catch (e: DateTimeParseException) {
            Log.e(TAG, "Corrupted last-tap date '$storedDate' for widget $appWidgetId", e)
            null
        }
    }

    fun saveLastTap(appWidgetId: Int, date: LocalDate, atMillis: Long) {
        prefs.edit()
            .putString(key(appWidgetId, KEY_LAST_TAP_DATE), date.toString())
            .putLong(key(appWidgetId, KEY_LAST_TAP_AT), atMillis)
            .apply()
    }

    fun clearLastTap(appWidgetId: Int) {
        prefs.edit()
            .remove(key(appWidgetId, KEY_LAST_TAP_DATE))
            .remove(key(appWidgetId, KEY_LAST_TAP_AT))
            .apply()
    }

    fun remove(appWidgetId: Int) {
        val editor = prefs.edit()
        ALL_KEYS.forEach { editor.remove(key(appWidgetId, it)) }
        editor.apply()
    }

    private fun getString(appWidgetId: Int, key: String, default: String): String =
        prefs.getString(key(appWidgetId, key), null) ?: default

    private fun key(appWidgetId: Int, key: String) = "$appWidgetId.$key"

    companion object {
        private const val TAG = "WidgetPreferences"
        private const val PREFS_NAME = "daily_notes_calendar_widget_prefs"

        private const val KEY_DISPLAYED_MONTH = "displayedMonth"
        private const val KEY_SELECTED_DATE = "selectedDate"
        private const val KEY_LAST_TAP_DATE = "lastTapDate"
        private const val KEY_LAST_TAP_AT = "lastTapAt"
        private const val KEY_VAULT_PATH = "vaultPath"
        private const val KEY_TODAYS_NOTES_COMMAND = "todaysNotesCommand"
        private const val KEY_NVIM_PATH = "nvimPath"
        private const val KEY_SHELL_PATH = "shellPath"
        private const val KEY_AGENDA_VAULT_PATH = "agendaVaultPath"
        private const val KEY_AGENDA_PREVIEW_DATE = "agendaPreviewDate"
        private const val KEY_AGENDA_PREVIEW_TEXT = "agendaPreviewText"

        private val ALL_KEYS = listOf(
            KEY_DISPLAYED_MONTH,
            KEY_SELECTED_DATE,
            KEY_LAST_TAP_DATE,
            KEY_LAST_TAP_AT,
            KEY_VAULT_PATH,
            KEY_TODAYS_NOTES_COMMAND,
            KEY_NVIM_PATH,
            KEY_SHELL_PATH,
            KEY_AGENDA_VAULT_PATH,
            KEY_AGENDA_PREVIEW_DATE,
            KEY_AGENDA_PREVIEW_TEXT
        )
    }
}
