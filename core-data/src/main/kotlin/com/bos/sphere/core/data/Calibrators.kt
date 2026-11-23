package com.bos.sphere.core.data

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Wave 56 (looksPresets): the pure measurement behind the "Try it" areas of Settings (Clackpad's calibrators: the person does the thing and the
 * value is written, so nobody has to guess a number). Every function takes the times it measures from (the touch events' own timestamps, or the
 * sensor's), so a test needs no clock and no device. The settings sliders stay the source of truth: a measurement only calls the same setter.
 */
object Calibrators {
    /** The hot corner delay in ms (the slider is 50..600 in steps of 10). */
    const val HOT_CORNER_MIN_MS = 50
    const val HOT_CORNER_MAX_MS = 600
    const val HOT_CORNER_STEP_MS = 10

    /** A hold of [heldMs] is the delay: rounded to the slider's step and kept on the slider. */
    fun hotCornerDelay(heldMs: Long): Int =
        ((heldMs.coerceAtLeast(0) / HOT_CORNER_STEP_MS.toDouble()).roundToInt() * HOT_CORNER_STEP_MS).coerceIn(HOT_CORNER_MIN_MS, HOT_CORNER_MAX_MS)

    /** The time the person waited between two taps, in whole seconds (the stepper is [GlanceControls.MIN_SECONDS]..[GlanceControls.MAX_SECONDS]). */
    fun controlsSeconds(betweenMs: Long): Int = GlanceControls.clampSeconds((betweenMs.coerceAtLeast(0) / 1000.0).roundToInt())

    /**
     * The sensitivity (0..1) at which a shake whose strongest push was [peak] m/s² (gravity removed) just trips the shake detector: the threshold sits at
     * [SHAKE_MARGIN] of the measured peak, so the same shake fires it with room to spare and a softer one does not. A hand that cannot reach the
     * firmest threshold gets 0, one that is lighter than the lightest threshold gets 1.
     */
    fun motionSensitivity(peak: Float): Float {
        if (peak.isNaN()) return 0f
        val target = (peak * SHAKE_MARGIN).coerceIn(SHAKE_LIGHT, SHAKE_FIRM)
        return (((SHAKE_FIRM - target) / (SHAKE_FIRM - SHAKE_LIGHT)) * 100f).roundToInt() / 100f
    }

    /** The shake threshold (m/s² above gravity) at [sensitivity]; the detector uses this very function, so the calibrator and the detector cannot drift. */
    fun shakeThreshold(sensitivity: Float): Float = SHAKE_FIRM + (SHAKE_LIGHT - SHAKE_FIRM) * sensitivity.coerceIn(0f, 1f)

    const val SHAKE_FIRM = 17f
    const val SHAKE_LIGHT = 9f
    const val SHAKE_MARGIN = 0.85f
}

/** One press and release in the test area: [down] then [up] with the touch events' own times; null from [up] without a [down]. */
class HoldMeter {
    private var downAt = -1L
    val holding: Boolean get() = downAt >= 0
    fun down(tMs: Long) { downAt = tMs }
    /** The held time in ms, once. */
    fun up(tMs: Long): Long? = if (downAt < 0) null else (tMs - downAt).coerceAtLeast(0).also { downAt = -1L }
    fun cancel() { downAt = -1L }
}

/** The wait between two taps: the first tap starts it, the next one ends it (and does not start another). */
class IntervalMeter {
    private var firstAt = -1L
    val waiting: Boolean get() = firstAt >= 0
    /** The ms since the first tap on the second tap, else null (the first tap). */
    fun tap(tMs: Long): Long? {
        if (firstAt < 0) { firstAt = tMs; return null }
        return (tMs - firstAt).coerceAtLeast(0).also { firstAt = -1L }
    }
    fun cancel() { firstAt = -1L }
}

/**
 * The strongest push of one shake: accelerometer samples (m/s², gravity included, time in ms) in, the peak of the gravity-removed acceleration out when the
 * shake has ended (it was quiet for [QUIET_MS] after at least [MIN_PEAK] m/s²). Gravity is removed the way [MotionGestureDetector] does it, with a slow low-pass.
 */
class ShakePeakMeter {
    private val grav = floatArrayOf(Float.NaN, 0f, 0f)
    private var peak = 0f
    private var lastLoudAt = -1L
    /** The latest linear acceleration, for a live bar. */
    var current = 0f; private set

    /** A finished shake's peak, or null while it is going on or nothing happened. */
    fun feed(tMs: Long, x: Float, y: Float, z: Float): Float? {
        if (grav[0].isNaN()) { grav[0] = x; grav[1] = y; grav[2] = z }
        grav[0] += (x - grav[0]) * ALPHA; grav[1] += (y - grav[1]) * ALPHA; grav[2] += (z - grav[2]) * ALPHA
        val lx = x - grav[0]; val ly = y - grav[1]; val lz = z - grav[2]
        val lin = sqrt(lx * lx + ly * ly + lz * lz)
        current = lin
        if (lin >= LOUD) { peak = maxOf(peak, lin); lastLoudAt = tMs }
        if (lastLoudAt >= 0 && tMs - lastLoudAt >= QUIET_MS) {
            val p = peak
            peak = 0f; lastLoudAt = -1L
            if (p >= MIN_PEAK) return p
        }
        return null
    }

    fun reset() { grav[0] = Float.NaN; peak = 0f; lastLoudAt = -1L; current = 0f }

    companion object {
        const val ALPHA = 0.08f
        /** Below this a movement is not part of a shake. */
        const val LOUD = 3f
        /** A shake softer than this is not measured (a table bump, a footstep). */
        const val MIN_PEAK = 6f
        const val QUIET_MS = 600L
    }
}
