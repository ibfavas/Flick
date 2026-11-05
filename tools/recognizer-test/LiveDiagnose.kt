import com.ibfavas.flick.gesture.Recognizer
import kotlin.random.Random

// Diagnostic for the LIVE suite: for every LIVE failure, print what
// pass 1 and pass 2 saw (top-3 per-name scores, winner, margin), so
// the next matcher change targets the actual failure mode instead of
// guessing. Uses Recognizer.debugMatch (injected into a COPY of
// Recognizer.kt by make-debug-copy.py, never into the shipped file).
private val KNOWN = listOf("circle", "square", "triangle", "check", "v", "line", "l", "star", "q", "x", "s", "c", "z")

private fun top3(perName: Map<String, Float>): String =
    perName.entries.sortedByDescending { it.value }.take(3)
        .joinToString(" ") { "${it.key}=${"%.3f".format(it.value)}" }

private fun runLiveDiag(label: String, templates: List<Recognizer.MultiTemplate>) {
    val rnd = Random(20261105)
    var correct = 0; var wrong = 0; var noMatch = 0
    val wrongPairs = mutableMapOf<String, Int>()
    val failDetail = mutableListOf<String>()
    for (name in KNOWN) {
        repeat(30) {
            val clean = ShapeGen.shapePoints(name, rnd, 1f, 0f, 0f, 0f, 64)
            val strokes = live(clean, rnd)
            val debug = Recognizer.debugMatch(strokes, Recognizer.prepareTemplates(templates))
            val m = debug.first
            val passes = debug.second
            val p1 = passes.getOrNull(0)
            val p2 = passes.getOrNull(1)
            if (m == null) {
                noMatch++
                val p1s = if (p1 != null) "P1[${top3(p1.perName)}] best=${p1.best.first}:${"%.3f".format(p1.best.second)}" else "P1[-]"
                val p2s = if (p2 != null) "P2[${top3(p2.perName)}] best=${p2.best.first}:${"%.3f".format(p2.best.second)}" else "P2[not run]"
                failDetail.add("NO-MATCH true=$name $p1s $p2s")
            } else if (m.template.name == name) {
                correct++
            } else {
                wrong++
                wrongPairs["$name->${m.template.name}"] =
                    (wrongPairs["$name->${m.template.name}"] ?: 0) + 1
                val p1s = if (p1 != null) "P1[${top3(p1.perName)}]" else "P1[-]"
                val p2s = if (p2 != null) "P2[${top3(p2.perName)}]" else "P2[not run]"
                failDetail.add("WRONG true=$name got=${m.template.name} $p1s $p2s")
            }
        }
    }
    println("=== $label LIVE diag: correct=$correct wrong=$wrong no-match=$noMatch ===")
    println("wrong pairs: " + wrongPairs.entries.sortedByDescending { it.value }.joinToString(" ") { "${it.key}=${it.value}" })
    for (line in failDetail) println(line)
}

fun main() {
    val cleanRnd = Random(42)
    val single = KNOWN.map { name ->
        Recognizer.MultiTemplate(name, ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64))
    }
    runLiveDiag("SINGLE", single)
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
    runLiveDiag("MULTI", multi)
}
