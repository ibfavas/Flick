import com.ibfavas.flick.gesture.StrokePoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic shape generator shared by the test and the probe. */
object ShapeGen {
fun shapePoints(kind: String, rnd: Random, scale: Float, rotDeg: Float, ox: Float, oy: Float, density: Int): List<List<StrokePoint>> {
    fun xform(pts: List<StrokePoint>): List<StrokePoint> {
        val r = Math.toRadians(rotDeg.toDouble()).toFloat()
        val c = cos(r); val s = sin(r)
        return pts.map { p ->
            val x = p.x * scale; val y = p.y * scale
            StrokePoint(ox + x * c - y * s, oy + x * s + y * c)
        }
    }
    fun line(a: StrokePoint, b: StrokePoint, n: Int): List<StrokePoint> =
        (0..n).map { i ->
            val t = i.toFloat() / n
            StrokePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
    fun polyline(vararg pts: StrokePoint): List<StrokePoint> {
        val out = mutableListOf<StrokePoint>()
        val per = maxOf(2, density / maxOf(1, pts.size - 1))
        for (i in 0 until pts.size - 1) {
            val seg = line(pts[i], pts[i + 1], per)
            if (i > 0) out.addAll(seg.drop(1)) else out.addAll(seg)
        }
        return out
    }
    val strokes: List<List<StrokePoint>> = when (kind) {
        "circle" -> listOf((0..density).map { i ->
            val a = 2 * PI * i / density
            StrokePoint(100f + 80f * cos(a).toFloat(), 100f + 80f * sin(a).toFloat())
        })
        "square" -> listOf(polyline(StrokePoint(20f, 20f), StrokePoint(180f, 20f), StrokePoint(180f, 180f), StrokePoint(20f, 180f), StrokePoint(20f, 20f)))
        "triangle" -> listOf(polyline(StrokePoint(100f, 20f), StrokePoint(180f, 180f), StrokePoint(20f, 180f), StrokePoint(100f, 20f)))
        "check" -> listOf(polyline(StrokePoint(20f, 110f), StrokePoint(80f, 170f), StrokePoint(180f, 30f)))
        "v" -> listOf(polyline(StrokePoint(20f, 30f), StrokePoint(100f, 170f), StrokePoint(180f, 30f)))
        "line" -> listOf(line(StrokePoint(20f, 100f), StrokePoint(180f, 100f), density))
        "l" -> listOf(polyline(StrokePoint(40f, 20f), StrokePoint(40f, 180f), StrokePoint(160f, 180f)))
        "star" -> listOf(polyline(StrokePoint(100f, 10f), StrokePoint(125f, 80f), StrokePoint(195f, 80f), StrokePoint(140f, 125f), StrokePoint(160f, 195f), StrokePoint(100f, 150f), StrokePoint(40f, 195f), StrokePoint(60f, 125f), StrokePoint(5f, 80f), StrokePoint(75f, 80f), StrokePoint(100f, 10f)))
        "q" -> listOf(
            (0..density).map { i ->
                val a = 2 * PI * i / density
                StrokePoint(90f + 70f * cos(a).toFloat(), 90f + 70f * sin(a).toFloat())
            },
            line(StrokePoint(130f, 130f), StrokePoint(175f, 175f), maxOf(4, density / 4))
        )
        "x" -> listOf(
            line(StrokePoint(30f, 30f), StrokePoint(170f, 170f), density / 2),
            line(StrokePoint(170f, 30f), StrokePoint(30f, 170f), density / 2)
        )
        "s" -> listOf(polyline(StrokePoint(150f, 40f), StrokePoint(90f, 30f), StrokePoint(50f, 70f), StrokePoint(60f, 110f), StrokePoint(140f, 120f), StrokePoint(160f, 160f), StrokePoint(100f, 180f), StrokePoint(50f, 165f)))
        "c" -> listOf((0..density).map { i ->
            val a = PI * 0.15 + (2 * PI * 0.85) * i / density
            StrokePoint(100f + 80f * cos(a).toFloat(), 100f + 80f * sin(a).toFloat())
        })
        "z" -> listOf(polyline(StrokePoint(20f, 30f), StrokePoint(180f, 30f), StrokePoint(20f, 170f), StrokePoint(180f, 170f)))
        "spiral" -> listOf((0..density).map { i ->
            val t = i.toFloat() / density
            val a = 2 * PI * 3 * t
            val r = 10f + 80f * t
            StrokePoint(100f + r * cos(a).toFloat(), 100f + r * sin(a).toFloat())
        })
        "m" -> listOf(polyline(StrokePoint(20f, 180f), StrokePoint(20f, 40f), StrokePoint(100f, 130f), StrokePoint(180f, 40f), StrokePoint(180f, 180f)))
        "w" -> listOf(polyline(StrokePoint(20f, 40f), StrokePoint(60f, 180f), StrokePoint(100f, 80f), StrokePoint(140f, 180f), StrokePoint(180f, 40f)))
        "plus" -> listOf(
            line(StrokePoint(100f, 20f), StrokePoint(100f, 180f), density / 2),
            line(StrokePoint(20f, 100f), StrokePoint(180f, 100f), density / 2)
        )
        else -> error("unknown shape $kind")
    }
    val noisy = strokes.map { stroke ->
        stroke.map { p ->
            val nx = if (rnd.nextFloat() < 0.5f) p.x + rnd.nextFloat() * 6f - 3f else p.x
            val ny = if (rnd.nextFloat() < 0.5f) p.y + rnd.nextFloat() * 6f - 3f else p.y
            StrokePoint(nx, ny)
        }
    }
    return noisy.map { xform(it) }
}

fun randomWalk(rnd: Random, n: Int): List<List<StrokePoint>> {
    var x = 100f; var y = 100f
    val pts = mutableListOf(StrokePoint(x, y))
    repeat(n) {
        x += rnd.nextFloat() * 60f - 30f
        y += rnd.nextFloat() * 60f - 30f
        pts.add(StrokePoint(x, y))
    }
    return listOf(pts)
}

}
