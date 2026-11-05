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
object RecognizerV2 {

    /**
     * Accuracy tuning (v1.1.0, measured with tools/recognizer-test on
     * 390 varied copies of 13 shapes plus 100 unknowns and random
     * walks, plus 104 tilted copies, plus a SLOPPY suite that models
     * real finger drawing — non-uniform point spacing, hooks at stroke
     * ends, lateral wobble, bigger noise, and random stroke-direction
     * reversal — and a REV suite of clean shapes drawn backwards).
     * The shipped harness was saturated on clean shapes (390/390),
     * which could not explain why recognition felt bad on a real
     * phone. The sloppy suite exposed it: the v1.0.12 recognizer
     * scored 182/390 on sloppy single-sample drawings and 0/390 on
     * reversed ones. $1/$N compare point sequences, so a circle drawn
     * counter-clockwise or a line drawn right-to-left is a different
     * sequence from the template, and the orientation penalty then
     * punished the 180-degree indicative-angle difference as well.
     *
     * v1.1.0 changes, each measured in tools/recognizer-test
     * (VariantProbe, two sweep rounds; the losing candidates were
     * hook-trimming, which destroys real matches, Protractor blending,
     * which raises false matches, and a lower threshold with a margin,
     * which also raises false matches):
     * - Direction handling: the candidate is tried as drawn and fully
     *   reversed (every stroke's points reversed, stroke order
     *   reversed), and the orientation penalty compares line
     *   orientation modulo 180 for the reversed form only — a line
     *   has no direction, but a shape rotated 180 degrees is still
     *   a different shape for the as-drawn form.
     * - Banded DTW (band 10) blended by max with the $N path-distance
     *   score: DTW tolerates the local speed variation of a real
     *   finger (sparse points on fast stretches, dense on slow ones),
     *   which is what the sloppy suite mostly punishes.
     * - Margin rule: the winner must beat the best other gesture by
     *   0.03, or the match is ambiguous and returns no match. This
     *   is what keeps wrong launches at zero while DTW is in play.
     * - Resample to 96 points, default threshold 0.87, stroke-count
     *   penalty 0.12, orientation penalty up to 0.20 (none under 20
     *   degrees of tilt, full at 40), as in v1.0.12.
     * - Templates are prepared once (prepareTemplates) and reused:
     *   normalising every template variant on every recognition was
     *   the pad's main CPU cost. recognizeMulti keeps its signature
     *   and prepares inline, so the harness tests the shipped path;
     *   the app caches the prepared library and calls
     *   recognizeMultiPrepared.
     * - Minimum size: a gesture smaller than 30 px across or shorter
     *   than 60 px of path is an accidental touch, not a gesture.
     *
     * v1.1.0 measured results (seeded harness; same suites, same
     * seeds, run against this file):
     * - SINGLE: 390/390 correct (100%), 2/100 false (2%), TILT
     *   104/104 (100%), SLOPPY 384/390 (98%, 0 wrong), REV 390/390
     *   (100%, 0 wrong).
     * - MULTI (3 samples): 390/390 correct (100%), 4/100 false
     *   (4%), TILT 104/104 (100%), SLOPPY 387/390 (99%, 0 wrong),
     *   REV 390/390 (100%, 0 wrong).
     * - v1.0.12 on the same SLOPPY/REV suites: SLOPPY 182/390
     *   (47%) single and 185/390 multi, REV 0/390. So the headline
     *   gain is sloppy finger drawing 47% to 98-99% and reversed
     *   drawing 0% to 100%, with zero wrong launches anywhere.
     *   The one cost: SINGLE false matches 1/100 to 2/100 on
     *   adversarial unknowns (MULTI unchanged at 4/100); a wider
     *   margin (0.05) was tried and rejected — it cost 15-21 sloppy
     *   matches and 2 tilted ones without reducing false matches.
     *
     * v1.2.0 changes, each measured in tools/recognizer-test
     * (LIVE suite in RecognizerTest/LiveGen, sweep2.sh rounds):
     * - LIVE suite: a later live draw versus the saved template —
     *   combined scale (±20%), translation, rotation (±10°),
     *   lateral wobble, non-uniform point density (a faster later
     *   draw is sparser), and partial strokes (the final stroke ends
     *   10-20% short by path length, and half the time one other
     *   stroke of a multi-stroke gesture loses 10-15%). Ablation
     *   against the v1.1.0 recognizer showed truncation is the
     *   killer: truncation alone cost 129 of 390 draws, while
     *   density, wobble and geometry alone each cost 0-1.
     *   Resampling a truncated path stretches it to 96
     *   points, so the candidate's points drift against the
     *   template's along the whole tail, and the banded DTW's
     *   forced endpoint alignment cannot skip the template's tail.
     * - Two-pass matching: pass 1 is exactly the v1.1.0 scorer.
     *   Pass 2 runs only when pass 1 finds no match, and re-scores
     *   with an open-ended DTW that may skip up to 24 template
     *   points at either end (end-skip for the as-drawn form,
     *   start-skip for the reversed form) at 10 px per skipped
     *   point, inside a band of 28. The skip is bounded and
     *   penalised, and the margin rule still applies in pass 2, so
     *   a short shape cannot silently become a longer one.
     * - Rejected: scoring everything with the open DTW in a single
     *   pass (LIVE rose to the same level, but it also lifts
     *   near-miss templates, compressing margins: SINGLE SLOPPY
     *   fell to 364-381/390 and TILT to 102/104 depending on the
     *   skip penalty); a pass-2 override rule (let the open DTW
     *   overturn a pass 1 match it strongly disagreed with — it
     *   never fired at any margin, because whenever pass 1 matches
     *   the open DTW agrees on the winner; the single-pass gain on
     *   wrong launches came from margin kills, not from the true
     *   template winning); a lower threshold (0.85: false matches
     *   rose to 3/100 single and 5/100 multi with no LIVE gain);
     *   margin 0.02 and 0.05 (0.02 raised LIVE wrong launches,
     *   0.05 cost 23-27 sloppy matches); resample 64 and 128
     *   (within noise of 96); skip penalties 0-80 (0 and 5 match
     *   10 within a few draws; 15 loses LIVE matches, and 60/80
     *   in the single-pass rounds lost most of the gain).
     *
     * v1.2.0 measured results (seeded harness; same suites, same
     * seeds, run against this file; LIVE baseline is the v1.1.0
     * recognizer on the same LIVE suite):
     * - SINGLE: 390/390 correct (100%), 2/100 false (2%), TILT
     *   104/104 (100%), SLOPPY 384/390 (98%, 0 wrong), REV 390/390
     *   (100%, 0 wrong), LIVE 342/390 (87%, 22 wrong) vs 241/390
     *   (61%, 22 wrong) baseline.
     * - MULTI (3 samples): 390/390 correct (100%), 4/100 false
     *   (4%), TILT 104/104 (100%), SLOPPY 387/390 (99%, 0 wrong),
     *   REV 390/390 (100%, 0 wrong), LIVE 341/390 (87%, 30 wrong)
     *   vs 252/390 (64%, 30 wrong) baseline.
     * - Every non-LIVE number is identical to v1.1.0: the two-pass
     *   design rescues only no-matches, so nothing that matched
     *   before can change. The LIVE gain was confirmed on a second
     *   seed set (fresh templates and draws): v1.1.0 LIVE 256/390
     *   both, v1.2.0 LIVE 349/390 single and 340/390 multi, with
     *   wrong launches unchanged (15 single, 30 multi) and false
     *   matches 3/100 single and 6/100 multi on those seeds for
     *   both versions (the fresh-seed false rate is seed variance
     *   in the unknown draws, not a rescue effect).
     * - Honest limits: the rescue converts no-matches into matches;
     *   it does not fix pass 1's wrong launches. A truncated circle
     *   is a 'c' by shape (a 306-degree arc either way) and a
     *   truncated check resembles a 'v', so those stay wrong in
     *   LIVE (circle 11/30 single, check 22/30 single) while the
     *   margin rule keeps them from spreading. On the fresh-seed
     *   run MULTI LIVE (87%) trailed SINGLE (89-91%): the extra
     *   samples add confusable near-neighbours for truncated draws.
     *   All of this is measured on synthetic shapes in the harness,
     *   not on a real device: whether a finger lifts early by
     *   10-20% as often as the suite assumes is unmeasured.
     * A gesture below threshold shows no match, never a wrong launch.
     */
    const val RESAMPLE_COUNT = 96
    const val DEFAULT_THRESHOLD = 0.87f
    private const val SQUARE_SIZE = 250f
    private const val HALF_DIAGONAL = 0.5f * 353.55f // 0.5 * sqrt(250^2 + 250^2)
    private const val MAX_PERMUTED_STROKES = 4
    private const val STROKE_COUNT_PENALTY = 0.12f
    private const val ORIENTATION_PENALTY_MAX = 0.20f
    private const val ORIENTATION_FREE_DEGREES = 20f
    private const val ORIENTATION_FULL_DEGREES = 40f
    private const val MIN_GESTURE_SIZE_PX = 30f
    private const val MIN_GESTURE_LENGTH_PX = 60f
    /**
     * Margin rule (v1.1.0): the winning gesture must beat the best
     * other gesture by this much, or the match is ambiguous and
     * returns no match. What keeps wrong launches at zero now that
     * DTW is in play.
     */
    private const val MARGIN = 0.03f
    /**
     * Decoupled threshold (v1.3.0 probe): when true, the acceptance
     * threshold applies to the SHAPE score (max of $N/DTW minus the
     * stroke-count penalty, WITHOUT the orientation penalty), while
     * the margin rule still compares FINAL scores (with the
     * orientation penalty). The orientation penalty's job is to
     * separate confusable neighbours in the ranking; it should not
     * also gate legitimately tilted draws at the threshold. When
     * false, the threshold applies to the final score, as shipped.
     */
    private const val USE_SHAPE_THRESHOLD = false
    private const val SHAPE_THRESHOLD = 0.90f
    /** Sakoe-Chiba band for the DTW alignment, in points. */
    private const val DTW_BAND = 10
    /**
     * Open-ended DTW (v1.2.0): how many template points the alignment
     * may skip at either end, in points. A live draw that ends early
     * covers only a prefix of the template (or, in the reversed form,
     * only a suffix); the skip lets the alignment end there instead of
     * forcing the candidate's last points onto the template's tail.
     * 24 of 96 points is a 25% truncation allowance.
     */
    private const val DTW_MAX_SKIP = 24
    /** Cost, in normalised px, of each template point skipped by the open DTW. */
    private const val DTW_SKIP_PENALTY = 10f
    /** Sakoe-Chiba band for the open-ended DTW, in points. Wider than
     * [DTW_BAND]: a truncated candidate drifts from the diagonal by up
     * to the skipped fraction, about 19 points at 20% truncation. */
    private const val DTW_OPEN_BAND = 28

    data class Template(val name: String, val points: List<StrokePoint>)

    /** A multi-stroke template: a name and its drawn strokes (raw points). */
    data class MultiTemplate(val name: String, val strokes: List<List<StrokePoint>>)

    data class Match(val template: Template, val score: Float, val source: MultiTemplate? = null)

    /**
     * A template variant prepared once and reused across recognitions:
     * the normalized points to score against, plus the raw joined path
     * data the orientation penalty needs (whether it is open, and its
     * indicative angle). Preparing every variant on every recognition
     * was the pad's main CPU cost.
     */
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

    /**
     * Precompute every variant of every template once. The result is
     * reused until the gesture library changes; callers keep it in a
     * remember keyed on the gesture list.
     */
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
     * Find the best matching multi-stroke template for raw drawn
     * strokes, or null if none scores above [threshold]. Scores
     * against templates prepared once by [prepareTemplates].
     *
     * v1.1.0: the candidate is tried as drawn and fully reversed
     * (every stroke's points reversed, stroke order reversed), so a
     * gesture drawn backwards still matches; the orientation penalty
     * compares line orientation modulo 180 for the reversed form
     * only. Each variant is scored by the better of the $N
     * path-distance score and a banded-DTW score, which tolerates
     * the uneven point spacing of a real finger. A candidate with a
     * different stroke count than the template, or a large
     * indicative-angle difference on open shapes, is penalised, so
     * near-misses fail cleanly instead of launching the wrong app.
     * Finally the margin rule: the winner must beat the best other
     * gesture by [MARGIN], or the match is ambiguous and returns
     * null. [Match.source] carries the template that won, so the
     * caller can find the gesture without relying on its name.
     *
     * v1.2.0: two passes. Pass 1 is exactly the v1.1.0 scorer above,
     * so every draw that matched before still matches the same way.
     * Pass 2 runs only when pass 1 finds no match, and re-scores
     * with the open-ended DTW, which tolerates a draw that ends
     * early (a truncated stroke). The rescue pass exists because the
     * open DTW also lifts near-miss templates, which compresses
     * margins: scoring everything with it in a single pass cost
     * 4-20 sloppy and tilted matches in the harness. As a rescue it
     * can only turn a no-match into a match, never change a match
     * that pass 1 made. An override rule (let pass 2 overturn a
     * pass 1 match when it strongly disagreed) was tried and
     * removed: it never fired at any margin, because whenever pass
     * 1 matches, the open DTW agrees with it on the winner — the
     * single-pass gain on wrong launches came from margin kills,
     * not from the true template winning, and those kills are what
     * cost the sloppy matches.
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

    /**
     * One scoring pass over the prepared templates. With
     * [openDtw] false this is the v1.1.0 scorer (banded DTW); with
     * true it is the v1.2.0 rescue scorer (open-ended DTW).
     */
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
        val perNameShape = HashMap<String, Float>()
        var bestName: String? = null
        var bestScore = -1f
        var bestShape = -1f
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
                    var shape = score(variant.points, candidate) - countPenalty
                    shape = maxOf(shape, dtwScore(variant.points, candidate, openDtw) - countPenalty)
                    var s = shape
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
                    val prevShape = perNameShape[template.name] ?: -1f
                    if (shape > prevShape) perNameShape[template.name] = shape
                    if (s > bestScore) {
                        bestScore = s
                        bestShape = shape
                        bestName = template.name
                        bestTemplate = Template(template.name, variant.points)
                        bestSource = template.source
                    }
                }
            }
        }
        if (bestName == null) return null
        if (USE_SHAPE_THRESHOLD) {
            if (bestShape < SHAPE_THRESHOLD) return null
        } else if (bestScore < threshold) return null
        val second = perName.entries
            .filter { it.key != bestName }
            .maxOfOrNull { it.value } ?: 0f
        if (bestScore - second < MARGIN) return null
        return Match(bestTemplate!!, bestScore, bestSource)
    }

    /**
     * Find the best matching multi-stroke template for raw drawn
     * strokes, or null if none scores above [threshold]. Prepares
     * the templates inline, so the harness tests the exact shipped
     * scoring path; the app caches the prepared library and calls
     * [recognizeMultiPrepared] instead.
     */
    fun recognizeMulti(
        rawStrokes: List<List<StrokePoint>>,
        templates: List<MultiTemplate>,
        threshold: Float = DEFAULT_THRESHOLD
    ): Match? = recognizeMultiPrepared(rawStrokes, prepareTemplates(templates), threshold)

    /** Every stroke's points reversed, and the stroke order reversed. */
    private fun reversedStrokes(strokes: List<List<StrokePoint>>): List<List<StrokePoint>> =
        strokes.reversed().map { it.reversed() }

    /**
     * Banded dynamic time warping between two normalized 96-point
     * sequences (v1.1.0, kept as the first-pass scorer in v1.2.0).
     * Unlike the $N pointwise distance, DTW lets the alignment
     * stretch locally, which is what a finger does: fast stretches
     * come out sparse, slow stretches dense, and the shape is the
     * same. The band keeps the cost near-linear, and the forced
     * endpoint-to-endpoint alignment is exactly right for a complete
     * draw. A truncated draw is rescued by the open-ended DTW below,
     * in a second pass that only runs when this one finds no match.
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

    /**
     * Open-ended dynamic time warping between two normalized 96-point
     * sequences (v1.2.0 second pass). Unlike the banded DTW above it
     * lets the alignment skip template points at either end, at
     * [DTW_SKIP_PENALTY] per point: a live draw that ends early
     * covers only a prefix of the template (in the reversed form, only
     * a suffix), and forcing its last points onto the template's tail
     * was what truncation punished. The skip is bounded
     * ([DTW_MAX_SKIP] points per end) and penalised, so a short shape
     * cannot silently become a longer one: a 'c' still has to beat a
     * circle on its own merits, and the margin rule still applies.
     * Rows are the candidate, columns the template; the final row
     * holds the cost of aligning the whole candidate with every
     * template prefix, so the best end position is a minimum over
     * that row plus the suffix-skip penalty.
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
