package com.bos.sphere.core.quality

/**
 * NFR-19: how an overlay surface follows the back gesture while it is held. Pure maths so the shape is testable on the
 * JVM; the surfaces apply it through a graphics layer. Reduced motion keeps the surface still (no scale) and only fades.
 */
object PredictiveBackMath {
    /** The surface shrinks by at most this share at full progress (Material predictive back guidance: about 10%). */
    const val MAX_SHRINK = 0.1f

    /** Alpha lost at full progress; the surface never disappears before the system commits the back. */
    const val MAX_FADE = 0.25f

    fun clamp(progress: Float): Float = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

    fun scale(progress: Float, reduced: Boolean): Float = if (reduced) 1f else 1f - MAX_SHRINK * clamp(progress)

    fun alpha(progress: Float): Float = 1f - MAX_FADE * clamp(progress)
}
