package com.ibfavas.flick

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.ibfavas.flick.data.ActionType
import com.ibfavas.flick.data.PadStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ibfavas.flick.actions.ActionExecutor
import com.ibfavas.flick.actions.Feedback
import com.ibfavas.flick.data.DatabaseProvider
import com.ibfavas.flick.data.Gesture
import com.ibfavas.flick.data.GestureDao
import com.ibfavas.flick.data.SettingsStore
import com.ibfavas.flick.gesture.Recognizer
import com.ibfavas.flick.gesture.StrokePoint
import com.ibfavas.flick.ui.ActionIcon
import com.ibfavas.flick.ui.FlickTheme
import com.ibfavas.flick.ui.accentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.function.Consumer

private const val LAUNCH_CONFIRMATION_MS = 650

private enum class PadPhase { IDLE, DRAWING, CHECKING, SUCCESS, MISS }

class DrawActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)

        val dao = DatabaseProvider.get(this).gestureDao()
        val settings = SettingsStore(this)

        setContent {
            val accentArgb by settings.accentColor.collectAsState(
                initial = SettingsStore.DEFAULT_ACCENT_COLOR_ARGB
            )
            FlickTheme(accent = Color(accentArgb)) {
                DrawScreen(
                    dao = dao,
                    settings = settings,
                    onMatched = { gesture, feedback ->
                        try {
                            ActionExecutor.execute(this, gesture, feedback, recognized = true)
                        } catch (e: Exception) {
                            Log.w("Flick", "Gesture action failed", e)
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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun DrawScreen(
    dao: GestureDao,
    settings: SettingsStore,
    onMatched: (Gesture, Feedback) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val blurPx by settings.overlayBlur.collectAsState(initial = SettingsStore.DEFAULT_BLUR_PX)
    val opacity by settings.overlayOpacity.collectAsState(initial = SettingsStore.DEFAULT_OPACITY)
    // Android can disable window blur at runtime. Increase the tint when it does.
    val windowManager = activity?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    var blurEnabled by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager?.isCrossWindowBlurEnabled == true)
    }
    DisposableEffect(windowManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager != null) {
            val listener = Consumer<Boolean> { enabled ->
                Handler(Looper.getMainLooper()).post { blurEnabled = enabled }
            }
            windowManager.addCrossWindowBlurEnabledListener(listener)
            blurEnabled = windowManager.isCrossWindowBlurEnabled
            onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
        } else {
            onDispose {}
        }
    }
    val tintAlpha = if (blurEnabled) opacity else opacity.coerceAtLeast(0.65f)
    val timeoutSeconds by settings.overlayTimeout.collectAsState(initial = SettingsStore.DEFAULT_TIMEOUT_S)
    val vibrateOnRun by settings.vibrateOnRun.collectAsState(
        initial = false
    )
    val soundOnRun by settings.soundOnRun.collectAsState(
        initial = SettingsStore.DEFAULT_SOUND_ON_RUN
    )
    val accent = accentColor()
    val style by settings.padStyle.collectAsState(initial = null)
    val strokeWidth by settings.strokeWidth.collectAsState(initial = SettingsStore.DEFAULT_STROKE_WIDTH_PX)
    val gestures by dao.observeAll().collectAsState(initial = emptyList())
    val strictness by settings.strictness.collectAsState(initial = Recognizer.DEFAULT_THRESHOLD)
    val matcher = remember(gestures) { com.ibfavas.flick.gesture.LibraryMatcher(gestures) }
    val hasMultiStroke = remember(gestures) { gestures.any { g -> g.enabled && g.allTemplates().any { '|' in it } } }
    var phase by remember { mutableStateOf(PadPhase.IDLE) }
    var misses by remember { mutableIntStateOf(0) }
    var closed by remember { mutableStateOf(false) }
    val strokes = remember { mutableListOf<List<StrokePoint>>() }
    val paths = remember { mutableStateListOf<Path>() }
    var tip by remember { mutableStateOf<Offset?>(null) }
    var target by remember { mutableStateOf<Gesture?>(null) }
    var targetName by remember { mutableStateOf("") }

    fun leave(action: () -> Unit) {
        if (!closed) {
            closed = true
            action()
        }
    }
    fun reset() {
        paths.clear()
        strokes.clear()
        tip = null
        phase = PadPhase.IDLE
    }

    fun applyBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                activity?.window?.setBackgroundBlurRadius(blurPx)
            } catch (e: Exception) {
                // Some devices do not support window blur; the tint alone
                // still reads as frosted glass.
                Log.w("Flick", "Setting background blur radius failed", e)
            }
        }
    }
    LaunchedEffect(blurPx, blurEnabled) { applyBlur() }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) applyBlur()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(timeoutSeconds, phase, closed) {
        if (!closed && phase == PadPhase.IDLE && timeoutSeconds > 0) {
            delay(timeoutSeconds * 1000L)
            leave(onClose)
        }
    }
    LaunchedEffect(phase, closed) {
        if (closed) return@LaunchedEffect
        when (phase) {
            PadPhase.CHECKING -> {
                delay(65)
                val input = strokes.toList()
                val hasEnoughPoints = input.sumOf { it.size } >= 2
                val gesture = if (!hasEnoughPoints) null else withContext(Dispatchers.Default) { matcher.match(input, strictness)?.first }
                // Give an unfinished multi-stroke gesture its normal continuation window.
                if (gesture == null && hasMultiStroke && hasEnoughPoints) delay(835)
                if (closed || phase != PadPhase.CHECKING) return@LaunchedEffect
                if (gesture == null) {
                    misses++
                    phase = PadPhase.MISS
                } else {
                    target = gesture
                    targetName = if (gesture.actionType == ActionType.LAUNCH_APP) withContext(Dispatchers.IO) {
                        runCatching { context.packageManager.getApplicationInfo(gesture.payload, 0)
                            .loadLabel(context.packageManager).toString() }.getOrDefault(gesture.name)
                    } else gesture.name
                    phase = PadPhase.SUCCESS
                }
            }
            PadPhase.MISS -> {
                if (vibrateOnRun) ActionExecutor.vibrate(context, success = false)
                delay(135)
                if (misses >= 2) leave(onClose) else reset()
            }
            PadPhase.SUCCESS -> {
                delay(LAUNCH_CONFIRMATION_MS.toLong())
                leave {
                    try { target?.let { onMatched(it, Feedback(vibrateOnRun, soundOnRun)) } }
                    finally { onClose() }
                }
            }
            else -> Unit
        }
    }

    if (closed || style == null) return
    val waveY by animateFloatAsState(
        targetValue = if (phase == PadPhase.SUCCESS) 0f else 1f,
        animationSpec = tween(LAUNCH_CONFIRMATION_MS), label = "Launch wave"
    )
    val card = style == PadStyle.CARD
    val inkStroke = remember(strokeWidth) {
        Stroke(strokeWidth.coerceAtLeast(1f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val shape = RoundedCornerShape(28.dp)
    val rim = Brush.linearGradient(listOf(accent.copy(alpha = .8f), accent.copy(alpha = .12f),
        accent.copy(alpha = .16f), accent.copy(alpha = .65f)))
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = tintAlpha))
        .statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        val surface = if (card) Modifier.padding(horizontal = 20.dp).widthIn(max = 440.dp)
            .fillMaxWidth().fillMaxHeight(.54f).clip(shape).background(Color(0xe6101012))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = .09f), Color.Transparent,
                Color.Transparent, accent.copy(alpha = .07f)))).border(1.dp, rim, shape)
        else Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Black.copy(alpha = .32f), Color.Transparent, Color.Black.copy(alpha = .28f))))
        Box(surface) {
            val ink = when (phase) {
                PadPhase.MISS -> Color(0xffff4848)
                PadPhase.CHECKING -> androidx.compose.ui.graphics.lerp(accent, Color.White, .22f)
                else -> accent
            }
            // Pointer input stays in one coordinate system through drawing, recognition and feedback.
            Canvas(Modifier.fillMaxSize().clipToBounds().pointerInput(card, hasMultiStroke) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (closed || (phase != PadPhase.IDLE && !(phase == PadPhase.CHECKING && hasMultiStroke))) return@awaitEachGesture
                    down.consume()
                    phase = PadPhase.DRAWING
                    val raw = mutableListOf(StrokePoint(down.position.x, down.position.y))
                    val path = Path().apply { moveTo(down.position.x, down.position.y) }
                    paths.add(path)
                    tip = down.position
                    var previous = down.position
                    fun append(point: Offset) {
                        if (point == previous) return
                        val mid = (previous + point) / 2f
                        path.quadraticTo(previous.x, previous.y, mid.x, mid.y)
                        raw.add(StrokePoint(point.x, point.y))
                        previous = point
                        tip = point
                    }
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed) {
                            reset()
                            return@awaitEachGesture
                        }
                        change.historical.forEach { append(it.position) }
                        append(change.position)
                        change.consume()
                    } while (change.pressed)
                    path.lineTo(previous.x, previous.y)
                    strokes.add(raw)
                    phase = PadPhase.CHECKING
                }
            }) {
                if (phase == PadPhase.IDLE) {
                    val x = 20.dp.toPx()
                    val top = (if (card) 80.dp else 128.dp).toPx().coerceAtMost(size.height * .32f)
                    val bottom = size.height - (if (card) 48.dp else 95.dp).toPx().coerceAtMost(size.height * .24f)
                    val arm = 16.dp.toPx()
                    val spacing = 20.dp.toPx()
                    val centre = Offset(size.width / 2, (top + bottom) / 2)
                    for (row in -3..3) for (column in -3..3) {
                        val distance = row * row + column * column
                        if (distance < 13) drawCircle(accent.copy(alpha = .16f - distance * .01f),
                            .9.dp.toPx(), centre + Offset(column * spacing, row * spacing))
                    }
                    for (left in listOf(true, false)) for (upper in listOf(true, false)) {
                        val anchor = Offset(if (left) x else size.width - x, if (upper) top else bottom)
                        drawLine(accent, anchor, anchor + Offset(if (left) arm else -arm, 0f), 2.5.dp.toPx(), StrokeCap.Round)
                        drawLine(accent, anchor, anchor + Offset(0f, if (upper) arm else -arm), 2.5.dp.toPx(), StrokeCap.Round)
                    }
                } else {
                    val width = strokeWidth.coerceAtLeast(1f)
                    for (path in paths) {
                        drawPath(path, ink, style = inkStroke)
                    }
                    tip?.let { drawCircle(ink, width * .5f, it) }
                }
            }
            if (phase == PadPhase.SUCCESS) {
                Canvas(Modifier.fillMaxSize()) {
                    val height = size.height * .22f
                    val y = waveY * size.height - height / 2
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = .30f),
                        Color.Transparent), startY = y, endY = y + height), Offset(0f, y), Size(size.width, height))
                }
            }
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) { PadChipButton(Icons.Filled.Close, "Close") { leave(onClose) } }
                if (phase != PadPhase.SUCCESS) com.ibfavas.flick.ui.FlickWordmark(Modifier.align(Alignment.Center).background(Color(0xef101011), CircleShape).padding(4.dp))
                Box(Modifier.align(Alignment.CenterEnd)) { PadChipButton(Icons.Filled.Settings, "Settings") { leave(onOpenSettings) } }
            }
            if (phase == PadPhase.IDLE) {
                val hint = if (card) Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                    else Modifier.align(Alignment.TopCenter).padding(top = 70.dp)
                Text("Draw a gesture", hint.background(Color.Black.copy(alpha = .46f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 4.dp), color = Color.White,
                    style = MaterialTheme.typography.labelLarge)
            }
            AnimatedVisibility(visible = phase == PadPhase.SUCCESS,
                modifier = Modifier.align(Alignment.TopCenter).padding(start = 62.dp, end = 62.dp, top = 20.dp),
                enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut()) {
                val gesture = target ?: return@AnimatedVisibility
                Row(Modifier.background(Color.Black.copy(alpha = .55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionIcon(gesture, size = 24.dp, iconSize = 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Launching $targetName", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
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
            .background(Color.Black.copy(alpha = .55f))
            .border(1.dp, Color.White.copy(alpha = .18f), CircleShape)
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
