package com.bos.sphere.feature.grid

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * PAL-24: comic-book speed / focus lines, a reverse calm-computing alert (wave 35: the wallpaper shows them at no signal, the Indicator alert scales them with severity). Thin lines run from the window edge inward along rays from the
 * centre, with irregular angles and lengths, redrawn at [FPS] steps per second. Deterministic by seed and frame; it
 * knows nothing about signal level (it is only shown at level 0).
 */
internal class ComicLines(private val seed: Long = 0x5EEDL, val count: Int = 72) {
    /** Segment endpoints (x0, y0, x1, y1, ...) per width class; [counts] holds the floats used in each. */
    val buffers: Array<FloatArray> = Array(CLASSES) { FloatArray(count * 4) }
    val counts = IntArray(CLASSES)

    private fun rnd(a: Int, b: Int, c: Int): Float =
        DotGridMath.hash01((seed xor (seed ushr 32)).toInt() * 31 + a * 7919 + c * 104729, b)

    /** Fill [buffers] for the window and time. [still] = frame 0 (one static composition). */
    fun fill(w: Float, h: Float, tMs: Long, still: Boolean, fraction: Float = 1f) {
        java.util.Arrays.fill(counts, 0)
        // Wave 35: a share of the set (every i-th line, evenly around the ring) for the low-signal alert; 1 = all lines, the unchanged PAL-24 math.
        val use = (count * fraction.coerceIn(0f, 1f) + 0.5f).toInt()
        val frame = if (still) 0 else frameOf(tMs)
        val cx = w / 2f; val cy = h / 2f
        val step = (2.0 * PI / count).toFloat()
        for (i in 0 until count) {
            if (use < count && (i + 1) * use / count == i * use / count) continue
            val angle = (i + (rnd(i, 1, 0) - 0.5f) * 0.9f) * step + (rnd(i, 3, frame) - 0.5f) * 0.5f * step
            val inner = (MIN_INNER + (MAX_INNER - MIN_INNER) * rnd(i, 2, 0) + (rnd(i, 4, frame) - 0.5f) * 0.05f).coerceIn(MIN_INNER - 0.03f, MAX_INNER + 0.03f)
            val dx = cos(angle); val dy = sin(angle)
            val tx = if (dx > 1e-4f) (w - cx) / dx else if (dx < -1e-4f) cx / -dx else Float.MAX_VALUE
            val ty = if (dy > 1e-4f) (h - cy) / dy else if (dy < -1e-4f) cy / -dy else Float.MAX_VALUE
            val edge = minOf(tx, ty)
            val cls = i % CLASSES
            val b = buffers[cls]; var n = counts[cls]
            b[n++] = cx + dx * edge * 1.02f; b[n++] = cy + dy * edge * 1.02f
            b[n++] = cx + dx * edge * inner; b[n++] = cy + dy * edge * inner
            counts[cls] = n
        }
    }

    companion object {
        const val CLASSES = 3
        /** Redrawn about 8 times a second, stepped (never smooth). */
        const val FPS = 8
        /** The lines stay in the outer part of the screen: the inner end sits at 62..86 percent of the way to the edge. */
        const val MIN_INNER = 0.62f
        const val MAX_INNER = 0.86f
        fun frameOf(tMs: Long): Int = (tMs * FPS / 1000L).toInt()
        val WIDTH_DP = floatArrayOf(0.8f, 1.2f, 1.8f)
        val ALPHA = floatArrayOf(0.10f, 0.15f, 0.20f)
    }
}
