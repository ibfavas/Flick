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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val dao = DatabaseProvider.get(this).gestureDao()
            val settings = SettingsStore(this)
            setContent {
                FlickTheme {
                    MainScreen(dao = dao, settings = settings)
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
