package com.example.android_home_cal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import java.time.LocalDate

object TermuxLauncher {

    private const val TAG = "TermuxLauncher"

    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    fun openNote(context: Context, date: LocalDate, settings: WidgetSettings) {
        if (!isTermuxInstalled(context)) {
            Log.e(TAG, "Termux is not installed; cannot open note for $date")
            Toast.makeText(context, R.string.error_termux_not_installed, Toast.LENGTH_LONG).show()
            return
        }

        val spec = TermuxCommandBuilder.buildOpenNoteCommand(date, settings)
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND_PATH, spec.executablePath)
            putExtra(EXTRA_ARGUMENTS, spec.arguments.toTypedArray())
            putExtra(EXTRA_WORKDIR, spec.workingDirectory)
            putExtra(EXTRA_BACKGROUND, false)
            putExtra(EXTRA_SESSION_ACTION, spec.sessionAction.toString())
        }

        Log.d(
            TAG,
            "openNote date=$date executable=${spec.executablePath} workdir=${spec.workingDirectory} " +
                "sessionAction=${spec.sessionAction} args=${spec.arguments}"
        )

        try {
            context.startForegroundService(intent)
            Log.d(TAG, "startForegroundService returned normally for date=$date")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to run Termux command", e)
            Toast.makeText(context, R.string.error_termux_permission, Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Termux RunCommandService not found", e)
            Toast.makeText(context, R.string.error_termux_not_installed, Toast.LENGTH_LONG).show()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Unable to start Termux foreground service", e)
            Toast.makeText(context, R.string.error_termux_launch_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun isTermuxInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
