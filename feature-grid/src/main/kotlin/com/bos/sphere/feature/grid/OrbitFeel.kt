package com.bos.sphere.feature.grid

import android.content.Context

/** PAL-23: the Orbit's tunable motion constants. Defaults equal the values that were hard-coded before. */
data class OrbitFeelValues(
    /** How fast the rings turn on their own, in dp per second. */
    val creepDp: Float = 2.5f,
    /** How quickly a fling's extra speed dies away, per second (higher = shorter glide). */
    val flingDecay: Float = 3f,
    /** How much of a drag's speed a fling keeps. */
    val flingGain: Float = 0.06f,
    /** The fastest a fling may spin the rings, in px per second. */
    val flingMax: Float = 700f,
) {
    fun coerced() = OrbitFeelValues(creepDp.coerceIn(0f, 20f), flingDecay.coerceIn(0.5f, 12f), flingGain.coerceIn(0.01f, 0.5f), flingMax.coerceIn(100f, 2500f))
}

/** One adjustable constant, as the Lab page lists it. */
class FeelHook(
    val id: String,
    val title: String,
    val default: Float,
    /** The values a tap cycles through, lowest first. */
    val steps: List<Float>,
    val get: () -> Float,
    val set: (Float) -> Unit,
) {
    fun label(): String = "${"%.3g".format(get())} (default ${"%.3g".format(default)})"

    /** The next value up, wrapping to the lowest after the highest. */
    fun next(): Float = steps.firstOrNull { it > get() + 1e-4f } ?: steps.first()
}

/** SharedPreferences-backed holder of [OrbitFeelValues]; the Orbit reads [current] every frame. */
object OrbitFeel {
    @Volatile var current: OrbitFeelValues = OrbitFeelValues()
        private set

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("orbit_feel", Context.MODE_PRIVATE)

    fun load(c: Context): OrbitFeelValues {
        val d = OrbitFeelValues()
        val v = runCatching {
            val p = sp(c)
            OrbitFeelValues(p.getFloat("creep", d.creepDp), p.getFloat("decay", d.flingDecay), p.getFloat("gain", d.flingGain), p.getFloat("max", d.flingMax)).coerced()
        }.getOrDefault(d)
        current = v
        return v
    }

    fun update(c: Context, v: OrbitFeelValues) {
        current = v.coerced()
        runCatching { sp(c).edit().putFloat("creep", current.creepDp).putFloat("decay", current.flingDecay).putFloat("gain", current.flingGain).putFloat("max", current.flingMax).apply() }
    }

    fun reset(c: Context) = update(c, OrbitFeelValues())

    /** The list the Lab page shows (CFG-17): each constant with the values a tap steps through. */
    fun labHooks(c: Context): List<FeelHook> {
        load(c)
        val d = OrbitFeelValues()
        return listOf(
            FeelHook("creep", "Orbit spin creep (dp/s)", d.creepDp, listOf(0f, 1f, 2.5f, 4f, 6f, 10f), { current.creepDp }) { update(c, current.copy(creepDp = it)) },
            FeelHook("decay", "Orbit fling decay (per s)", d.flingDecay, listOf(1f, 2f, 3f, 5f, 8f), { current.flingDecay }) { update(c, current.copy(flingDecay = it)) },
            FeelHook("gain", "Orbit fling gain", d.flingGain, listOf(0.03f, 0.06f, 0.1f, 0.2f, 0.3f), { current.flingGain }) { update(c, current.copy(flingGain = it)) },
            FeelHook("max", "Orbit fling max (px/s)", d.flingMax, listOf(300f, 500f, 700f, 1200f, 2000f), { current.flingMax }) { update(c, current.copy(flingMax = it)) },
        )
    }
}

/** The fling arithmetic of the Orbit, pulled out so the defaults can be tested to match the old behaviour. */
object OrbitMotion {
    /** The extra speed (px/s) after a drag of [dx] px, given the previous extra speed. Old formula: `extra*0.8 + dx/0.016*0.2*0.3`, clamped to ±700. */
    fun flingSpeed(extra: Float, dx: Float, feel: OrbitFeelValues): Float =
        (extra * 0.8f + dx / 0.016f * feel.flingGain).coerceIn(-feel.flingMax, feel.flingMax)

    /** The extra speed one frame of [dt] seconds later. */
    fun decay(extra: Float, dt: Float, feel: OrbitFeelValues): Float {
        val next = extra * (1f - feel.flingDecay * dt).coerceAtLeast(0f)
        return if (kotlin.math.abs(next) < 1f) 0f else next
    }
}
