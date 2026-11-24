package com.example.android_home_cal.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import com.example.android_home_cal.R
import com.example.android_home_cal.WidgetActionReceiver
import com.example.android_home_cal.cron.CronOccurrence
import com.example.android_home_cal.cron.CronOutcome
import com.example.android_home_cal.notes.InlineMarkdown
import com.example.android_home_cal.notes.NoteView
import com.example.android_home_cal.notes.PreviewKind
import com.example.android_home_cal.notes.PreviewLine
import com.example.android_home_cal.notes.PreviewMarker
import com.example.android_home_cal.notes.StyledText
import com.example.android_home_cal.reminders.TaskRef
import com.example.android_home_cal.reminders.TaskRefExtras
import com.example.android_home_cal.vault.VaultFiles
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The agenda pane's list, built with RemoteCollectionItems. Day mode:
 *
 *  1. Agenda: the note's whole `# Agenda`. Checkbox items are real
 *     checkboxes that tick the line in the note; other list items get a bullet.
 *  2. Cron: that day's Cal-Cron.md occurrences as checkboxes (logged to the history file).
 *  3. Scratchpad: the note's `# Scratchpad`, display only.
 *
 * Everything is drawn flush left with the same 24dp marker; indentation only cycles the marker
 * color. Only checkboxes respond to taps.
 */
object PaneListRenderer {

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private const val VIEW_TYPES = 3

    private val levelColors = intArrayOf(
        R.color.widget_level_0,
        R.color.widget_level_1,
        R.color.widget_level_2,
        R.color.widget_level_3
    )

    /** Returns false when there's nothing to show (caller falls back to the blank text_agenda). */
    fun bindDay(context: Context, views: RemoteViews, appWidgetId: Int, date: LocalDate, settings: WidgetSettings): Boolean {
        val vault = VaultFiles(settings.agendaVaultPath)
        val readable = VaultFiles.hasStorageAccess()
        val note = if (readable) vault.noteView(date) else NoteView.EMPTY
        val recurring = if (readable) recurring(vault, date) else emptyList()

        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setViewTypeCount(VIEW_TYPES)
            .setHasStableIds(false)
        var id = 0L
        note.agenda.forEach { line ->
            val task = line.task
            builder.addItem(
                id++,
                if (task == null) previewRow(context, line)
                else taskRow(context, TaskRef.Note(date, task.rawLine, task.occurrence), line.text, task.checked, line.level)
            )
        }
        if (recurring.isNotEmpty()) {
            builder.addItem(id++, label(context, context.getString(R.string.tasks_recurring)))
            recurring.forEach { (ref, done) ->
                val text = InlineMarkdown.parse("${ref.occurrence.format(timeFormat)} ${ref.text}")
                builder.addItem(id++, taskRow(context, ref, text, done, 0))
            }
        }
        if (note.scratchpad.isNotEmpty()) {
            builder.addItem(id++, label(context, context.getString(R.string.preview_scratchpad)))
            note.scratchpad.forEach { builder.addItem(id++, previewRow(context, it)) }
        }

        if (id == 0L) {
            views.setViewVisibility(R.id.list_agenda, View.GONE)
            return false
        }
        views.setViewVisibility(R.id.list_agenda, View.VISIBLE)
        views.setRemoteAdapter(R.id.list_agenda, builder.build())
        views.setPendingIntentTemplate(R.id.list_agenda, template(context, appWidgetId))
        return true
    }

    /**
     * Upcoming mode: every open timed task in daily notes from [today] on, soonest first,
     * under a label per day. Cron entries are left out. Returns false when there are none.
     */
    fun bindUpcoming(context: Context, views: RemoteViews, appWidgetId: Int, today: LocalDate, settings: WidgetSettings): Boolean {
        val tasks = if (VaultFiles.hasStorageAccess()) VaultFiles(settings.agendaVaultPath).upcomingTasks(today) else emptyList()
        if (tasks.isEmpty()) {
            views.setViewVisibility(R.id.list_agenda, View.GONE)
            return false
        }
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setViewTypeCount(VIEW_TYPES)
            .setHasStableIds(false)
        var id = 0L
        tasks.groupBy({ it.first }, { it.second }).forEach { (date, dayTasks) ->
            builder.addItem(id++, label(context, dayLabel(context, date, today)))
            dayTasks.forEach { task ->
                builder.addItem(
                    id++,
                    taskRow(
                        context,
                        TaskRef.Note(date, task.rawLine, task.occurrence),
                        InlineMarkdown.parse(task.displayText),
                        task.checked,
                        task.indentLevel
                    )
                )
            }
        }
        views.setViewVisibility(R.id.list_agenda, View.VISIBLE)
        views.setRemoteAdapter(R.id.list_agenda, builder.build())
        views.setPendingIntentTemplate(R.id.list_agenda, template(context, appWidgetId))
        return true
    }

    private fun dayLabel(context: Context, date: LocalDate, today: LocalDate): String = when (date) {
        today -> context.getString(R.string.day_today)
        today.plusDays(1) -> context.getString(R.string.day_tomorrow)
        else -> date.format(
            DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", java.util.Locale.getDefault())
        )
    }

    /** That day's cron occurrences, in time order, with whether each is logged as done. */
    private fun recurring(vault: VaultFiles, date: LocalDate): List<Pair<TaskRef.Cron, Boolean>> {
        val done = vault.cronHistory().filter { it.outcome == CronOutcome.DONE }.mapTo(HashSet()) { it.occurrence }
        val zone = ZoneId.systemDefault()
        return vault.cronFile().entries.flatMap { entry ->
            entry.expression.occurrences(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), zone).map { at ->
                TaskRef.Cron(at, entry.section, entry.text) to (CronOccurrence(at, entry.section, entry.text) in done)
            }
        }.sortedBy { it.first.occurrence }
    }

    private fun taskRow(context: Context, ref: TaskRef, text: StyledText, checked: Boolean, level: Int) =
        RemoteViews(context.packageName, R.layout.widget_task_row).apply {
            setTextViewText(R.id.task_text, StyledTextSpannable.from(text))
            setCompoundButtonChecked(R.id.task_check, checked)
            setColorStateList(R.id.task_check, "setButtonTintList", ColorStateList.valueOf(levelColor(context, level)))
            setOnCheckedChangeResponse(
                R.id.task_check,
                RemoteViews.RemoteResponse.fromFillInIntent(TaskRefExtras.put(Intent(), ref))
            )
        }

    private fun previewRow(context: Context, line: PreviewLine) =
        RemoteViews(context.packageName, R.layout.widget_preview_row).apply {
            val text = StyledTextSpannable.from(line.text)
            setTextViewText(R.id.preview_text, if (line.kind == PreviewKind.HEADING) bold(text) else text)
            val marker = when (line.marker) {
                PreviewMarker.UNCHECKED -> R.drawable.ic_box_unchecked
                PreviewMarker.CHECKED -> R.drawable.ic_box_checked
                PreviewMarker.BULLET -> R.drawable.ic_bullet
                PreviewMarker.NONE -> 0
            }
            // Rows are recycled across scrolls and redraws, so every property must be set on
            // every bind - a GONE left over from a plain-text line would otherwise stick.
            if (marker == 0) {
                setViewVisibility(R.id.preview_marker, View.GONE)
            } else {
                setViewVisibility(R.id.preview_marker, View.VISIBLE)
                setImageViewResource(R.id.preview_marker, marker)
                setInt(R.id.preview_marker, "setColorFilter", levelColor(context, line.level))
            }
        }

    private fun label(context: Context, text: String) =
        RemoteViews(context.packageName, R.layout.widget_task_label).apply {
            setTextViewText(R.id.task_label, text)
        }

    private fun levelColor(context: Context, level: Int): Int = context.getColor(levelColors[level % levelColors.size])

    private fun bold(text: CharSequence): CharSequence = SpannableString(text).apply {
        setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }

    /** Mutable: the system fills in each checkbox's TaskRef extras plus RemoteViews.EXTRA_CHECKED. */
    private fun template(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActions.ACTION_TASK_TOGGLE
            data = WidgetActions.taskToggleUri(appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
