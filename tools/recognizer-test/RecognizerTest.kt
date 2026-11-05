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
 */

fun main() {
    val known = listOf("circle", "square", "triangle", "check", "v", "line", "l", "star", "q", "x", "s", "c", "z")
    val unknown = listOf("spiral", "m", "w", "plus")
    val cleanRnd = Random(42)
    val templates = known.map { name ->
        Recognizer.MultiTemplate(name, ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64))
    }

    val rnd = Random(1234)
    var correct = 0; var wrong = 0; var noMatch = 0; var total = 0
    val perShape = mutableMapOf<String, Int>()
    for (name in known) {
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
    println("VARIATIONS: total=$total correct=$correct (${100 * correct / total}%) wrong=$wrong no-match=$noMatch")
    println("per-shape correct/30: " + known.joinToString(" ") { "$it=${perShape[it]}" })

    var falsePos = 0; var falseTotal = 0
    for (name in unknown) {
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
    println("FALSE MATCHES: $falsePos / $falseTotal (${100 * falsePos / falseTotal}%)")

    // Score distribution: best score of correct vs wrong matches, to see
    // how much room the threshold has.
    var correctScoreSum = 0f; var correctScoreN = 0
    var wrongScoreSum = 0f; var wrongScoreN = 0
    val rnd2 = Random(99)
    for (name in known) {
        repeat(10) {
            val strokes = ShapeGen.shapePoints(name, rnd2, 0.7f + rnd2.nextFloat(), rnd2.nextFloat() * 40f - 20f, 0f, 0f, 64)
            val m = Recognizer.recognizeMulti(strokes, templates, threshold = 0f)
            if (m != null) {
                if (m.template.name == name) { correctScoreSum += m.score; correctScoreN++ }
                else { wrongScoreSum += m.score; wrongScoreN++ }
            }
        }
    }
    println("MEAN BEST SCORE: correct=${if (correctScoreN > 0) "%.3f".format(correctScoreSum / correctScoreN) else "n/a"} (n=$correctScoreN), wrong=${if (wrongScoreN > 0) "%.3f".format(wrongScoreSum / wrongScoreN) else "n/a"} (n=$wrongScoreN)")
}
