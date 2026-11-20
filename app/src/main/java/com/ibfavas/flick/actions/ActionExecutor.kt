package com.ibfavas.flick.actions

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.ibfavas.flick.data.ActionType
import com.ibfavas.flick.data.Gesture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Feedback played when a gesture is recognised and runs. Read from
 * SettingsStore by the caller (DrawActivity / the test pad) and passed
 * in, because ActionExecutor runs synchronously and cannot collect a
 * DataStore Flow itself.
 */
data class Feedback(val vibrate: Boolean = true, val sound: Boolean = false)

/*
 * Runs the action attached to a gesture. Shared by the
 * in-app pad, the widget flow and the Quick Settings
 * tile flow so behaviour is identical everywhere.
 *
 * Wi-Fi and Bluetooth cannot be flipped silently by an
 * app on Android 12 and up, so those actions open the
 * system panel for the user to flip the switch. They are
 * labelled as such in the UI. Lock screen and screenshot
 * need Flick's accessibility service enabled; when it is
 * off, the user is guided to turn it on. Do Not Disturb
 * needs notification-policy access; when it is not
 * granted, the user is guided to grant it.
 */
object ActionExecutor {

    private val actionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun execute(context: Context, gesture: Gesture, feedback: Feedback = Feedback(), recognized: Boolean = false) {
        if (feedback.vibrate) vibrate(context)
        if (feedback.sound) playTone(context)
        fun record() = com.ibfavas.flick.data.DatabaseProvider.get(context).gestureDao().recordUse(gesture, recognized)
        val ran = when (gesture.actionType) {
            ActionType.LAUNCH_APP -> launchApp(context, gesture.payload)
            ActionType.OPEN_URL -> openUrl(context, gesture.payload)
            ActionType.FLASHLIGHT_ON -> setTorch(context, true)
            ActionType.FLASHLIGHT_OFF -> setTorch(context, false)
            ActionType.OPEN_CAMERA -> openCamera(context)
            ActionType.MEDIA_PLAY_PAUSE -> mediaPlayPause(context)
            ActionType.CALL_CONTACT -> callContact(context, gesture.payload)
            ActionType.LOCK_SCREEN -> lockScreen(context)
            ActionType.TAKE_SCREENSHOT -> {
                takeScreenshot(context, ::record)
                return
            }
            ActionType.TOGGLE_WIFI -> openWifiPanel(context)
            ActionType.TOGGLE_BLUETOOTH -> openBluetoothPanel(context)
            ActionType.TOGGLE_DND -> toggleDnd(context)
            ActionType.CHAT_CONTACT -> openChat(context, gesture.payload)
        }
        if (ran) record()
    }

    fun describe(gesture: Gesture): String = describeAction(gesture.actionType, gesture.payload)

    /** Human-readable label for a saved action. */
    fun describeAction(type: ActionType, payload: String): String = when (type) {
        ActionType.LAUNCH_APP -> "Launch app: $payload"
        ActionType.OPEN_URL -> "Open link: $payload"
        ActionType.FLASHLIGHT_ON -> "Flashlight on"
        ActionType.FLASHLIGHT_OFF -> "Flashlight off"
        ActionType.OPEN_CAMERA -> "Open camera"
        ActionType.MEDIA_PLAY_PAUSE -> "Media play/pause"
        ActionType.CALL_CONTACT -> "Call: ${contactName(payload)}"
        ActionType.LOCK_SCREEN -> "Lock screen"
        ActionType.TAKE_SCREENSHOT -> "Take a screenshot"
        ActionType.TOGGLE_WIFI -> "Open Wi-Fi settings"
        ActionType.TOGGLE_BLUETOOTH -> "Open Bluetooth settings"
        ActionType.TOGGLE_DND -> "Toggle Do Not Disturb"
        ActionType.CHAT_CONTACT -> "Chat with ${contactName(payload)}"
    }

    /** Payload for CALL_CONTACT is "Name\nNumber". */
    fun contactName(payload: String): String = payload.substringBefore('\n').ifBlank { payload }

    fun contactNumber(payload: String): String = payload.substringAfter('\n', "").substringBefore('\n')

    private fun launchApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(context, "App not installed: $packageName", Toast.LENGTH_SHORT).show()
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun openUrl(context: Context, url: String): Boolean {
        val fixed = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fixed))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun setTorch(context: Context, on: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: run {
                Toast.makeText(context, "No camera found", Toast.LENGTH_SHORT).show()
                return false
            }
            cameraManager.setTorchMode(cameraId, on)
            true
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Flashlight needs camera permission",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    private fun openCamera(context: Context): Boolean {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open camera", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun mediaPlayPause(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            audioManager.dispatchMediaKeyEvent(down)
            audioManager.dispatchMediaKeyEvent(up)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot control media", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun openChat(context: Context, payload: String): Boolean {
        val phone = contactNumber(payload)
        val region = java.util.Locale.getDefault().country
        val normalized = android.telephony.PhoneNumberUtils.formatNumberToE164(phone, region)
        if (normalized == null) {
            Toast.makeText(context, "Save the contact with its country code to open WhatsApp", Toast.LENGTH_LONG).show()
            return false
        }
        val uri = Uri.parse("https://wa.me/" + normalized.removePrefix("+"))
        for (app in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: android.content.ActivityNotFoundException) {
                // Try the other installed WhatsApp edition.
            }
        }
        Toast.makeText(context, "Install WhatsApp to open this chat", Toast.LENGTH_LONG).show()
        return false
    }

    private fun callContact(context: Context, payload: String): Boolean {
        val number = contactNumber(payload)
        if (number.isBlank()) {
            Toast.makeText(context, "No phone number saved for this contact", Toast.LENGTH_SHORT).show()
            return false
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Call permission needed to place calls", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // The toast already told the user what is missing.
            }
            return false
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot place the call", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun lockScreen(context: Context): Boolean {
        if (!FlickAccessibilityService.performAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) {
            guideToAccessibility(context, "lock the screen")
            return false
        }
        return true
    }

    /**
     * The pad closes right after a match, so the screenshot waits a
     * moment first, otherwise it would capture the pad itself.
     */
    private fun takeScreenshot(context: Context, onSuccess: () -> Unit) {
        actionScope.launch {
            delay(700)
            if (!FlickAccessibilityService.performAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)) {
                guideToAccessibility(context, "take a screenshot")
            } else onSuccess()
        }
    }

    private fun guideToAccessibility(context: Context, what: String) {
        Toast.makeText(
            context,
            "Turn on Flick's accessibility service in Settings to $what",
            Toast.LENGTH_LONG
        ).show()
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // The toast already told the user what to do.
        }
    }

    private fun openWifiPanel(context: Context): Boolean {
        // Android 12 and up do not let an app flip Wi-Fi
        // silently, so the honest behaviour is the system
        // panel, where the user flips it.
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open Wi-Fi settings", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun openBluetoothPanel(context: Context): Boolean {
        // There is no Bluetooth panel action (unlike Wi-Fi), so this
        // opens Bluetooth settings, where the user flips the switch.
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open Bluetooth settings", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun toggleDnd(context: Context): Boolean {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Toast.makeText(
                context,
                "Allow Flick to change Do Not Disturb in Settings",
                Toast.LENGTH_LONG
            ).show()
            try {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                // The toast already told the user what to do.
            }
            return false
        }
        return try {
            val on = notificationManager.currentInterruptionFilter !=
                NotificationManager.INTERRUPTION_FILTER_ALL
            notificationManager.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_NONE
            )
            Toast.makeText(
                context,
                if (on) "Do Not Disturb off" else "Do Not Disturb on",
                Toast.LENGTH_SHORT
            ).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot change Do Not Disturb", Toast.LENGTH_SHORT).show()
            false
        }
    }

    // feedback

    /*
     * A short vibration when a gesture runs. Respects the
     * system: no vibrator, no buzz. Explicit full amplitude
     * and an 80 ms buzz: the old 50 ms at the default
     * amplitude was too faint to feel on some phones.
     * Failures are now logged (tag "Flick"), and if the
     * VibratorManager path fails or reports no vibrator, the
     * legacy vibrator service is tried before giving up.
     * Vibration still never breaks the action launch.
     */
    fun testVibrate(context: Context) {
        vibrate(context)
    }

    fun vibrate(context: Context, success: Boolean = true) {
        try {
            val managerVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val manager =
                        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    manager.defaultVibrator
                } catch (e: Exception) {
                    Log.w("Flick", "VibratorManager lookup failed, trying legacy vibrator", e)
                    null
                }
            } else {
                null
            }
            val vibrator = managerVibrator?.takeIf { it.hasVibrator() }
                ?: legacyVibrator(context)?.takeIf { it.hasVibrator() }
                ?: run {
                    Log.w("Flick", "No working vibrator on this device")
                    return
                }
            val effect = if (success) VibrationEffect.createOneShot(80, 255)
                else VibrationEffect.createWaveform(longArrayOf(0, 35, 55, 35), -1)
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            // Vibration must never break running the action.
            Log.w("Flick", "Vibration failed", e)
        }
    }

    private fun legacyVibrator(context: Context): Vibrator? {
        return try {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        } catch (e: Exception) {
            Log.w("Flick", "Legacy vibrator lookup failed", e)
            null
        }
    }

    /*
     * A short beep when a gesture runs. Plays the phone's
     * notification sound, so it follows notification volume.
     * The old ToneGenerator beep failed silently on some
     * phones: its constructor can throw when the audio
     * service cannot allocate a tone generator, and the
     * failure was swallowed. ToneGenerator is kept only as a
     * fallback when no notification sound is set.
     */
    private fun playTone(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            if (ringtone != null) {
                ringtone.play()
                return
            }
        } catch (e: Exception) {
            // Fall through to the ToneGenerator beep below.
        }
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 400)
        } catch (e: Exception) {
            // Sound must never break running the action.
        }
    }
}
