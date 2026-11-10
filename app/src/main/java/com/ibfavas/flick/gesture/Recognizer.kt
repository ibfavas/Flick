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

    /*
     * Tuning notes. The numbers below were measured with the
     * harness in tools/recognizer-test on synthetic shapes,
     * seeded, not on a real phone.
     *
     * $1/$N plus banded DTW, blended by max. The candidate is
     * tried as drawn and fully reversed, so a backwards draw
     * still matches. The orientation penalty compares line
     * direction modulo 180 for the reversed form only. The
     * winner must beat the runner-up by MARGIN or it is no
     * match. Pass 2 runs only when pass 1 finds nothing: an
     * open-ended DTW that can skip up to 24 template points at
     * either end, 10 px per skipped point, so a draw that ends
     * early can still match. Current numbers: threshold 0.87,
     * margin 0.025, orientation penalty max 0.28 (free under
     * 20 degrees, full at 40), stroke-count penalty 0.12.
     * 0.28 was picked because 0.27 loses multi LIVE matches
     * and 0.29 puts a wrong launch into the clean suite on
     * fresh seeds; 0.27 to 0.29 all measure the same on the
     * shipped harness. A truncated circle is still a 'c' by
     * shape and a truncated check still looks like a 'v', so
     * pass 2 rescues no-matches, it does not fix wrong
     * launches.
     */
    const val RESAMPLE_COUNT = 96
    const val DEFAULT_THRESHOLD = 0.87f
    private const val SQUARE_SIZE = 250f
    private const val HALF_DIAGONAL = 0.5f * 353.55f // 0.5 * sqrt(250^2 + 250^2)
    private const val MAX_PERMUTED_STROKES = 4
    private const val STROKE_COUNT_PENALTY = 0.12f
    private const val ORIENTATION_PENALTY_MAX = 0.28f
    private const val ORIENTATION_FREE_DEGREES = 20f
    private const val ORIENTATION_FULL_DEGREES = 40f
    private const val MIN_GESTURE_SIZE_PX = 30f
    private const val MIN_GESTURE_LENGTH_PX = 60f
    // Winner must beat the runner-up by this much, or the
    // match is ambiguous and returns no match.
    private const val MARGIN = 0.025f
    /** Sakoe-Chiba band for the DTW alignment, in points. */
    private const val DTW_BAND = 10
    // Open-ended DTW: how many template points the alignment
    // may skip at either end. A live draw that ends early covers
    // only a prefix of the template (or, reversed, only a
    // suffix); the skip lets the alignment end there instead of
    // forcing the last points onto the template's tail.
    // 24 of 96 points is a 25% truncation allowance.
    private const val DTW_MAX_SKIP = 24
    /** Cost, in normalised px, of each template point skipped by the open DTW. */
    private const val DTW_SKIP_PENALTY = 10f
    // Sakoe-Chiba band for the open-ended DTW, in points. Wider
    // than DTW_BAND: a truncated candidate drifts from the
    // diagonal by up to the skipped fraction, about 19 points
    // at 20% truncation.
    private const val DTW_OPEN_BAND = 28

    data class Template(val name: String, val points: List<StrokePoint>)

    /** A multi-stroke template: a name and its drawn strokes (raw points). */
    data class MultiTemplate(val name: String, val strokes: List<List<StrokePoint>>)

    data class Match(val template: Template, val score: Float, val source: MultiTemplate? = null)

    // One prepared template variant: the points to score
    // against, plus the raw path data the orientation penalty
    // needs (whether it is open, and its indicative angle).
    data class PreparedVariant(
        val points: List<StrokePoint>,
        val raw: List<StrokePoint>,
        val open: Boolean,
        val angle: Float
    )

    data class PreparedTemplate(
        val source: MultiTemplate,
        val name: String,
        val strokeCount: Int,
        val variants: List<PreparedVariant>
    )

    // Precompute every variant of every template once. Reused
    // until the gesture library changes; callers keep it in a
    // remember keyed on the gesture list.
    fun prepareTemplates(templates: List<MultiTemplate>): List<PreparedTemplate> =
        templates.map { template ->
            val variants = templateVariants(template.strokes).map { (points, raw) ->
                PreparedVariant(points, raw, isOpenPath(raw), indicativeAngle(raw))
            }
            PreparedTemplate(
                template,
                template.name,
                template.strokes.count { it.size >= 2 },
                variants
            )
        }

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

    // Join the strokes in order into one path, then run the $1
    // pipeline on it. Returns empty if there are not enough
    // points to form a gesture.
    fun normalizeMulti(strokes: List<List<StrokePoint>>): List<StrokePoint> {
        val joined = strokes.filter { it.size >= 2 }.flatten()
        if (joined.size < 2) return emptyList()
        return normalize(joined)
    }

    // Every normalized variant of a template, one per stroke
    // ordering, each paired with the raw joined path of that
    // ordering (needed for the orientation penalty). With more
    // than MAX_PERMUTED_STROKES strokes only the drawn order is
    // used, to keep the permutation count bounded. Identical
    // variants are de-duplicated.
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

    /*
     * Best match for raw drawn strokes, or null. Templates
     * prepared once by prepareTemplates. The candidate is tried
     * as drawn and fully reversed, so a gesture drawn backwards
     * still matches; the orientation penalty compares line
     * orientation modulo 180 for the reversed form only. Each
     * variant is scored by the better of the $N path-distance
     * score and a banded-DTW score. A different stroke count
     * than the template, or a large indicative-angle difference
     * on open shapes, is penalised, so near-misses fail
     * cleanly. The margin rule applies: the winner must beat
     * the best other gesture by MARGIN or the match is
     * ambiguous. Pass 2 runs only when pass 1 finds nothing,
     * re-scoring with the open-ended DTW, which tolerates a
     * draw that ends early. As a rescue it can only turn a
     * no-match into a match, never change a match pass 1 made.
     */
    fun recognizeMultiPrepared(
        rawStrokes: List<List<StrokePoint>>,
        prepared: List<PreparedTemplate>,
        threshold: Float = DEFAULT_THRESHOLD
    ): Match? {
        val usable = rawStrokes.filter { it.size >= 2 }
        if (usable.isEmpty() || prepared.isEmpty()) return null
        if (!isBigEnough(usable)) return null
        return matchPrepared(usable, prepared, threshold, openDtw = false)
            ?: matchPrepared(usable, prepared, threshold, openDtw = true)
    }

    // One scoring pass over the prepared templates. openDtw
    // false is the banded DTW scorer, true is the open-ended
    // rescue scorer.
    private fun matchPrepared(
        usable: List<List<StrokePoint>>,
        prepared: List<PreparedTemplate>,
        threshold: Float,
        openDtw: Boolean
    ): Match? {
        // Candidate forms: as drawn, and fully reversed. A reversed
        // form is the same gesture drawn backwards.
        val forms = listOf(false to usable, true to reversedStrokes(usable))
        val perName = HashMap<String, Float>()
        var bestName: String? = null
        var bestScore = -1f
        var bestTemplate: Template? = null
        var bestSource: MultiTemplate? = null
        for ((reversed, form) in forms) {
            val candidate = normalizeMulti(form)
            if (candidate.isEmpty()) continue
            val raw = form.flatten()
            val candidateAngle = indicativeAngle(raw)
            val candidateOpen = isOpenPath(raw)
            for (template in prepared) {
                val countPenalty =
                    if (template.strokeCount != usable.size) STROKE_COUNT_PENALTY else 0f
                for (variant in template.variants) {
                    var s = score(variant.points, candidate) - countPenalty
                    s = maxOf(s, dtwScore(variant.points, candidate, openDtw) - countPenalty)
                    if (candidateOpen && variant.open) {
                        var diff = angleDifference(candidateAngle, variant.angle)
                        // A line has no direction: for the reversed
                        // form, orientation is modulo 180, or every
                        // backwards gesture would eat the full
                        // penalty. The as-drawn form keeps the plain
                        // difference, so a shape rotated 180 degrees
                        // stays a different shape.
                        if (reversed) diff = minOf(diff, 180f - diff)
                        val excess = (diff - ORIENTATION_FREE_DEGREES).coerceIn(
                            0f,
                            ORIENTATION_FULL_DEGREES - ORIENTATION_FREE_DEGREES
                        )
                        s -= ORIENTATION_PENALTY_MAX * excess /
                            (ORIENTATION_FULL_DEGREES - ORIENTATION_FREE_DEGREES)
                    }
                    val prev = perName[template.name] ?: -1f
                    if (s > prev) perName[template.name] = s
                    if (s > bestScore) {
                        bestScore = s
                        bestName = template.name
                        bestTemplate = Template(template.name, variant.points)
                        bestSource = template.source
                    }
                }
            }
        }
        if (bestName == null || bestScore < threshold) return null
        val second = perName.entries
            .filter { it.key != bestName }
            .maxOfOrNull { it.value } ?: 0f
        if (bestScore - second < MARGIN) return null
        return Match(bestTemplate!!, bestScore, bestSource)
    }

    // Same as recognizeMultiPrepared, but prepares the
    // templates inline, so the harness tests the shipped
    // scoring path. The app caches the prepared library and
    // calls recognizeMultiPrepared instead.
    fun recognizeMulti(
        rawStrokes: List<List<StrokePoint>>,
        templates: List<MultiTemplate>,
        threshold: Float = DEFAULT_THRESHOLD
    ): Match? = recognizeMultiPrepared(rawStrokes, prepareTemplates(templates), threshold)

    /** Every stroke's points reversed, and the stroke order reversed. */
    private fun reversedStrokes(strokes: List<List<StrokePoint>>): List<List<StrokePoint>> =
        strokes.reversed().map { it.reversed() }

    /*
     * Banded DTW between two normalized 96-point sequences.
     * Unlike the $N pointwise distance, DTW lets the alignment
     * stretch locally, which is what a finger does: fast
     * stretches come out sparse, slow stretches dense, and the
     * shape is the same. The band keeps the cost near-linear,
     * and the forced endpoint-to-endpoint alignment is exactly
     * right for a complete draw. A truncated draw is rescued
     * by the open-ended DTW in a second pass that only runs
     * when this one finds no match.
     */
    private fun dtwBandedDistance(a: List<StrokePoint>, b: List<StrokePoint>): Float {
        val n = a.size
        val m = b.size
        val inf = Float.MAX_VALUE / 4
        var prev = FloatArray(m + 1) { inf }
        var cur = FloatArray(m + 1) { inf }
        prev[0] = 0f
        for (i in 1..n) {
            cur[0] = inf
            val lo = maxOf(1, i - DTW_BAND)
            val hi = minOf(m, i + DTW_BAND)
            for (j in lo..hi) {
                val cost = distance(a[i - 1], b[j - 1])
                cur[j] = cost + minOf(prev[j], cur[j - 1], prev[j - 1])
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[m]
    }

    /*
     * Open-ended DTW between two normalized 96-point sequences.
     * Unlike the banded DTW above it lets the alignment skip
     * template points at either end, at DTW_SKIP_PENALTY per
     * point: a live draw that ends early covers only a prefix
     * of the template (in the reversed form, only a suffix),
     * and forcing its last points onto the template's tail was
     * what truncation punished. The skip is bounded
     * (DTW_MAX_SKIP points per end) and penalised, so a short
     * shape cannot silently become a longer one. Rows are the
     * candidate, columns the template; the final row holds the
     * cost of aligning the whole candidate with every template
     * prefix, so the best end position is a minimum over that
     * row plus the suffix-skip penalty.
     */
    private fun dtwOpenDistance(a: List<StrokePoint>, b: List<StrokePoint>): Float {
        val n = a.size // template points (columns)
        val m = b.size // candidate points (rows)
        val inf = Float.MAX_VALUE / 4
        var prev = FloatArray(n + 1) { inf }
        var cur = FloatArray(n + 1) { inf }
        // Row 0: aligning zero candidate points with a template
        // prefix of length c means skipping those c template points.
        for (c in 0..minOf(n, DTW_MAX_SKIP)) prev[c] = DTW_SKIP_PENALTY * c
        for (r in 1..m) {
            cur[0] = inf
            val lo = maxOf(1, r - DTW_OPEN_BAND)
            val hi = minOf(n, r + DTW_OPEN_BAND)
            for (c in lo..hi) {
                val cost = distance(a[c - 1], b[r - 1])
                cur[c] = cost + minOf(prev[c], cur[c - 1], prev[c - 1])
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        // Best end position: align the whole candidate with a template
        // prefix ending at c, skipping the remaining n - c points.
        var best = inf
        for (c in maxOf(0, n - DTW_MAX_SKIP)..n) {
            val total = prev[c] + DTW_SKIP_PENALTY * (n - c)
            if (total < best) best = total
        }
        return best
    }

    /** DTW distance as a 0..1 score, on the same scale as [score]. */
    private fun dtwScore(a: List<StrokePoint>, b: List<StrokePoint>, open: Boolean = false): Float {
        val d = if (open) dtwOpenDistance(a, b) else dtwBandedDistance(a, b)
        return (1f - (d / a.size) / HALF_DIAGONAL).coerceIn(0f, 1f)
    }

    /** Smallest absolute difference between two angles, in degrees (0..180). */
    private fun angleDifference(a: Float, b: Float): Float {
        var diff = kotlin.math.abs(a - b) * 180f / Math.PI.toFloat()
        diff %= 360f
        if (diff > 180f) diff = 360f - diff
        return diff
    }

    // True if the path is open: its ends are far apart relative
    // to its size. Closed shapes (a circle, a square) end where
    // they started, so their indicative angle depends on the
    // arbitrary start point and must not be compared.
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

    // Strokes separated by "|", points by ";" as "x,y".
    // Templates store raw drawn strokes; normalisation happens
    // at match time.
    fun strokesToString(strokes: List<List<StrokePoint>>): String =
        strokes.joinToString("|") { pointsToString(it) }

    // Strings with no "|" are old single-stroke templates
    // (stored as one normalized stroke) and parse as exactly
    // one stroke, so they keep working.
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
