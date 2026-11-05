import com.ibfavas.flick.gesture.Recognizer
import kotlin.random.Random

// Diagnostic: which unknown shapes match which templates at high scores,
// and whether their stroke counts differ from the matched template.
fun main() {
    val known = listOf("circle", "square", "triangle", "check", "v", "line", "l", "star", "q", "x", "s", "c", "z")
    val unknown = listOf("spiral", "m", "w", "plus")
    val cleanRnd = Random(42)
    val templates = known.map { name ->
        Recognizer.MultiTemplate(name, ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64))
    }
    val rnd = Random(1234)
    for (name in unknown) {
        repeat(10) {
            val strokes = ShapeGen.shapePoints(name, rnd, 0.8f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 40f - 20f, 0f, 0f, 64)
            val m = Recognizer.recognizeMulti(strokes, templates, threshold = 0f)
            if (m != null && m.score >= 0.80f) {
                val templateStrokes = templates.first { it.name == m.template.name }.strokes.size
                println("unknown=$name (${strokes.size} strokes) matched=${m.template.name} ($templateStrokes strokes) score=${"%.3f".format(m.score)}")
            }
        }
    }
    var walksHigh = 0
    repeat(60) {
        val strokes = ShapeGen.randomWalk(rnd, 40 + rnd.nextInt(60))
        val m = Recognizer.recognizeMulti(strokes, templates, threshold = 0f)
        if (m != null && m.score >= 0.80f) walksHigh++
    }
    println("random walks scoring >= 0.80: $walksHigh / 60")
}
