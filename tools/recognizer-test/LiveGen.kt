import com.ibfavas.flick.gesture.StrokePoint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * LIVE-suite transforms (v1.2.0): model a LATER live draw of a gesture
 * versus the template saved when the gesture was created. The same hand
 * drawing the same gesture minutes later will not reproduce it exactly:
 *
 * - Combined scale (±20%), translation (±30 px) and rotation (±10°).
 *   The $1 pipeline normalises these away, so on their own they should
 *   not matter; they are here so the other transforms act on a
 *   realistically varied base, not on a pristine template.
 * - Lateral wobble (±5 px sinusoid) plus ±4 px noise: the hand shakes.
 * - Point-count / speed variation: touch sampling is time-based, so a
 *   faster later draw comes out sparser and non-uniformly dense (fast
 *   stretches sparse, slow stretches dense). The candidate keeps
 *   roughly 55-80% of the points, with a keep probability that varies
 *   along the stroke.
 * - Partial strokes: the finger lifts early, so the final stroke ends
 *   10-20% short; and in about half the multi-stroke draws, one other
 *   stroke also loses its last 10-15%. A truncated stroke is the one
 *   transform the $1 pipeline cannot normalise away: resampling
 *   stretches the shorter path to 96 points, so the candidate's points
 *   drift against the template's along the whole tail.
 *
 * Everything is driven by the caller's seeded Random, so runs are
 * reproducible and comparable across recognizer changes.
 */

/**
 * Which LIVE transforms to apply. All on by default; the ablation probe
 * switches individual ones off (or on alone) to find which transform
 * actually breaks recognition.
 */
data class LiveOpts(
    val partial: Boolean = true,
    val density: Boolean = true,
    val wobble: Boolean = true,
    val geometry: Boolean = true
)

/** Keep the leading (1 - frac) of a stroke by path length. */
private fun truncateByLength(stroke: List<StrokePoint>, frac: Float): List<StrokePoint> {
    var total = 0f
    for (i in 1 until stroke.size) total += distance(stroke[i - 1], stroke[i])
    val keepLength = total * (1f - frac)
    val out = mutableListOf(stroke.first())
    var acc = 0f
    for (i in 1 until stroke.size) {
        val d = distance(stroke[i - 1], stroke[i])
        if (acc + d > keepLength) break
        acc += d
        out.add(stroke[i])
    }
    return if (out.size < 4) stroke.take(4) else out
}

private fun distance(a: StrokePoint, b: StrokePoint): Float {
    val dx = (b.x - a.x).toDouble()
    val dy = (b.y - a.y).toDouble()
    return kotlin.math.hypot(dx, dy).toFloat()
}

fun live(strokes: List<List<StrokePoint>>, rnd: Random, opts: LiveOpts = LiveOpts()): List<List<StrokePoint>> {
    var out = strokes

    // 1. Partial strokes: truncate the final stroke by 10-20% of its
    //    PATH LENGTH, and (for multi-stroke gestures, half the time)
    //    one other stroke by 10-15%. Length-based, not point-count
    //    based: a finger lifting early shortens the stroke, and point
    //    density along a stroke is non-uniform, so cutting N% of the
    //    points would cut a much longer fraction of a long final
    //    segment than of the stroke as a whole.
    if (opts.partial) {
        val truncateOtherIdx =
            if (out.size > 1 && rnd.nextFloat() < 0.5f) rnd.nextInt(out.size - 1) else -1
        out = out.mapIndexed { idx, stroke ->
            val frac = when {
                idx == out.size - 1 -> 0.10f + rnd.nextFloat() * 0.10f
                idx == truncateOtherIdx -> 0.10f + rnd.nextFloat() * 0.05f
                else -> 0f
            }
            if (frac > 0f && stroke.size >= 8) truncateByLength(stroke, frac) else stroke
        }
    }

    // 2. Speed variation: non-uniform density. The keep probability
    //    varies sinusoidally along the stroke (fast stretches sparse,
    //    slow stretches dense), on top of an overall sparser draw.
    if (opts.density) {
        out = out.map { stroke ->
            val phase = rnd.nextFloat() * 6.28f
            val freq = 0.15f + rnd.nextFloat() * 0.35f
            val base = 0.55f + rnd.nextFloat() * 0.25f
            val kept = mutableListOf<StrokePoint>()
            for ((i, p) in stroke.withIndex()) {
                val prob = (base + 0.35f * sin(i * freq + phase)).coerceIn(0.25f, 0.95f)
                if (i == 0 || i == stroke.size - 1 || rnd.nextFloat() < prob) kept.add(p)
            }
            if (kept.size < 2) stroke else kept
        }
    }

    // 3. Lateral wobble + noise.
    if (opts.wobble) {
        out = out.map { stroke ->
            val phase = rnd.nextFloat() * 6.28f
            stroke.mapIndexed { i, p ->
                val wob = sin(i * 0.7f + phase) * 5f
                StrokePoint(
                    p.x + wob + rnd.nextFloat() * 8f - 4f,
                    p.y + wob * 0.6f + rnd.nextFloat() * 8f - 4f
                )
            }
        }
    }

    // 4. Scale ±20%, rotation ±10°, translation ±30 px.
    if (opts.geometry) {
        val scale = 0.80f + rnd.nextFloat() * 0.40f
        val rot = Math.toRadians((rnd.nextFloat() * 20f - 10f).toDouble()).toFloat()
        val tx = rnd.nextFloat() * 60f - 30f
        val ty = rnd.nextFloat() * 60f - 30f
        val c = cos(rot)
        val s = sin(rot)
        out = out.map { stroke ->
            stroke.map { p ->
                val x = p.x * scale
                val y = p.y * scale
                StrokePoint(tx + x * c - y * s, ty + x * s + y * c)
            }
        }
    }
    return out
}
