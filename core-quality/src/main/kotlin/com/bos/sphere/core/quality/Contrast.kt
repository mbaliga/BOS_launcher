package com.bos.sphere.core.quality

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** NFR-15: WCAG 2.x contrast math on packed ARGB ints (alpha ignored unless stated), no Android dependency. */
object Contrast {
    const val AA_NORMAL = 4.5
    const val AA_LARGE = 3.0

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** [fg] composited over [bg] at [alpha] (0..1); result is opaque. */
    fun blend(fg: Int, bg: Int, alpha: Double): Int {
        fun mix(s: Int) = Math.round(((fg shr s) and 0xFF) * alpha + ((bg shr s) and 0xFF) * (1 - alpha)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    fun meetsAa(fg: Int, bg: Int, largeText: Boolean = false): Boolean = ratio(fg, bg) >= if (largeText) AA_LARGE else AA_NORMAL

    /** Black or white, whichever reads better on [bg]. */
    fun bestText(bg: Int): Int = if (ratio(WHITE, bg) >= ratio(BLACK, bg)) WHITE else BLACK

    /**
     * Smallest scrim alpha (steps of 1/100, scrim colour [scrim]) so that [text] reaches [target] contrast against the
     * worst-case wallpaper colour [wallpaper] (the one that gives the lowest contrast in the text region). Null if even
     * a full scrim cannot do it (text colour too close to the scrim colour).
     */
    fun scrimAlphaFor(text: Int, wallpaper: Int, scrim: Int = BLACK, target: Double = AA_NORMAL): Double? {
        for (i in 0..100) {
            val a = i / 100.0
            if (ratio(text, blend(scrim, wallpaper, a)) >= target) return a
        }
        return null
    }

    /**
     * Scrim alpha that guarantees [target] for [text] over every colour in [wallpaperSamples] (sampled from the area
     * behind the text). Empty samples give the conservative answer for mid-grey.
     */
    fun scrimAlphaForAny(text: Int, wallpaperSamples: List<Int>, scrim: Int = BLACK, target: Double = AA_NORMAL): Double? {
        val samples = wallpaperSamples.ifEmpty { listOf(0xFF808080.toInt()) }
        var worst = 0.0
        for (w in samples) worst = max(worst, scrimAlphaFor(text, w, scrim, target) ?: return null)
        return worst
    }

    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
}
