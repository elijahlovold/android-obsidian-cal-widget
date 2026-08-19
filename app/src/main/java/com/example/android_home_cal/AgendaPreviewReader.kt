package com.example.android_home_cal

import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException
import java.time.LocalDate

object AgendaPreviewReader {

    private const val TAG = "AgendaPreviewReader"

    fun read(date: LocalDate, settings: WidgetSettings): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Log.e(TAG, "All-files storage access not granted; cannot read agenda preview for $date")
            return ""
        }

        val file = File(AgendaPreviewExtractor.notePath(settings.agendaVaultPath, date))
        if (!file.isFile) return ""

        return try {
            AgendaPreviewExtractor.extractAgendaSection(file.readText())
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read note for agenda preview: ${file.path}", e)
            ""
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied reading note for agenda preview: ${file.path}", e)
            ""
        }
    }
}
