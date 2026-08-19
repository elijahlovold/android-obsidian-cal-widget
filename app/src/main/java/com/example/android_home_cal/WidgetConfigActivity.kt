package com.example.android_home_cal

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.android_home_cal.ui.theme.AndroidhomecalTheme

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val hasStorageAccess = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val preferences = WidgetPreferences(this)
        val initialSettings = preferences.loadSettings(appWidgetId)

        setContent {
            AndroidhomecalTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigScreen(
                        initialSettings = initialSettings,
                        hasStorageAccess = hasStorageAccess.value,
                        onGrantStorageAccess = { openStorageAccessSettings() },
                        onSave = { settings -> saveAndFinish(preferences, settings) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasStorageAccess.value = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    private fun openStorageAccessSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun saveAndFinish(preferences: WidgetPreferences, settings: WidgetSettings) {
        preferences.saveSettings(appWidgetId, settings)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        CalendarWidgetRenderer.updateWidget(this, appWidgetManager, appWidgetId)

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}

@Composable
private fun ConfigScreen(
    initialSettings: WidgetSettings,
    hasStorageAccess: Boolean,
    onGrantStorageAccess: () -> Unit,
    onSave: (WidgetSettings) -> Unit
) {
    var vaultPath by remember { mutableStateOf(initialSettings.vaultPath) }
    var todaysNotesCommand by remember { mutableStateOf(initialSettings.todaysNotesCommand) }
    var nvimPath by remember { mutableStateOf(initialSettings.nvimPath) }
    var shellPath by remember { mutableStateOf(initialSettings.shellPath) }
    var agendaVaultPath by remember { mutableStateOf(initialSettings.agendaVaultPath) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = stringResource(R.string.config_title), style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = vaultPath,
                onValueChange = { vaultPath = it },
                label = { Text(stringResource(R.string.config_label_vault_path)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = todaysNotesCommand,
                onValueChange = { todaysNotesCommand = it },
                label = { Text(stringResource(R.string.config_label_todays_notes_command)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = nvimPath,
                onValueChange = { nvimPath = it },
                label = { Text(stringResource(R.string.config_label_nvim_path)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = shellPath,
                onValueChange = { shellPath = it },
                label = { Text(stringResource(R.string.config_label_shell_path)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = agendaVaultPath,
                onValueChange = { agendaVaultPath = it },
                label = { Text(stringResource(R.string.config_label_agenda_vault_path)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (!hasStorageAccess) {
                OutlinedButton(onClick = onGrantStorageAccess, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.config_grant_storage_access))
                }
            }

            Button(
                onClick = {
                    onSave(
                        WidgetSettings(
                            vaultPath = vaultPath,
                            todaysNotesCommand = todaysNotesCommand,
                            nvimPath = nvimPath,
                            shellPath = shellPath,
                            agendaVaultPath = agendaVaultPath
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.config_save))
            }
        }
    }
}
