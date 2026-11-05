import com.ibfavas.flick.gesture.Recognizer
import kotlin.random.Random

/**
 * JVM accuracy harness for the $N recognizer. No Android, no Gradle:
 * compile with the kotlinc CLI together with Recognizer.kt and run.
 *
 *   kotlinc Recognizer.kt RecognizerTest.kt -include-runtime -d test.jar
 *   java -jar test.jar
 *
 * Shapes are generated synthetically (clean templates, then varied
 * copies with random scale, rotation, translation, noise, point density
 * and stroke order). Unknown shapes and random walks must NOT match.
 * Everything is seeded, so runs are reproducible and comparable across
 * recognizer changes.
 *
 * Two suites run on the same seeded variations:
 * - SINGLE: one clean template per gesture (the v1.0.5-v1.0.7 behaviour).
 * - MULTI: three samples per gesture (clean + two varied copies, the
 *   v1.0.8 behaviour: the recognizer matches against every sample and
 *   takes the best score). Multi-sample should lift correct-match
 *   scores, so the threshold can sit higher and false matches drop.
 */

private val KNOWN = listOf("circle", "square", "triangle", "check", "v", "line", "l", "star", "q", "x", "s", "c", "z")
private val UNKNOWN = listOf("spiral", "m", "w", "plus")

private fun runSuite(label: String, templates: List<Recognizer.MultiTemplate>) {
    val rnd = Random(1234)
    var correct = 0; var wrong = 0; var noMatch = 0; var total = 0
    val perShape = mutableMapOf<String, Int>()
    for (name in KNOWN) {
        var ok = 0
        repeat(30) {
            val scale = 0.6f + rnd.nextFloat() * 1.2f
            val rot = rnd.nextFloat() * 50f - 25f
            val ox = rnd.nextFloat() * 60f - 30f
            val oy = rnd.nextFloat() * 60f - 30f
            val density = 20 + rnd.nextInt(100)
            var strokes = ShapeGen.shapePoints(name, rnd, scale, rot, ox, oy, density)
            if (strokes.size > 1 && rnd.nextFloat() < 0.5f) strokes = strokes.reversed()
            val match = Recognizer.recognizeMulti(strokes, templates)
            total++
            when {
                match == null -> noMatch++
                match.template.name == name -> { correct++; ok++ }
                else -> wrong++
            }
        }
        perShape[name] = ok
    }
    println("$label VARIATIONS: total=$total correct=$correct (${100 * correct / total}%) wrong=$wrong no-match=$noMatch")
    println("$label per-shape correct/30: " + KNOWN.joinToString(" ") { "$it=${perShape[it]}" })

    var falsePos = 0; var falseTotal = 0
    for (name in UNKNOWN) {
        repeat(10) {
            val strokes = ShapeGen.shapePoints(name, rnd, 0.8f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 40f - 20f, 0f, 0f, 64)
            falseTotal++
            if (Recognizer.recognizeMulti(strokes, templates) != null) falsePos++
        }
    }
    repeat(60) {
        falseTotal++
        if (Recognizer.recognizeMulti(ShapeGen.randomWalk(rnd, 40 + rnd.nextInt(60)), templates) != null) falsePos++
    }
    println("$label FALSE MATCHES: $falsePos / $falseTotal (${100 * falsePos / falseTotal}%)")

    // Score distribution: best score of correct vs wrong matches, to see
    // how much room the threshold has.
    var correctScoreSum = 0f; var correctScoreN = 0
    var wrongScoreSum = 0f; var wrongScoreN = 0
    val rnd2 = Random(99)
    for (name in KNOWN) {
        repeat(10) {
            val strokes = ShapeGen.shapePoints(name, rnd2, 0.7f + rnd2.nextFloat(), rnd2.nextFloat() * 40f - 20f, 0f, 0f, 64)
            val m = Recognizer.recognizeMulti(strokes, templates, threshold = 0f)
            if (m != null) {
                if (m.template.name == name) { correctScoreSum += m.score; correctScoreN++ }
                else { wrongScoreSum += m.score; wrongScoreN++ }
            }
        }
    }
    println("$label MEAN BEST SCORE: correct=${if (correctScoreN > 0) "%.3f".format(correctScoreSum / correctScoreN) else "n/a"} (n=$correctScoreN), wrong=${if (wrongScoreN > 0) "%.3f".format(wrongScoreSum / wrongScoreN) else "n/a"} (n=$wrongScoreN)")
}

/**
 * Tilt suite (v1.0.12): the user's complaint is a gesture drawn
 * slightly tilted. Each known shape is drawn rotated by exactly
 * 10, 15, 20 and 25 degrees in both directions, cleanly (no scale or
 * translation noise), and must still match its own template. A tilt
 * that scores below the threshold is a no-match, never a wrong launch.
 */
private fun runTiltSuite(label: String, templates: List<Recognizer.MultiTemplate>) {
    val angles = listOf(-25f, -20f, -15f, -10f, 10f, 15f, 20f, 25f)
    val rnd = Random(777)
    var correct = 0; var wrong = 0; var noMatch = 0; var total = 0
    val perAngle = mutableMapOf<Float, Int>()
    for (angle in angles) {
        var ok = 0
        for (name in KNOWN) {
            val strokes = ShapeGen.shapePoints(name, rnd, 1f, angle, 0f, 0f, 64)
            val match = Recognizer.recognizeMulti(strokes, templates)
            total++
            when {
                match == null -> noMatch++
                match.template.name == name -> { correct++; ok++ }
                else -> wrong++
            }
        }
        perAngle[angle] = ok
    }
    println("$label TILT: total=$total correct=$correct (${100 * correct / total}%) wrong=$wrong no-match=$noMatch")
    println("$label tilt per-angle correct/13: " + angles.joinToString(" ") { "${it.toInt()}deg=${perAngle[it]}" })
}

/**
 * SLOPPY suite: each shape drawn 30 times with the finger-drawn
 * transforms in SloppyGen — non-uniform point spacing, hooks at
 * stroke ends, lateral wobble, bigger noise — and half of the copies
 * drawn backwards as well. A clean harness says nothing about a real
 * phone: this is the suite that exposed the v1.0.12 recognizer's
 * real-world failures (47% correct, 0% on reversed shapes).
 */
private fun runSloppySuite(label: String, templates: List<Recognizer.MultiTemplate>) {
    val rnd = Random(424242)
    var correct = 0; var wrong = 0; var noMatch = 0; var total = 0
    var reversed = 0; var revCorrect = 0
    for (name in KNOWN) {
        repeat(30) {
            val scale = 0.85f + rnd.nextFloat() * 0.3f
            val rot = rnd.nextFloat() * 20f - 10f
            val tx = rnd.nextFloat() * 40f - 20f
            val ty = rnd.nextFloat() * 40f - 20f
            val clean = ShapeGen.shapePoints(name, rnd, scale, rot, tx, ty, 64)
            val reverse = rnd.nextFloat() < 0.5f
            val strokes = sloppy(clean, rnd, reverse)
            val match = Recognizer.recognizeMulti(strokes, templates)
            total++
            if (reverse) reversed++
            when {
                match == null -> noMatch++
                match.template.name == name -> { correct++; if (reverse) revCorrect++ }
                else -> wrong++
            }
        }
    }
    println("$label SLOPPY: total=$total correct=$correct (${100 * correct / total}%) wrong=$wrong no-match=$noMatch (reversed $revCorrect/$reversed correct)")
}

/**
 * REV suite: each shape drawn cleanly but fully backwards (every
 * stroke's points reversed, stroke order reversed). A circle drawn
 * counter-clockwise is the same gesture as one drawn clockwise.
 */
private fun runRevSuite(label: String, templates: List<Recognizer.MultiTemplate>) {
    val rnd = Random(99)
    var correct = 0; var wrong = 0; var noMatch = 0; var total = 0
    for (name in KNOWN) {
        repeat(30) {
            val scale = 0.85f + rnd.nextFloat() * 0.3f
            val rot = rnd.nextFloat() * 20f - 10f
            val strokes = reversedStrokes(ShapeGen.shapePoints(name, rnd, scale, rot, 0f, 0f, 64))
            val match = Recognizer.recognizeMulti(strokes, templates)
            total++
            when {
                match == null -> noMatch++
                match.template.name == name -> correct++
                else -> wrong++
            }
        }
    }
    println("$label REV: total=$total correct=$correct (${100 * correct / total}%) wrong=$wrong no-match=$noMatch")
}

fun main() {
    val cleanRnd = Random(42)
    val single = KNOWN.map { name ->
        Recognizer.MultiTemplate(name, ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64))
    }
    runSuite("SINGLE", single)
    runTiltSuite("SINGLE", single)
    runSloppySuite("SINGLE", single)
    runRevSuite("SINGLE", single)

    // Three samples per gesture: the clean template plus two varied
    // copies, as a user recording extra samples would produce.
    val multiRnd = Random(4242)
    val multi = KNOWN.flatMap { name ->
        val samples = mutableListOf(
            ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64)
        )
        repeat(2) {
            val scale = 0.85f + multiRnd.nextFloat() * 0.3f
            val rot = multiRnd.nextFloat() * 30f - 15f
            var strokes = ShapeGen.shapePoints(name, multiRnd, scale, rot, 0f, 0f, 64)
            if (strokes.size > 1 && multiRnd.nextFloat() < 0.5f) strokes = strokes.reversed()
            samples.add(strokes)
        }
        samples.map { Recognizer.MultiTemplate(name, it) }
    }
    runSuite("MULTI", multi)
    runTiltSuite("MULTI", multi)
    runSloppySuite("MULTI", multi)
    runRevSuite("MULTI", multi)
}
