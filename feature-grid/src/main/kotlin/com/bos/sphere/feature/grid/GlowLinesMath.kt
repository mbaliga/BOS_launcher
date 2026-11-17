package com.bos.sphere.feature.grid

import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.PI

/**
 * PAL-24: the pure maths of the "Glow lines" signal view. The owner's eight outline shapes are flattened once into
 * centre lines (artboard-normalised polylines, start = the end nearest the clock); a pulse is a slice of one of them.
 */
internal class Polyline(val nx: FloatArray, val ny: FloatArray) {
    val size: Int get() = nx.size
    /** Cumulative length 0..1 per point, in artboard-normalised units. */
    val cum: FloatArray = FloatArray(nx.size).also { c ->
        var acc = 0f
        for (i in 1 until nx.size) { acc += hypot(nx[i] - nx[i - 1], ny[i] - ny[i - 1]); c[i] = acc }
        if (acc > 0f) for (i in c.indices) c[i] /= acc
    }
}

internal object GlowLinesMath {
    const val LINES = 8
    /** Lines light in this order (SVG order indices): opposite corners first, then the pairs, so weak signal reads as two faint pulses. */
    val PRIORITY = intArrayOf(0, 7, 1, 6, 2, 4, 3, 5)

    /** Position of each line in [PRIORITY] (inverse table, so the per-frame code never searches). */
    private val RANK: IntArray = IntArray(LINES).also { r -> PRIORITY.forEachIndexed { rank, line -> r[line] = rank } }

    /** Tokens of a path that uses only absolute M, C and Z (the owner's export); numbers may carry an exponent. */
    private val NUM = Regex("""-?\d*\.?\d+(?:[eE][-+]?\d+)?""")

    /** Flatten one outline into a closed polygon (artboard units). */
    fun flatten(d: String, stepsPerCurve: Int = 16): List<Pair<Float, Float>> {
        val pts = ArrayList<Pair<Float, Float>>()
        var i = 0
        var cx = 0f; var cy = 0f
        while (i < d.length) {
            when (d[i]) {
                'M' -> {
                    val (n, next) = numbers(d, i + 1, 2); cx = n[0]; cy = n[1]; pts += cx to cy; i = next
                }
                'C' -> {
                    var at = i + 1
                    while (true) {
                        val (n, next) = numbers(d, at, 6)
                        if (n.size < 6) { at = next; break }
                        for (s in 1..stepsPerCurve) {
                            val t = s.toFloat() / stepsPerCurve; val u = 1 - t
                            val x = u * u * u * cx + 3 * u * u * t * n[0] + 3 * u * t * t * n[2] + t * t * t * n[4]
                            val y = u * u * u * cy + 3 * u * u * t * n[1] + 3 * u * t * t * n[3] + t * t * t * n[5]
                            pts += x to y
                        }
                        cx = n[4]; cy = n[5]; at = next
                        if (at >= d.length || d[at].isLetter()) break
                    }
                    i = at
                }
                else -> i++
            }
        }
        return pts
    }

    private fun numbers(d: String, from: Int, want: Int): Pair<FloatArray, Int> {
        val out = ArrayList<Float>()
        var pos = from
        while (out.size < want) {
            val m = NUM.find(d, pos) ?: break
            // Stop at a command letter that sits before the next number (the 'e' of an exponent is part of the match).
            val gap = d.substring(pos, m.range.first)
            if (gap.any { it.isLetter() }) break
            out += m.value.toFloat(); pos = m.range.last + 1
        }
        return out.toFloatArray() to pos
    }

    private fun len(p: List<Pair<Float, Float>>): FloatArray {
        val c = FloatArray(p.size)
        for (i in 1 until p.size) c[i] = c[i - 1] + hypot(p[i].first - p[i - 1].first, p[i].second - p[i - 1].second)
        return c
    }

    private fun at(p: List<Pair<Float, Float>>, c: FloatArray, s: Float): Pair<Float, Float> {
        var k = 1
        while (k < c.size - 1 && c[k] < s) k++
        val seg = c[k] - c[k - 1]
        val t = if (seg <= 0f) 0f else ((s - c[k - 1]) / seg).coerceIn(0f, 1f)
        return (p[k - 1].first + (p[k].first - p[k - 1].first) * t) to (p[k - 1].second + (p[k].second - p[k - 1].second) * t)
    }

    /**
     * The centre line of a closed, tapering outline: the outline runs out along one edge to the tip (the point farthest
     * from the start) and back along the other, so the midpoints of matching positions on the two edges are the middle.
     * Returned in artboard-normalised coordinates, start (nearest the clock) first.
     */
    fun centreLine(d: String, samples: Int = 96): Polyline {
        val p = flatten(d)
        val c = len(p)
        val total = c.last()
        val start = p.first()
        var tipIdx = 0; var best = -1f
        for (i in p.indices) { val dd = hypot(p[i].first - start.first, p[i].second - start.second); if (dd > best) { best = dd; tipIdx = i } }
        val sTip = c[tipIdx]
        val nx = FloatArray(samples + 1); val ny = FloatArray(samples + 1)
        for (k in 0..samples) {
            val u = k.toFloat() / samples
            val a = at(p, c, u * sTip)
            val b = at(p, c, total - u * (total - sTip))
            nx[k] = (a.first + b.first) / 2f / GlowLinesSvg.ARTBOARD_W
            ny[k] = (a.second + b.second) / 2f / GlowLinesSvg.ARTBOARD_H
        }
        return Polyline(nx, ny)
    }

    /** All eight centre lines of the owner's SVG, built once. */
    val CENTRE_LINES: List<Polyline> by lazy { GlowLinesSvg.PATHS.map { centreLine(it) } }

    // ------------------------------------------------------------------ levels

    class LevelParams(val lines: Int, val pulses: Int, val periodMs: Long, val brightness: Float, val length: Float)

    /** 1 weak: two slow faint pulses. Each level adds lines or pulses, speed and brightness. 4: all eight lines, two pulses each. */
    private val PARAMS = arrayOf(
        LevelParams(2, 1, 9000, 0.35f, 0.10f),
        LevelParams(4, 1, 7500, 0.55f, 0.12f),
        LevelParams(6, 1, 6000, 0.75f, 0.14f),
        LevelParams(8, 2, 6000, 1.0f, 0.16f),
    )

    fun params(level: Int): LevelParams? = if (level <= 0) null else PARAMS[(level - 1).coerceAtMost(3)]

    fun isLit(level: Int, line: Int): Boolean {
        val p = params(level) ?: return false
        for (r in 0 until p.lines) if (PRIORITY[r] == line) return true
        return false
    }

    fun litLines(level: Int): Int = params(level)?.lines ?: 0

    /** Total pulses on screen for a level. */
    fun pulseCount(level: Int): Int = params(level)?.let { it.lines * it.pulses } ?: 0

    /**
     * Where pulse [k] of [line] sits along the line (0 = clock end, 1 = outer end) and how bright its life makes it (0..1,
     * fading in and out at the ends). Pulses travel in toward the clock. [still] = evenly spaced and frozen.
     */
    fun pulse(level: Int, line: Int, k: Int, tMs: Long, still: Boolean, out: FloatArray) {
        val p = params(level)!!
        val lineRank = RANK[line]
        val base = if (still) (k + 0.5f) / p.pulses else fract(tMs.toFloat() / p.periodMs + lineRank * 0.381966f + k.toFloat() / p.pulses)
        // base = progress of the travel; outer end (1) to clock end (0).
        out[0] = 1f - base
        out[1] = if (still) 1f else sin(PI.toFloat() * base).coerceAtLeast(0f).let { Math.sqrt(it.toDouble()).toFloat() }
    }

    /**
     * Fade of a point near the clock: the owner's calm ellipse, as a multiplier 0 (centre) .. 1 (out), in artboard-normalised
     * coordinates (0.5, 0.5 = centre). Ellipse 134.5 x 82 of 1081 x 608, softened.
     */
    fun calm(nx: Float, ny: Float): Float {
        val d = hypot((nx - 0.5f) / (134.5f * 1.3f / 1081f), (ny - 0.5f) / (82f * 1.3f / 608f))
        return smoothstep(0.85f, 1.4f, d)
    }

    /**
     * Fill [out] with segment endpoints (x0, y0, x1, y1, ...) of the part of [line] between [s0] and [s1] (0..1 of its length),
     * mapped to the window by [mapX]/[mapY] affine factors. Returns the number of floats written. No allocation.
     */
    fun slice(
        line: Polyline, s0: Float, s1: Float, w: Float, h: Float, portrait: Boolean, out: FloatArray,
    ): Int {
        val a = s0.coerceIn(0f, 1f); val b = s1.coerceIn(0f, 1f)
        if (b <= a) return 0
        var n = 0
        val cum = line.cum
        for (i in 1 until line.size) {
            val c0 = cum[i - 1]; val c1 = cum[i]
            if (c1 <= a) continue
            if (c0 >= b) break
            val span = (c1 - c0).coerceAtLeast(1e-9f)
            val t0 = (maxOf(a, c0) - c0) / span; val t1 = (minOf(b, c1) - c0) / span
            val ax = line.nx[i - 1]; val ay = line.ny[i - 1]; val dx = line.nx[i] - ax; val dy = line.ny[i] - ay
            val x0 = ax + dx * t0; val y0 = ay + dy * t0; val x1 = ax + dx * t1; val y1 = ay + dy * t1
            out[n++] = sx(x0, y0, w, h, portrait); out[n++] = sy(x0, y0, w, h, portrait)
            out[n++] = sx(x1, y1, w, h, portrait); out[n++] = sy(x1, y1, w, h, portrait)
        }
        return n
    }

    /** Artboard-normalised point to window pixels: landscape stretches the artboard, portrait transposes it (the design is symmetric). */
    fun sx(nx: Float, ny: Float, w: Float, h: Float, portrait: Boolean): Float = if (portrait) ny * w else nx * w
    fun sy(nx: Float, ny: Float, w: Float, h: Float, portrait: Boolean): Float = if (portrait) nx * h else ny * h
}
