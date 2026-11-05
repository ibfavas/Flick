import com.ibfavas.flick.gesture.Recognizer
import com.ibfavas.flick.gesture.StrokePoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * v1.1.0 recognizer probe. The shipped harness is saturated (390/390 on
 * clean synthetic shapes), so it cannot explain why recognition feels bad
 * on a real phone. This probe adds suites that model real finger drawing
 * and tries candidate improvements, all measured in one run:
 *
 * Suites (all seeded, same 13 known + 4 unknown shapes):
 * - ORIG:   the shipped RecognizerTest suites (varied copies, tilt, false).
 * - SLOPPY: variations with non-uniform point spacing (fast/slow drawing),
 *           hooks at stroke ends, lateral wobble, bigger noise, and random
 *           stroke-direction reversal — what a finger actually produces.
 * - REV:    clean shapes with every stroke drawn backwards (point order
 *           reversed). $1/$N compare point sequences, so a backwards
 *           circle or a right-to-left line is a different sequence.
 *
 * Variants (flags on one recognize implementation):
 * - dir:    also try the candidate with every stroke reversed (and stroke
 *           order reversed), and fix the orientation penalty to compare
 *           line orientation modulo 180 (a line has no direction).
 * - margin: accept if best >= lowThr AND best - secondBestName >= margin.
 * - dtw:    blend in a banded-DTW score (max of $N and DTW).
 * - prot:   blend in a Protractor cosine score (max of $N and $P).
 * - perStr: resample each stroke to an equal share before joining, so a
 *           long jump between strokes does not eat the 96 points.
 * - trim:   drop the first/last 2 points of each stroke (pen-down/up hooks).
 *
 * Usage (from tools/recognizer-test):
 *   kotlinc Recognizer.kt VariantProbe.kt ShapeGen.kt -include-runtime -d variant.jar
 *   java -jar variant.jar
 */

private val KNOWN = listOf("circle", "square", "triangle", "check", "v", "line", "l", "star", "q", "x", "s", "c", "z")
private val UNKNOWN = listOf("spiral", "m", "w", "plus")

// ---- local copies of the private helpers in Recognizer (probe only) ----

private fun centroid(points: List<StrokePoint>): StrokePoint {
    var x = 0f; var y = 0f
    for (p in points) { x += p.x; y += p.y }
    return StrokePoint(x / points.size, y / points.size)
}

private fun indicativeAngle(points: List<StrokePoint>): Float {
    val c = centroid(points)
    return atan2(c.y - points.first().y, c.x - points.first().x)
}

private fun dist(a: StrokePoint, b: StrokePoint): Float =
    hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()

private fun pathLength(points: List<StrokePoint>): Float {
    var l = 0f
    for (i in 1 until points.size) l += dist(points[i - 1], points[i])
    return l
}

private fun angleDiff(a: Float, b: Float): Float {
    var d = kotlin.math.abs(a - b) * 180f / PI.toFloat()
    d %= 360f
    if (d > 180f) d = 360f - d
    return d
}

private fun isOpenPath(points: List<StrokePoint>): Boolean {
    if (points.size < 2) return false
    val endGap = dist(points.first(), points.last())
    var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
    for (p in points) {
        if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
    }
    val diag = hypot((maxX - minX).toDouble(), (maxY - minY).toDouble()).toFloat()
    return diag > 0f && endGap > diag * 0.25f
}

private fun resample(points: List<StrokePoint>, n: Int): List<StrokePoint> {
    val interval = pathLength(points) / (n - 1)
    var d = 0f
    val src = points.toMutableList()
    val out = mutableListOf(src.first())
    var i = 1
    while (i < src.size) {
        val dd = dist(src[i - 1], src[i])
        if (d + dd >= interval) {
            val t = (interval - d) / dd
            val q = StrokePoint(src[i - 1].x + t * (src[i].x - src[i - 1].x), src[i - 1].y + t * (src[i].y - src[i - 1].y))
            out.add(q); src.add(i, q); d = 0f
        } else d += dd
        i++
    }
    while (out.size < n) out.add(src.last())
    return out.take(n)
}

// ---- sloppy drawing transforms (model a real finger) ----

/** Reverse every stroke's points and the stroke order. */
private fun reversedStrokes(strokes: List<List<StrokePoint>>): List<List<StrokePoint>> =
    strokes.reversed().map { it.reversed() }

/**
 * Make a clean synthetic shape look finger-drawn: non-uniform spacing
 * (fast stretches sparse, slow stretches dense), a hook at each stroke
 * end, lateral wobble, and bigger noise. Optionally reverses direction.
 */
private fun sloppy(strokes: List<List<StrokePoint>>, rnd: Random, reverse: Boolean): List<List<StrokePoint>> {
    val out = mutableListOf<List<StrokePoint>>()
    for (stroke in strokes) {
        var pts = stroke
        // 1. Non-uniform density: keep each point with a probability that
        //    varies along the stroke (fast = sparse, slow = dense).
        val speed = 0.4f + rnd.nextFloat() * 0.8f // slow or fast stretch
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

// ---- candidate matchers ----

private const val HALF_DIAG = 0.5f * 353.55f

/** Banded DTW distance between two normalized 96-point sequences. */
private fun dtwDistance(a: List<StrokePoint>, b: List<StrokePoint>, band: Int = 10): Float {
    val n = a.size; val m = b.size
    val inf = Float.MAX_VALUE / 4
    var prev = FloatArray(m + 1) { inf }; var cur = FloatArray(m + 1) { inf }
    prev[0] = 0f
    for (i in 1..n) {
        cur[0] = inf
        val lo = maxOf(1, i - band); val hi = minOf(m, i + band)
        for (j in lo..hi) {
            val cost = dist(a[i - 1], b[j - 1])
            cur[j] = cost + minOf(prev[j], cur[j - 1], prev[j - 1])
        }
        val tmp = prev; prev = cur; cur = tmp
    }
    return prev[m]
}

private fun dtwScore(a: List<StrokePoint>, b: List<StrokePoint>): Float {
    val d = dtwDistance(a, b)
    // Warping path length is between n and 2n; use n as the divisor.
    return (1f - (d / a.size) / HALF_DIAG).coerceIn(0f, 1f)
}

/** Protractor cosine similarity on the normalized point vectors. */
private fun protractorScore(a: List<StrokePoint>, b: List<StrokePoint>): Float {
    var dot = 0f; var na = 0f; var nb = 0f
    for (i in a.indices) {
        dot += a[i].x * b[i].x + a[i].y * b[i].y
        na += a[i].x * a[i].x + a[i].y * a[i].y
        nb += b[i].x * b[i].x + b[i].y * b[i].y
    }
    if (na <= 0f || nb <= 0f) return 0f
    return (dot / sqrt(na * nb)).coerceIn(-1f, 1f)
}

/** Trim pen-down/up hooks: drop the first/last k points of each stroke. */
private fun trimmed(strokes: List<List<StrokePoint>>, k: Int): List<List<StrokePoint>> =
    strokes.map { s -> if (s.size > 2 * k + 2) s.drop(k).dropLast(k) else s }

private data class Opts(
    val dir: Boolean = false,
    val margin: Boolean = false,
    val lowThr: Float = 0.80f,
    val marginVal: Float = 0.05f,
    val thr: Float = 0.87f,
    val dtw: Boolean = false,
    val dtwAvg: Boolean = false,
    val prot: Boolean = false,
    val perStroke: Boolean = false,
    val trim: Boolean = false
)

private const val STROKE_PENALTY = 0.12f
private const val ORIENT_MAX = 0.20f
private const val ORIENT_FREE = 20f
private const val ORIENT_FULL = 40f

/**
 * Variant of Recognizer.recognizeMulti. Scores a candidate against every
 * template variant; with dir=true also tries the fully-reversed candidate
 * and uses modulo-180 orientation differences (a line has no direction).
 * With margin=true, accepts only if best >= lowThr and best beats the
 * best other gesture name by marginVal.
 */
private fun recognizeVariant(
    rawStrokes: List<List<StrokePoint>>,
    templates: List<Recognizer.MultiTemplate>,
    opts: Opts
): Recognizer.Match? {
    var usable = rawStrokes.filter { it.size >= 2 }
    if (usable.isEmpty() || templates.isEmpty()) return null
    if (opts.trim) usable = trimmed(usable, 2).filter { it.size >= 2 }
    if (usable.isEmpty()) return null

    // Candidate forms: as drawn, and (with dir) fully reversed.
    val forms = mutableListOf<List<List<StrokePoint>>>(usable)
    if (opts.dir) forms.add(reversedStrokes(usable))

    // Best score per gesture name, plus best overall.
    val perName = mutableMapOf<String, Float>()
    var bestName: String? = null
    var bestScore = -1f
    for (form in forms) {
        val candidate = if (opts.perStroke) {
            // Resample each stroke to an equal share, then normalize
            // the joined path without re-resampling by length.
            val per = 96 / form.size
            val joined = form.flatMap { resample(it, per) }
            Recognizer.normalize(joined)
        } else {
            Recognizer.normalizeMulti(form)
        }
        if (candidate.isEmpty()) continue
        val raw = form.flatten()
        val candAngle = indicativeAngle(raw)
        val candOpen = isOpenPath(raw)
        for (template in templates) {
            val countPenalty = if (template.strokes.count { it.size >= 2 } != usable.size) STROKE_PENALTY else 0f
            for ((variant, variantRaw) in Recognizer.templateVariants(template.strokes)) {
                var s = Recognizer.score(variant, candidate) - countPenalty
                if (opts.dtw) {
                    val d = dtwScore(variant, candidate) - countPenalty
                    s = if (opts.dtwAvg) (s + d) / 2f else maxOf(s, d)
                }
                if (opts.prot) s = maxOf(s, protractorScore(variant, candidate) - countPenalty)
                if (candOpen && isOpenPath(variantRaw)) {
                    var diff = angleDiff(candAngle, indicativeAngle(variantRaw))
                    if (opts.dir) diff = minOf(diff, 180f - diff) // orientation, not direction
                    val excess = (diff - ORIENT_FREE).coerceIn(0f, ORIENT_FULL - ORIENT_FREE)
                    s -= ORIENT_MAX * excess / (ORIENT_FULL - ORIENT_FREE)
                }
                val prev = perName[template.name] ?: -1f
                if (s > prev) perName[template.name] = s
                if (s > bestScore) { bestScore = s; bestName = template.name }
            }
        }
    }
    if (bestName == null) return null
    val threshold = if (opts.margin) opts.lowThr else opts.thr
    if (bestScore < threshold) return null
    if (opts.margin) {
        val second = perName.entries.filter { it.key != bestName }.maxOfOrNull { it.value } ?: 0f
        if (bestScore - second < opts.marginVal) return null
    }
    return Recognizer.Match(Recognizer.Template(bestName, emptyList()), bestScore)
}

// ---- suites ----

private data class SuiteResult(val correct: Int, val wrong: Int, val noMatch: Int, val total: Int, val falsePos: Int, val falseTotal: Int)

private fun runSuite(
    label: String,
    templates: List<Recognizer.MultiTemplate>,
    gen: (String, Random) -> List<List<StrokePoint>>,
    opts: Opts
): SuiteResult {
    val rnd = Random(1234)
    var correct = 0; var wrong = 0; var noMatch = 0; var total = 0
    for (name in KNOWN) {
        repeat(30) {
            val strokes = gen(name, rnd)
            val m = recognizeVariant(strokes, templates, opts)
            total++
            when {
                m == null -> noMatch++
                m.template.name == name -> correct++
                else -> wrong++
            }
        }
    }
    var falsePos = 0; var falseTotal = 0
    for (name in UNKNOWN) {
        repeat(10) {
            val strokes = ShapeGen.shapePoints(name, rnd, 0.8f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 40f - 20f, 0f, 0f, 64)
            falseTotal++
            if (recognizeVariant(strokes, templates, opts) != null) falsePos++
        }
    }
    repeat(60) {
        falseTotal++
        if (recognizeVariant(ShapeGen.randomWalk(rnd, 40 + rnd.nextInt(60)), templates, opts) != null) falsePos++
    }
    println("  $label: correct=$correct/$total wrong=$wrong no-match=$noMatch false=$falsePos/$falseTotal")
    return SuiteResult(correct, wrong, noMatch, total, falsePos, falseTotal)
}

private fun runTilt(templates: List<Recognizer.MultiTemplate>, opts: Opts): SuiteResult {
    val angles = listOf(-25f, -20f, -15f, -10f, 10f, 15f, 20f, 25f)
    val rnd = Random(777)
    var correct = 0; var wrong = 0; var noMatch = 0; var total = 0
    for (angle in angles) {
        for (name in KNOWN) {
            val strokes = ShapeGen.shapePoints(name, rnd, 1f, angle, 0f, 0f, 64)
            val m = recognizeVariant(strokes, templates, opts)
            total++
            when {
                m == null -> noMatch++
                m.template.name == name -> correct++
                else -> wrong++
            }
        }
    }
    println("  TILT: correct=$correct/$total wrong=$wrong no-match=$noMatch")
    return SuiteResult(correct, wrong, noMatch, total, 0, 0)
}

private fun origGen(name: String, rnd: Random): List<List<StrokePoint>> {
    val scale = 0.6f + rnd.nextFloat() * 1.2f
    val rot = rnd.nextFloat() * 50f - 25f
    val ox = rnd.nextFloat() * 60f - 30f
    val oy = rnd.nextFloat() * 60f - 30f
    val density = 20 + rnd.nextInt(100)
    var strokes = ShapeGen.shapePoints(name, rnd, scale, rot, ox, oy, density)
    if (strokes.size > 1 && rnd.nextFloat() < 0.5f) strokes = strokes.reversed()
    return strokes
}

fun main() {
    val cleanRnd = Random(42)
    val single = KNOWN.map { name ->
        Recognizer.MultiTemplate(name, ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64))
    }
    val multiRnd = Random(4242)
    val multi = KNOWN.flatMap { name ->
        val samples = mutableListOf(ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64))
        repeat(2) {
            val scale = 0.85f + multiRnd.nextFloat() * 0.3f
            val rot = multiRnd.nextFloat() * 30f - 15f
            var strokes = ShapeGen.shapePoints(name, multiRnd, scale, rot, 0f, 0f, 64)
            if (strokes.size > 1 && multiRnd.nextFloat() < 0.5f) strokes = strokes.reversed()
            samples.add(strokes)
        }
        samples.map { Recognizer.MultiTemplate(name, it) }
    }

    val combos = listOf(
        "E dir+dtw (round1 winner)" to Opts(dir = true, dtw = true),
        "K dir+dtw+margin.87/.04" to Opts(dir = true, dtw = true, margin = true, lowThr = 0.87f, marginVal = 0.04f),
        "L dir+dtw+margin.87/.03" to Opts(dir = true, dtw = true, margin = true, lowThr = 0.87f, marginVal = 0.03f),
        "M dir+dtw-avg" to Opts(dir = true, dtw = true, dtwAvg = true),
        "N dir+dtw thr0.88" to Opts(dir = true, dtw = true, thr = 0.88f),
        "O dir+dtw+perStroke" to Opts(dir = true, dtw = true, perStroke = true),
        "P dir+dtw+margin.85/.04" to Opts(dir = true, dtw = true, margin = true, lowThr = 0.85f, marginVal = 0.04f)
    )
    for ((label, opts) in combos) {
        println("=== $label ===")
        println(" SINGLE (orig):")
        runSuite("ORIG", single, ::origGen, opts)
        runTilt(single, opts)
        println(" SINGLE (sloppy):")
        runSuite("SLOPPY", single, { n, r -> sloppy(origGen(n, r), r, r.nextFloat() < 0.5f) }, opts)
        println(" SINGLE (reversed):")
        runSuite("REV", single, { n, r -> reversedStrokes(ShapeGen.shapePoints(n, r, 1f, 0f, 0f, 0f, 64)) }, opts)
        println(" MULTI (orig):")
        runSuite("ORIG", multi, ::origGen, opts)
        runTilt(multi, opts)
        println(" MULTI (sloppy):")
        runSuite("SLOPPY", multi, { n, r -> sloppy(origGen(n, r), r, r.nextFloat() < 0.5f) }, opts)
        println(" MULTI (reversed):")
        runSuite("REV", multi, { n, r -> reversedStrokes(ShapeGen.shapePoints(n, r, 1f, 0f, 0f, 0f, 64)) }, opts)
        // Wrong-case breakdown on the harshest suite (MULTI sloppy):
        // which shapes launch the wrong thing, if any.
        val rnd = Random(1234)
        // Re-seed the same way runSuite does for the sloppy MULTI run is
        // not possible here (its rnd is internal), so run a fresh
        // breakdown with its own seed and report wrong pairs.
        val wrongPairs = mutableMapOf<String, Int>()
        val rndB = Random(555)
        for (name in KNOWN) {
            repeat(30) {
                val strokes = sloppy(origGen(name, rndB), rndB, rndB.nextFloat() < 0.5f)
                val m = recognizeVariant(strokes, multi, opts)
                if (m != null && m.template.name != name) {
                    wrongPairs["$name->${m.template.name}"] =
                        (wrongPairs["$name->${m.template.name}"] ?: 0) + 1
                }
            }
        }
        println("  WRONG PAIRS (MULTI sloppy, seed 555): $wrongPairs")
    }
}
