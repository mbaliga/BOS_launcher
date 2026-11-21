package com.bos.sphere.feature.grid

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AlertEffect
import com.bos.sphere.core.data.CalmEffect
import com.bos.sphere.core.data.IndicatorColors
import com.bos.sphere.core.data.IndicatorMetric
import com.bos.sphere.core.data.IndicatorReading
import com.bos.sphere.core.data.IndicatorSeverity
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

    /** Seconds of one urgent pulse (the owner spec: about 1.2 s). */
    const val URGENT_PERIOD_S = 1.2f

    /** Brightness factor 0.7..1 of the urgent pulse at clock [t] seconds; steady 1 when not urgent or when motion is reduced (the static frame is full strength). */
    fun pulse(t: Float, urgent: Boolean, reduced: Boolean): Float =
        if (!urgent || reduced) 1f else 0.7f + 0.3f * (0.5f + 0.5f * cos(t * TWO_PI / URGENT_PERIOD_S))

    /** Alpha multiplier of an alert effect: urgent is full strength whatever the slider says (unless it is at zero = off); otherwise it scales with the severity. */
    fun alertStrength(level: Float, urgent: Boolean, intensity: Float): Float {
        val k = intensity.coerceIn(0f, 1f)
        if (k <= 0f) return 0f
        return if (urgent) 1f else (k * (0.35f + 0.65f * level.coerceIn(0f, 1f))).coerceIn(0f, 1f)
    }

    /** Where the vignette's clear centre ends (fraction of the corner radius): the coloured band thickens from 20 percent to 70 percent of the way in as severity rises. */
    fun vignetteInner(level: Float): Float = 0.80f - 0.50f * level.coerceIn(0f, 1f)

    /** The share of the comic lines drawn: a few when low, every one when lost. */
    fun comicFraction(level: Float, urgent: Boolean): Float = if (urgent) 1f else 0.17f + 0.78f * level.coerceIn(0f, 1f)

    /** Line width multiplier: thin when low, bold when lost. */
    fun comicWidth(level: Float, urgent: Boolean): Float = if (urgent) 2.2f else 0.8f + 1.0f * level.coerceIn(0f, 1f)

    private val SQRT2 = sqrt(2f)
    private const val TWO_PI = (2.0 * PI).toFloat()
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
    val comic = ComicLines()
    var vignette: Brush? = null
    var vigW = -1f
    var vigH = -1f
    var vigColor = 0
    var vigInner = -1f
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
 * The background indicator (wave 35): a calm effect (Dot bloom or Animated dots, lit while the reading is healthy) and an alert effect
 * (Coloured vignette or Comic-book lines: the inverse, invisible while healthy, growing as the value falls, urgent when it is lost), painted in one Canvas.
 * [colorArgb] is the already-resolved ramp colour (see IndicatorColors.argb) for the calm effect; the alert colour comes from [low] / [mid] and the severity
 * ([IndicatorSeverity.of] for [metric] and [reading]); urgent is always red. Zero cost when healthy: the clock only runs for animated dots or an urgent alert,
 * and never under reduced motion (then urgent is one static, full-strength frame).
 */
@Composable
fun IndicatorLayer(
    reading: IndicatorReading,
    calm: CalmEffect?,
    alert: AlertEffect?,
    metric: IndicatorMetric,
    colorArgb: Long,
    intensity: Float,
    speed: Float,
    modifier: Modifier = Modifier,
    low: Long = 0xFFFF4D4D,
    mid: Long = 0xFFFFB347,
) {
    val live = reading.kind == ReadingKind.Live
    // NFR-14: with system animations off the indicators draw one still frame.
    val stillIndicators = com.bos.sphere.core.design.rememberMotionPrefs().reduced
    val severity = IndicatorSeverity.of(metric, reading)
    val alertShown = alert != null && severity.active
    val dotsMove = calm != null && reading.kind != ReadingKind.Off && when (calm) { CalmEffect.AnimatedDots -> live; CalmEffect.Bloom -> true }
    val needsClock = !stillIndicators && intensity > 0f && (dotsMove || (alertShown && severity.urgent))
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(needsClock) {
        if (!needsClock) return@LaunchedEffect
        val start = indicatorFrameNanos()
        while (true) {
            // Seconds of animation clock; wraps (invisibly) once an hour.
            time.floatValue = ((indicatorFrameNanos() - start) / 1_000_000_000.0 % 3600.0).toFloat()
        }
    }
    val cache = remember { IndicatorCache() }
    val color = Color(colorArgb.toInt())
    val alertColor = Color(IndicatorColors.alertArgb(severity, low, mid).toInt())

    Canvas(modifier) {
        val k = intensity.coerceIn(0f, 1f)
        if (k <= 0f) return@Canvas
        val raw = time.floatValue
        val t = raw * (0.2f + 1.6f * speed.coerceIn(0f, 1f))
        val steady = reading.kind == ReadingKind.Idle
        if (calm != null && reading.kind != ReadingKind.Off) drawDots(cache, calm, color, k, t, steady)
        if (alertShown) {
            val strength = IndicatorMath.alertStrength(severity.level, severity.urgent, k) * IndicatorMath.pulse(raw, severity.urgent, stillIndicators)
            when (alert) {
                AlertEffect.Vignette -> drawVignette(cache, alertColor, severity.level, strength)
                AlertEffect.ComicLines -> drawComic(cache, alertColor, severity, strength, raw, stillIndicators || !severity.urgent)
                null -> Unit
            }
        }
    }
}

/** The next frame time for an endless animation: the platform's infinite-animation policy decides (none on a device, so this is plain withFrameNanos there; a test that auto-advances its clock ends the loop). */
private suspend fun indicatorFrameNanos(): Long {
    val policy = kotlin.coroutines.coroutineContext[androidx.compose.ui.platform.InfiniteAnimationPolicy]
    return if (policy != null) policy.onInfiniteOperation { androidx.compose.runtime.withFrameNanos { it } } else androidx.compose.runtime.withFrameNanos { it }
}

private fun DrawScope.drawVignette(c: IndicatorCache, color: Color, level: Float, strength: Float) {
    val argb = color.toArgb()
    val inner = (IndicatorMath.vignetteInner(level) * 32f).toInt() / 32f
    if (c.vignette == null || c.vigW != size.width || c.vigH != size.height || c.vigColor != argb || c.vigInner != inner) {
        c.vigW = size.width; c.vigH = size.height; c.vigColor = argb; c.vigInner = inner
        c.vignette = Brush.radialGradient(
            0f to Color.Transparent,
            inner to Color.Transparent,
            1f to color.copy(alpha = 0.85f),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = hypot(size.width, size.height) / 2f,
        )
    }
    drawRect(c.vignette!!, alpha = strength, style = Fill)
}

private fun DrawScope.drawDots(
    c: IndicatorCache, calm: CalmEffect, color: Color, k: Float, t: Float, steady: Boolean,
) {
    c.ensure(size.width, size.height, 22.dp.toPx())
    val breathe = calm == CalmEffect.AnimatedDots
    val bloom = calm == CalmEffect.Bloom
    // ~4 s per breath at default speed; steady for Idle.
    val glow = if (steady) 0.6f else if (breathe) 0.35f + 0.65f * (0.5f - 0.5f * cos(t * TWO_PI / 4f)) else 0.5f
    val width = 0.16f
    val centre = IndicatorMath.ringCentre(IndicatorMath.wrap01(t / 5f), width)
    val n = c.n
    for (i in 0 until n) {
        val w = c.weight[i]
        var a = w * glow
        if (bloom) a += (0.5f + 0.5f * w) * IndicatorMath.ringWave(c.dist[i], centre, width)
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

/** Comic-book lines as the alert: the deterministic [ComicLines] set, thinned to a few thin lines when low and the full bold ring when lost; static unless [still] is false. */
private fun DrawScope.drawComic(c: IndicatorCache, color: Color, sev: IndicatorSeverity, strength: Float, raw: Float, still: Boolean) {
    val lines = c.comic
    lines.fill(size.width, size.height, (raw * 1000f).toLong(), still, IndicatorMath.comicFraction(sev.level, sev.urgent))
    val paint = c.paint
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeCap = android.graphics.Paint.Cap.BUTT
    val widthK = IndicatorMath.comicWidth(sev.level, sev.urgent)
    val nc = drawContext.canvas.nativeCanvas
    val base = color.toArgb()
    for (cls in 0 until ComicLines.CLASSES) {
        if (lines.counts[cls] == 0) continue
        paint.strokeWidth = ComicLines.WIDTH_DP[cls].dp.toPx() * widthK
        val alpha = ((0.55f + 0.45f * cls / (ComicLines.CLASSES - 1)) * strength * 255f).toInt().coerceIn(0, 255)
        paint.color = (base and 0x00FFFFFF) or (alpha shl 24)
        nc.drawLines(lines.buffers[cls], 0, lines.counts[cls], paint)
    }
    paint.strokeCap = android.graphics.Paint.Cap.ROUND
}
