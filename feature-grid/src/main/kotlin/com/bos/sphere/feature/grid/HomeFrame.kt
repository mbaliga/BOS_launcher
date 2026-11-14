package com.bos.sphere.feature.grid

import kotlin.math.min

/**
 * HOM-19 / HOM-20: the uniform home frame as pure functions. One margin band of about 15% of the
 * short side runs round every edge, in either orientation and for every home style; apps live in
 * the rectangle inside it, and the corners, clock, page dots and dock rail live in the band.
 */
object HomeFrame {
    /** About 15% of the short side. */
    const val FRACTION = 0.148f

    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        fun intersects(o: Rect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    }

    /** Thickness of the band for a window of [width] x [height] (any unit). */
    fun band(width: Float, height: Float): Float = min(width, height) * FRACTION

    /** The app area: the window inset by [band] on every side. */
    fun appArea(width: Float, height: Float): Rect {
        val b = band(width, height)
        return Rect(b, b, (width - b).coerceAtLeast(b), (height - b).coerceAtLeast(b))
    }

    /** The four band strips (top, bottom, start, end), which together with [appArea] tile the window. */
    fun bandStrips(width: Float, height: Float): List<Rect> {
        val b = band(width, height)
        return listOf(Rect(0f, 0f, width, b), Rect(0f, height - b, width, height), Rect(0f, b, b, height - b), Rect(width - b, b, width, height - b))
    }
}

/**
 * HOM-20: with the clock at the bottom, the clock and the page dots share the bottom band. The
 * weather line is dropped first; if the clock still does not fit it is scaled down (never below
 * [MIN_CLOCK_SCALE]). The page dots are never dropped or moved out of the band.
 */
data class ClockBandFit(val showWeather: Boolean, val clockScale: Float, val usedDp: Float, val fits: Boolean) {
    companion object {
        const val MIN_CLOCK_SCALE = 0.4f
        const val EDGE_DP = 4f
        const val DOTS_DP = 10f
        const val GAP_DP = 4f

        /**
         * @param bandDp thickness of the bottom band
         * @param clockFullDp clock block height with the weather line
         * @param clockNoWeatherDp clock block height without it
         * @param dotsAtBottom false when the dots run along the start edge and do not share the bottom band
         */
        fun compute(bandDp: Float, clockFullDp: Float, clockNoWeatherDp: Float, dotsAtBottom: Boolean = true): ClockBandFit {
            val avail = bandDp - EDGE_DP - if (dotsAtBottom) DOTS_DP + GAP_DP else 0f
            val used0 = EDGE_DP + if (dotsAtBottom) DOTS_DP + GAP_DP else 0f
            if (clockFullDp <= avail) return ClockBandFit(true, 1f, used0 + clockFullDp, true)
            if (clockNoWeatherDp <= avail) return ClockBandFit(false, 1f, used0 + clockNoWeatherDp, true)
            val scale = (avail / clockNoWeatherDp.coerceAtLeast(1f)).coerceIn(MIN_CLOCK_SCALE, 1f)
            val used = used0 + clockNoWeatherDp * scale
            return ClockBandFit(false, scale, used, used <= bandDp + 0.01f)
        }
    }
}
