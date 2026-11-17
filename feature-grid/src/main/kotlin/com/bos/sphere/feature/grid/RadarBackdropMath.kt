package com.bos.sphere.feature.grid

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * PAL-24: the pure maths of the Radar background (dot grid, scope sweep, ping). No Android types, no per-frame
 * allocation: layouts are built once per size, frames only fill preallocated arrays.
 */

/** The dots of one window size: position, elliptical envelope (1 centre .. 0 edge), normalised distance and phase. */
internal class DotGridLayout(
    val xy: FloatArray,
    val env: FloatArray,
    val dist: FloatArray,
    val phase: FloatArray,
    val count: Int,
    val portrait: Boolean,
)

internal object DotGridMath {
    const val PITCH_DP = 24f
    const val RADIUS_DP = 2f
    const val RADIUS_MAX_DP = 3f
    /** Owner's dots: a 30 percent white fill seen through a #D9D9D9 mask, so 0.30 x 0.85 at the middle. */
    const val PEAK_ALPHA = 0.255f
    const val BUCKETS = 8
    /** One breath, in ms (spec: 4 to 7 s). */
    const val PERIOD_MS = 5500L
    /** The breath a still (reduced-motion) grid is frozen at. */
    const val STILL_BREATH = 0.5f
    /** ry / height of the owner's ellipse: 189.5 / 440. */
    const val SHORT_AXIS = 189.5f / 440f

    fun hash01(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }

    /**
     * Lay the dots out for a [wPx] x [hPx] window: pitch [PITCH_DP] dp, centred, ellipse follows the window (landscape
     * 956 x 440 artboard, portrait the 440 x 956 one). Dots whose envelope is below 2 percent are not kept.
     */
    fun layout(wPx: Float, hPx: Float, densityPx: Float): DotGridLayout {
        val pitch = PITCH_DP * densityPx
        val portrait = hPx > wPx
        val cx = wPx / 2f; val cy = hPx / 2f
        val rx = if (portrait) wPx * SHORT_AXIS else wPx / 2f
        val ry = if (portrait) hPx / 2f else hPx * SHORT_AXIS
        val cols = (wPx / pitch).toInt().coerceAtLeast(1)
        val rows = (hPx / pitch).toInt().coerceAtLeast(1)
        val x0 = (wPx - (cols - 1) * pitch) / 2f
        val y0 = (hPx - (rows - 1) * pitch) / 2f
        val xy = FloatArray(cols * rows * 2); val env = FloatArray(cols * rows); val dist = FloatArray(cols * rows); val ph = FloatArray(cols * rows)
        var n = 0
        for (j in 0 until rows) for (i in 0 until cols) {
            val x = x0 + i * pitch; val y = y0 + j * pitch
            val d = hypot((x - cx) / rx, (y - cy) / ry)
            val e = 1f - d
            if (e < 0.02f) continue
            xy[n * 2] = x; xy[n * 2 + 1] = y; env[n] = e; dist[n] = d.coerceIn(0f, 1f); ph[n] = hash01(i, j) * 0.4f
            n++
        }
        return DotGridLayout(xy, env, dist, ph, n, portrait)
    }

    /** One dot's breath 0..1: a slow wave out from the centre plus the dot's own phase offset. */
    fun breath(tMs: Long, dist: Float, phase: Float): Float {
        val cycle = (tMs % PERIOD_MS).toFloat() / PERIOD_MS
        return 0.5f + 0.5f * sin(2f * PI.toFloat() * (cycle - 0.55f * dist - phase))
    }

    /** Bucket (0 = not drawn .. BUCKETS-1) of a dot with brightness [v] 0..1. */
    fun bucketOf(v: Float): Int = (v * (BUCKETS - 1) + 0.5f).toInt().coerceIn(0, BUCKETS - 1)

    /** Brightness of a dot: envelope scaled by breath (a dot never fully fades inside the envelope: 45 to 100 percent). */
    fun brightness(env: Float, breath: Float): Float = env * (0.45f + 0.55f * breath)

    /** The breath depth of the owner's grid (0.55 = the 45 to 100 percent above); wave 15 lets the signal level change it. */
    const val BASE_AMPLITUDE = 0.55f

    /** Brightness with a chosen breath [amplitude] (0 steady .. 1 to nothing); [BASE_AMPLITUDE] equals [brightness]. */
    fun brightness(env: Float, breath: Float, amplitude: Float): Float = env * ((1f - amplitude) + amplitude * breath)

    /**
     * Sort the dots into [BUCKETS] arrays of x,y pairs; returns nothing, fills [counts] (dots per bucket). Buckets must
     * be able to hold `layout.count * 2` floats. [still] freezes the grid at mid-breath.
     */
    fun fillBuckets(layout: DotGridLayout, tMs: Long, still: Boolean, buckets: Array<FloatArray>, counts: IntArray, amplitude: Float = BASE_AMPLITUDE, lit: Float = 1f) {
        java.util.Arrays.fill(counts, 0)
        for (i in 0 until layout.count) {
            val b = if (still) STILL_BREATH else breath(tMs, layout.dist[i], layout.phase[i])
            // [lit] < 1 shrinks the lit area: the envelope reaches zero at that fraction of the grid's radius
            val env = if (lit >= 1f) layout.env[i] else (1f - layout.dist[i] / lit).coerceAtLeast(0f)
            val k = bucketOf(brightness(env, b, amplitude))
            if (k == 0) continue
            val c = counts[k]
            buckets[k][c * 2] = layout.xy[i * 2]; buckets[k][c * 2 + 1] = layout.xy[i * 2 + 1]
            counts[k] = c + 1
        }
    }

    fun bucketFraction(k: Int): Float = k / (BUCKETS - 1).toFloat()
    fun bucketRadiusDp(k: Int): Float = RADIUS_DP + (RADIUS_MAX_DP - RADIUS_DP) * bucketFraction(k)
}

/** The scope sweep and the ping ring. */
internal object ScopeMath {
    const val SWEEP_PERIOD_MS = 6000L
    const val PING_EVERY_MS = 5000L
    const val PING_LIFE_MS = 2600L
    /** The angle the still (reduced-motion) wedge rests at, and the time a still frame is read at. */
    const val STILL_ANGLE = 40f
    const val STILL_TIME_MS = 1800L

    /** Degrees clockwise from 3 o'clock, one turn per [SWEEP_PERIOD_MS]. */
    fun sweepAngle(tMs: Long): Float = (tMs % SWEEP_PERIOD_MS).toFloat() / SWEEP_PERIOD_MS * 360f

    /** 0..1 progress of the ping in its life, or -1 when none is showing. */
    fun pingProgress(tMs: Long): Float = pingProgress(tMs, PING_EVERY_MS.toFloat())

    /** The same with a chosen gap between pings (wave 15: the signal level sets it); [everyMs] <= 0 means no ping at all. */
    fun pingProgress(tMs: Long, everyMs: Float): Float {
        if (everyMs <= 0f) return -1f
        val every = everyMs.toLong().coerceAtLeast(PING_LIFE_MS + 1)
        val into = tMs % every
        return if (into < PING_LIFE_MS) into.toFloat() / PING_LIFE_MS else -1f
    }

    /** Ping radius as a fraction of the scope radius: fast out, slowing down. */
    fun pingRadius(p: Float): Float { val q = 1f - p; return 1f - q * q }

    /** Ping alpha multiplier: bright at birth, gone at the end. */
    fun pingAlpha(p: Float): Float { val q = 1f - p; return q * q }

    /** Ring radii as fractions of the scope radius. */
    val RINGS = floatArrayOf(0.14f, 0.28f, 0.44f, 0.62f, 0.82f)
}

internal fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** A value that eases to a target over [durMs], driven by the shared clock (no allocation). */
internal class Fade(var value: Float = 0f) {
    private var last = -1L
    fun step(target: Float, nowMs: Long, durMs: Int, instant: Boolean): Float {
        if (instant || last < 0 || nowMs < last) { value = target } else {
            val d = (nowMs - last).toFloat() / durMs
            value = if (target > value) minOf(target, value + d) else maxOf(target, value - d)
        }
        last = nowMs
        return value
    }
}

internal fun fract(x: Float): Float = x - floor(x)
internal fun absF(x: Float) = abs(x)
