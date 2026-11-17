package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.BgItem

/**
 * HOM-16, PAL-24 (wave 15): how every background item glows differently with the signal level. Pure and allocation free:
 * each parameter is a table of five values for the levels 0 (no signal: the dim resting state) to 4 (excellent), read by
 * linear interpolation for the eased, fractional level the painters hold, so a change of level slides over about one
 * second instead of jumping. The tables are the one place where "signal 1 looks like this, signal 4 like that" is written.
 */
internal object BackgroundCurves {
    /** Level changes ease over this long (ms). */
    const val EASE_MS = 1000
    const val MAX_LEVEL = 4f

    /** Value of [table] (five entries, level 0..4) at the fractional [level]. */
    fun at(table: FloatArray, level: Float): Float {
        val l = level.coerceIn(0f, MAX_LEVEL)
        val i = l.toInt().coerceAtMost(3)
        val f = l - i
        return table[i] + (table[i + 1] - table[i]) * f
    }

    private fun t(vararg v: Float): FloatArray { require(v.size == 5); return floatArrayOf(*v) }

    // ---- living wallpapers
    /** Beam: how far the beam reaches from its entry edge (fraction of the long side), how wide it spreads, the brightness of the white-lilac tip, the sparkle band along the core. */
    val BEAM_REACH = t(0.30f, 0.46f, 0.63f, 0.82f, 1.00f)
    val BEAM_WIDTH = t(0.30f, 0.50f, 0.70f, 0.88f, 1.00f)
    val BEAM_TIP = t(0.10f, 0.35f, 0.58f, 0.80f, 1.00f)
    val BEAM_SPARKLE = t(0.00f, 0.20f, 0.50f, 0.78f, 1.00f)
    val BEAM_BODY = t(0.22f, 0.45f, 0.68f, 0.86f, 1.00f)

    /** Spire: height of the blue cusp (fraction of the window height) and the brightness of its white core. */
    val SPIRE_HEIGHT = t(0.14f, 0.26f, 0.38f, 0.49f, 0.58f)
    val SPIRE_CORE = t(0.06f, 0.32f, 0.58f, 0.82f, 1.00f)
    val SPIRE_BLOOM = t(0.25f, 0.45f, 0.65f, 0.85f, 1.00f)

    /** Halo: degrees of the visible, bright arc (short and bright up to nearly closed), and the brightness of the trailing haze. */
    val HALO_ARC = t(24f, 70f, 150f, 250f, 335f)
    val HALO_HAZE = t(0.18f, 0.40f, 0.62f, 0.82f, 1.00f)
    val HALO_HEAD = t(0.35f, 0.60f, 0.80f, 0.92f, 1.00f)

    /** Laser: length of the red beam (fraction of the full beam), how far the thin arc is drawn (fraction of its full run), the floor glow. */
    val LASER_BEAM = t(0.14f, 0.34f, 0.56f, 0.78f, 1.00f)
    val LASER_ARC = t(0.08f, 0.28f, 0.52f, 0.76f, 1.00f)
    val LASER_FLOOR = t(0.12f, 0.38f, 0.62f, 0.84f, 1.00f)

    /** Shaft: width of the violet diagonal shaft (fraction of its full width) and its brightness. */
    val SHAFT_WIDTH = t(0.32f, 0.50f, 0.68f, 0.85f, 1.00f)
    val SHAFT_BRIGHT = t(0.16f, 0.38f, 0.60f, 0.80f, 1.00f)

    // ---- effects
    /** Bottom glow: bloom height (fraction of the window height) and brightness. */
    val BOTTOM_HEIGHT = t(0.05f, 0.14f, 0.27f, 0.42f, 0.58f)
    val BOTTOM_BRIGHT = t(0.20f, 0.42f, 0.62f, 0.82f, 1.00f)

    /** Breathing dots: depth of the breath (0 = steady, 1 = to nothing) and the radius of the lit area (fraction of the full grid). */
    val DOTS_AMPLITUDE = t(0.15f, 0.30f, 0.45f, 0.55f, 0.70f)
    val DOTS_LIT = t(0.40f, 0.55f, 0.70f, 0.85f, 1.00f)

    /** Radar sweep: brightness of the wedge, and the milliseconds between pings (0 = no ping at all). */
    val SWEEP_WEDGE = t(0.30f, 0.55f, 0.75f, 0.90f, 1.00f)
    val PING_EVERY_MS = t(0f, 9000f, 6500f, 5000f, 3200f)

    /** The named parameter tables of one item, for the tests that prove "it glows differently with the level". */
    fun tablesOf(item: BgItem): List<FloatArray> = when (item) {
        BgItem.Beam -> listOf(BEAM_REACH, BEAM_WIDTH, BEAM_TIP, BEAM_SPARKLE)
        BgItem.Spire -> listOf(SPIRE_HEIGHT, SPIRE_CORE)
        BgItem.Halo -> listOf(HALO_ARC, HALO_HAZE)
        BgItem.Laser -> listOf(LASER_BEAM, LASER_ARC, LASER_FLOOR)
        BgItem.Shaft -> listOf(SHAFT_WIDTH, SHAFT_BRIGHT)
        BgItem.BottomGlow -> listOf(BOTTOM_HEIGHT, BOTTOM_BRIGHT)
        BgItem.BreathingDots -> listOf(DOTS_AMPLITUDE, DOTS_LIT)
        BgItem.RadarSweep -> listOf(SWEEP_WEDGE)
        BgItem.GlowLines, BgItem.None -> emptyList()
    }
}

/**
 * The signal level as the painters see it: eased toward the target with a time constant of [TAU_MS] (about 95 percent after
 * one second, whatever the size of the step), instant under reduced motion. One instance per painter, no allocation.
 */
internal class LevelEase {
    var value = 3f
        private set
    private var last = -1L

    /** Eased level 0..4. [target] is the signal level, or null while the live source has not said anything yet (calm: good). */
    fun step(target: Int?, nowMs: Long, instant: Boolean): Float {
        val goal = (target ?: 3).coerceIn(0, 4).toFloat()
        if (instant || last < 0 || nowMs < last) value = goal else {
            val k = 1f - kotlin.math.exp(-(nowMs - last).toFloat() / TAU_MS)
            value += (goal - value) * k
            if (kotlin.math.abs(goal - value) < 0.004f) value = goal
        }
        last = nowMs
        return value
    }

    companion object { const val TAU_MS = 330f }
}
