package com.ibfavas.flick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * Quick Settings tile. The user draws a gesture; on a match the action runs
 * and the activity finishes. On no match, a message shows and the pad stays
 * open for another try.
 *
 * Note: Android widgets only support taps, not stroke tracking, so drawing
 * happens here in this transparent activity rather than inside the widget.
 */
class DrawActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)

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
                    onClose = { finish() }
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
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val strokeColor by settings.strokeColor.collectAsState(initial = SettingsStore.DEFAULT_COLOR)
    val strokeWidth by settings.strokeWidth.collectAsState(initial = SettingsStore.DEFAULT_WIDTH)
    var message by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        DrawPad(
            strokeColor = Color(strokeColor),
            strokeWidth = strokeWidth,
            modifier = Modifier.fillMaxSize(),
            onStrokeFinished = { raw: List<StrokePoint> ->
                scope.launch {
                    val gestures = dao.getAll()
                    val templates = gestures.map { gesture ->
                        Recognizer.Template(
                            gesture.name,
                            Recognizer.pointsFromString(gesture.template)
                        )
                    }
                    val match = Recognizer.recognize(raw, templates)
                    val gesture = match?.let { m ->
                        gestures.firstOrNull { it.name == m.template.name }
                    }
                    if (gesture == null) {
                        message = "No matching gesture"
                    } else {
                        onMatched(gesture)
                    }
                }
            }
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            message?.let {
                Text(
                    text = it,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Button(onClick = onClose) { Text("Close") }
        }
    }
}
