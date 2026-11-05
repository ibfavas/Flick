package com.ibfavas.flick.gesture

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A single point of a drawn stroke.
 */
data class StrokePoint(val x: Float, val y: Float)

/**
 * The $1 Unistroke Recognizer (Wobbrock et al., 2007), implemented from scratch
 * in pure Kotlin, plus its multi-stroke generalisation, the $N Recognizer
 * (Anthony & Wobbrock, 2010). No ML model, no dependency.
 *
 * $1 pipeline: resample to [RESAMPLE_COUNT] points, rotate to the indicative
 * angle, scale to a square, translate to the origin, then score candidates by
 * average path distance.
 *
 * $N generalisation: a gesture is a list of strokes. To normalise it, the
 * strokes are joined in order into one path and the $1 pipeline runs on that
 * path. A template is stored as its raw strokes; at match time every ordering
 * of its strokes is tried (a circle drawn first and a line second matches
 * whether the user draws the circle or the line first), and the best
 * ordering wins. Single-stroke gestures behave exactly as $1.
 */
object Recognizer {

    /**
     * Accuracy tuning (v1.0.8, measured with tools/recognizer-test on
     * 390 varied copies of 13 shapes plus 100 unknown shapes and random
     * walks, all seeded). The harness runs two suites: SINGLE (one
     * template per gesture, the v1.0.7 behaviour) and MULTI (three
     * samples per gesture, the v1.0.8 behaviour). More templates mean
     * more chances for an unknown shape to match something, so the
     * constants below are tuned for the MULTI suite first:
     *
     * - Resample to 96 points (was 64): finer shape detail, so similar
     *   shapes are less likely to collide.
     * - Default threshold 0.87 (was 0.85 in v1.0.7): correct matches
     *   score 0.92 and up even when sloppy, rotated or rescaled, while
     *   scribbles and unknown shapes mostly score 0.70-0.85. A
     *   low-confidence match must show "no match", never launch the
     *   wrong app.
     * - Stroke-count penalty: a candidate drawn with a different number
     *   of strokes than the template loses 0.12. A one-stroke circle
     *   must not match a two-stroke Q, and vice versa. Small enough
     *   that an accidental extra lift (one stroke split in two) still
     *   passes at ~0.86.
     * - Orientation penalty: the indicative-angle rotation makes a "+"
     *   and an "x" identical, so a plus matched an x template at 0.98.
     *   When both joined paths are open (not closed shapes like a
     *   circle), the score loses up to 0.20 for a large difference in
     *   indicative angle (none under 10 degrees of tilt, full at 40).
     *   Closed shapes skip it, because their start point is arbitrary.
     *   v1.0.8 raised the cap from 0.15 to 0.20 and tightened the
     *   free/full band from 15/45 to 10/40: with three samples per
     *   gesture the plus-vs-x confusion was the main source of false
     *   matches, and 0.20 is what pushes MULTI false matches from 8%
     *   (at the v1.0.7 constants) to 3%. 0.25 was tried and kills real
     *   matches (SINGLE correct fell to 83%).
     *
     * v1.0.8 measured results (seeded harness, 13 shapes):
     * - SINGLE: 386/390 correct (98%, the 4 misses are no-matches,
     *   never a wrong launch), 0/100 false matches (0%).
     * - MULTI (3 samples): 390/390 correct (100%), 3/100 false
     *   matches (3%).
     * - v1.0.7 constants for comparison: SINGLE 390/390 (100%),
     *   4/100 false (4%); MULTI 390/390 (100%), 8/100 false (8%).
     * So v1.0.8 trades 4 single-sample no-matches for a false-match
     * rate under the v1.0.7 4% on both suites.
     * - Minimum size: a gesture smaller than 30 px across or shorter
     *   than 60 px of path is an accidental touch, not a gesture.
     */
    const val RESAMPLE_COUNT = 96
    const val DEFAULT_THRESHOLD = 0.87f
    private const val SQUARE_SIZE = 250f
    private const val HALF_DIAGONAL = 0.5f * 353.55f // 0.5 * sqrt(250^2 + 250^2)
    private const val MAX_PERMUTED_STROKES = 4
    private const val STROKE_COUNT_PENALTY = 0.12f
    private const val ORIENTATION_PENALTY_MAX = 0.20f
    private const val ORIENTATION_FREE_DEGREES = 10f
    private const val ORIENTATION_FULL_DEGREES = 40f
    private const val MIN_GESTURE_SIZE_PX = 30f
    private const val MIN_GESTURE_LENGTH_PX = 60f

    data class Template(val name: String, val points: List<StrokePoint>)

    /** A multi-stroke template: a name and its drawn strokes (raw points). */
    data class MultiTemplate(val name: String, val strokes: List<List<StrokePoint>>)

    data class Match(val template: Template, val score: Float)

    /** Normalize a raw stroke into canonical form. */
    fun normalize(raw: List<StrokePoint>): List<StrokePoint> {
        if (raw.size < 2) return raw
        var points = resample(raw, RESAMPLE_COUNT)
        val angle = indicativeAngle(points)
        points = rotateBy(points, -angle)
        points = scaleTo(points, SQUARE_SIZE)
        points = translateTo(points, StrokePoint(0f, 0f))
        return points
    }

    /**
     * Normalize a multi-stroke gesture: join the strokes in order into one
     * path, then run the $1 pipeline on it. Returns empty if there are not
     * enough points to form a gesture.
     */
    fun normalizeMulti(strokes: List<List<StrokePoint>>): List<StrokePoint> {
        val joined = strokes.filter { it.size >= 2 }.flatten()
        if (joined.size < 2) return emptyList()
        return normalize(joined)
    }

    /**
     * Every normalized variant of a template, one per stroke ordering,
     * each paired with the raw joined path of that ordering (needed for
     * the orientation penalty). With more than [MAX_PERMUTED_STROKES]
     * strokes only the drawn order is used, to keep the permutation
     * count bounded. Identical variants are de-duplicated.
     */
    fun templateVariants(strokes: List<List<StrokePoint>>): List<Pair<List<StrokePoint>, List<StrokePoint>>> {
        val usable = strokes.filter { it.size >= 2 }
        if (usable.isEmpty()) return emptyList()
        if (usable.size == 1) {
            val joined = usable[0]
            return listOf(normalize(joined) to joined)
        }
        val orders: List<List<List<StrokePoint>>> =
            if (usable.size <= MAX_PERMUTED_STROKES) permutations(usable) else listOf(usable)
        val seen = LinkedHashSet<List<StrokePoint>>()
        val out = mutableListOf<Pair<List<StrokePoint>, List<StrokePoint>>>()
        for (order in orders) {
            val normalized = normalizeMulti(order)
            if (normalized.isNotEmpty() && seen.add(normalized)) {
                out.add(normalized to order.flatten())
            }
        }
        return out
    }

    /** Score [candidate] against a normalized [template]. Higher is better (0..1). */
    fun score(template: List<StrokePoint>, candidate: List<StrokePoint>): Float {
        if (template.isEmpty() || candidate.isEmpty()) return 0f
        val distance = pathDistance(template, candidate) / candidate.size
        return 1f - (distance / HALF_DIAGONAL)
    }

    /** Find the best matching template for a raw stroke, or null if none scores above [threshold]. */
    fun recognize(
        raw: List<StrokePoint>,
        templates: List<Template>,
        threshold: Float = DEFAULT_THRESHOLD
    ): Match? {
        if (raw.size < 2 || templates.isEmpty()) return null
        val candidate = normalize(raw)
        var best: Match? = null
        for (template in templates) {
            val s = score(template.points, candidate)
            if (best == null || s > best.score) best = Match(template, s)
        }
        return best?.takeIf { it.score >= threshold }
    }

    /**
     * Find the best matching multi-stroke template for raw drawn strokes, or
     * null if none scores above [threshold]. The candidate is normalised in
     * its drawn order; each template is tried in every stroke ordering.
     * A candidate with a different stroke count than the template, or a
     * large indicative-angle difference on open shapes, is penalised, so
     * near-misses fail cleanly instead of launching the wrong app.
     */
    fun recognizeMulti(
        rawStrokes: List<List<StrokePoint>>,
        templates: List<MultiTemplate>,
        threshold: Float = DEFAULT_THRESHOLD
    ): Match? {
        val usable = rawStrokes.filter { it.size >= 2 }
        if (usable.isEmpty() || templates.isEmpty()) return null
        if (!isBigEnough(usable)) return null
        val candidate = normalizeMulti(usable)
        if (candidate.isEmpty()) return null
        val candidateRaw = usable.flatten()
        val candidateAngle = indicativeAngle(candidateRaw)
        val candidateOpen = isOpenPath(candidateRaw)
        var best: Match? = null
        for (template in templates) {
            val templateStrokeCount = template.strokes.count { it.size >= 2 }
            val countPenalty =
                if (templateStrokeCount != usable.size) STROKE_COUNT_PENALTY else 0f
            for ((variant, variantRaw) in templateVariants(template.strokes)) {
                var s = score(variant, candidate) - countPenalty
                if (candidateOpen && isOpenPath(variantRaw)) {
                    val diff = angleDifference(candidateAngle, indicativeAngle(variantRaw))
                    val excess = (diff - ORIENTATION_FREE_DEGREES).coerceIn(
                        0f,
                        ORIENTATION_FULL_DEGREES - ORIENTATION_FREE_DEGREES
                    )
                    s -= ORIENTATION_PENALTY_MAX * excess /
                        (ORIENTATION_FULL_DEGREES - ORIENTATION_FREE_DEGREES)
                }
                if (best == null || s > best.score) {
                    best = Match(Template(template.name, variant), s)
                }
            }
        }
        return best?.takeIf { it.score >= threshold }
    }

    /** Smallest absolute difference between two angles, in degrees (0..180). */
    private fun angleDifference(a: Float, b: Float): Float {
        var diff = kotlin.math.abs(a - b) * 180f / Math.PI.toFloat()
        diff %= 360f
        if (diff > 180f) diff = 360f - diff
        return diff
    }

    /**
     * True if the path is open: its ends are far apart relative to its
     * size. Closed shapes (a circle, a square) end where they started,
     * so their indicative angle depends on the arbitrary start point and
     * must not be compared.
     */
    private fun isOpenPath(points: List<StrokePoint>): Boolean {
        if (points.size < 2) return false
        val first = points.first()
        val last = points.last()
        val endGap = distance(first, last)
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val diagonal = hypot((maxX - minX).toDouble(), (maxY - minY).toDouble()).toFloat()
        return diagonal > 0f && endGap > diagonal * 0.25f
    }

    /** True if the gesture is big enough to be deliberate, not an accidental touch. */
    private fun isBigEnough(strokes: List<List<StrokePoint>>): Boolean {
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var length = 0f
        for (stroke in strokes) {
            for (p in stroke) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
            length += pathLength(stroke)
        }
        val size = maxOf(maxX - minX, maxY - minY)
        return size >= MIN_GESTURE_SIZE_PX && length >= MIN_GESTURE_LENGTH_PX
    }

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        val out = mutableListOf<List<T>>()
        fun permute(prefix: List<T>, rest: List<T>) {
            if (rest.isEmpty()) {
                out.add(prefix)
                return
            }
            for (i in rest.indices) {
                permute(prefix + rest[i], rest.filterIndexed { idx, _ -> idx != i })
            }
        }
        permute(emptyList(), items)
        return out
    }

    private fun resample(points: List<StrokePoint>, n: Int): List<StrokePoint> {
        val interval = pathLength(points) / (n - 1)
        var dist = 0f
        val src = points.toMutableList()
        val out = mutableListOf(src.first())
        var i = 1
        while (i < src.size) {
            val d = distance(src[i - 1], src[i])
            if (dist + d >= interval) {
                val t = (interval - dist) / d
                val q = StrokePoint(
                    src[i - 1].x + t * (src[i].x - src[i - 1].x),
                    src[i - 1].y + t * (src[i].y - src[i - 1].y)
                )
                out.add(q)
                src.add(i, q)
                dist = 0f
            } else {
                dist += d
            }
            i++
        }
        while (out.size < n) out.add(src.last())
        return out.take(n)
    }

    private fun indicativeAngle(points: List<StrokePoint>): Float {
        val centroid = centroid(points)
        return atan2(centroid.y - points.first().y, centroid.x - points.first().x)
    }

    private fun rotateBy(points: List<StrokePoint>, angle: Float): List<StrokePoint> {
        val centroid = centroid(points)
        val cosA = cos(angle)
        val sinA = sin(angle)
        return points.map { p ->
            val dx = p.x - centroid.x
            val dy = p.y - centroid.y
            StrokePoint(
                dx * cosA - dy * sinA + centroid.x,
                dx * sinA + dy * cosA + centroid.y
            )
        }
    }

    private fun scaleTo(points: List<StrokePoint>, size: Float): List<StrokePoint> {
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val width = maxX - minX
        val height = maxY - minY
        val scale = if (width > height) size / width else size / height
        return points.map { StrokePoint(it.x * scale, it.y * scale) }
    }

    private fun translateTo(points: List<StrokePoint>, to: StrokePoint): List<StrokePoint> {
        val centroid = centroid(points)
        return points.map { StrokePoint(it.x + to.x - centroid.x, it.y + to.y - centroid.y) }
    }

    private fun centroid(points: List<StrokePoint>): StrokePoint {
        var x = 0f
        var y = 0f
        for (p in points) {
            x += p.x
            y += p.y
        }
        return StrokePoint(x / points.size, y / points.size)
    }

    private fun pathLength(points: List<StrokePoint>): Float {
        var length = 0f
        for (i in 1 until points.size) length += distance(points[i - 1], points[i])
        return length
    }

    private fun pathDistance(a: List<StrokePoint>, b: List<StrokePoint>): Float {
        var total = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) total += distance(a[i], b[i])
        return total
    }

    private fun distance(a: StrokePoint, b: StrokePoint): Float =
        hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()

    /** Serialize normalized points to a compact string: "x,y;x,y;..." */
    fun pointsToString(points: List<StrokePoint>): String =
        points.joinToString(";") { "${it.x},${it.y}" }

    /** Parse [pointsToString] output back into points. */
    fun pointsFromString(raw: String): List<StrokePoint> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val bits = part.split(",")
            if (bits.size != 2) null
            else {
                val x = bits[0].toFloatOrNull()
                val y = bits[1].toFloatOrNull()
                if (x == null || y == null) null else StrokePoint(x, y)
            }
        }
    }

    /**
     * Serialize strokes: strokes separated by "|", points by ";" as "x,y".
     * Example: "10.0,20.0;30.0,40.0|50.0,60.0;70.0,80.0" is two strokes.
     * Templates store raw drawn strokes; normalisation happens at match time.
     */
    fun strokesToString(strokes: List<List<StrokePoint>>): String =
        strokes.joinToString("|") { pointsToString(it) }

    /**
     * Parse [strokesToString] output back into strokes. Strings with no "|"
     * are old single-stroke templates (stored by v1.0.4 as one normalized
     * stroke) and parse as exactly one stroke, so they keep working.
     */
    fun strokesFromString(raw: String): List<List<StrokePoint>> {
        if (raw.isBlank()) return emptyList()
        return if ('|' in raw) {
            raw.split("|").map { pointsFromString(it) }.filter { it.size >= 2 }
        } else {
            val single = pointsFromString(raw)
            if (single.isEmpty()) emptyList() else listOf(single)
        }
    }
}
