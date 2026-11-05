import com.ibfavas.flick.gesture.Recognizer
import kotlin.random.Random

// Probe: run BOTH scoring passes on every LIVE draw (the shipped
// matcher only runs pass 2 when pass 1 finds no match) and ask:
// when pass 1 launches the WRONG gesture, does pass 2 rank the TRUE
// gesture first? And when pass 1 is right, would pass 2 overturn it?
// This tests a "pass 2 confirms/overturns pass 1" design.
private val KNOWN = listOf("circle", "square", "triangle", "check", "v", "line", "l", "star", "q", "x", "s", "c", "z")

private fun winnerOf(perName: Map<String, Float>): Pair<String, Float>? =
    perName.entries.maxByOrNull { it.value }?.let { it.key to it.value }

private fun runBoth(label: String, templates: List<Recognizer.MultiTemplate>) {
    val rnd = Random(20261105)
    var p1Correct = 0; var p1Wrong = 0
    var p2AgreesWithP1Wrong = 0; var p2RanksTrueFirst = 0
    var p2OverturnsCorrect = 0
    var p1WrongMarginSum = 0f; var p1WrongMarginN = 0
    var p2TrueMinusWrongSum = 0f; var p2TrueMinusWrongN = 0
    val p2MarginsOnWrong = mutableListOf<Float>()
    for (name in KNOWN) {
        repeat(30) {
            val clean = ShapeGen.shapePoints(name, rnd, 1f, 0f, 0f, 0f, 64)
            val strokes = live(clean, rnd)
            val passes = Recognizer.debugBoth(strokes, Recognizer.prepareTemplates(templates))
            val p1 = passes.getOrNull(0) ?: return@repeat
            val p2 = passes.getOrNull(1) ?: return@repeat
            val w1 = winnerOf(p1.perName) ?: return@repeat
            val w2 = winnerOf(p2.perName) ?: return@repeat
            // Pass-1 outcome as the shipped matcher sees it (threshold
            // 0.87 + margin 0.03, ignoring pass 2 for this analysis).
            val second1 = p1.perName.entries.filter { it.key != w1.first }.maxOfOrNull { it.value } ?: 0f
            val p1Matches = w1.second >= 0.87f && w1.second - second1 >= 0.03f
            if (p1Matches && w1.first == name) {
                p1Correct++
                // Would pass 2 overturn this correct pass-1 match?
                if (w2.first != name && w2.second - (p2.perName[name] ?: 0f) >= 0.03f) p2OverturnsCorrect++
            } else if (p1Matches) {
                p1Wrong++
                p1WrongMarginSum += w1.second - second1; p1WrongMarginN++
                val trueP2 = p2.perName[name] ?: 0f
                val wrongP2 = p2.perName[w1.first] ?: 0f
                if (w2.first == name) p2RanksTrueFirst++
                if (w2.first == w1.first) p2AgreesWithP1Wrong++
                p2TrueMinusWrongSum += trueP2 - wrongP2; p2TrueMinusWrongN++
                p2MarginsOnWrong.add(trueP2 - wrongP2)
            }
        }
    }
    println("=== $label: pass1 correct=$p1Correct wrong=$p1Wrong ===")
    println("  on pass-1 wrong draws: pass2 ranks TRUE first $p2RanksTrueFirst/$p1Wrong, agrees with wrong $p2AgreesWithP1Wrong/$p1Wrong")
    if (p1WrongMarginN > 0) println("  pass-1 wrong margin mean=${"%.3f".format(p1WrongMarginSum / p1WrongMarginN)}")
    if (p2TrueMinusWrongN > 0) {
        p2MarginsOnWrong.sort()
        println("  pass-2 (true minus wrong) mean=${"%.3f".format(p2TrueMinusWrongSum / p2TrueMinusWrongN)} min=${"%.3f".format(p2MarginsOnWrong.first())} p25=${"%.3f".format(p2MarginsOnWrong[p2MarginsOnWrong.size / 4])} median=${"%.3f".format(p2MarginsOnWrong[p2MarginsOnWrong.size / 2])} max=${"%.3f".format(p2MarginsOnWrong.last())}")
        println("  pass-2 true-minus-wrong > 0: ${p2MarginsOnWrong.count { it > 0f }}/$p1Wrong, > 0.03: ${p2MarginsOnWrong.count { it > 0.03f }}/$p1Wrong, > 0.05: ${p2MarginsOnWrong.count { it > 0.05f }}/$p1Wrong")
    }
    println("  pass-2 would overturn a CORRECT pass-1 match: $p2OverturnsCorrect/$p1Correct")
}

fun main() {
    val cleanRnd = Random(42)
    val single = KNOWN.map { name ->
        Recognizer.MultiTemplate(name, ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64))
    }
    runBoth("SINGLE", single)
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
    runBoth("MULTI", multi)
}
