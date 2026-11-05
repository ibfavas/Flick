package com.ibfavas.flick

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        // Edge-to-edge (v1.0.10): the app draws behind the system bars
        // with transparent, dark-icon bars, and the screens pad
        // themselves below the status bar (statusBarsPadding) instead
        // of the old fitsSystemWindows behaviour, so no title or
        // button ever sits under the notification bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
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
