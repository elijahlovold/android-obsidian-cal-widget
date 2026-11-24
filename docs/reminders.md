# Reminders: design and implementation plan

Status: implemented (see §9 for the code layout). Not yet tested on a device.

The app sends push notifications for (a) timed todo items in daily notes and (b) recurring
entries in a cron file. Each notification can be marked **Done**, **Snoozed**, or **Dismissed**.
The widget also gets a checkbox task view for the selected day.

---

## 1. Daily-note reminder format

Only **unchecked checkbox items in the `# Agenda` section** can carry reminders. Plain bullets
(`- 10:00 standup`) and checked items (`- [x] ...`) never do. That keeps agenda event lines
silent and makes reminders opt-in by writing `[ ]`.

### Grammar

```
- [ ] <time> [<lead> ...] <text>

time  := H:MM | HH:MM | H[:MM]am | H[:MM]pm        (24h or 12h, case-insensitive)
lead  := -<N>m                  N minutes before <time>
       | -<N>h                  N hours before <time>
       | -<N>d                  N days before, same clock time
       | -<N>d<time>            N days before, at that clock time
```

- The date is the note's date (`Calendar/<year>/<yyyy-MM-dd>.md`).
- A reminder always fires **at `<time>`**. Each `<lead>` adds one earlier reminder.
- Leads must come straight after the time. The first token that isn't a lead starts `<text>`.
- `-0d<time>` means "the same day at that time", e.g. a morning-of nudge.
- Day leads have no cap (`-14d9am` works), since every future note is scanned (§4).
- The `*`/`+` bullet characters and indented (nested) items work too.

### Examples

```markdown
# Agenda
- [ ] 14:30 dentist                    → 14:30
- [ ] 14:30 -30m dentist               → 14:00, 14:30
- [ ] 9am -1h -10m flight              → 08:00, 08:50, 09:00
- [ ] 14:30 -1d dentist                → yesterday 14:30, 14:30
- [ ] 14:30 -1d8pm -0d8am -15m dentist → yesterday 20:00, 08:00, 14:15, 14:30
- [ ] 7pm -2d9am -1d9am dinner w/ Sam  → 2 days before 09:00, day before 09:00, 19:00
- [ ] buy milk                         → no reminder (no leading time)
- 10:00 standup                        → no reminder (not a checkbox)
- [x] 14:30 dentist                    → no reminder (already done)
```

The notification and the task list show `14:30 dentist`. Lead tokens are hidden.

## 2. `Calendar/Cal-Cron.md`

```markdown
# Daily
- 0 9 * * 1-5 take meds
30 21 * * * plan tomorrow

# Birthdays
0 9 14 3 * Mom's birthday
0 9 13 3 * Mom's birthday tomorrow - buy card

# Chores
0 10 1 * * pay rent
0 18 * * sun take bins out
```

- **Headings** (`#`, `##`, …) set the section for the lines below them. The section appears in
  the notification (`Birthdays · Mom's birthday`) and in the history log. Sections have no
  other effect.
- **Entry lines** are a standard 5-field cron expression (`min hour dom month dow`) followed by
  the text. A leading `- ` is optional, so the file still renders as a list.
- Supported field syntax: `*`, numbers, `a-b` ranges, `a,b` lists, `*/n` and `a-b/n` steps,
  month names (`jan`…) and weekday names (`sun`…, where 0 and 7 both mean Sunday).
  Day-of-month and day-of-week follow the Vixie rule: if both are restricted, a day matching
  **either** one fires.
- A line is treated as an entry only if, after the optional `- `, it starts with a digit or
  `*`. Other lines are free-form notes and are ignored. A candidate line that fails to parse
  raises one "Cal-Cron.md: line N invalid" notification for each version of the file (keyed on
  its mtime), so typos don't go unnoticed.
- No lead syntax: for an earlier reminder, add another line (see Birthdays above).
- Times are local. Across DST, a time that doesn't exist that day is skipped, and a time that
  occurs twice fires once.

## 3. `Calendar/Cal-Cron-History.md`

The log lives in its own file, so `Cal-Cron.md` stays small and fast to parse. The app only ever
appends to it, apart from the uncheck case below.

```markdown
- 2026-11-23 09:00 done    [Daily] take meds
- 2026-11-23 21:30 skipped [Daily] plan tomorrow
```

- The timestamp is the **scheduled** occurrence, not the moment you tapped. That makes each
  line a stable key for the occurrence.
- `done` comes from the notification's Done action or from ticking the item in the task list.
  `skipped` comes from Dismiss or swiping the notification away.
- Unticking a cron item in the task list removes that occurrence's `done` line. This is the
  only non-append edit.
- The file is created on first write. It's only read when the task list or a write-back needs
  it, and only the lines for the relevant date are matched, so its growth doesn't matter.

## 4. Scanning and scheduling

### Inputs scanned
- **Every daily note dated today or later.** Future notes are sparse, since a note only
  exists once something is planned for that day. They're found by listing
  `Calendar/<year>/` for the current and later years and parsing the dates from the
  `yyyy-MM-dd.md` file names. No file is opened just to learn its date.
- `Calendar/Cal-Cron.md`, for occurrences from today through today+3.
- `Cal-Cron-History.md` and the reminder store, to drop anything already done or handled.

Each file is cached by `(path, lastModified, length)`, so an unchanged file isn't re-parsed.
A scan normally costs a few directory listings and one `stat` per future note.

### Triggers
| Trigger | Mechanism |
|---|---|
| Every 10 min | `AlarmManager.set()` (inexact, **not** allow-while-idle), re-armed each time it fires. Doze may delay it, which is fine: an idle phone isn't editing notes, and the first poll after wake catches up. WorkManager can't be used because its minimum is 15 min. |
| Next reminder | One `setExactAndAllowWhileIdle` alarm for the earliest pending fire time. Re-armed after every scan. |
| Boot | `RECEIVE_BOOT_COMPLETED` → scan. |
| Clock/zone change | `TIME_SET`, `TIMEZONE_CHANGED` → scan. |
| Widget activity | Widget updates (placement, periodic refresh), checkbox ticks and the refresh button start a scan. Plain date taps don't. |
| Any write-back | Scan right after Done, Snooze, Dismiss or a task-list tick. |

The poll and the exact alarm are separate on purpose. Allow-while-idle alarms share a Doze
quota of roughly one per 9 minutes, so polling with them could push a real reminder back.

### Keeping the widget current
Each scan also fingerprints `(path, mtime, length)` of every file the widget can show: future
notes, each widget's displayed date, and the two cron files. When the fingerprint differs
from the last scan's, it redraws every widget. Edits
made outside the widget (sync, nvim) therefore show up within one poll, about 10 minutes,
and an unchanged vault costs only the `stat` calls. There's no filesystem watching: a
`FileObserver` only lives as long as the app's process, which is normally dead between
events.

### Planning (pure function)
`plan(tasks, cronEntries, now, store) → List<Reminder>` where each `Reminder` has a stable
**key** = hash(source file, normalized task text, scheduled fire instant).

- Skip any key already **fired**, **dismissed** or **done**.
- **Catch-up:** if fire times were missed (phone off, Doze), fire only the **latest** missed
  one for each task, and only if it falls today and the task is still unchecked. Never fire a
  stack of stale lead reminders.
- Snoozes are stored as `key → newFireInstant` and treated as a pending reminder.
- Prune store entries older than 4 days.

### Vault path
Reminders are app-wide, but settings are per widget. Use the `agendaVaultPath` of the first
configured widget. If no widgets remain (`onDisabled`), cancel both alarms.

## 5. Notifications

- Channel: "Reminders", high importance.
- Title: the task text (`14:30 dentist`). Body: relative time (`in 30 min`, `now`, `tomorrow
  14:30`) and, for cron entries, the section name.
- Actions: **Done**, **Snooze 10m**, **Dismiss**. Swiping the notification away also counts
  as Dismiss (`deleteIntent`). Tapping the body opens that day's note in nvim via the
  existing `TermuxLauncher`. For cron entries, the tap opens `Cal-Cron.md`.
- Notification ID = the reminder key's hash. A newer lead reminder for the same task replaces
  the earlier notification rather than piling up.

### Done / Dismiss semantics
| Source | Done | Dismiss |
|---|---|---|
| Daily note | Change `- [ ]` to `- [x]` on the matching line. All of the task's remaining lead reminders go away, because the task is now checked. | Record this one key as dismissed. Later lead reminders for the task still fire. |
| Cron | Append a `done` line to history | Append a `skipped` line to history |

## 6. Write-back to daily notes

`NoteEditor.setChecked(content, match, checked): Result` is a pure function, with a thin IO
wrapper around it.

- **Match by content, never by line number.** The intent carries the note date, the original
  raw line, and its occurrence index among identical lines (usually 0).
- Re-read the file, find the Nth line equal to the raw line, and flip only its `[ ]`/`[x]`.
  If the line is missing (edited since), change nothing and post "Couldn't find '<task>' in
  <date>.md - edited?".
- Write to `<file>.tmp` in the same directory, then `renameTo`. If the rename fails, fall back
  to a direct write.
- Afterwards the caller rescans. The rescan sees the changed file and redraws the widgets,
  so write-back itself never redraws.
- **Known conflicts:** if the note is open in nvim, nvim will warn on its next check (normal
  `autoread` behavior). If the note is edited on another device at the same moment, the sync
  tool may create a conflict copy. Both are rare and recoverable, so no locking.

## 7. Widget: agenda pane

**Day dots:** a day from today on whose note has at least one unchecked checkbox in
`# Agenda`, timed or not, gets a small dot under its number. Past days are never scanned.
The dates come from the same directory listing and cached parses as the reminder scan
(`VaultFiles.datesWithOpenTasks`). Every cell carries a bottom drawable, transparent when
there's no dot, so the numbers stay aligned.

A thin pill on the divider between the calendar and the pane switches between two modes. It
shows the current mode, **Day** or **Upcoming**, and the choice is saved per widget.

- **Upcoming:** every open (unchecked) timed task in daily notes from today on, i.e. every
  task that will send a notification, soonest first. Each day gets its own label (`Today`,
  `Tomorrow`, `Mon 30 Nov`), and the selected date doesn't matter. Cron entries are left out.
  Checkboxes tick the note the same way as in Day mode, and a ticked task drops off the list
  on the next redraw. An empty list says "No upcoming reminders".
- **Day** (default): described below.

A small refresh button at the far left of the divider (the pill stays centered) forces a full
rescan. It clears the parse cache, in
case a sync tool kept a file's old mtime, redraws every widget,
and replans reminders.

In Day mode, the pane is one fixed, scrollable list (`PaneListRenderer`, built with
`RemoteViews.RemoteCollectionItems`). It shows the selected date in three parts:

1. **Agenda:** the note's whole `# Agenda`, including sub-items under "work" lines.
   - Checkbox items are real `CheckBox`es: ticking one changes `[ ]`/`[x]` on that line of
     the note.
   - Other list items get a bullet. `##` sub-headings are bold, and runs of blank lines
     become one spacer.
   - A timed item shows without its lead tokens (`14:30 dentist`).
2. **Cron:** a "Cron" label, then that day's `Cal-Cron.md` occurrences as checkboxes. Ticking
   one adds or removes a `done` line in `Cal-Cron-History.md`. Omitted if there are none.
3. **Scratchpad:** a label, then the note's `# Scratchpad`, display only. Checkbox items there
   show a static box. Omitted if empty.

**Look:** every item is drawn flush left with the same 24dp marker (checkbox box or bullet).
Indentation doesn't shift an item; it picks the marker color, cycling through
`widget_level_0..3`. Real checkboxes use the same drawables (`checkbox_widget`), tinted with
`setButtonTintList`. Inline markdown is styled as before.

**Taps:** only checkboxes respond.
- A tick goes through the same `TaskActions` path as the notification actions, then triggers
  a rescan, so the task's notification clears.
- All checkboxes share one `FLAG_MUTABLE` template (`ACTION_TASK_TOGGLE`). Each row's fill-in
  carries its `TaskRef`, and the system adds `RemoteViews.EXTRA_CHECKED`.

**Data:** the list is built from the note file itself, via `NoteParser.parse` and the mtime
cache. One parse per note yields both its tasks (for reminders) and its display rows, and
each checkbox row knows its line's identity (raw line plus occurrence) for write-back. A mode
with nothing to show displays `text_pane_empty` instead of the list.

## 8. Month header tap (unrelated tweak)

Today `text_month_year` already sends `OpenToday`, which selects today and opens nvim but
never resets `displayedMonth`. Change the `OpenToday` handler in `WidgetActionReceiver` to:

- `saveDisplayedMonth(YearMonth.now())`
- select today the same way `select()` does (start the deselect-expiry)
- **not** call `TermuxLauncher.openNote`. Double-tapping today still opens the note.

Optionally rename the action to `GoToToday`, since it no longer opens anything.

## 9. Code layout

`minSdk` is 31 (Android 12): the pane relies on `RemoteCollectionItems` and `CheckBox` in
RemoteViews. Packages under `com.example.android_home_cal`:

| Package | Contents |
|---|---|
| (root) | Manifest components only, so their class names stay stable: `CalendarWidgetProvider`, `WidgetConfigActivity`, `WidgetActionReceiver`, `ReminderReceiver` and the exported `ReminderSystemReceiver`. The latter only ever requests a rescan, so other apps can't drive write-back. |
| `notes` | Pure. `ReminderLineParser` (§1 grammar → `NoteTask`), `NoteParser` (one pass → `ParsedNote`: tasks + display rows), `NoteEditor` (§6), `InlineMarkdown`, `NotePaths`. |
| `cron` | Pure. `CronExpression`, `CronFileParser`, `CronHistoryFormat` / `CronOccurrence`. |
| `reminders` | `ReminderPlanner`, `TaskRef` and `ReminderText` (pure), plus `ReminderScheduler` (scan → plan → notify → alarms → redraw on change), `ReminderNotifier`, `ReminderStore`, `TaskActions` (write-back, no redraw) and `TaskRefExtras`. |
| `vault` | `VaultFiles` (paths, future-note discovery, atomic writes) and `FileParseCache`. |
| `widget` | `CalendarWidgetRenderer`, `PaneListRenderer`, `Widgets` (placed instances, primary vault), `WidgetActions`, `WidgetPreferences`, `WidgetSettings`, `StyledTextSpannable`, `CalendarMath`. |
| `termux` | `TermuxCommandBuilder`, `TermuxLauncher`. |

Tests mirror the packages. The pure packages are tested directly. `vault` and
`TaskActions` are tested against real temp folders on the JVM, with
`unitTests.isReturnDefaultValues` set so the android.util.Log calls in that code don't throw.

Manifest additions: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` (or `USE_EXACT_ALARM`, which
is fine for a sideloaded app), `RECEIVE_BOOT_COMPLETED`, and the `ReminderReceiver` with
boot/time intent filters.

`WidgetConfigActivity` gets a "Reminders" section: request notification permission, open
exact-alarm settings when `canScheduleExactAlarms()` is false, and link to the
battery-optimization exemption screen. Some vendor ROMs kill alarms without the exemption.

## 10. Build order (completed)

1. **Month-tap fix** (§8). Small and independent.
2. **Parsers + tests:** `ReminderLineParser`, note task extraction, `CronExpression` (test
   DST, the dom/dow OR rule, names, steps, end-of-month), `CronFileParser`.
3. **Write-back + tests:** `NoteEditor` (duplicate lines, line not found, CRLF, keeping the
   trailing newline), `CronHistoryFormat`.
4. **Planner + tests:** catch-up rules, dedup, snooze, the 3-day window edge.
5. **Scheduler, notifications, receivers, permissions.** Test on the device: reboot, Doze
   (`adb shell dumpsys deviceidle force-idle`), editing a note from desktop sync, and Done
   while the note is open in nvim.
6. **Widget task view.**

## Open points

- Snooze length is fixed at 10 min. Add more choices later if needed.
- Should the task view also list *timed* agenda bullets that aren't checkboxes, read-only?
  Currently no.
