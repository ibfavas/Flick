package com.ibfavas.flick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ibfavas.flick.data.DatabaseProvider
import com.ibfavas.flick.data.SettingsStore
import com.ibfavas.flick.ui.FlickTheme
import com.ibfavas.flick.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = DatabaseProvider.get(this).gestureDao()
        val settings = SettingsStore(this)
        setContent {
            FlickTheme {
                MainScreen(dao = dao, settings = settings)
            }
        }
    }
}
