package com.bos.sphere.core.quality

/**
 * NFR-15: the scrim that keeps home text at WCAG AA over whatever wallpaper is behind it. Pure math over sampled
 * wallpaper colours; the app samples the wallpaper (solid, gradient stops, a downscaled photo, system wallpaper colours).
 */
object HomeLegibility {
    /** A scrim stronger than this hides the wallpaper; past it the text gets its AA from the cap alone and is reported. */
    const val MAX_ALPHA = 0.85

    data class Plan(val scrimColor: Int, val alpha: Double, val reachesAa: Boolean)

    /** Scrim colour opposite to the text: dark scrim under light text, light scrim under dark text. */
    fun scrimColorFor(text: Int): Int = if (Contrast.luminance(text) >= 0.5) Contrast.BLACK else Contrast.WHITE

    private fun planWith(scrim: Int, texts: List<Int>, wallpaper: List<Int>, target: Double): Plan {
        var worst = 0.0
        var ok = true
        for (t in texts) {
            val a = Contrast.scrimAlphaForAny(t, wallpaper, scrim, target)
            if (a == null) { ok = false; worst = MAX_ALPHA } else worst = maxOf(worst, a)
        }
        if (worst > MAX_ALPHA) { worst = MAX_ALPHA; ok = false }
        return Plan(scrim, worst, ok)
    }

    /**
     * The weakest scrim (black or white, whichever needs less) that gives every colour in [texts] AA contrast over every
     * colour in [wallpaper]. [largeText] uses the 3:1 threshold. When no scrim reaches AA the result has
     * [Plan.reachesAa] false and the capped alpha.
     */
    fun plan(texts: List<Int>, wallpaper: List<Int>, largeText: Boolean = false): Plan {
        val target = if (largeText) Contrast.AA_LARGE else Contrast.AA_NORMAL
        val first = scrimColorFor(texts.first())
        val other = if (first == Contrast.BLACK) Contrast.WHITE else Contrast.BLACK
        val a = planWith(first, texts, wallpaper, target)
        val b = planWith(other, texts, wallpaper, target)
        return when {
            a.reachesAa && b.reachesAa -> if (b.alpha < a.alpha) b else a
            b.reachesAa -> b
            else -> a
        }
    }
}
