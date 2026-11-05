package com.ibfavas.flick.actions

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import android.widget.Toast
import com.ibfavas.flick.data.ActionType
import com.ibfavas.flick.data.Gesture

/**
 * Runs the action attached to a gesture. Shared by the in-app pad, the
 * widget flow and the Quick Settings tile flow so behaviour is identical
 * everywhere.
 */
object ActionExecutor {

    fun execute(context: Context, gesture: Gesture) {
        when (gesture.actionType) {
            ActionType.LAUNCH_APP -> launchApp(context, gesture.payload)
            ActionType.OPEN_URL -> openUrl(context, gesture.payload)
            ActionType.FLASHLIGHT_ON -> setTorch(context, true)
            ActionType.FLASHLIGHT_OFF -> setTorch(context, false)
            ActionType.OPEN_CAMERA -> openCamera(context)
            ActionType.MEDIA_PLAY_PAUSE -> mediaPlayPause(context)
        }
    }

    fun describe(gesture: Gesture): String = when (gesture.actionType) {
        ActionType.LAUNCH_APP -> "Launch app: ${gesture.payload}"
        ActionType.OPEN_URL -> "Open link: ${gesture.payload}"
        ActionType.FLASHLIGHT_ON -> "Flashlight on"
        ActionType.FLASHLIGHT_OFF -> "Flashlight off"
        ActionType.OPEN_CAMERA -> "Open camera"
        ActionType.MEDIA_PLAY_PAUSE -> "Media play/pause"
    }

    private fun launchApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(context, "App not installed: $packageName", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun openUrl(context: Context, url: String) {
        val fixed = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fixed))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setTorch(context: Context, on: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: run {
                Toast.makeText(context, "No camera found", Toast.LENGTH_SHORT).show()
                return
            }
            cameraManager.setTorchMode(cameraId, on)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Flashlight needs camera permission",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openCamera(context: Context) {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mediaPlayPause(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            audioManager.dispatchMediaKeyEvent(down)
            audioManager.dispatchMediaKeyEvent(up)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot control media", Toast.LENGTH_SHORT).show()
        }
    }
}
