package com.ibfavas.flick

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ibfavas.flick.actions.ActionExecutor
import com.ibfavas.flick.data.DatabaseProvider
import com.ibfavas.flick.data.Gesture
import com.ibfavas.flick.data.GestureDao
import com.ibfavas.flick.data.SettingsStore
import com.ibfavas.flick.gesture.Recognizer
import com.ibfavas.flick.gesture.StrokePoint
import com.ibfavas.flick.ui.DrawPad
import com.ibfavas.flick.ui.FlickTheme
import kotlinx.coroutines.launch

/**
 * Transparent full-screen activity launched by the invisible widget and the
 * Quick Settings tile. The pad is a see-through overlay: the screen the
 * user was on stays visible underneath, with only a very light dim so the
 * stroke reads. The user draws a gesture; on a match the action runs and
 * the activity finishes. On no match, a message shows and the pad stays
 * open for another try.
 *
 * Note: Android widgets only support taps, not stroke tracking, so drawing
 * happens here in this transparent activity rather than inside the widget.
 */
class DrawActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent window, no dim: the underlying screen must stay
        // visible. The theme (Theme.Flick.Draw) already sets this up;
        // these calls make it explicit so a theme change cannot make the
        // pad solid by accident.
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)

        val dao = DatabaseProvider.get(this).gestureDao()
        val settings = SettingsStore(this)

        setContent {
            FlickTheme {
                DrawScreen(
                    dao = dao,
                    settings = settings,
                    onMatched = { gesture ->
                        ActionExecutor.execute(this, gesture)
                        finish()
                    },
                    onClose = { finish() },
                    onOpenSettings = {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun DrawScreen(
    dao: GestureDao,
    settings: SettingsStore,
    onMatched: (Gesture) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val strokeColor by settings.strokeColor.collectAsState(initial = SettingsStore.DEFAULT_COLOR)
    val strokeWidth by settings.strokeWidth.collectAsState(initial = SettingsStore.DEFAULT_WIDTH)
    var message by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) +
            scaleIn(initialScale = 0.98f, animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(150)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            // At most a very light dim: 8% black just lifts the stroke
            // off the screen underneath. The screen stays clearly visible.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.08f))
            )
            Column(modifier = Modifier.fillMaxSize()) {
                DrawPad(
                    strokeColor = Color(strokeColor),
                    strokeWidth = strokeWidth,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onGestureFinished = { raw: List<List<StrokePoint>> ->
                        scope.launch {
                            val gestures = dao.getAll()
                            val templates = gestures.map { gesture ->
                                Recognizer.MultiTemplate(
                                    gesture.name,
                                    Recognizer.strokesFromString(gesture.template)
                                )
                            }
                            val match = Recognizer.recognizeMulti(raw, templates)
                            val gesture = match?.let { m ->
                                gestures.firstOrNull { it.name == m.template.name }
                            }
                            if (gesture == null) {
                                message = "No matching gesture"
                            } else {
                                onMatched(gesture)
                            }
                        }
                    },
                    onCleared = { message = null },
                    // The pad sits on top of the user's own screen, so the
                    // stroke gets a dark outline to stay readable anywhere.
                    strokeOutline = true
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    message?.let {
                        Text(
                            text = it,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    } ?: Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) {
                        Text("Close", color = Color.White)
                    }
                }
            }
            // Top bar: close on the left, settings on the right. Both sit
            // in translucent chips so they read over any background.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PadChipButton(
                    icon = Icons.Filled.Close,
                    description = "Close",
                    onClick = onClose
                )
                PadChipButton(
                    icon = Icons.Filled.Settings,
                    description = "Settings",
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun PadChipButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}
