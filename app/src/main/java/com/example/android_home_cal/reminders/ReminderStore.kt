package com.example.android_home_cal.reminders

import android.annotation.SuppressLint
import android.content.Context
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reminder bookkeeping that doesn't belong in the vault: which reminder keys have already
 * fired (or been skipped), and active snoozes. Uses commit(), since receivers can be killed
 * right after returning.
 */
@SuppressLint("ApplySharedPref")
class ReminderStore(context: Context) {

    private val app = context.applicationContext
    private val handled = app.getSharedPreferences("reminders_handled", Context.MODE_PRIVATE)
    private val snoozed = app.getSharedPreferences("reminders_snoozed", Context.MODE_PRIVATE)
    private val meta = app.getSharedPreferences("reminders_meta", Context.MODE_PRIVATE)

    fun handledKeys(): Set<String> = handled.all.keys

    fun markHandled(keys: Collection<String>, nowMillis: Long) {
        if (keys.isEmpty()) return
        val editor = handled.edit()
        keys.forEach { editor.putLong(it, nowMillis) }
        editor.commit()
    }

    fun snoozes(zone: ZoneId): Map<String, LocalDateTime> =
        snoozed.all.mapNotNull { (key, value) ->
            (value as? Long)?.let { key to LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
        }.toMap()

    fun snooze(key: String, untilMillis: Long) {
        snoozed.edit().putLong(key, untilMillis).commit()
    }

    fun removeSnoozes(keys: Collection<String>) {
        val present = keys.filter { snoozed.contains(it) }
        if (present.isEmpty()) return
        val editor = snoozed.edit()
        present.forEach { editor.remove(it) }
        editor.commit()
    }

    /**
     * Handled keys are dropped 5 days after being recorded. Forgetting one is harmless: a
     * missed reminder is only ever posted if it falls on today (see ReminderPlanner), so an
     * old fire time just gets marked handled again. Snoozes are dropped a day after they
     * were due.
     */
    fun prune(nowMillis: Long) {
        prune(handled, nowMillis - 5 * DAY_MILLIS)
        prune(snoozed, nowMillis - DAY_MILLIS)
    }

    /** Identifies the Cal-Cron.md version whose parse errors were already reported. */
    var reportedCronErrorStamp: String?
        get() = meta.getString(KEY_CRON_ERROR_STAMP, null)
        set(value) {
            meta.edit().putString(KEY_CRON_ERROR_STAMP, value).commit()
        }

    /** Fingerprint of the vault files the widget shows, as of the last scan. */
    var lastVaultFingerprint: String?
        get() = meta.getString(KEY_VAULT_FINGERPRINT, null)
        set(value) {
            meta.edit().putString(KEY_VAULT_FINGERPRINT, value).commit()
        }

    private fun prune(prefs: android.content.SharedPreferences, olderThan: Long) {
        val stale = prefs.all.filter { (_, v) -> (v as? Long ?: 0L) < olderThan }.keys
        if (stale.isEmpty()) return
        val editor = prefs.edit()
        stale.forEach { editor.remove(it) }
        editor.commit()
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val KEY_CRON_ERROR_STAMP = "cronErrorStamp"
        const val KEY_VAULT_FINGERPRINT = "vaultFingerprint"
    }
}
