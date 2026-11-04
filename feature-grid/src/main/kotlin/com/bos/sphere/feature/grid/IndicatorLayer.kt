package com.bos.sphere.feature.grid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.IndicatorEffect
import com.bos.sphere.core.data.IndicatorReading
import com.bos.sphere.core.data.ReadingKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure geometry for [IndicatorLayer]; unit-tested on the JVM. */
internal object IndicatorMath {
    /** Minimum dot weight at the screen edges so the lattice stays faintly visible. */
    const val FLOOR = 0.15f

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Normalised distance from the centre: 0 at the centre, 1 in the corners. [nx], [ny] in -1..1. */
    fun radial(nx: Float, ny: Float): Float = (hypot(nx, ny) / SQRT2).coerceIn(0f, 1f)

    /** Centre-weighted spotlight, 1 near the centre falling smoothly to [FLOOR] at the edges. */
    fun falloff(nx: Float, ny: Float): Float {
        val d = radial(nx, ny)
        return FLOOR + (1f - FLOOR) * (1f - smoothstep(0.12f, 0.9f, d))
    }

    /** Brightness 0..1 of a ring of half-[width] centred at [centre], at distance [d]. */
    fun ringWave(d: Float, centre: Float, width: Float): Float {
        val x = 1f - abs(d - centre) / width
        return if (x <= 0f) 0f else x * x * (3f - 2f * x)
    }

    /** Ring centre for a wave [phase] 0..1, travelling from just before 0 to just past 1. */
    fun ringCentre(phase: Float, width: Float): Float = -width + phase * (1f + 2f * width)

    fun wrap01(x: Float): Float = x - floor(x)

    fun perimeterLength(w: Float, h: Float, r: Float): Float {
        val rr = r.coerceIn(0f, minOf(w, h) / 2f)
        return 2f * (w - 2f * rr) + 2f * (h - 2f * rr) + 2f * PI.toFloat() * rr
    }

    /**
     * Point at fraction [t] (wrapped to 0..1) along the clockwise perimeter of a w x h rounded
     * rectangle with corner radius [r], starting at the top edge just after the top-left corner.
     * Writes x, y into [out].
     */
    fun perimeterPoint(t: Float, w: Float, h: Float, r: Float, out: FloatArray) {
        val rr = r.coerceIn(0f, minOf(w, h) / 2f)
        val top = w - 2f * rr
        val side = h - 2f * rr
        val arc = PI.toFloat() * rr / 2f
        var s = wrap01(t) * (2f * top + 2f * side + 4f * arc)
        fun onArc(cx: Float, cy: Float, startDeg: Float) {
            val a = (startDeg + (if (arc > 0f) s / arc else 0f) * 90f) * (PI.toFloat() / 180f)
            out[0] = cx + rr * cos(a); out[1] = cy + rr * sin(a)
        }
        if (s <= top) { out[0] = rr + s; out[1] = 0f; return }
        s -= top
        if (s <= arc) { onArc(w - rr, rr, -90f); return }
        s -= arc
        if (s <= side) { out[0] = w; out[1] = rr + s; return }
        s -= side
        if (s <= arc) { onArc(w - rr, h - rr, 0f); return }
        s -= arc
        if (s <= top) { out[0] = w - rr - s; out[1] = h; return }
        s -= top
        if (s <= arc) { onArc(rr, h - rr, 90f); return }
        s -= arc
        if (s <= side) { out[0] = 0f; out[1] = h - rr - s; return }
        s -= side
        onArc(rr, rr, 180f)
    }

    /** Deterministic hash to 0..1 (no Random object, no state). */
    fun hash01(i: Int, seed: Int): Float {
        var x = i * 374761393 + seed * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFFFF) / 16777216f
    }

    /** Distance from the centre to the rectangle edge along [angle] (radians). */
    fun rayEdgeDistance(angle: Float, halfW: Float, halfH: Float): Float {
        val c = abs(cos(angle)); val s = abs(sin(angle))
        val dx = if (c < 1e-6f) Float.MAX_VALUE else halfW / c
        val dy = if (s < 1e-6f) Float.MAX_VALUE else halfH / s
        return minOf(dx, dy)
    }

    private val SQRT2 = sqrt(2f)
}

private const val SIZE_BUCKETS = 4
private const val LEVELS = 6
private const val TWO_PI = (2.0 * PI).toFloat()

/** Cached lattice + scratch buffers. Rebuilt only when the canvas size / density changes. */
private class IndicatorCache {
    var w = -1f
    var h = -1f
    var spacing = -1f
    var n = 0
    var xs = FloatArray(0)
    var ys = FloatArray(0)
    var weight = FloatArray(0)
    var dist = FloatArray(0)
    var bucket = ByteArray(0)
    var level = ByteArray(0)
    var scratch = FloatArray(0)
    val perim = FloatArray(2)
    val perim2 = FloatArray(2)
    val path = Path()
    var vignette: Brush? = null
    var vigW = -1f
    var vigH = -1f
    var vigColor = 0
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
        style = android.graphics.Paint.Style.STROKE
    }

    fun ensure(width: Float, height: Float, spacingPx: Float) {
        if (width == w && height == h && spacingPx == spacing) return
        w = width; h = height; spacing = spacingPx
        val cols = (width / spacingPx).toInt() + 1
        val rows = (height / spacingPx).toInt() + 1
        n = cols * rows
        xs = FloatArray(n); ys = FloatArray(n); weight = FloatArray(n); dist = FloatArray(n)
        bucket = ByteArray(n); level = ByteArray(n); scratch = FloatArray(n * 2)
        val ox = (width - (cols - 1) * spacingPx) / 2f
        val oy = (height - (rows - 1) * spacingPx) / 2f
        var i = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            val x = ox + c * spacingPx
            val y = oy + r * spacingPx
            val nx = (x - width / 2f) / (width / 2f)
            val ny = (y - height / 2f) / (height / 2f)
            xs[i] = x; ys[i] = y
            weight[i] = IndicatorMath.falloff(nx, ny)
            dist[i] = IndicatorMath.radial(nx, ny)
            val wn = (weight[i] - IndicatorMath.FLOOR) / (1f - IndicatorMath.FLOOR)
            bucket[i] = (wn * (SIZE_BUCKETS - 1) + 0.5f).toInt().coerceIn(0, SIZE_BUCKETS - 1).toByte()
            i++
        }
    }
}

/**
 * The background indicator: dots, bloom, vignette, comic lines, snake outline and edge meter,
 * all painted in one Canvas. Effects combine freely. [colorArgb] is the already-resolved colour
 * (see IndicatorColors.argb); [reading] supplies kind (Off paints nothing, Idle is steady) and value.
 */
@Composable
fun IndicatorLayer(
    reading: IndicatorReading,
    effects: Set<IndicatorEffect>,
    colorArgb: Long,
    intensity: Float,
    speed: Float,
    modifier: Modifier = Modifier,
) {
    val live = reading.kind == ReadingKind.Live
    val animated = reading.kind != ReadingKind.Off && effects.any {
        when (it) {
            IndicatorEffect.DotsBreathe -> live
            IndicatorEffect.DotsBloomCentre, IndicatorEffect.DotsBloomEdges,
            IndicatorEffect.ComicLines, IndicatorEffect.SnakeOutline -> true
            else -> false
        }
    }
    val time: State<Float>? = if (animated) {
        val transition = rememberInfiniteTransition(label = "indicator")
        // Seconds of animation clock; wraps (invisibly) once an hour.
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 3600f,
            animationSpec = infiniteRepeatable(tween(3_600_000, easing = LinearEasing), RepeatMode.Restart),
            label = "t",
        )
    } else null
    val cache = remember { IndicatorCache() }
    val color = Color(colorArgb.toInt())

    Canvas(modifier) {
        if (reading.kind == ReadingKind.Off) return@Canvas
        val k = intensity.coerceIn(0f, 1f)
        if (k <= 0f) return@Canvas
        val t = (time?.value ?: 0f) * (0.2f + 1.6f * speed.coerceIn(0f, 1f))
        val steady = reading.kind == ReadingKind.Idle
        val v = if (live) reading.value.coerceIn(0f, 1f) else 0.15f

        if (IndicatorEffect.Vignette in effects) drawVignette(cache, color, k)
        val dots = IndicatorEffect.DotsBreathe in effects ||
            IndicatorEffect.DotsBloomCentre in effects || IndicatorEffect.DotsBloomEdges in effects
        if (dots) drawDots(cache, effects, color, k, t, steady)
        if (IndicatorEffect.ComicLines in effects) drawComic(cache, color, k, t)
        if (IndicatorEffect.EdgeMeter in effects) drawEdgeMeter(color, k, if (live) v else 1f, steady)
        if (IndicatorEffect.SnakeOutline in effects) drawSnake(cache, color, k, t, v)
    }
}

private fun DrawScope.drawVignette(c: IndicatorCache, color: Color, k: Float) {
    val argb = color.toArgb()
    if (c.vignette == null || c.vigW != size.width || c.vigH != size.height || c.vigColor != argb) {
        c.vigW = size.width; c.vigH = size.height; c.vigColor = argb
        c.vignette = Brush.radialGradient(
            0f to Color.Transparent,
            0.55f to Color.Transparent,
            1f to color.copy(alpha = 0.55f),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = hypot(size.width, size.height) / 2f,
        )
    }
    drawRect(c.vignette!!, alpha = k, style = Fill)
}

private fun DrawScope.drawDots(
    c: IndicatorCache, effects: Set<IndicatorEffect>, color: Color, k: Float, t: Float, steady: Boolean,
) {
    c.ensure(size.width, size.height, 22.dp.toPx())
    val breathe = IndicatorEffect.DotsBreathe in effects
    val bc = IndicatorEffect.DotsBloomCentre in effects
    val be = IndicatorEffect.DotsBloomEdges in effects
    // ~4 s per breath at default speed; steady for Idle.
    val glow = if (steady) 0.6f else if (breathe) 0.35f + 0.65f * (0.5f - 0.5f * cos(t * TWO_PI / 4f)) else 0.5f
    val width = 0.16f
    val centre = IndicatorMath.ringCentre(IndicatorMath.wrap01(t / 5f), width)
    val n = c.n
    for (i in 0 until n) {
        val w = c.weight[i]
        var a = w * glow
        if (bc) a += (0.5f + 0.5f * w) * IndicatorMath.ringWave(c.dist[i], centre, width)
        if (be) a += (0.5f + 0.5f * w) * IndicatorMath.ringWave(1f - c.dist[i], centre, width)
        a = (a * k).coerceIn(0f, 1f)
        c.level[i] = (if (a < 0.02f) -1 else (a * LEVELS).toInt().coerceAtMost(LEVELS - 1)).toByte()
    }
    val paint = c.paint
    paint.style = android.graphics.Paint.Style.STROKE
    val base = color.toArgb()
    val nc = drawContext.canvas.nativeCanvas
    for (b in 0 until SIZE_BUCKETS) {
        val radius = (1.0f + 1.4f * b / (SIZE_BUCKETS - 1)).dp.toPx()
        paint.strokeWidth = radius * 2f
        for (l in 0 until LEVELS) {
            var cnt = 0
            for (i in 0 until n) {
                if (c.bucket[i].toInt() == b && c.level[i].toInt() == l) {
                    c.scratch[cnt++] = c.xs[i]; c.scratch[cnt++] = c.ys[i]
                }
            }
            if (cnt == 0) continue
            val alpha = ((l + 0.5f) / LEVELS * 0.95f * 255f).toInt().coerceIn(0, 255)
            paint.color = (base and 0x00FFFFFF) or (alpha shl 24)
            nc.drawPoints(c.scratch, 0, cnt, paint)
        }
    }
}

private fun DrawScope.drawComic(c: IndicatorCache, color: Color, k: Float, t: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val lines = 40
    val cycle = t / 6f // bursts re-seeded every ~6 s
    val epoch = floor(cycle).toInt()
    val path = c.path
    for (i in 0 until lines) {
        val off = IndicatorMath.hash01(i, 17)
        val local = IndicatorMath.wrap01(cycle * 2f + off) // two bursts per epoch per line
        if (local > 0.3f) continue
        val env = sin(local / 0.3f * PI.toFloat())
        val ang = TWO_PI * (i + IndicatorMath.hash01(i, epoch * 31 + 5) * 0.8f) / lines
        val edge = IndicatorMath.rayEdgeDistance(ang, cx, cy)
        val len = edge * (0.10f + 0.2f * IndicatorMath.hash01(i, epoch * 13 + 9))
        val ca = cos(ang); val sa = sin(ang)
        val ox = cx + ca * edge; val oy = cy + sa * edge
        val ix = cx + ca * (edge - len); val iy = cy + sa * (edge - len)
        val half = 2.2.dp.toPx()
        path.reset()
        path.moveTo(ox - sa * half, oy + ca * half)
        path.lineTo(ox + sa * half, oy - ca * half)
        path.lineTo(ix, iy)
        path.close()
        drawPath(path, color.copy(alpha = (0.55f * env * k).coerceIn(0f, 1f)))
    }
    // Faint halftone near each corner: dots shrink with distance from the corner.
    val sp = 10.dp.toPx()
    val reach = 96.dp.toPx()
    val steps = (reach / sp).toInt()
    for (corner in 0 until 4) {
        val sx = if (corner and 1 == 0) 1f else -1f
        val sy = if (corner and 2 == 0) 1f else -1f
        val bx = if (sx > 0) 0f else size.width
        val by = if (sy > 0) 0f else size.height
        for (gy in 0..steps) for (gx in 0..steps) {
            val px = gx * sp; val py = gy * sp
            val d = hypot(px, py) / reach
            if (d >= 1f) continue
            val r = (1f - d) * 2.4.dp.toPx()
            drawCircle(color.copy(alpha = 0.22f * k), r, Offset(bx + sx * px, by + sy * py))
        }
    }
}

private fun DrawScope.drawSnake(c: IndicatorCache, color: Color, k: Float, t: Float, v: Float) {
    val corner = 32.dp.toPx()
    val segs = 28
    val length = 0.08f + 0.32f * v
    val head = IndicatorMath.wrap01(t / 8f)
    val stroke = 3.dp.toPx()
    IndicatorMath.perimeterPoint(head, size.width, size.height, corner, c.perim)
    for (s in 1..segs) {
        val f = s / segs.toFloat()
        IndicatorMath.perimeterPoint(head - f * length, size.width, size.height, corner, c.perim2)
        val fade = (1f - f)
        drawLine(
            color.copy(alpha = (fade * fade * k).coerceIn(0f, 1f)),
            Offset(c.perim[0], c.perim[1]), Offset(c.perim2[0], c.perim2[1]),
            strokeWidth = stroke * (0.5f + 0.5f * fade), cap = StrokeCap.Round,
        )
        c.perim[0] = c.perim2[0]; c.perim[1] = c.perim2[1]
    }
}

private fun DrawScope.drawEdgeMeter(color: Color, k: Float, v: Float, steady: Boolean) {
    val inset = 3.dp.toPx()
    val stroke = 3.dp.toPx()
    val pad = 24.dp.toPx()
    val span = size.height - 2f * pad
    if (span <= 0f) return
    val xs = floatArrayOf(inset, size.width - inset)
    for (x in xs) {
        drawLine(color.copy(alpha = 0.12f * k), Offset(x, pad), Offset(x, pad + span), stroke, StrokeCap.Round)
        val len = span * (if (steady) 1f else v)
        if (len > 0f) {
            drawLine(
                color.copy(alpha = (if (steady) 0.5f else 0.9f) * k),
                Offset(x, pad + span), Offset(x, pad + span - len), stroke, StrokeCap.Round,
            )
        }
    }
}
