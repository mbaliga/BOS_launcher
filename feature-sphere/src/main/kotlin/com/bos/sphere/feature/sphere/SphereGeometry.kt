package com.bos.sphere.feature.sphere

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sign

/**
 * Coverflow ("equator") geometry, ported verbatim from `sphere-launcher.html`.
 *
 * The prototype is the visual source of truth, so the magnification-lens spacing integral
 * ([txAt]) and the lens falloff ([magAt]) are reproduced exactly — same constants, same
 * 8-sample integral — so the native surface matches pixel-for-pixel. All distances are in the
 * prototype's CSS-px domain, which we treat as dp at the call site.
 *
 * Note: Compose `graphicsLayer` has no `translationZ`, so the CSS `translateZ` depth is folded
 * into a per-tile scale/translation via the perspective projection in [TileTransform]. The card
 * *turn* (`rotationY`) is left to `graphicsLayer.cameraDistance`.
 */
object SphereGeometry {

    const val STEP_DEG = 12.0
    private const val DEG = Math.PI / 180.0
    val STEP_R = STEP_DEG * DEG

    /** How many columns out from focus are considered on-surface. `clamp(floor(82/STEP), 2, 7)`. */
    val range: Int = floor(82.0 / STEP_DEG).toInt().coerceIn(2, 7)

    /** Hermite smoothstep, `t*t*(3-2t)`. */
    fun smooth01(t: Double): Double = t * t * (3 - 2 * t)

    /** Lens magnification at column offset [o] for focus strength [foc]. */
    fun magAt(o: Double, foc: Double): Double {
        val fall = (1.0 - abs(o) / (range * 0.9)).coerceAtLeast(0.0)
        return 1.0 + (foc - 1.0) * smooth01(fall)
    }

    /**
     * Horizontal position of column [o] — the integral of the magnification across the lens
     * (8-sample midpoint rule), so spacing compresses toward the magnified focal point.
     */
    fun txAt(o: Double, foc: Double, radius: Double): Double {
        val sgn = sign(o)
        val a = abs(o)
        val samples = 8
        var sum = 0.0
        for (k in 0 until samples) {
            val t = a * (k + 0.5) / samples
            sum += cos(min(t * STEP_R, 1.466)) * magAt(t, foc)
        }
        return sgn * radius * STEP_R * (a / samples) * sum
    }
}
