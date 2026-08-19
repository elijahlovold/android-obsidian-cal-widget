# Daily Notes Calendar

A small Android home-screen widget that shows a monthly calendar. Tap a date
to select it, double-tap to open that day's note in `nvim` inside Termux.

## How it opens a note

The widget does not construct the note path itself. Instead it runs, inside
Termux, the equivalent of:

```sh
cd <vault path>
nvim "$(todays-notes <selected-date>)"
```

via `zsh -c '...'` by default, with `HOME` and `PATH` **exported explicitly**
at the top of that script rather than relying on zsh's own login-shell
startup-file sourcing (`.zprofile`/`.zshrc`). On-device testing showed
Termux's `RunCommandService` does not set `$HOME` the way a normal
interactive Termux session does, which silently broke both zsh's own
`~/.zprofile` lookup (login-file resolution happens at shell startup, before
any `-c` script line can fix it) and `todays-notes` itself, since it's a
Python script that calls `Path.home()`. The exported `PATH` covers the
common script/tool locations a `~/.zprofile` typically would (`~/bin`,
`~/.local/bin`, `~/.config/scripts`, `~/.cargo/bin`, plus Termux's own
`usr/bin`) so `todays-notes` and other user tools resolve the same way they
would interactively, without depending on shell startup-file semantics that
turned out to behave differently under plugin-triggered execution.
`todays-notes` is expected to accept an absolute `YYYY-MM-DD` date and print
the note path to resolve (create it yourself in `~/vault` or wherever your
dotfiles keep it).

The Termux command is sent with `RUN_COMMAND_SESSION_ACTION=0`
(`SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY`), Termux's own documented
default. `KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY` (`1`) was tried first to
reuse a running session, but on-device testing showed it's unreliable when
there's no pre-existing session to keep (the normal case, since Termux
usually isn't already running when the widget is tapped) - it foregrounds
Termux but silently fails to run the command. Every tap opens a fresh
session as a result.

## Build

Open the project in Android Studio, sync Gradle, and build, or from the
command line:

```sh
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## Install

```sh
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Termux setup

Use the **F-Droid or GitHub release build of Termux**, not the Google Play
Store build — Play Store policy forced Termux to strip the RUN_COMMAND
plugin API entirely (no `RunCommandService`, no `RUN_COMMAND` permission),
so the widget cannot work with it at all.

```sh
mkdir -p ~/.termux
printf '\nallow-external-apps=true\n' >> ~/.termux/termux.properties
termux-reload-settings
```

Make sure `todays-notes` is on your `$PATH` in an interactive Termux login
shell (e.g. in `~/bin` or `~/.local/bin`, exported from `.zshrc`/`.zprofile`),
and that `nvim` is installed at the path configured in the widget (default:
`/data/data/com.termux/files/usr/bin/nvim`).

Two Android-side settings also have to be granted manually, and neither can
be done from the app's manifest alone:

- **RUN_COMMAND permission**: it's a `dangerous`-protection-level runtime
  permission, so declaring `<uses-permission>` isn't enough — Android
  Settings → Apps → Daily Notes Calendar → Permissions must show it granted.
  There's no in-app prompt for this yet (see Known limitations below); grant
  it manually, or via `adb shell pm grant com.example.android_home_cal
  com.termux.permission.RUN_COMMAND`.
- **Termux notifications**: Android Settings → Apps → Termux →
  Notifications must be **on**. If Termux can't post its foreground-service
  notification, it can't reliably promote itself and bring its terminal
  activity to the front, so it'll run the command but stay in the
  background.

## Usage

1. Long press the home screen and select Widgets.
2. Find Daily Notes Calendar and add it.
3. On first add, set your vault path, `todays-notes` command, nvim path,
   and shell path.
4. Tap a date to select it (it gets a subtle highlight).
5. Double-tap a date to open it in Termux/nvim.
6. Tap the month/year header to jump straight to today's note.
7. Use `‹` / `›` to change the displayed month; each widget instance
   remembers its own month independently.

## Notes

- No network access, no background services beyond the widget's own
  periodic redraw (used to keep "today" accurate across midnight).
- If Termux isn't installed, or denies the `RUN_COMMAND` permission, the
  widget shows a toast and logs the error instead of crashing. Samsung/One
  UI sometimes suppresses toasts from background-triggered broadcasts, so
  absence of a toast doesn't guarantee success — check Logcat
  (`TermuxLauncher`/`WidgetActionReceiver` tags) if in doubt.
- **Known limitation**: the app doesn't yet request the `RUN_COMMAND`
  runtime permission itself (see Termux setup above for the manual grant).
  A clean fix would request it from `WidgetConfigActivity` on first setup.
- The widget tracks a `selectedDate` per instance, which is the natural
  extension point for a future single-tap note preview (e.g. via a
  background Termux command with a result `PendingIntent`) without
  changing the double-tap-to-open behavior.
