package com.bos.sphere.feature.grid

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.CalmStyle
import com.bos.sphere.core.data.CalmStyles
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
import kotlin.math.min

/** Pure geometry and timing for [IndicatorLayer]; unit-tested on the JVM. */
internal object IndicatorMath {
    /** Seconds of one urgent pulse: slow, so even a lost signal stays calm (wave 55; it was 1.2 s). */
    const val URGENT_PERIOD_S = 3.2f

    /** Where the dots of Edge dots sit: this far in from the screen edge (dp) on the outer ring, and on the inner ring. */
    const val OUTER_INSET_DP = 7f
    const val INNER_INSET_DP = 16f
    const val OUTER_SPACING_DP = 14f
    const val INNER_SPACING_DP = 20f

    /** Corner bloom: dot spacing and the radius of the quarter disc (dp, never more than [CORNER_MAX_FRACTION] of the short side). */
    const val CORNER_SPACING_DP = 12f
    const val CORNER_RADIUS_DP = 52f
    const val CORNER_MAX_FRACTION = 0.14f

    /** Bottom glow: the glow's height as a fraction of the window height for a reading [value] (never more than 6 percent). */
    fun glowHeightFraction(value: Float): Float = 0.02f + 0.04f * value.coerceIn(0f, 1f)

    /** Beam: the lit share of the track for a reading [value]; the track is the middle 76 percent of the edge. */
    const val BEAM_TRACK_FRACTION = 0.76f
    const val BEAM_INSET_DP = 3f
    const val BEAM_WIDTH_DP = 2f

    /** How strong the style is relative to its cap: a quiet 0.7 while healthy, rising to 1 as the value falls; urgent is full. */
    fun strength(level: Float, urgent: Boolean): Float = if (urgent) 1f else 0.7f + 0.3f * level.coerceIn(0f, 1f)

    /** Brightness factor 0.8..1 of the urgent pulse at clock [t] seconds; steady 1 when not urgent or when motion is reduced (the static frame is full strength). */
    fun pulse(t: Float, urgent: Boolean, reduced: Boolean): Float =
        if (!urgent || reduced) 1f else 0.8f + 0.2f * (0.5f + 0.5f * cos(t * TWO_PI / URGENT_PERIOD_S))

    /** 0..1 breath of period [periodS] at [t]; 0.5 (the middle of the breath) when [still]. */
    fun breath(t: Float, periodS: Float, still: Boolean): Float = if (still) 0.5f else 0.5f - 0.5f * cos(t * TWO_PI / periodS)

    /** Brightness 0..1 of a ring of half-[width] centred at [centre], at distance [d]. */
    fun ringWave(d: Float, centre: Float, width: Float): Float {
        val x = 1f - abs(d - centre) / width
        return if (x <= 0f) 0f else x * x * (3f - 2f * x)
    }

    /** Ring centre for a wave [phase] 0..1, travelling from just before 0 to just past 1. */
    fun ringCentre(phase: Float, width: Float): Float = -width + phase * (1f + 2f * width)

    fun wrap01(x: Float): Float = x - floor(x)

    /** Point [i] of an evenly spaced walk of [spacing] around the rectangle inset by [inset] inside a [w] x [h] window, as (x, y); the walk starts at the top-left and runs clockwise. */
    fun perimeterPoint(i: Int, spacing: Float, inset: Float, w: Float, h: Float, out: FloatArray) {
        val x0 = inset; val y0 = inset; val x1 = w - inset; val y1 = h - inset
        val rw = x1 - x0; val rh = y1 - y0
        var s = i * spacing
        val per = 2f * (rw + rh)
        s %= per
        when {
            s < rw -> { out[0] = x0 + s; out[1] = y0 }
            s < rw + rh -> { out[0] = x1; out[1] = y0 + (s - rw) }
            s < 2 * rw + rh -> { out[0] = x1 - (s - rw - rh); out[1] = y1 }
            else -> { out[0] = x0; out[1] = y1 - (s - 2 * rw - rh) }
        }
    }

    fun perimeterCount(spacing: Float, inset: Float, w: Float, h: Float): Int = (2f * ((w - 2 * inset) + (h - 2 * inset)) / spacing).toInt().coerceAtLeast(0)

    private const val TWO_PI = (2.0 * PI).toFloat()
}

/** The ink of a state: the ramp colour, the fixed urgent red when lost; null when nothing is drawn (a battery reading that is not live). */
internal fun calmInk(reading: IndicatorReading, severity: IndicatorSeverity, rampArgb: Long): Color? = when {
    severity.urgent -> Color(IndicatorColors.URGENT_RED.toInt())
    reading.kind == ReadingKind.Off -> null
    else -> Color(rampArgb.toInt())
}

/**
 * The calm indicator (wave 55): one of four ambient styles drawn ON TOP of the wallpaper as edge, corner or line accents, never a fill, scrim, vignette or tint, and
 * never above [CalmStyles.alphaCap] of [intensity] (at most [CalmStyles.MAX_ALPHA]) for any pixel. Healthy is a quiet low-contrast glow in the ramp colour; as the
 * value falls ([IndicatorSeverity.of] for [metric] and [reading]) the accent grows stronger and moves toward the ramp's amber and red; lost (Signal: offline,
 * Battery: 5 percent and not charging) it is full strength within the cap, red, with a slow pulse. [CalmStyle.None] and [CalmStyle.Regular] draw nothing.
 * Motion is slow and the clock runs only while something moves: never under reduced motion, never in battery saver, never when the reading is idle (airplane
 * mode), when the intensity is zero or when the style is off; then the layer paints one still frame.
 */
@Composable
fun IndicatorLayer(
    reading: IndicatorReading,
    style: CalmStyle,
    metric: IndicatorMetric,
    colorArgb: Long,
    intensity: Float,
    speed: Float,
    modifier: Modifier = Modifier,
    low: Long = 0xFFFF4D4D,
    mid: Long = 0xFFFFB347,
) {
    if (!style.ambient) return
    val still = com.bos.sphere.core.design.rememberMotionPrefs().reduced || rememberBatterySaver() // NFR-14: with system animations off, or in battery saver, one still frame
    val severity = IndicatorSeverity.of(metric, reading)
    val ink = calmInk(reading, severity, colorArgb)
    val needsClock = !still && intensity > 0f && ink != null && reading.kind != ReadingKind.Idle
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(needsClock) {
        if (!needsClock) return@LaunchedEffect
        val start = indicatorFrameNanos()
        while (true) {
            // Seconds of animation clock; wraps (invisibly) once an hour.
            time.floatValue = ((indicatorFrameNanos() - start) / 1_000_000_000.0 % 3600.0).toFloat()
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        val cap = CalmStyles.alphaCap(intensity)
        if (cap <= 0f || ink == null) return@Canvas
        val raw = time.floatValue
        val t = raw * (0.4f + 1.2f * speed.coerceIn(0f, 1f))
        val s = IndicatorMath.strength(severity.level, severity.urgent) * IndicatorMath.pulse(raw, severity.urgent, still || reading.kind == ReadingKind.Idle)
        val a = cap * s.coerceIn(0f, 1f)
        val value = if (reading.kind == ReadingKind.Live) reading.value.let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) } else 0f
        val moving = needsClock
        when (style) {
            CalmStyle.EdgeDots -> drawEdgeDots(ink, a, t, severity, moving)
            CalmStyle.CornerBloom -> drawCornerBloom(ink, a, t, moving)
            CalmStyle.BottomGlow -> drawBottomGlow(ink, a, t, value, severity.urgent, moving)
            CalmStyle.Beam -> drawBeam(ink, a, t, value, severity.urgent, rtl, moving)
            CalmStyle.None, CalmStyle.Regular -> Unit
        }
    }
}

/** The next frame time for an endless animation: the platform's infinite-animation policy decides (none on a device, so this is plain withFrameNanos there; a test that auto-advances its clock ends the loop). */
private suspend fun indicatorFrameNanos(): Long {
    val policy = kotlin.coroutines.coroutineContext[androidx.compose.ui.platform.InfiniteAnimationPolicy]
    return if (policy != null) policy.onInfiniteOperation { androidx.compose.runtime.withFrameNanos { it } } else androidx.compose.runtime.withFrameNanos { it }
}

/** Edge dots: two rings of dots just inside the screen edge; a slow swell travels around them. */
private fun DrawScope.drawEdgeDots(ink: Color, a: Float, t: Float, sev: IndicatorSeverity, moving: Boolean) {
    val pt = FloatArray(2)
    val rings = listOf(
        Triple(IndicatorMath.OUTER_INSET_DP.dp.toPx(), IndicatorMath.OUTER_SPACING_DP.dp.toPx(), 1f),
        Triple(IndicatorMath.INNER_INSET_DP.dp.toPx(), IndicatorMath.INNER_SPACING_DP.dp.toPx(), 0.6f),
    )
    val grow = 1f + sev.level
    for ((inset, spacing, weight) in rings) {
        val n = IndicatorMath.perimeterCount(spacing, inset, size.width, size.height)
        for (i in 0 until n) {
            IndicatorMath.perimeterPoint(i, spacing, inset, size.width, size.height, pt)
            val swell = if (sev.urgent || !moving) 0.5f else IndicatorMath.breath(t - 6f * i / n.coerceAtLeast(1), 6f, false)
            val alpha = a * weight * (0.45f + 0.55f * swell)
            val r = (1.1f + 1.3f * swell * grow / 2f).dp.toPx()
            drawCircle(ink.copy(alpha = alpha), r, Offset(pt[0], pt[1]))
        }
    }
}

/** Corner bloom: quarter discs of dots in the four corners; a ring of light travels slowly outward from each corner. */
private fun DrawScope.drawCornerBloom(ink: Color, a: Float, t: Float, moving: Boolean) {
    val spacing = IndicatorMath.CORNER_SPACING_DP.dp.toPx()
    val radius = min(IndicatorMath.CORNER_RADIUS_DP.dp.toPx(), IndicatorMath.CORNER_MAX_FRACTION * min(size.width, size.height))
    val width = 0.3f
    val centre = IndicatorMath.ringCentre(IndicatorMath.wrap01(t / 7f), width)
    val steps = (radius / spacing).toInt() + 1
    for (cx in 0..1) for (cy in 0..1) {
        for (i in 0 until steps) for (j in 0 until steps) {
            val dx = (i + 0.5f) * spacing; val dy = (j + 0.5f) * spacing
            val d = hypot(dx, dy)
            if (d > radius) continue
            val fall = 1f - d / radius * 0.5f
            val wave = if (moving) IndicatorMath.ringWave(d / radius, centre, width) else 0.5f
            val alpha = a * fall * (0.5f + 0.5f * wave)
            val x = if (cx == 0) dx else size.width - dx
            val y = if (cy == 0) dy else size.height - dy
            drawCircle(ink.copy(alpha = alpha), (1.4f + 1.4f * wave).dp.toPx(), Offset(x, y))
        }
    }
}

/** Bottom glow: a hair-thin bright line along the bottom edge with a low bloom above it; the bloom's height follows the reading and it breathes slowly. */
private fun DrawScope.drawBottomGlow(ink: Color, a: Float, t: Float, value: Float, urgent: Boolean, moving: Boolean) {
    val breathe = if (urgent) 0.5f else IndicatorMath.breath(t, 6f, !moving)
    val h = size.height * IndicatorMath.glowHeightFraction(if (urgent) 0f else value) * (0.85f + 0.3f * breathe)
    drawRect(
        Brush.verticalGradient(0f to Color.Transparent, 1f to ink.copy(alpha = a * 0.7f), startY = size.height - h, endY = size.height),
        topLeft = Offset(0f, size.height - h), size = Size(size.width, h),
    )
    val line = 1.5.dp.toPx()
    drawRect(
        Brush.horizontalGradient(0f to Color.Transparent, 0.2f to ink.copy(alpha = a * 0.5f), 0.8f to ink.copy(alpha = a * 0.5f), 1f to Color.Transparent),
        topLeft = Offset(0f, size.height - line), size = Size(size.width, line),
    )
}

/** Edge beam: a thin line down the trailing edge (the right edge, the left one in right-to-left layouts): a faint track and a lit part whose length is the reading, with a soft glow inside it. */
private fun DrawScope.drawBeam(ink: Color, a: Float, t: Float, value: Float, urgent: Boolean, rtl: Boolean, moving: Boolean) {
    val inset = IndicatorMath.BEAM_INSET_DP.dp.toPx()
    val wd = IndicatorMath.BEAM_WIDTH_DP.dp.toPx()
    val trackLen = size.height * IndicatorMath.BEAM_TRACK_FRACTION
    val top = (size.height - trackLen) / 2f
    val x = if (rtl) inset else size.width - inset - wd
    drawRect(ink.copy(alpha = a * 0.25f), Offset(x, top), Size(wd, trackLen))
    val frac = if (urgent) 1f else value
    val lit = trackLen * frac
    if (lit <= 0f) return
    val breathe = if (urgent) 0.5f else IndicatorMath.breath(t, 6f, !moving)
    val k = 0.7f + 0.3f * breathe
    val glow = 9.dp.toPx()
    val gx = if (rtl) x else x + wd - glow
    val glowBrush = if (rtl) Brush.horizontalGradient(0f to ink.copy(alpha = a * 0.35f * k), 1f to Color.Transparent, startX = gx, endX = gx + glow)
    else Brush.horizontalGradient(0f to Color.Transparent, 1f to ink.copy(alpha = a * 0.35f * k), startX = gx, endX = gx + glow)
    drawRect(glowBrush, Offset(gx, top + trackLen - lit), Size(glow, lit))
    drawRect(ink.copy(alpha = a * 0.6f * k), Offset(x, top + trackLen - lit), Size(wd, lit))
}
