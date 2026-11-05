package com.ibfavas.flick

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibfavas.flick.R
import com.ibfavas.flick.actions.ActionExecutor
import com.ibfavas.flick.actions.Feedback
import com.ibfavas.flick.data.DatabaseProvider
import com.ibfavas.flick.data.Gesture
import com.ibfavas.flick.data.GestureDao
import com.ibfavas.flick.data.SettingsStore
import com.ibfavas.flick.gesture.Recognizer
import com.ibfavas.flick.gesture.StrokePoint
import com.ibfavas.flick.ui.ActionIcon
import com.ibfavas.flick.ui.DrawPad
import com.ibfavas.flick.ui.FlickBrand
import com.ibfavas.flick.ui.FlickTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Frosted-glass full-screen activity launched by the invisible widget and
 * the Quick Settings tile. The pad is glass, not invisible: the screen
 * the user was on shows through faintly, blurred, under a semi-transparent
 * black tint, so the pad is clearly a surface of its own. The stroke
 * follows the Settings colour and width.
 *
 * The pad has no Done or Clear buttons (v1.0.11): a gesture is
 * recognised automatically once the pen-up timer expires. On a match
 * the pad shows "Opening <name>" with the target's icon at the top
 * and a soft cyan wave sweeping from the bottom of the pad to the
 * top, then runs the action and closes. On no match it closes at
 * once — no message, no prompt. If no gesture is drawn within the
 * overlay timeout, the pad closes on its own (once drawing starts,
 * multi-stroke keeps it open until the gesture is done).
 *
 * Note: Android widgets only support taps, not stroke tracking, so drawing
 * happens here in this activity rather than inside the widget.
 */
class DrawActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge (v1.0.10), same as MainActivity: transparent
        // system bars, and the pad's header/top row pad themselves
        // below the status bar (statusBarsPadding) so the logo, the
        // close X and the gear never sit under the notification bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
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
                    // Runs the action only (v1.0.12): DrawScreen closes
                    // the pad itself with a smooth fade-out after this
                    // returns, so the close is never cut short by a
                    // finish here, and never skipped if this throws —
                    // DrawScreen's finally handles the close.
                    onMatched = { gesture, feedback ->
                        try {
                            ActionExecutor.execute(this, gesture, feedback)
                        } catch (e: Exception) {
                            // Swallowed: the pad still closes smoothly.
                        }
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
    onMatched: (Gesture, Feedback) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? android.app.Activity
    val blurPx by settings.overlayBlur.collectAsState(initial = SettingsStore.DEFAULT_BLUR_PX)
    val opacity by settings.overlayOpacity.collectAsState(initial = SettingsStore.DEFAULT_OPACITY)
    val timeoutSeconds by settings.overlayTimeout.collectAsState(initial = SettingsStore.DEFAULT_TIMEOUT_S)
    val vibrateOnRun by settings.vibrateOnRun.collectAsState(
        initial = SettingsStore.DEFAULT_VIBRATE_ON_RUN
    )
    val soundOnRun by settings.soundOnRun.collectAsState(
        initial = SettingsStore.DEFAULT_SOUND_ON_RUN
    )
    val strokeColorArgb by settings.strokeColor.collectAsState(
        initial = SettingsStore.DEFAULT_STROKE_COLOR_ARGB
    )
    val strokeWidthPx by settings.strokeWidth.collectAsState(
        initial = SettingsStore.DEFAULT_STROKE_WIDTH_PX
    )
    // The gesture that matched, once one does. While set, the pad shows
    // the "Opening" banner and the wave, then runs the action. A second
    // finished gesture is ignored: the pad is already on its way out.
    var matched by remember { mutableStateOf<Gesture?>(null) }
    var drawn by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    // The gesture library, observed so the prepared templates stay
    // fresh. Prepared once per library change (v1.1.0): normalising
    // every template variant on every recognition was the pad's main
    // CPU cost, and recognition now runs off the main thread.
    val gestures by dao.observeAll().collectAsState(initial = emptyList())
    val prepared = remember(gestures) {
        Recognizer.prepareTemplates(
            gestures.filter { it.enabled }.flatMap { it.multiTemplates() }
        )
    }
    val templateToGesture = remember(gestures) {
        gestures.flatMap { gesture ->
            gesture.multiTemplates().map { it to gesture }
        }.toMap()
    }
    LaunchedEffect(Unit) { visible = true }
    // Smooth close (v1.0.12): the pad never snaps shut. Every close
    // goes through closeSmoothly(), which starts the fade-out (and a
    // slight scale-down) and only finishes the activity once the
    // animation has played (~250 ms). The `closing` guard keeps a
    // second close request from cutting the animation short.
    var closing by remember { mutableStateOf(false) }
    fun closeSmoothly() {
        if (closing) return
        closing = true
        visible = false
        scope.launch {
            delay(250)
            onClose()
        }
    }

    // The wave sweeps from the bottom of the pad (1) to the top (0)
    // while the "Opening" banner shows.
    val waveY by animateFloatAsState(
        targetValue = if (matched != null) 0f else 1f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "openingWave"
    )

    // Safety net (v1.0.12): once a gesture has matched, the pad must
    // go away even if the launch path above is interrupted for any
    // reason. The normal path finishes at ~650 ms + a 250 ms fade;
    // this fires at 1500 ms only if the activity is somehow still
    // alive. It closes smoothly too, and the `closing` guard makes
    // it a no-op if a fade is already running.
    LaunchedEffect(matched) {
        if (matched != null) {
            delay(1500)
            closeSmoothly()
        }
    }

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
    LaunchedEffect(timeoutSeconds, drawn, matched) {
        if (timeoutSeconds > 0 && !drawn && matched == null) {
            delay(timeoutSeconds * 1000L)
            closeSmoothly()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) +
            scaleIn(initialScale = 0.98f, animationSpec = tween(200)),
        // Smooth exit (v1.0.12): a 250 ms fade-out with a slight
        // scale-down, instead of snapping shut. closeSmoothly() waits
        // this out before finishing the activity.
        exit = fadeOut(animationSpec = tween(250)) +
            scaleOut(targetScale = 0.97f, animationSpec = tween(250)),
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
                    if (matched != null) return@DrawPad
                    scope.launch {
                        try {
                            // Recognition is CPU work: off the main
                            // thread (v1.1.0), against the prepared
                            // library. Disabled gestures never run:
                            // they are left out of the match entirely
                            // (v1.0.10).
                            val match = withContext(Dispatchers.Default) {
                                Recognizer.recognizeMultiPrepared(raw, prepared)
                            }
                            // The match carries its source template, so
                            // the gesture is found even when two gestures
                            // share a name; the name lookup is the
                            // fallback for gestures recorded before
                            // v1.1.0.
                            val gesture = match?.source?.let { templateToGesture[it] }
                                ?: match?.let { m ->
                                    gestures.firstOrNull { it.name == m.template.name }
                                }
                            if (gesture == null) {
                                // No match: close smoothly. No message,
                                // no prompt, no setting for it (v1.0.11).
                                closeSmoothly()
                            } else {
                                matched = gesture
                                delay(650)
                                try {
                                    // Launch first, fade behind it: the
                                    // app comes up while the pad
                                    // dissolves underneath (v1.0.12).
                                    onMatched(gesture, Feedback(vibrateOnRun, soundOnRun))
                                } finally {
                                    closeSmoothly()
                                }
                            }
                        } catch (e: Exception) {
                            // Never strand the user on the pad: a failure
                            // reading the gestures or recognising still
                            // closes it, exactly like a miss.
                            closeSmoothly()
                        }
                    }
                },
                onFirstStroke = { drawn = true },
                // The pad sits on top of the user's own screen, so the
                // stroke gets a dark outline to stay readable anywhere.
                strokeOutline = true,
                strokeColor = Color(strokeColorArgb),
                strokeWidth = strokeWidthPx,
                // No Done/Clear on the overlay pad (v1.0.11): the
                // gesture is recognised automatically when the timer
                // expires, so the buttons have nothing to do.
                showControls = false
            )
            // The wave: a soft cyan band sweeping from the bottom of
            // the pad to the top while the "Opening" banner shows.
            if (matched != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bandH = size.height * 0.22f
                    val y = waveY * size.height - bandH / 2f
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                FlickBrand.Cyan.copy(alpha = 0.30f),
                                Color.Transparent
                            ),
                            startY = y,
                            endY = y + bandH
                        ),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, bandH)
                    )
                }
            }
            // Brand header: the transparent logo and the wordmark,
            // centred at the top of the pad, above the drawing area.
            // Neither is clickable, so strokes pass straight through
            // to the pad underneath. The close X (top-left) and the
            // settings gear (top-right) keep their places at the sides.
            // While a match shows, the header gives way to the banner.
            if (matched == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
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
            }
            // "Opening <name>" pill: the matched gesture's name with
            // its target's icon, at the top of the pad, in place of the
            // brand header while the wave sweeps. Kept small and
            // compact (v1.0.12) so it reads as a chip, not a banner.
            AnimatedVisibility(
                visible = matched != null,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 20.dp)
            ) {
                matched?.let { gesture ->
                    Row(
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.55f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIcon(gesture, size = 24.dp, iconSize = 14.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Opening ${gesture.name}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Top bar: close on the left, settings on the right. Both sit
            // in translucent chips so they read over any background.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                PadChipButton(
                    icon = Icons.Filled.Close,
                    description = "Close",
                    onClick = { closeSmoothly() }
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
