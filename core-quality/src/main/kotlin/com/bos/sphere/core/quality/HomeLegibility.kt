package com.bos.sphere.core.quality

/**
 * NFR-15: the scrim that keeps home text at WCAG AA over whatever wallpaper is behind it. Pure math over sampled
 * wallpaper colours; the app samples the wallpaper (solid, gradient stops, a downscaled photo, system wallpaper colours).
 */
object HomeLegibility {
    /** A scrim stronger than this hides the wallpaper; past it the text gets its AA from the cap alone and is reported. */
    const val MAX_ALPHA = 0.85

    /**
     * When [MAX_ALPHA] cannot give AA, the scrim is automatically strengthened up to this (still leaving a trace of the
     * wallpaper); [Plan.strengthened] says it happened.
     */
    const val STRONG_ALPHA = 0.95

    data class Plan(val scrimColor: Int, val alpha: Double, val reachesAa: Boolean, val strengthened: Boolean = false)

    /** Scrim colour opposite to the text: dark scrim under light text, light scrim under dark text. */
    fun scrimColorFor(text: Int): Int = if (Contrast.luminance(text) >= 0.5) Contrast.BLACK else Contrast.WHITE

    private fun planWith(scrim: Int, texts: List<Int>, wallpaper: List<Int>, target: Double, cap: Double = MAX_ALPHA): Plan {
        var worst = 0.0
        var ok = true
        for (t in texts) {
            val a = Contrast.scrimAlphaForAny(t, wallpaper, scrim, target)
            if (a == null) { ok = false; worst = cap } else worst = maxOf(worst, a)
        }
        if (worst > cap) { worst = cap; ok = false }
        return Plan(scrim, worst, ok)
    }

    /**
     * The weakest scrim (black or white, whichever needs less) that gives every colour in [texts] AA contrast over every
     * colour in [wallpaper]. [largeText] uses the 3:1 threshold. When no scrim reaches AA the result has
     * [Plan.reachesAa] false and the capped alpha.
     */
    fun plan(texts: List<Int>, wallpaper: List<Int>, largeText: Boolean = false): Plan {
        val normal = planCapped(texts, wallpaper, largeText, MAX_ALPHA)
        if (normal.reachesAa) return normal
        // Automatic strengthening: allow a heavier scrim before giving up, and say so.
        val strong = planCapped(texts, wallpaper, largeText, STRONG_ALPHA)
        return strong.copy(strengthened = true)
    }

    private fun planCapped(texts: List<Int>, wallpaper: List<Int>, largeText: Boolean, cap: Double): Plan {
        val target = if (largeText) Contrast.AA_LARGE else Contrast.AA_NORMAL
        val first = scrimColorFor(texts.first())
        val other = if (first == Contrast.BLACK) Contrast.WHITE else Contrast.BLACK
        val a = planWith(first, texts, wallpaper, target, cap)
        val b = planWith(other, texts, wallpaper, target, cap)
        return when {
            a.reachesAa && b.reachesAa -> if (b.alpha < a.alpha) b else a
            b.reachesAa -> b
            else -> a
        }
    }

    /** One line for the Lab row: what scrim the current background needs and whether the text reaches AA. */
    fun describe(background: String, plan: Plan): String {
        val pct = Math.round(plan.alpha * 100).toInt()
        val scrim = if (plan.alpha <= 0.0) "no scrim needed" else "${if (plan.scrimColor == Contrast.BLACK) "dark" else "light"} scrim $pct%"
        val aa = when {
            plan.reachesAa && plan.strengthened -> "AA reached only after strengthening the scrim past ${Math.round(MAX_ALPHA * 100)}%"
            plan.reachesAa -> "text reaches AA"
            else -> "AA NOT reached even at the ${Math.round(STRONG_ALPHA * 100)}% limit"
        }
        return "$background: $scrim, $aa"
    }
}
