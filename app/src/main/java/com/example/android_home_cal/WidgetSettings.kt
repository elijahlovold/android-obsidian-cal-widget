package com.example.android_home_cal

data class WidgetSettings(
    val vaultPath: String = Defaults.VAULT_PATH,
    val todaysNotesCommand: String = Defaults.TODAYS_NOTES_COMMAND,
    val nvimPath: String = Defaults.NVIM_PATH,
    val shellPath: String = Defaults.SHELL_PATH,
    val agendaVaultPath: String = Defaults.AGENDA_VAULT_PATH
) {
    object Defaults {
        const val VAULT_PATH = "/data/data/com.termux/files/home/vault"
        const val TODAYS_NOTES_COMMAND = "todays-notes"
        const val NVIM_PATH = "/data/data/com.termux/files/usr/bin/nvim"
        const val SHELL_PATH = "/data/data/com.termux/files/usr/bin/zsh"

        // Where the vault is actually visible on shared storage, for direct file reads by
        // this app (distinct from vaultPath, which is Termux's own internal view of the same
        // vault - Termux's RUN_COMMAND execution context isn't available for a lightweight
        // preview read, and Termux's private app storage isn't readable by other apps anyway).
        const val AGENDA_VAULT_PATH = "/storage/emulated/0/Documents/Vault"
    }
}
