package com.bos.sphere.ui.viz

import androidx.compose.ui.graphics.Color

/**
 * ASOC viz for Compose: a port of the visual language of the ASOC viz toolkit (version [TOOLKIT_VERSION], the dependency-free
 * SVG/JS charting kit the owner attached in wave 28) to Compose Canvas. The toolkit's one rule: ONE ink on ONE ground; categories are
 * TEXTURES, not hues; marks are thin; there is no number on every point. This package keeps that rule and drops the web parts
 * (DOM, tooltips, ResizeObserver, the Orrery engines).
 *
 * What was ported from which toolkit function:
 *
 * | here                                   | toolkit                                              | notes |
 * |----------------------------------------|------------------------------------------------------|-------|
 * | [VizTexture], [VizTexture.ORDER]       | `ASOC.viz.order`                                     | fixed order solid, hatch, dots, fade, cross, lines, checks, stipple; a ninth part folds into Other, never a ninth texture |
 * | [VizTextures.brush]                    | `ASOC.viz.fills` and `ASOC.viz.canvasFills`          | the tile painters (hatch 7, dots 9, cross 7, lines 6, checks 8, stipple 4.5 px at scale 1), cached per size |
 * | [VizTextures.fadeBrush]                | `fills(...)('fade', dir)`                            | ink at 7 percent, 50 percent at 55 percent, 100 percent; direction up or right |
 * | [VizTone]                              | `ASOC.viz.tones` (`night`) and `tone(o)`             | ink on ground plus an accent for the selected or today mark; light derived |
 * | [VizSteps]                             | `steps` (and its `mount` frame: marks, labels, table) | columns from one baseline, number above, name below; the number only on the selected column |
 * | [VizBar], [VizBarGeometry]             | `bars`                                               | the bar of a ranked row: 4 dp, as long as its share of the longest, filled with its texture (wave 29; replaces `stack` and `legend`) |
 * | [VizStat]                              | `stat`                                               | the number itself, caption muted, light weight 300 |
 * | [VizTable]                             | `table()` on every chart                             | the same data as a plain table, for TalkBack and for "Show as table" |
 * | [VizFormat.fold]                       | `fold`                                               | more parts than textures collapse into Other |
 *
 * Not ported: `fan`, `line`, `spark`, `halftone`, `meter`, `graph`, `rose`, `gantt` (the launcher does not draw them yet), pointer
 * hover (touch has no hover; selection replaces it), animations (the toolkit only transitions opacity .15 s; here nothing animates, which
 * satisfies reduced motion by construction).
 *
 * The toolkit's pixel is a density-independent pixel here.
 */
object Viz {
    const val TOOLKIT_VERSION = "0.3.0"
}

/** The eight textures in the toolkit's fixed order (`ASOC.viz.order`). */
enum class VizTexture {
    Solid, Hatch, Dots, Fade, Cross, Lines, Checks, Stipple;

    companion object {
        /** `ASOC.viz.order`. */
        val ORDER: List<VizTexture> = entries.toList()

        /** The texture of the [i]th part: the order repeats only if a caller forgets to fold (the toolkit does `ORDER[i % 8]`). */
        fun at(i: Int): VizTexture = ORDER[Math.floorMod(i, ORDER.size)]
    }
}

/**
 * One ink on one ground, plus an accent that only the selected or today mark uses (`TONES.night`: ground #0c0c0d, ink #f2efe9, accent #f6ee2d).
 * [dark] says which way the accent was derived.
 */
data class VizTone(val ink: Color, val ground: Color, val accent: Color, val dark: Boolean = true) {
    companion object {
        /** The toolkit's `night` tone exactly. */
        val Night = VizTone(Color(0xFFF2EFE9), Color(0xFF0C0C0D), Color(0xFFF6EE2D), true)

        /**
         * The tone of a host surface: [ink] and [ground] are the host's own text and page colours; the accent is the toolkit's yellow on a
         * dark ground and a darker olive-yellow on a light one (the yellow does not read on white; this keeps 3:1 against the ground).
         */
        fun of(ink: Color, ground: Color): VizTone {
            val dark = luminance(ground) < 0.5f
            return VizTone(ink, ground, if (dark) Night.accent else Color(0xFF8A7F00), dark)
        }

        private fun luminance(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    }
}

/** Shared helpers of the port. */
object VizFormat {
    /** `fold`: more parts than there are textures collapse into one Other; returns (kept, folded rest). */
    fun <T> fold(parts: List<T>, max: Int = VizTexture.ORDER.size): Pair<List<T>, List<T>> =
        if (parts.size <= max) parts to emptyList() else parts.take(max - 1) to parts.drop(max - 1)

    /** A finite, non-negative number or 0: the guard every geometry function applies to its input. */
    fun safe(v: Double): Double = if (v.isNaN() || v.isInfinite() || v < 0.0) 0.0 else v

    /** Height of a column of [v] against [max] in [plot]: 0 for nothing, at least [minBar] for something (`bh = v>0 ? max(3, v/max*plot) : 0`). */
    fun barHeight(v: Double, max: Double, plot: Float, minBar: Float = 3f): Float {
        val s = safe(v); val m = safe(max)
        if (s <= 0.0 || plot <= 0f) return 0f
        return maxOf(minBar, if (m <= 0.0) 0f else (s / m * plot).toFloat()).coerceAtMost(plot)
    }

    /** Whole percent of [part] in [total]; 0 when the total is not positive. */
    fun percent(part: Long, total: Long): Int = if (total <= 0L || part <= 0L) 0 else Math.round(part * 100.0 / total).toInt()
}
