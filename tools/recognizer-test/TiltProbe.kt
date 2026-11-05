import com.ibfavas.flick.gesture.Recognizer
import kotlin.random.Random

// Probe: which (angle, shape) fails the TILT suite, and what are the
// top scores for that draw? Run against a Recognizer copy with the
// constants under test (passed as the Recognizer.kt on the command line).
private val KNOWN = listOf("circle", "square", "triangle", "check", "v", "line", "l", "star", "q", "x", "s", "c", "z")

fun main() {
    val cleanRnd = Random(42)
    val single = KNOWN.map { name ->
        Recognizer.MultiTemplate(name, ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64))
    }
    val angles = listOf(-25f, -20f, -15f, -10f, 10f, 15f, 20f, 25f)
    val rnd = Random(777)
    for (angle in angles) {
        for (name in KNOWN) {
            val strokes = ShapeGen.shapePoints(name, rnd, 1f, angle, 0f, 0f, 64)
            val m = Recognizer.recognizeMulti(strokes, single)
            if (m == null || m.template.name != name) {
                val m0 = Recognizer.recognizeMulti(strokes, single, threshold = 0f)
                println("FAIL angle=$angle shape=$name result=${m?.template?.name ?: "null"} score0=${m0?.score?.let { "%.3f".format(it) } ?: "null"} score0name=${m0?.template?.name}")
            }
        }
    }
    println("tilt probe done")
}
