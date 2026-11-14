package com.bos.sphere.core.catalog

import kotlin.math.max
import kotlin.math.min

/**
 * HOM-16: drawing home over the system wallpaper. Scroll offsets are forwarded as the fraction of
 * the way through the pages, and the accent is the most vivid of the wallpaper's own colours that
 * still reads on the dimmed scrim.
 */
object WallpaperMath {

    /** x offset in 0..1 for [page] of [pageCount] (or a continuous [position] in pages). */
    fun xOffset(position: Float, pageCount: Int): Float =
        if (pageCount <= 1) 0.5f else (position / (pageCount - 1)).coerceIn(0f, 1f)

    /** Offset step the wallpaper service needs: 1 / (pages - 1), or 0 for a single page. */
    fun xStep(pageCount: Int): Float = if (pageCount <= 1) 0f else 1f / (pageCount - 1)

    private fun hsv(argb: Int): FloatArray {
        val r = (argb shr 16 and 0xFF) / 255f; val g = (argb shr 8 and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> ((g - b) / d + 6f) % 6f
            mx == g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return floatArrayOf(h, if (mx == 0f) 0f else d / mx, mx)
    }

    /**
     * Picks the accent from [candidates] (primary, secondary, tertiary, ARGB): the one with the
     * highest saturation x brightness; if none is vivid enough, [fallback]. Brightness is lifted to
     * at least [minValue] so the accent stays visible on a dark scrim.
     */
    fun accent(candidates: List<Int>, fallback: Int, minValue: Float = 0.6f): Int {
        val best = candidates.maxByOrNull { val h = hsv(it); h[1] * h[2] } ?: return fallback
        val h = hsv(best)
        if (h[1] < 0.15f) return fallback
        val v = max(h[2], minValue)
        return hsvToArgb(h[0], h[1], v)
    }

    internal fun hsvToArgb(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f); h < 120 -> Triple(x, c, 0f); h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c); h < 300 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
