package com.ibfavas.flick

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ibfavas.flick.data.DatabaseProvider
import com.ibfavas.flick.data.SettingsStore
import com.ibfavas.flick.ui.FlickTheme
import com.ibfavas.flick.ui.MainScreen

class MainActivity : ComponentActivity() {
    companion object {
        /** Launch extra: open the settings screen first, used by the
         *  transparent pad's gear button. */
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val dao = DatabaseProvider.get(this).gestureDao()
            val settings = SettingsStore(this)
            val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
            setContent {
                FlickTheme {
                    MainScreen(dao = dao, settings = settings, openSettings = openSettings)
                }
            }
        } catch (e: Throwable) {
            // Diagnostic safety net (v1.0.2): log, then rethrow so the
            // global handler in FlickApp captures and displays the trace.
            Log.e("Flick", "MainActivity onCreate failed", e)
            e.printStackTrace()
            throw e
        }
    }
}
