package com.bos.sphere.feature.homewidgets.model

import kotlin.math.ceil

/** A widget's size in dp, as a launcher reports it. */
data class SizeDp(val width: Float, val height: Float)

/** The three layouts every widget has; each shows more than the one before it. */
enum class Tier { SMALL, MEDIUM, LARGE }

/**
 * One layout and the smallest size (dp) its content needs at font scale 1: the key it is mapped
 * under. [textHeight] is the part of [minHeight] that is text (dp at font scale 1), which grows
 * with the user's font scale while padding, glyphs and 48 dp tap targets don't.
 */
data class TierKey(val tier: Tier, val minWidth: Float, val minHeight: Float, val textHeight: Float = 0f) {
    /** The height this layout needs at [fontScale] (never less than at 1×: smaller fonts only add room). */
    fun heightAt(fontScale: Float): Float = minHeight + textHeight * (fontScale - 1f).coerceAtLeast(0f)
}

/**
 * Sphere's own home-screen widgets and the size each of their layouts needs. Sizes are content
 * minima, not cell counts: a launcher's cells differ by device, grid and orientation, so a widget
 * shows whichever layout its real size can hold. Rows and pads that take a tap are 48 dp tall.
 * Each kind's keys nest (a larger layout's key is at least as wide and as tall), so under the
 * platform's closest-fit rule a widget never loses content as it grows.
 *
 * Each key is (width, height, text height): the text height is the stacked text lines' share of
 * the height at font scale 1 (a line is about 1.33 × its sp size), so [WidgetTiers.atFontScale]
 * can make a larger font pick the next-smaller layout instead of clipping (see `layout/hw_*.xml`).
 */
enum class WidgetKind(val tiers: List<TierKey>) {
    /** The VAIO-gate clock: time; + date; + next alarm. The time autosizes, from 14 / 20 sp. */
    CLOCK(keys(Triple(100f, 40f, 19f), Triple(110f, 90f, 44f), Triple(110f, 150f, 63f))),

    /** Battery: level and state; + bar and "as of"; + time to full, plug and temperature. */
    BATTERY(keys(Triple(100f, 40f, 32f), Triple(110f, 115f, 59f), Triple(110f, 155f, 94f))),

    /** Now playing: title; + artist, app, state; + previous / play-pause / next. */
    NOW_PLAYING(keys(Triple(100f, 40f, 51f), Triple(180f, 100f, 72f), Triple(180f, 160f, 72f))),

    /**
     * Needs you: the total; + three apps with counts; + five. Large: 10 dp padding × 2, the header
     * (19 + 4), five 48 dp rows with 4 dp gaps, and the footer (4 + 16) = 323 dp, plus headroom.
     */
    NEEDS_YOU(keys(Triple(100f, 40f, 53f), Triple(180f, 210f, 35f), Triple(180f, 326f, 35f))),

    /**
     * App pads: two apps; + four in a row; + six in two rows, with names. One row of pads is the
     * full widget height (no vertical padding), so 48 dp keeps each pad a 48 dp tap target.
     */
    PADS(keys(Triple(100f, 48f, 0f), Triple(200f, 48f, 0f), Triple(200f, 135f, 32f))),
    ;

    fun key(tier: Tier): TierKey = tiers.first { it.tier == tier }
}

private fun keys(small: Triple<Float, Float, Float>, medium: Triple<Float, Float, Float>, large: Triple<Float, Float, Float>) = listOf(
    TierKey(Tier.SMALL, small.first, small.second, small.third),
    TierKey(Tier.MEDIUM, medium.first, medium.second, medium.third),
    TierKey(Tier.LARGE, large.first, large.second, large.third),
)

object WidgetTiers {
    /**
     * The keys to hand the launcher at the system's [fontScale]: each height grows by its text
     * share, so at 1.3× or 2× a widget shows the layout its text still fits in. Sphere redraws
     * when the font scale changes while its process runs; otherwise at the next redraw.
     */
    fun atFontScale(tiers: List<TierKey>, fontScale: Float): List<TierKey> =
        tiers.map { it.copy(minHeight = it.heightAt(fontScale), textHeight = 0f) }

    /**
     * Which layout a launcher shows at [size], by the platform's rule for size-mapped RemoteViews
     * (API 31+, `RemoteViews.findBestFitLayout`): among the layouts whose key fits inside the size,
     * the one closest to it; if none fits, the smallest. The platform does this itself; this copy
     * exists so the breakpoints can be checked against real cell sizes.
     */
    fun pick(tiers: List<TierKey>, size: SizeDp): Tier {
        val fitting = tiers.filter { fits(it, size) }
        if (fitting.isEmpty()) return tiers.minBy { it.minWidth * it.minHeight }.tier
        return fitting.minBy { sq(size.width - it.minWidth) + sq(size.height - it.minHeight) }.tier
    }

    private fun fits(key: TierKey, size: SizeDp): Boolean =
        ceil(key.minWidth) <= ceil(size.width) && ceil(key.minHeight) <= ceil(size.height)

    private fun sq(v: Float) = v * v
}
