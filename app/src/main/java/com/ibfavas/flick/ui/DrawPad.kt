package com.ibfavas.flick.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.ibfavas.flick.gesture.StrokePoint

/**
 * A full-size drawing pad. The user draws one stroke; when the finger lifts,
 * [onStrokeFinished] receives the captured points and the pad clears.
 */
@Composable
fun DrawPad(
    strokeColor: Color,
    strokeWidth: Float,
    modifier: Modifier = Modifier,
    onStrokeFinished: (List<StrokePoint>) -> Unit
) {
    var points by remember { mutableStateOf(listOf<Offset>()) }
    var currentStroke by remember { mutableStateOf(listOf<Offset>()) }

    Box(
        modifier = modifier
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentStroke = listOf(offset)
                        points = listOf(offset)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentStroke = currentStroke + change.position
                        points = points + change.position
                    },
                    onDragEnd = {
                        val captured = points.map { StrokePoint(it.x, it.y) }
                        currentStroke = emptyList()
                        points = emptyList()
                        if (captured.size >= 2) onStrokeFinished(captured)
                    },
                    onDragCancel = {
                        currentStroke = emptyList()
                        points = emptyList()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (points.size >= 2) {
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
}
