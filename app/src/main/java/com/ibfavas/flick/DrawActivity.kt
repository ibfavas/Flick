package com.ibfavas.flick

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ibfavas.flick.R
import com.ibfavas.flick.actions.ActionExecutor
import com.ibfavas.flick.data.DatabaseProvider
import com.ibfavas.flick.data.Gesture
import com.ibfavas.flick.data.GestureDao
import com.ibfavas.flick.data.SettingsStore
import com.ibfavas.flick.gesture.Recognizer
import com.ibfavas.flick.gesture.StrokePoint
import com.ibfavas.flick.ui.DrawPad
import com.ibfavas.flick.ui.FlickBrand
import com.ibfavas.flick.ui.FlickTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Frosted-glass full-screen activity launched by the invisible widget and
 * the Quick Settings tile. The pad is glass, not invisible: the screen
 * the user was on shows through faintly, blurred, under a semi-transparent
 * black tint, so the pad is clearly a surface of its own. The stroke is
 * brand cyan with a dark outline, so it reads over any background.
 *
 * The user draws a gesture; on a match the action runs and the activity
 * finishes. On no match, a message shows; with auto-close on fail the pad
 * closes shortly after, otherwise it stays open for another try. If no
 * gesture is drawn within the overlay timeout, the pad closes on its own
 * (once drawing starts, multi-stroke keeps it open until the gesture
 * is done).
 *
 * Note: Android widgets only support taps, not stroke tracking, so drawing
 * happens here in this activity rather than inside the widget.
 */
class DrawActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Frosted glass starts at the window: a transparent background
        // with no dim, so the screen underneath can be blurred and
        // tinted by the content instead of hidden. The theme
        // (Theme.Flick.Draw) already sets this up; these calls make it
        // explicit so a theme change cannot make the pad solid by accident.
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
    val activity = LocalContext.current as? android.app.Activity
    val blurPx by settings.overlayBlur.collectAsState(initial = SettingsStore.DEFAULT_BLUR_PX)
    val opacity by settings.overlayOpacity.collectAsState(initial = SettingsStore.DEFAULT_OPACITY)
    val timeoutSeconds by settings.overlayTimeout.collectAsState(initial = SettingsStore.DEFAULT_TIMEOUT_S)
    val autoCloseOnFail by settings.autoCloseOnFail.collectAsState(
        initial = SettingsStore.DEFAULT_AUTO_CLOSE_ON_FAIL
    )
    var message by remember { mutableStateOf<String?>(null) }
    var drawn by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Blur the screen behind the window (Android 12 and up). The blur
    // radius comes from settings; 0 means no blur, just the tint.
    LaunchedEffect(blurPx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                activity?.window?.setBackgroundBlurRadius(blurPx)
            } catch (e: Exception) {
                // Some devices do not support window blur; the tint alone
                // still reads as frosted glass.
            }
        }
    }

    // Close on its own if no gesture is drawn within the timeout. Once
    // drawing starts, multi-stroke keeps the pad open until the gesture
    // finishes, so the timer is cancelled for good.
    LaunchedEffect(timeoutSeconds, drawn) {
        if (timeoutSeconds > 0 && !drawn) {
            delay(timeoutSeconds * 1000L)
            onClose()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) +
            scaleIn(initialScale = 0.98f, animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(150)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Frosted glass: a semi-transparent black tint over the
            // blurred screen underneath. The screen stays faintly
            // visible; the pad is unmistakably a surface.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FlickBrand.AmoledBackground.copy(alpha = opacity))
            )
            DrawPad(
                modifier = Modifier.fillMaxSize(),
                onGestureFinished = { raw: List<List<StrokePoint>> ->
                    scope.launch {
                        val gestures = dao.getAll()
                        // Every recorded sample of every gesture: the
                        // recognizer matches against all of them and
                        // takes the best score.
                        val templates = gestures.flatMap { it.multiTemplates() }
                        val match = Recognizer.recognizeMulti(raw, templates)
                        val gesture = match?.let { m ->
                            gestures.firstOrNull { it.name == m.template.name }
                        }
                        if (gesture == null) {
                            message = "No match, try again"
                            if (autoCloseOnFail) {
                                delay(1200)
                                onClose()
                            }
                        } else {
                            onMatched(gesture)
                        }
                    }
                },
                onCleared = { message = null },
                onFirstStroke = { drawn = true },
                // The pad sits on top of the user's own screen, so the
                // stroke gets a dark outline to stay readable anywhere.
                strokeOutline = true
            )
            // No-match message, centred, over the glass. It never sits
            // at the bottom of the screen, where it would cover the
            // home screen's own controls.
            AnimatedVisibility(
                visible = message != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                message?.let {
                    Text(
                        text = it,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.45f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
            // Brand header: the transparent logo and the wordmark,
            // centred at the top of the pad, above the drawing area.
            // Neither is clickable, so strokes pass straight through
            // to the pad underneath. The close X (top-left) and the
            // settings gear (top-right) keep their places at the sides.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.flick_logo_transparent),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Image(
                    painter = painterResource(R.drawable.flick_wordmark),
                    contentDescription = "Flick",
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier.height(22.dp)
                )
            }

            // Top bar: close on the left, settings on the right. Both sit
            // in translucent chips so they read over any background.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
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
