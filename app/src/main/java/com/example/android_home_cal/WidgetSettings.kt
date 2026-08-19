package com.example.android_home_cal

data class WidgetSettings(
    val vaultPath: String = Defaults.VAULT_PATH,
    val todaysNotesCommand: String = Defaults.TODAYS_NOTES_COMMAND,
    val nvimPath: String = Defaults.NVIM_PATH,
    val shellPath: String = Defaults.SHELL_PATH
) {
    object Defaults {
        const val VAULT_PATH = "/data/data/com.termux/files/home/vault"
        const val TODAYS_NOTES_COMMAND = "todays-notes"
        const val NVIM_PATH = "/data/data/com.termux/files/usr/bin/nvim"
        const val SHELL_PATH = "/data/data/com.termux/files/usr/bin/zsh"
    }
}
