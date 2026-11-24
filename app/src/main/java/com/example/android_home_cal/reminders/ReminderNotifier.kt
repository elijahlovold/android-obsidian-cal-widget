package com.example.android_home_cal.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.android_home_cal.R
import com.example.android_home_cal.ReminderReceiver
import com.example.android_home_cal.cron.CronFileError
import java.time.LocalDateTime

/** Posts and clears reminder notifications (docs/reminders.md §5). */
object ReminderNotifier {

    private const val TAG = "ReminderNotifier"
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_PROBLEMS = "reminder_problems"
    private const val TAG_REMINDER = "reminder"
    private const val TAG_PROBLEM = "reminder_problem"
    private const val ID_CRON_ERRORS = 1

    const val EXTRA_KEY = "reminder.key"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.reminders_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROBLEMS,
                context.getString(R.string.reminders_problem_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, reminder: Reminder, now: LocalDateTime) {
        if (!canPost(context)) {
            Log.w(TAG, "Notification permission missing; dropping reminder ${reminder.key}")
            return
        }
        ensureChannels(context)
        val ref = reminder.ref
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(ReminderText.body(reminder, now))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(action(context, ReminderReceiver.ACTION_OPEN, reminder))
            .setDeleteIntent(action(context, ReminderReceiver.ACTION_DISMISS, reminder))
            .addAction(0, context.getString(R.string.reminder_action_done), action(context, ReminderReceiver.ACTION_DONE, reminder))
            .addAction(0, context.getString(R.string.reminder_action_snooze), action(context, ReminderReceiver.ACTION_SNOOZE, reminder))
            .addAction(0, context.getString(R.string.reminder_action_dismiss), action(context, ReminderReceiver.ACTION_DISMISS, reminder))
            .build()
        manager(context)?.notify(TAG_REMINDER, notificationId(ref), notification)
    }

    fun cancel(context: Context, ref: TaskRef) {
        manager(context)?.cancel(TAG_REMINDER, notificationId(ref))
    }

    /** Clears notifications for tasks that were completed elsewhere (e.g. ticked in nvim). */
    fun cancelCompleted(context: Context, completedGroups: Set<String>) {
        val manager = manager(context) ?: return
        val ids = completedGroups.map { it.hashCode() }.toSet()
        manager.activeNotifications
            .filter { it.tag == TAG_REMINDER && it.id in ids }
            .forEach { manager.cancel(TAG_REMINDER, it.id) }
    }

    fun postProblem(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_PROBLEMS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager(context)?.notify(TAG_PROBLEM, id, notification)
    }

    fun postCronErrors(context: Context, errors: List<CronFileError>) {
        val text = errors.take(5).joinToString("\n") { "Line ${it.lineNumber}: ${it.message}" } +
            if (errors.size > 5) "\n…and ${errors.size - 5} more" else ""
        postProblem(context, ID_CRON_ERRORS, context.getString(R.string.reminder_cron_errors_title), text)
    }

    fun clearCronErrors(context: Context) {
        manager(context)?.cancel(TAG_PROBLEM, ID_CRON_ERRORS)
    }

    private fun notificationId(ref: TaskRef): Int = ref.groupId.hashCode()

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun action(context: Context, action: String, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            // Unique data per (action, reminder) keeps each PendingIntent's extras distinct.
            data = Uri.Builder().scheme("dailynotesreminder").authority(action.substringAfterLast('.'))
                .appendPath(reminder.key).build()
            putExtra(EXTRA_KEY, reminder.key)
            TaskRefExtras.put(this, reminder.ref)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
