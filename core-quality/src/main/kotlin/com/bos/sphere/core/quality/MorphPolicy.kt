package com.bos.sphere.core.quality

/**
 * NFR-14: how a surface that normally morphs (scales, slides, springs) is drawn when motion is reduced.
 * With [MotionPrefs.reduced] the geometry is not animated at all: it is fully applied while the surface is visible or
 * still fading, and only the alpha moves (a cross-fade). With motion on, [geometry] follows the animated progress.
 */
object MorphPolicy {
    /** Fraction of the geometry (scale, offset, ring radius) to draw for an animated [progress] in 0..1. */
    fun geometry(prefs: MotionPrefs, progress: Float, visible: Boolean): Float =
        if (!prefs.reduced) progress else if (visible || progress > 0.002f) 1f else 0f

    /** Alpha for the whole surface: a cross-fade when reduced, otherwise whatever the morph itself decides (1). */
    fun alpha(prefs: MotionPrefs, progress: Float): Float =
        if (!prefs.reduced) 1f else progress.coerceIn(0f, 1f)

    /** Duration of the progress animation in ms for a morph that normally takes [baseMs]. */
    fun progressMs(prefs: MotionPrefs, baseMs: Int): Int = if (prefs.reduced) prefs.crossFadeMs(baseMs) else prefs.durationMs(baseMs)
}
