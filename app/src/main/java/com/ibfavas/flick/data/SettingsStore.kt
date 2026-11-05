package com.ibfavas.flick.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flick_settings")

/**
 * App settings stored in DataStore Preferences.
 *
 * v1.0.7: the stroke colour and width settings are gone. The stroke is
 * fixed brand cyan at a fixed width (see FlickBrand.StrokeColor and
 * FlickBrand.StrokeWidth in ui/Theme.kt) — there is nothing to configure.
 * What remains is the drawing overlay: how frosted it is, how long it
 * waits before closing on its own, and whether it closes after a failed
 * gesture. Old stroke keys, if present on a device, are simply ignored.
 */
class SettingsStore(private val context: Context) {

    companion object {
        private val OVERLAY_BLUR = intPreferencesKey("overlay_blur_px")
        private val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
        private val OVERLAY_TIMEOUT = intPreferencesKey("overlay_timeout_s")
        private val AUTO_CLOSE_ON_FAIL = booleanPreferencesKey("auto_close_on_fail")

        /** Background blur behind the pad, in px (Android 12 and up). */
        const val DEFAULT_BLUR_PX: Int = 25
        const val MAX_BLUR_PX: Int = 100

        /** Frosted teal tint over the blurred background, 0..1. */
        const val DEFAULT_OPACITY: Float = 0.45f

        /**
         * Seconds the pad waits with no gesture before closing on its
         * own. 0 means never. Once the user starts drawing, multi-stroke
         * keeps the pad open until the gesture is done.
         */
        const val DEFAULT_TIMEOUT_S: Int = 7
        const val MAX_TIMEOUT_S: Int = 30

        /** Close the pad after a gesture that matched nothing. */
        const val DEFAULT_AUTO_CLOSE_ON_FAIL: Boolean = true
    }

    val overlayBlur: Flow<Int> = context.dataStore.data.map { it[OVERLAY_BLUR] ?: DEFAULT_BLUR_PX }
    val overlayOpacity: Flow<Float> = context.dataStore.data.map { it[OVERLAY_OPACITY] ?: DEFAULT_OPACITY }
    val overlayTimeout: Flow<Int> = context.dataStore.data.map { it[OVERLAY_TIMEOUT] ?: DEFAULT_TIMEOUT_S }
    val autoCloseOnFail: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CLOSE_ON_FAIL] ?: DEFAULT_AUTO_CLOSE_ON_FAIL }

    suspend fun setOverlayBlur(px: Int) {
        context.dataStore.edit { it[OVERLAY_BLUR] = px.coerceIn(0, MAX_BLUR_PX) }
    }

    suspend fun setOverlayOpacity(opacity: Float) {
        context.dataStore.edit { it[OVERLAY_OPACITY] = opacity.coerceIn(0f, 1f) }
    }

    suspend fun setOverlayTimeout(seconds: Int) {
        context.dataStore.edit { it[OVERLAY_TIMEOUT] = seconds.coerceIn(0, MAX_TIMEOUT_S) }
    }

    suspend fun setAutoCloseOnFail(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_CLOSE_ON_FAIL] = enabled }
    }
}
