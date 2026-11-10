package com.ibfavas.flick.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ibfavas.flick.gesture.StrokePoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Time with no new stroke after which the gesture is finished automatically. */
private const val STROKE_TIMEOUT_MS = 900L

/** How long the "Draw a gesture" hint stays visible before fading away. */
private const val HINT_VISIBLE_MS = 2000L

/*
 * The live stroke's curve, built one segment at a time as
 * the finger moves. Raw touch points arrive in straight
 * segments, which look jagged, so the curve is a quadratic
 * Bezier through the midpoint of each pair of samples. The
 * old code rebuilt the whole curve from every point on each
 * drag event, O(n) per event, so long strokes stuttered.
 * Now each new point appends one segment to the path
 * already on screen, the same geometry for a fraction of
 * the work. A single point becomes a tiny round dot.
 *
 * Appending one segment: from the previous raw point, a
 * quadratic through the midpoint of (previous, new), then
 * the new point becomes the previous one. At the end of
 * the stroke a final line to the last point completes the
 * curve.
 */
private fun appendToPath(path: Path, prev: Offset, next: Offset) {
    val mid = Offset((prev.x + next.x) / 2f, (prev.y + next.y) / 2f)
    path.quadraticTo(prev.x, prev.y, mid.x, mid.y)
}

/*
 * A drawing pad that captures multi-stroke gestures. Draw a
 * stroke, lift, and draw again to add another stroke to the
 * same gesture, just like drawing a circle and then a line
 * to make a Q. When no new stroke starts within
 * STROKE_TIMEOUT_MS the gesture finishes and
 * onGestureFinished receives all strokes. Done finishes
 * immediately, Clear wipes the pad. Every stroke drawn so
 * far stays visible.
 *
 * The raw touch samples, including the MotionEvent
 * historical points between them, are drawn as a quadratic
 * Bezier curve with round caps and joins, so the stroke
 * bends fluidly while the finger moves. Finished strokes
 * keep a cached smoothed Path; the live stroke's path is
 * extended one segment per new point, never rebuilt from
 * scratch.
 *
 * The stroke colour and width come from Settings (cyan,
 * 14px by default). The Done/Clear chips are part of the
 * pad's chrome in the app, but the transparent overlay pad
 * hides them (showControls = false): there a gesture is
 * recognised automatically when the timer expires, so the
 * buttons have nothing to do. The hint is a short "Draw a
 * gesture" that fades after a couple of seconds, and the
 * Done/Clear chips only appear while a stroke is actually
 * on screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawPad(
    modifier: Modifier = Modifier,
    onGestureFinished: (List<List<StrokePoint>>) -> Unit,
    onCleared: () -> Unit = {},
    /** Called once, when the first stroke of a gesture starts. */
    onFirstStroke: () -> Unit = {},
    // Draw a dark outline under the stroke so it stays readable
    // over any background. Used by the frosted-glass pad, which
    // sits on top of whatever the user was doing.
    strokeOutline: Boolean = false,
    /** The stroke colour, from Settings (cyan by default). */
    strokeColor: Color = FlickBrand.StrokeColor,
    /** The stroke width in px, from Settings (14px by default). */
    strokeWidth: Float = FlickBrand.StrokeWidth,
    /** Show the Done/Clear chips. False on the overlay pad. */
    showControls: Boolean = true
) {
    var strokes by remember { mutableStateOf(listOf<List<Offset>>()) }
    var currentStroke by remember { mutableStateOf(listOf<Offset>()) }
    val strokePaths = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    // The last raw point of the live stroke: the anchor for the next
    // appended curve segment. State, not a plain local, so it survives
    // the recompositions that each new point triggers.
    var lastRaw by remember { mutableStateOf<Offset?>(null) }
    val scope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var showHint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(HINT_VISIBLE_MS)
        showHint = false
    }

    fun finishGesture() {
        timerJob?.cancel()
        timerJob = null
        val done = strokes
        strokes = emptyList()
        currentStroke = emptyList()
        strokePaths.clear()
        currentPath = null
        lastRaw = null
        val totalPoints = done.sumOf { it.size }
        if (done.isNotEmpty() && totalPoints >= 2) {
            onGestureFinished(done.map { points -> points.map { StrokePoint(it.x, it.y) } })
        }
    }

    fun armTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay(STROKE_TIMEOUT_MS)
            finishGesture()
        }
    }

    fun clearAll() {
        timerJob?.cancel()
        timerJob = null
        strokes = emptyList()
        currentStroke = emptyList()
        strokePaths.clear()
        currentPath = null
        lastRaw = null
        onCleared()
    }

    fun strokeLength(points: List<Offset>): Float {
        var length = 0f
        for (i in 1 until points.size) length += (points[i] - points[i - 1]).getDistance()
        return length
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (strokes.isEmpty() && currentStroke.isEmpty()) {
                                onFirstStroke()
                            }
                            timerJob?.cancel()
                            timerJob = null
                            currentStroke = listOf(offset)
                            currentPath = Path().apply { moveTo(offset.x, offset.y) }
                            lastRaw = offset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            // Historical points are the samples the
                            // system recorded between this event and
                            // the last one: without them the curve
                            // jumps in straight steps on fast strokes.
                            val points = change.historical.map { it.position } +
                                change.position
                            // Extend the live path one segment per new
                            // point, instead of rebuilding the whole
                            // curve.
                            for (point in points) {
                                val prev = lastRaw ?: point
                                currentPath?.let { appendToPath(it, prev, point) }
                                lastRaw = point
                            }
                            currentStroke = currentStroke + points
                        },
                        onDragEnd = {
                            // Complete the curve to the last point,
                            // exactly as the old whole-path builder did.
                            val last = lastRaw
                            if (last != null) currentPath?.lineTo(last.x, last.y)
                            if (currentStroke.size >= 2 && strokeLength(currentStroke) > 4f) {
                                strokes = strokes + listOf(currentStroke)
                                currentPath?.let { strokePaths.add(it) }
                            }
                            currentStroke = emptyList()
                            currentPath = null
                            lastRaw = null
                            if (strokes.isNotEmpty()) armTimer()
                        },
                        onDragCancel = {
                            currentStroke = emptyList()
                            currentPath = null
                            lastRaw = null
                            if (strokes.isNotEmpty()) armTimer()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val outlineStyle = Stroke(
                    width = strokeWidth + 6f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                val strokeStyle = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                fun drawStroke(path: Path) {
                    if (strokeOutline) {
                        drawPath(
                            path = path,
                            color = Color.Black.copy(alpha = 0.35f),
                            style = outlineStyle
                        )
                    }
                    drawPath(path = path, color = strokeColor, style = strokeStyle)
                }
                for (path in strokePaths) drawStroke(path)
                if (currentStroke.size >= 2) {
                    currentPath?.let { drawStroke(it) }
                } else if (currentStroke.size == 1) {
                    // A lone touch point: show the dot under the finger.
                    if (strokeOutline) {
                        drawCircle(
                            Color.Black.copy(alpha = 0.35f),
                            radius = (strokeWidth + 6f) / 2f,
                            center = currentStroke[0]
                        )
                    }
                    drawCircle(
                        strokeColor,
                        radius = strokeWidth / 2f,
                        center = currentStroke[0]
                    )
                }
            }
        }
        // Short hint, gone after a couple of seconds. It never sits on
        // top of anything the user needs to read.
        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Draw a gesture",
                color = FlickBrand.TextWhite,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.35f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        // Done and Clear only exist while there is something to finish
        // or clear, and only where they are wanted: the overlay pad
        // (showControls = false) recognises automatically, so it has
        // no buttons at all. They sit above the hint's spot, so the
        // two never overlap.
        AnimatedVisibility(
            visible = showControls && strokes.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { finishGesture() },
                    modifier = Modifier.background(
                        FlickBrand.AmoledSurface.copy(alpha = 0.85f),
                        RoundedCornerShape(20.dp)
                    )
                ) {
                    Text("Done", color = accentColor())
                }
                TextButton(
                    onClick = { clearAll() },
                    modifier = Modifier.background(
                        FlickBrand.AmoledSurface.copy(alpha = 0.85f),
                        RoundedCornerShape(20.dp)
                    )
                ) {
                    Text("Clear", color = FlickBrand.TextWhite)
                }
            }
        }
    }
}
