package com.ibfavas.flick.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

/**
 * A drawing pad that captures multi-stroke gestures. Draw a stroke, lift,
 * and draw again to add another stroke to the same gesture, just like
 * drawing a circle and then a line to make a Q. When no new stroke starts
 * within [STROKE_TIMEOUT_MS] the gesture finishes and [onGestureFinished]
 * receives all strokes. Done finishes immediately, Clear wipes the pad.
 * Every stroke drawn so far stays visible.
 */
@Composable
fun DrawPad(
    strokeColor: Color,
    strokeWidth: Float,
    modifier: Modifier = Modifier,
    onGestureFinished: (List<List<StrokePoint>>) -> Unit,
    onCleared: () -> Unit = {}
) {
    var strokes by remember { mutableStateOf(listOf<List<Offset>>()) }
    var currentStroke by remember { mutableStateOf(listOf<Offset>()) }
    val scope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }

    fun finishGesture() {
        timerJob?.cancel()
        timerJob = null
        val done = strokes
        strokes = emptyList()
        currentStroke = emptyList()
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
        onCleared()
    }

    fun strokeLength(points: List<Offset>): Float {
        var length = 0f
        for (i in 1 until points.size) length += (points[i] - points[i - 1]).getDistance()
        return length
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            timerJob?.cancel()
                            timerJob = null
                            currentStroke = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentStroke = currentStroke + change.position
                        },
                        onDragEnd = {
                            if (currentStroke.size >= 2 && strokeLength(currentStroke) > 4f) {
                                strokes = strokes + listOf(currentStroke)
                            }
                            currentStroke = emptyList()
                            if (strokes.isNotEmpty()) armTimer()
                        },
                        onDragCancel = {
                            currentStroke = emptyList()
                            if (strokes.isNotEmpty()) armTimer()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val visible = strokes + listOf(currentStroke).filter { it.size >= 2 }
                for (points in visible) {
                    if (points.size < 2) continue
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                    }
                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (strokes.isEmpty()) {
                    "Draw a gesture. Lift and draw again to add a stroke."
                } else {
                    val count = strokes.size
                    "$count stroke${if (count == 1) "" else "s"} so far. Draw again, or tap Done."
                },
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { finishGesture() },
                enabled = strokes.isNotEmpty()
            ) {
                Text("Done", color = MaterialTheme.colorScheme.primary)
            }
            TextButton(
                onClick = { clearAll() },
                enabled = strokes.isNotEmpty() || currentStroke.isNotEmpty()
            ) {
                Text("Clear", color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}
