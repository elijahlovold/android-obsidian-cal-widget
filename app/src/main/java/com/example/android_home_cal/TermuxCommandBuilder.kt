package com.example.android_home_cal

import java.time.LocalDate

/**
 * Session action values from com.termux.shared.termux.TermuxConstants.TERMUX_SERVICE.
 * On-device testing showed KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY (1) is unreliable when
 * there is no pre-existing session to keep - the normal case here, since Termux usually
 * isn't already running when the widget is tapped from the home screen. It would bring
 * Termux to the foreground but silently fail to actually run the command (nvim opened
 * with no file). SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY (0) is Termux's own documented
 * default and always spawns a fresh session, trading "reuse an existing session" for
 * reliability.
 */
const val TERMUX_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY = 0

data class TermuxCommandSpec(
    val executablePath: String,
    val arguments: List<String>,
    val workingDirectory: String,
    val sessionAction: Int
)

// PATH is exported explicitly rather than relying on the shell's own login-file sourcing
// (~/.zprofile etc.), since on-device testing showed that behaves inconsistently between
// an interactive Termux session and one spawned by RunCommandService.
private const val TERMUX_HOME = "/data/data/com.termux/files/home"
private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"

// RunCommandService does not set LD_PRELOAD, unlike Termux's own interactive sessions.
// Without it, termux-exec's execve() hook is inactive, so any script with the standard
// #!/usr/bin/env shebang (there is no real /usr/bin/env on Android) fails outright with
// "bad interpreter: /usr/bin/env: no such file or directory" - confirmed on-device via a
// direct RunCommandService call with `env` dumped to a file. LD_PRELOAD only takes effect
// for a process at its own exec time, and setting it with `export` mid-script doesn't
// retroactively hook the already-running shell, so the shell has to re-exec itself with
// LD_PRELOAD already in its environment for termux-exec to activate.
private const val TERMUX_EXEC_LD_PRELOAD = "$TERMUX_PREFIX/lib/libtermux-exec-ld-preload.so"

object TermuxCommandBuilder {

    fun buildOpenNoteCommand(date: LocalDate, settings: WidgetSettings): TermuxCommandSpec {
        val path = listOf(
            "$TERMUX_HOME/bin",
            "$TERMUX_HOME/.local/bin",
            "$TERMUX_HOME/.config/scripts",
            "$TERMUX_HOME/.cargo/bin",
            "$TERMUX_PREFIX/bin"
        ).joinToString(":")

        val innerScript = "exec ${shellQuote(settings.nvimPath)} " +
            "\"\$(${shellQuote(settings.todaysNotesCommand)} ${shellQuote(date.toString())})\""

        val script = "export HOME=${shellQuote(TERMUX_HOME)}; " +
            "export PATH=${shellQuote(path)}; " +
            "export LD_PRELOAD=${shellQuote(TERMUX_EXEC_LD_PRELOAD)}; " +
            "exec ${shellQuote(settings.shellPath)} -c ${shellQuote(innerScript)}"

        return TermuxCommandSpec(
            executablePath = settings.shellPath,
            arguments = listOf("-c", script),
            workingDirectory = settings.vaultPath,
            sessionAction = TERMUX_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY
        )
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
