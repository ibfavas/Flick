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
 * v1.0.11: the stroke is configurable again (v1.0.7 removed these
 * settings; the user changed his mind and asked for them back). The
 * stroke width is a slider and the colour is one of a small set of
 * presets, cyan by default. "Auto close on fail" is gone: on the
 * overlay pad a gesture that matches nothing now always closes the
 * pad at once, with no message and no setting for it.
 */
class SettingsStore(private val context: Context) {

    companion object {
        private val OVERLAY_BLUR = intPreferencesKey("overlay_blur_px")
        private val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
        private val OVERLAY_TIMEOUT = intPreferencesKey("overlay_timeout_s")
        private val VIBRATE_ON_RUN = booleanPreferencesKey("vibrate_on_run")
        private val SOUND_ON_RUN = booleanPreferencesKey("sound_on_run")
        private val STROKE_WIDTH = floatPreferencesKey("stroke_width_px")
        private val STROKE_COLOR = intPreferencesKey("stroke_color_argb")
        private val ACCENT_COLOR = intPreferencesKey("accent_color_argb")

        /** Background blur behind the pad, in px (Android 12 and up). */
        const val DEFAULT_BLUR_PX: Int = 25
        const val MAX_BLUR_PX: Int = 100

        /** Frosted black tint over the blurred background, 0..1. */
        const val DEFAULT_OPACITY: Float = 0.45f

        /**
         * Seconds the pad waits with no gesture before closing on its
         * own. 0 means never. Once the user starts drawing, multi-stroke
         * keeps the pad open until the gesture is done.
         */
        const val DEFAULT_TIMEOUT_S: Int = 7
        const val MAX_TIMEOUT_S: Int = 30

        /** Short vibration when a gesture is recognised and runs. */
        const val DEFAULT_VIBRATE_ON_RUN: Boolean = true

        /** Short beep when a gesture is recognised and runs. */
        const val DEFAULT_SOUND_ON_RUN: Boolean = false

        /** Drawn stroke thickness, in px. */
        const val DEFAULT_STROKE_WIDTH_PX: Float = 14f
        const val MIN_STROKE_WIDTH_PX: Float = 4f
        const val MAX_STROKE_WIDTH_PX: Float = 24f

        /** Drawn stroke colour, as an ARGB int. Cyan by default. */
        const val DEFAULT_STROKE_COLOR_ARGB: Int = -0xFF1A01 // 0xFF00E5FF as a signed int

        /** Stroke colour presets offered in Settings, as ARGB ints. */
        val STROKE_COLOR_PRESETS: List<Int> = listOf(
            0xFF00E5FF, 0xFFFFFFFF, 0xFFFFEB3B,
            0xFF69F0AE, 0xFFFF80AB, 0xFFFFAB40
        ).map { it.toInt() }

        /**
         * App accent colour (v1.2.0): the colour of buttons,
         * selections, highlights, the Save pill and gesture glyphs,
         * app-wide. Cyan by default. Every preset is light enough
         * for black text on top. This is separate from the stroke
         * colour above, which colours only the stroke you draw.
         */
        const val DEFAULT_ACCENT_COLOR_ARGB: Int = -0xFF1A01 // 0xFF00E5FF as a signed int

        /** Accent colour presets offered in Settings, as ARGB ints. */
        val ACCENT_COLOR_PRESETS: List<Int> = listOf(
            0xFF00E5FF, // Cyan
            0xFF69F0AE, // Green
            0xFFFFAB40, // Amber
            0xFFB388FF, // Purple
            0xFFFF80AB, // Pink
            0xFF448AFF  // Blue
        ).map { it.toInt() }
    }

    val overlayBlur: Flow<Int> = context.dataStore.data.map { it[OVERLAY_BLUR] ?: DEFAULT_BLUR_PX }
    val overlayOpacity: Flow<Float> = context.dataStore.data.map { it[OVERLAY_OPACITY] ?: DEFAULT_OPACITY }
    val overlayTimeout: Flow<Int> = context.dataStore.data.map { it[OVERLAY_TIMEOUT] ?: DEFAULT_TIMEOUT_S }
    val vibrateOnRun: Flow<Boolean> = context.dataStore.data.map { it[VIBRATE_ON_RUN] ?: DEFAULT_VIBRATE_ON_RUN }
    val soundOnRun: Flow<Boolean> = context.dataStore.data.map { it[SOUND_ON_RUN] ?: DEFAULT_SOUND_ON_RUN }
    val strokeWidth: Flow<Float> = context.dataStore.data.map { it[STROKE_WIDTH] ?: DEFAULT_STROKE_WIDTH_PX }
    val strokeColor: Flow<Int> = context.dataStore.data.map { it[STROKE_COLOR] ?: DEFAULT_STROKE_COLOR_ARGB }
    val accentColor: Flow<Int> = context.dataStore.data.map { it[ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR_ARGB }

    suspend fun setOverlayBlur(px: Int) {
        context.dataStore.edit { it[OVERLAY_BLUR] = px.coerceIn(0, MAX_BLUR_PX) }
    }

    suspend fun setOverlayOpacity(opacity: Float) {
        context.dataStore.edit { it[OVERLAY_OPACITY] = opacity.coerceIn(0f, 1f) }
    }

    suspend fun setOverlayTimeout(seconds: Int) {
        context.dataStore.edit { it[OVERLAY_TIMEOUT] = seconds.coerceIn(0, MAX_TIMEOUT_S) }
    }

    suspend fun setVibrateOnRun(enabled: Boolean) {
        context.dataStore.edit { it[VIBRATE_ON_RUN] = enabled }
    }

    suspend fun setSoundOnRun(enabled: Boolean) {
        context.dataStore.edit { it[SOUND_ON_RUN] = enabled }
    }

    suspend fun setStrokeWidth(px: Float) {
        context.dataStore.edit {
            it[STROKE_WIDTH] = px.coerceIn(MIN_STROKE_WIDTH_PX, MAX_STROKE_WIDTH_PX)
        }
    }

    suspend fun setStrokeColor(argb: Int) {
        context.dataStore.edit { it[STROKE_COLOR] = argb }
    }

    suspend fun setAccentColor(argb: Int) {
        context.dataStore.edit { it[ACCENT_COLOR] = argb }
    }
}
