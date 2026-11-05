import com.ibfavas.flick.gesture.Recognizer
import com.ibfavas.flick.gesture.StrokePoint
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

// Probe: what indicative angles do the templates and a truncated
// candidate actually have, and what orientation penalty does each
// template variant eat? Tests whether the orientation penalty is
// already separating the confusable pairs (circle/c, check/v, v/l).
private val KNOWN = listOf("circle", "square", "triangle", "check", "v", "line", "l", "star", "q", "x", "s", "c", "z")

private fun centroid(points: List<StrokePoint>): StrokePoint {
    var x = 0f; var y = 0f
    for (p in points) { x += p.x; y += p.y }
    return StrokePoint(x / points.size, y / points.size)
}

private fun indAngle(points: List<StrokePoint>): Float {
    val c = centroid(points)
    return atan2(c.y - points.first().y, c.x - points.first().x) * 180f / Math.PI.toFloat()
}

private fun isOpen(points: List<StrokePoint>): Boolean {
    if (points.size < 2) return false
    val gap = hypot((points.last().x - points.first().x).toDouble(), (points.last().y - points.first().y).toDouble()).toFloat()
    var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
    for (p in points) {
        if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
    }
    val diag = hypot((maxX - minX).toDouble(), (maxY - minY).toDouble()).toFloat()
    return diag > 0f && gap > diag * 0.25f
}

private fun angleDiff(a: Float, b: Float): Float {
    var d = kotlin.math.abs(a - b) % 360f
    if (d > 180f) d = 360f - d
    return d
}

fun main() {
    val cleanRnd = Random(42)
    val templates = KNOWN.map { name ->
        Recognizer.MultiTemplate(name, ShapeGen.shapePoints(name, cleanRnd, 1f, 0f, 0f, 0f, 64))
    }
    // Template variant angles (first variant only).
    println("template angles (first variant raw path):")
    for (t in templates) {
        val variants = Recognizer.templateVariants(t.strokes)
        val raw = variants.firstOrNull()?.second ?: continue
        println("  ${t.name}: angle=${"%.1f".format(indAngle(raw))} open=${isOpen(raw)}")
    }
    // Truncated candidates: circle, check, v (15% truncation, no rotation).
    println("truncated candidates (15% off final stroke, no rotation):")
    val rnd = Random(7)
    for (name in listOf("circle", "check", "v")) {
        val clean = ShapeGen.shapePoints(name, rnd, 1f, 0f, 0f, 0f, 64)
        val strokes = live(clean, rnd, LiveOpts(partial = true, density = false, wobble = false, geometry = false))
        val raw = strokes.flatten()
        val candAngle = indAngle(raw)
        val candOpen = isOpen(raw)
        println("  true=$name candAngle=${"%.1f".format(candAngle)} open=$candOpen")
        for (t in templates) {
            val variants = Recognizer.templateVariants(t.strokes)
            val vraw = variants.firstOrNull()?.second ?: continue
            if (!candOpen || !isOpen(vraw)) continue
            val diff = angleDiff(candAngle, indAngle(vraw))
            val excess = (diff - 20f).coerceIn(0f, 20f)
            val pen = 0.20f * excess / 20f
            if (pen > 0.005f || t.name == name) {
                println("    vs ${t.name}: diff=${"%.1f".format(diff)} penalty=${"%.3f".format(pen)}")
            }
        }
    }
}
