package com.example.android_home_cal

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.android_home_cal.reminders.ReminderNotifier
import com.example.android_home_cal.ui.theme.AndroidhomecalTheme
import com.example.android_home_cal.vault.VaultFiles
import com.example.android_home_cal.widget.CalendarWidgetRenderer
import com.example.android_home_cal.widget.WidgetPreferences
import com.example.android_home_cal.widget.WidgetSettings

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val hasStorageAccess = mutableStateOf(false)
    private val reminderAccess = mutableStateOf(ReminderAccess())
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshReminderAccess() }

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
                        reminderAccess = reminderAccess.value,
                        onGrantNotifications = { requestNotificationPermission() },
                        onGrantExactAlarms = { openExactAlarmSettings() },
                        onBatteryOptimization = { openBatteryOptimizationSettings() },
                        onSave = { settings -> saveAndFinish(preferences, settings) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasStorageAccess.value = VaultFiles.hasStorageAccess()
        refreshReminderAccess()
    }

    private fun refreshReminderAccess() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val powerManager = getSystemService(PowerManager::class.java)
        reminderAccess.value = ReminderAccess(
            notifications = ReminderNotifier.canPost(this),
            exactAlarms = alarmManager.canScheduleExactAlarms(),
            batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(packageName)
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openExactAlarmSettings() {
        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
    }

    @SuppressLint("BatteryLife") // Sideloaded personal reminder app; not subject to Play policy.
    private fun openBatteryOptimizationSettings() {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
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
        ReminderReceiver.requestScan(this)

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}

data class ReminderAccess(
    val notifications: Boolean = true,
    val exactAlarms: Boolean = true,
    val batteryUnrestricted: Boolean = true
)

@Composable
private fun ConfigScreen(
    initialSettings: WidgetSettings,
    hasStorageAccess: Boolean,
    onGrantStorageAccess: () -> Unit,
    reminderAccess: ReminderAccess,
    onGrantNotifications: () -> Unit,
    onGrantExactAlarms: () -> Unit,
    onBatteryOptimization: () -> Unit,
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

            if (!reminderAccess.notifications || !reminderAccess.exactAlarms || !reminderAccess.batteryUnrestricted) {
                Text(stringResource(R.string.config_reminders_title), style = MaterialTheme.typography.titleMedium)
            }
            if (!reminderAccess.notifications) {
                OutlinedButton(onClick = onGrantNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.config_grant_notifications))
                }
            }
            if (!reminderAccess.exactAlarms) {
                OutlinedButton(onClick = onGrantExactAlarms, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.config_grant_exact_alarms))
                }
            }
            if (!reminderAccess.batteryUnrestricted) {
                OutlinedButton(onClick = onBatteryOptimization, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.config_battery_optimization))
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
