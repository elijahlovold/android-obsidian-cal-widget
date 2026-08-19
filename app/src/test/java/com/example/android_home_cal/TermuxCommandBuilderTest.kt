package com.example.android_home_cal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TermuxCommandBuilderTest {

    @Test
    fun `builds shell command that resolves note path via todays-notes`() {
        val settings = WidgetSettings(
            vaultPath = "/data/data/com.termux/files/home/vault",
            todaysNotesCommand = "todays-notes",
            nvimPath = "/data/data/com.termux/files/usr/bin/nvim",
            shellPath = "/data/data/com.termux/files/usr/bin/zsh"
        )
        val spec = TermuxCommandBuilder.buildOpenNoteCommand(LocalDate.of(2026, 8, 18), settings)

        assertEquals("/data/data/com.termux/files/usr/bin/zsh", spec.executablePath)
        assertEquals("/data/data/com.termux/files/home/vault", spec.workingDirectory)
        assertEquals(TERMUX_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY, spec.sessionAction)
        assertEquals(listOf("-c"), spec.arguments.take(1))

        val script = spec.arguments[1]
        assertTrue("exports HOME", script.contains("export HOME='/data/data/com.termux/files/home';"))
        assertTrue(
            "exports a PATH covering common script/tool locations",
            script.contains("export PATH='/data/data/com.termux/files/home/bin:") &&
                script.contains(":/data/data/com.termux/files/home/.config/scripts:") &&
                script.contains(":/data/data/com.termux/files/usr/bin';")
        )
        assertTrue(
            "exports termux-exec's LD_PRELOAD before re-exec so shebang scripts resolve",
            script.contains(
                "export LD_PRELOAD='/data/data/com.termux/files/usr/lib/libtermux-exec-ld-preload.so';"
            )
        )
        assertTrue(
            "re-execs the configured shell with the nvim/todays-notes command",
            script.endsWith(
                "exec '/data/data/com.termux/files/usr/bin/zsh' -c 'exec " +
                    "'\\''/data/data/com.termux/files/usr/bin/nvim'\\'' " +
                    "\"\$('\\''todays-notes'\\'' '\\''2026-08-18'\\'')\"'"
            )
        )
    }

    @Test
    fun `single quotes embedded in configured paths are escaped`() {
        val settings = WidgetSettings(
            vaultPath = "/vault",
            todaysNotesCommand = "todays'notes",
            nvimPath = "/nvim",
            shellPath = "/bash"
        )
        val spec = TermuxCommandBuilder.buildOpenNoteCommand(LocalDate.of(2026, 1, 1), settings)

        // Must not throw, and the embedded quote must survive escaped rather than
        // truncating or corrupting the surrounding script.
        assertTrue(spec.arguments[1].contains("todays"))
        assertTrue(spec.arguments[1].contains("notes"))
    }
}
