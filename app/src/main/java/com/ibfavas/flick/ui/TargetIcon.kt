package com.ibfavas.flick.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.ibfavas.flick.data.ActionType
import com.ibfavas.flick.data.Gesture

// Only material-icons-core is in the build deps (see tools/seeds.txt), so
// only core icons are used here: Apps, Link, CameraAlt and the flashlight
// icons live in material-icons-extended and would not compile. Wi-Fi and
// Bluetooth have no core icon; both open a system panel, so the gear is
// honest for them.
fun iconFor(type: ActionType): ImageVector = when (type) {
    ActionType.LAUNCH_APP -> Icons.AutoMirrored.Filled.ExitToApp
    ActionType.OPEN_URL -> Icons.Filled.Share
    ActionType.FLASHLIGHT_ON -> Icons.Filled.Star
    ActionType.FLASHLIGHT_OFF -> Icons.Filled.Close
    ActionType.OPEN_CAMERA -> Icons.Filled.Face
    ActionType.MEDIA_PLAY_PAUSE -> Icons.Filled.PlayArrow
    ActionType.CALL_CONTACT -> Icons.Filled.Call
    ActionType.LOCK_SCREEN -> Icons.Filled.Lock
    ActionType.TAKE_SCREENSHOT -> Icons.Filled.Create
    ActionType.TOGGLE_WIFI -> Icons.Filled.Settings
    ActionType.TOGGLE_BLUETOOTH -> Icons.Filled.Settings
    ActionType.TOGGLE_DND -> Icons.Filled.Notifications
    ActionType.RUN_FLOW -> Icons.AutoMirrored.Filled.List
}

/**
 * The target's icon for a gesture: the app's own icon for app gestures,
 * the action's icon otherwise. Shared by the gesture cards, the detail
 * sheet and the overlay pad's "Opening" banner. [circular] clips the
 * app icon to a circle (home cards); the default rounded
 * rectangle is kept everywhere else.
 */
@Composable
fun ActionIcon(gesture: Gesture, size: Dp = 44.dp, iconSize: Dp = 24.dp, circular: Boolean = false) {
    val context = LocalContext.current
    if (gesture.actionType == ActionType.LAUNCH_APP) {
        var bitmap by remember(gesture.payload) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(gesture.payload) {
            bitmap = try {
                context.packageManager.getApplicationIcon(gesture.payload)
                    .toBitmap(width = 96, height = 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(if (circular) CircleShape else RoundedCornerShape(10.dp))
            )
        } else {
            FallbackIcon(Icons.AutoMirrored.Filled.ExitToApp, size, iconSize)
        }
    } else {
        FallbackIcon(iconFor(gesture.actionType), size, iconSize)
    }
}

@Composable
fun FallbackIcon(icon: ImageVector, size: Dp = 44.dp, iconSize: Dp = 24.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize)
        )
    }
}
