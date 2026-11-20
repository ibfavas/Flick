package com.ibfavas.flick.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flick_settings")

enum class PadStyle(val label: String) { FULL_SCREEN("Full screen"), CARD("Card") }

class SettingsStore(private val context: Context) {

    companion object {
        private val PAD_STYLE = stringPreferencesKey("pad_style")
        private val OVERLAY_BLUR = intPreferencesKey("overlay_blur_px")
        private val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
        private val OVERLAY_TIMEOUT = intPreferencesKey("overlay_timeout_s")
        private val VIBRATE_ON_RUN = booleanPreferencesKey("vibrate_on_run")
        private val SOUND_ON_RUN = booleanPreferencesKey("sound_on_run")
        private val STROKE_WIDTH = floatPreferencesKey("stroke_width_px")
        private val ACCENT_COLOR = intPreferencesKey("accent_color_argb")
        private val STRICTNESS = floatPreferencesKey("match_strictness")
        private val SORT_ORDER = stringPreferencesKey("sort_order")

        /** Background blur behind the pad, in px (Android 12 and up). */
        const val DEFAULT_BLUR_PX: Int = 60
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
        const val MAX_STROKE_WIDTH_PX: Float = 30f

        const val DEFAULT_ACCENT_COLOR_ARGB: Int = 0xFFC6FF00.toInt()

        val accentPresets = linkedMapOf(
            DEFAULT_ACCENT_COLOR_ARGB to "Lime",
            0xFF35C78D.toInt() to "Emerald",
            0xFF00E5FF.toInt() to "Cyan",
            0xFF5486E8.toInt() to "Sapphire",
            0xFFBB92F2.toInt() to "Amethyst",
            0xFFE35A78.toInt() to "Ruby",
            0xFFD78D58.toInt() to "Copper",
            0xFFE8CD99.toInt() to "Champagne Gold"
        )

        /** Gesture list sort order: creation order or name A to Z. */
        const val SORT_CREATED: String = "created"
        const val SORT_USED = "most_used"
        const val SORT_AZ: String = "az"
        const val DEFAULT_SORT_ORDER: String = SORT_CREATED
    }

    val padStyle = context.dataStore.data.map { if (it[PAD_STYLE] == PadStyle.CARD.name) PadStyle.CARD else PadStyle.FULL_SCREEN }
    suspend fun setPadStyle(style: PadStyle) {
        context.dataStore.edit { it[PAD_STYLE] = style.name }
    }

    val overlayBlur: Flow<Int> = context.dataStore.data.map { it[OVERLAY_BLUR] ?: DEFAULT_BLUR_PX }
    val overlayOpacity: Flow<Float> = context.dataStore.data.map { it[OVERLAY_OPACITY] ?: DEFAULT_OPACITY }
    val overlayTimeout: Flow<Int> = context.dataStore.data.map { it[OVERLAY_TIMEOUT] ?: DEFAULT_TIMEOUT_S }
    val vibrateOnRun: Flow<Boolean> = context.dataStore.data.map { it[VIBRATE_ON_RUN] ?: DEFAULT_VIBRATE_ON_RUN }
    val soundOnRun: Flow<Boolean> = context.dataStore.data.map { it[SOUND_ON_RUN] ?: DEFAULT_SOUND_ON_RUN }
    val strokeWidth: Flow<Float> = context.dataStore.data.map { it[STROKE_WIDTH] ?: DEFAULT_STROKE_WIDTH_PX }
    val accentColor: Flow<Int> = context.dataStore.data.map { it[ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR_ARGB }
    val strictness = context.dataStore.data.map { it[STRICTNESS] ?: com.ibfavas.flick.gesture.Recognizer.DEFAULT_THRESHOLD }

    suspend fun setStrictness(value: Float) {
        context.dataStore.edit { it[STRICTNESS] = value.coerceIn(.82f, .92f) }
    }

    val sortOrder: Flow<String> = context.dataStore.data.map { it[SORT_ORDER] ?: DEFAULT_SORT_ORDER }

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

    suspend fun setAccentColor(argb: Int) {
        context.dataStore.edit { it[ACCENT_COLOR] = argb }
        com.ibfavas.flick.widget.VisiblePadWidget.refresh(context.applicationContext)
    }

    suspend fun setSortOrder(order: String) {
        context.dataStore.edit {
            it[SORT_ORDER] = if (order == SORT_AZ || order == SORT_USED) order else SORT_CREATED
        }
    }
}
