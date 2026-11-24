package com.example.android_home_cal.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.android_home_cal.CalendarWidgetProvider
import com.example.android_home_cal.vault.VaultFiles

/**
 * Placed widget instances. Reminders are app-wide but settings are per widget, so the first
 * widget's vault is the one reminders use. No widgets placed means reminders are off.
 */
object Widgets {

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))

    fun primarySettings(context: Context): WidgetSettings? =
        ids(context).minOrNull()?.let { WidgetPreferences(context).loadSettings(it) }

    fun primaryVault(context: Context): VaultFiles? = primarySettings(context)?.let { VaultFiles(it.agendaVaultPath) }
}
