import com.ibfavas.flick.gesture.Recognizer
import kotlin.random.Random

// Ablation probe for the LIVE suite: which transform breaks recognition?
// SINGLE templates, same seed as the LIVE suite in RecognizerTest.
private val KNOWN = listOf("circle", "square", "triangle", "check", "v", "line", "l", "star", "q", "x", "s", "c", "z")

private fun run(label: String, templates: List<Recognizer.MultiTemplate>, opts: LiveOpts) {
    val rnd = Random(20261105)
    var correct = 0; var wrong = 0; var noMatch = 0; var total = 0
    val wrongPairs = mutableMapOf<String, Int>()
    for (name in KNOWN) {
        repeat(30) {
            val clean = ShapeGen.shapePoints(name, rnd, 1f, 0f, 0f, 0f, 64)
            val strokes = live(clean, rnd, opts)
            val match = Recognizer.recognizeMulti(strokes, templates)
            total++
            when {
                match == null -> noMatch++
                match.template.name == name -> correct++
                else -> {
                    wrong++
                    wrongPairs["$name->${match.template.name}"] =
                        (wrongPairs["$name->${match.template.name}"] ?: 0) + 1
                }
            }
        }
    }
    println("$label: correct=$correct/390 wrong=$wrong no-match=$noMatch")
    if (wrongPairs.isNotEmpty()) println("  wrong pairs: " + wrongPairs.entries.sortedByDescending { it.value }.joinToString(" ") { "${it.key}=${it.value}" })
}

fun main() {
    val cleanRnd = Random(42)
    val single = KNOWN.map { name ->
        Recognizer.MultiTemplate(name, ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64))
    }
    run("ALL", single, LiveOpts())
    run("partial-only", single, LiveOpts(partial = true, density = false, wobble = false, geometry = false))
    run("density-only", single, LiveOpts(partial = false, density = true, wobble = false, geometry = false))
    run("wobble-only", single, LiveOpts(partial = false, density = false, wobble = true, geometry = false))
    run("geometry-only", single, LiveOpts(partial = false, density = false, wobble = false, geometry = true))
    run("no-partial", single, LiveOpts(partial = false))
    run("no-density", single, LiveOpts(density = false))
    run("no-wobble", single, LiveOpts(wobble = false))
}
