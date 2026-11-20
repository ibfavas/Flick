package com.ibfavas.flick.data

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupManager
import android.app.backup.FullBackupDataOutput
import android.content.Context

class GestureBackupAgent : BackupAgentHelper() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        if (enabled(this)) super.onFullBackup(data)
    }

    companion object {
        private fun preferences(context: Context) =
            context.getSharedPreferences("gesture_backup", Context.MODE_PRIVATE)

        fun enabled(context: Context) = preferences(context).getBoolean("enabled", true)

        fun setEnabled(context: Context, enabled: Boolean) {
            preferences(context).edit().putBoolean("enabled", enabled).apply()
            BackupManager(context).dataChanged()
        }
    }
}
