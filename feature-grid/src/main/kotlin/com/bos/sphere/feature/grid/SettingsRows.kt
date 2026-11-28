package com.bos.sphere.feature.grid

import kotlin.math.floor
import kotlin.math.roundToInt

/** Pure maths behind the inline settings controls (kept separate so it is unit-tested). */
object SettingsMath {
    /** Index reached by moving [delta] from [index] in a list of [count]; wraps round when [wrap], else clamps. */
    fun stepIndex(count: Int, index: Int, delta: Int, wrap: Boolean = true): Int {
        if (count <= 0) return 0
        val cur = index.coerceIn(0, count - 1)
        return if (wrap) ((cur + delta) % count + count) % count else (cur + delta).coerceIn(0, count - 1)
    }

    /** The default snap step for a slider range: 1% for fractions, whole numbers for wide ranges. */
    fun autoStep(range: ClosedFloatingPointRange<Float>): Float = if (range.endInclusive - range.start <= 2f) 0.01f else 1f

    /** [value] snapped to the nearest multiple of [step] from the range start, clamped into [range]. */
    fun snap(value: Float, range: ClosedFloatingPointRange<Float>, step: Float = autoStep(range)): Float {
        val v = value.coerceIn(range.start, range.endInclusive)
        if (step <= 0f) return v
        val n = ((v - range.start) / step).roundToInt()
        return (range.start + n * step).coerceIn(range.start, range.endInclusive)
    }

    /** Whether [a] and [b] are the same value on the slider: within half a step. */
    fun sameValue(a: Float, b: Float, range: ClosedFloatingPointRange<Float>, step: Float = autoStep(range)): Boolean {
        val half = (if (step > 0f) step else 0.001f) / 2f
        return kotlin.math.abs(a - b) < half
    }

    /** The fraction (0..1) of the range at which [value] sits, for the default tick. */
    fun fraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
        val span = range.endInclusive - range.start
        return if (span <= 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)
    }

    /** Which of [ticks] equal bands of the range [value] sits in — a haptic tick fires when this changes. */
    fun tickBand(value: Float, range: ClosedFloatingPointRange<Float>, ticks: Int = 20): Int {
        val span = range.endInclusive - range.start
        if (span <= 0f) return 0
        return floor((value - range.start) / span * ticks).toInt().coerceIn(0, ticks)
    }
}
