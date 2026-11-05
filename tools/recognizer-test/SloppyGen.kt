import com.ibfavas.flick.gesture.StrokePoint
import kotlin.math.sin
import kotlin.random.Random

/**
 * Real-finger drawing transforms for the harness (v1.1.0). The shipped
 * harness was saturated on clean synthetic shapes (390/390), which
 * could not explain why recognition felt bad on a real phone. These
 * transforms model what a finger actually produces, and the SLOPPY and
 * REV suites in RecognizerTest use them.
 *
 * - Non-uniform point spacing: fast stretches come out sparse, slow
 *   stretches dense, because touch sampling is time-based.
 * - Hooks at stroke ends: the pen lands and lifts with a small
 *   doubling-back.
 * - Lateral wobble and bigger noise than the clean generator's ±3px.
 * - Random stroke-direction reversal: a circle drawn counter-clockwise
 *   or a line drawn right-to-left is the same gesture to a human.
 */

/** Reverse every stroke's points and the stroke order. */
fun reversedStrokes(strokes: List<List<StrokePoint>>): List<List<StrokePoint>> =
    strokes.reversed().map { it.reversed() }

/**
 * Make a clean synthetic shape look finger-drawn. When [reverse] is
 * true the whole gesture is drawn backwards as well.
 */
fun sloppy(strokes: List<List<StrokePoint>>, rnd: Random, reverse: Boolean): List<List<StrokePoint>> {
    val out = mutableListOf<List<StrokePoint>>()
    for (stroke in strokes) {
        var pts = stroke
        // 1. Non-uniform density: keep each point with a probability
        //    that varies along the stroke (fast = sparse, slow = dense).
        val speed = 0.4f + rnd.nextFloat() * 0.8f
        val kept = mutableListOf<StrokePoint>()
        var acc = 0f
        for (p in pts) {
            acc += speed
            if (acc >= 1f || p == pts.first() || p == pts.last()) { kept.add(p); acc = 0f }
        }
        pts = kept
        // 2. Hook at the start: a few points doubling back from the start.
        if (pts.size >= 4) {
            val d = StrokePoint(pts[1].x - pts[0].x, pts[1].y - pts[0].y)
            val hook = (1..3).map { i ->
                StrokePoint(pts[0].x - d.x * 0.25f * i, pts[0].y - d.y * 0.25f * i)
            }
            pts = hook.reversed() + pts
        }
        // 3. Lateral wobble + bigger noise.
        val phase = rnd.nextFloat() * 6.28f
        pts = pts.mapIndexed { i, p ->
            val wob = sin(i * 0.9f + phase) * 4f
            StrokePoint(
                p.x + wob + rnd.nextFloat() * 10f - 5f,
                p.y + wob * 0.6f + rnd.nextFloat() * 10f - 5f
            )
        }
        out.add(pts)
    }
    var result: List<List<StrokePoint>> = out
    if (reverse) result = reversedStrokes(result)
    return result
}
