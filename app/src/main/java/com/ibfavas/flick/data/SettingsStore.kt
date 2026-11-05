package com.ibfavas.flick.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flick_settings")

/** App settings stored in DataStore Preferences. */
class SettingsStore(private val context: Context) {

    companion object {
        private val STROKE_COLOR = longPreferencesKey("stroke_color")
        private val STROKE_WIDTH = floatPreferencesKey("stroke_width")
        const val DEFAULT_COLOR: Long = 0xFF00E5FF // brand cyan
        const val DEFAULT_WIDTH: Float = 10f
    }

    val strokeColor: Flow<Long> = context.dataStore.data.map { it[STROKE_COLOR] ?: DEFAULT_COLOR }
    val strokeWidth: Flow<Float> = context.dataStore.data.map { it[STROKE_WIDTH] ?: DEFAULT_WIDTH }

    suspend fun setStrokeColor(color: Long) {
        context.dataStore.edit { it[STROKE_COLOR] = color }
    }

    suspend fun setStrokeWidth(width: Float) {
        context.dataStore.edit { it[STROKE_WIDTH] = width }
    }
}

