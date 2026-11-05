import com.ibfavas.flick.gesture.Recognizer
import kotlin.random.Random

// Probe: score distribution of false matches vs correct matches, to pick a threshold.
fun main() {
    val known = listOf("circle", "square", "triangle", "check", "v", "line", "l", "star", "q", "x", "s", "c", "z")
    val unknown = listOf("spiral", "m", "w", "plus")
    val cleanRnd = Random(42)
    val templates = known.map { name ->
        Recognizer.MultiTemplate(name, ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64))
    }
    val rnd = Random(1234)
    val falseScores = mutableListOf<Float>()
    for (name in unknown) {
        repeat(10) {
            val strokes = ShapeGen.shapePoints(name, rnd, 0.8f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 40f - 20f, 0f, 0f, 64)
            Recognizer.recognizeMulti(strokes, templates, threshold = 0f)?.let { falseScores.add(it.score) }
        }
    }
    repeat(60) {
        Recognizer.recognizeMulti(ShapeGen.randomWalk(rnd, 40 + rnd.nextInt(60)), templates, threshold = 0f)?.let { falseScores.add(it.score) }
    }
    falseScores.sort()
    println("FALSE best scores n=${falseScores.size}: min=${"%.3f".format(falseScores.firstOrNull() ?: 0f)} p50=${"%.3f".format(falseScores.getOrNull(falseScores.size / 2) ?: 0f)} p90=${"%.3f".format(falseScores.getOrNull((falseScores.size * 0.9).toInt()) ?: 0f)} max=${"%.3f".format(falseScores.lastOrNull() ?: 0f)}")
    println("FALSE count >= 0.75: ${falseScores.count { it >= 0.75f }}, >= 0.80: ${falseScores.count { it >= 0.80f }}, >= 0.85: ${falseScores.count { it >= 0.85f }}, >= 0.90: ${falseScores.count { it >= 0.90f }}")

    val correctScores = mutableListOf<Float>()
    val rnd2 = Random(777)
    for (name in known) {
        repeat(30) {
            val scale = 0.6f + rnd2.nextFloat() * 1.2f
            val rot = rnd2.nextFloat() * 50f - 25f
            val density = 20 + rnd2.nextInt(100)
            var strokes = ShapeGen.shapePoints(name, rnd2, scale, rot, 0f, 0f, density)
            if (strokes.size > 1 && rnd2.nextFloat() < 0.5f) strokes = strokes.reversed()
            val m = Recognizer.recognizeMulti(strokes, templates, threshold = 0f)
            if (m != null && m.template.name == name) correctScores.add(m.score)
        }
    }
    correctScores.sort()
    println("CORRECT best scores n=${correctScores.size}: min=${"%.3f".format(correctScores.firstOrNull() ?: 0f)} p5=${"%.3f".format(correctScores.getOrNull((correctScores.size * 0.05).toInt()) ?: 0f)} p10=${"%.3f".format(correctScores.getOrNull((correctScores.size * 0.1).toInt()) ?: 0f)}")
    println("CORRECT count < 0.80: ${correctScores.count { it < 0.80f }}, < 0.85: ${correctScores.count { it < 0.85f }}, < 0.90: ${correctScores.count { it < 0.90f }}")
}
