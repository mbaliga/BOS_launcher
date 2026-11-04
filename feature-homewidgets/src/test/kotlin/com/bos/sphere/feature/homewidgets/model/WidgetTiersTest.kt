package com.bos.sphere.feature.homewidgets.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTiersTest {

    // Approximate phone cell sizes in dp (developer.android.com "Determine a size for your widget").
    private val portrait2x1 = SizeDp(130f, 102f)
    private val portrait4x1 = SizeDp(276f, 102f)
    private val portrait2x2 = SizeDp(130f, 220f)
    private val portrait3x2 = SizeDp(203f, 220f)
    private val portrait4x2 = SizeDp(276f, 220f)
    private val portrait4x3 = SizeDp(276f, 337f)
    private val landscape2x1 = SizeDp(269f, 51f)
    private val landscape4x2 = SizeDp(554f, 117f)

    private fun tier(kind: WidgetKind, size: SizeDp) = WidgetTiers.pick(kind.tiers, size)

    @Test
    fun everyKindHasOneLayoutPerTier() {
        for (kind in WidgetKind.entries) {
            assertEquals(kind.name, Tier.entries.toList(), kind.tiers.map { it.tier })
        }
    }

    @Test
    fun theDeclaredMinimumResizeSizeShowsTheSmallLayout() {
        // Provider XMLs declare minResizeWidth 110dp × minResizeHeight 40dp (pads: 48dp).
        for (kind in WidgetKind.entries) assertEquals(kind.name, Tier.SMALL, tier(kind, SizeDp(110f, 40f)))
        assertEquals(Tier.SMALL, tier(WidgetKind.PADS, SizeDp(110f, 48f)))
        // A landscape 2×1 is wide but one short row: summaries, except pads, which fit four across.
        for (kind in WidgetKind.entries - WidgetKind.PADS) assertEquals(kind.name, Tier.SMALL, tier(kind, landscape2x1))
        assertEquals(Tier.MEDIUM, tier(WidgetKind.PADS, landscape2x1))
    }

    @Test
    fun aFourByThreeShowsTheLargeLayout() {
        for (kind in WidgetKind.entries) assertEquals(kind.name, Tier.LARGE, tier(kind, portrait4x3))
    }

    @Test
    fun representativeCellSizes() {
        assertEquals(Tier.MEDIUM, tier(WidgetKind.CLOCK, portrait2x1)) // time + date
        assertEquals(Tier.LARGE, tier(WidgetKind.CLOCK, portrait2x2)) // + next alarm
        assertEquals(Tier.SMALL, tier(WidgetKind.BATTERY, portrait2x1)) // level, state, as of
        assertEquals(Tier.MEDIUM, tier(WidgetKind.BATTERY, landscape4x2)) // + level bar
        assertEquals(Tier.LARGE, tier(WidgetKind.BATTERY, portrait2x2)) // + time to full, temperature
        assertEquals(Tier.SMALL, tier(WidgetKind.NOW_PLAYING, portrait2x2))
        assertEquals(Tier.MEDIUM, tier(WidgetKind.NOW_PLAYING, portrait4x1))
        assertEquals(Tier.LARGE, tier(WidgetKind.NOW_PLAYING, portrait4x2)) // controls
        assertEquals(Tier.MEDIUM, tier(WidgetKind.NOW_PLAYING, landscape4x2))
        assertEquals(Tier.SMALL, tier(WidgetKind.NEEDS_YOU, portrait4x1))
        assertEquals(Tier.MEDIUM, tier(WidgetKind.NEEDS_YOU, portrait4x2)) // three rows
        assertEquals(Tier.SMALL, tier(WidgetKind.NEEDS_YOU, landscape4x2))
        assertEquals(Tier.SMALL, tier(WidgetKind.PADS, portrait2x2))
        assertEquals(Tier.MEDIUM, tier(WidgetKind.PADS, portrait4x1)) // four in a row
        assertEquals(Tier.LARGE, tier(WidgetKind.PADS, portrait3x2)) // six with names
    }

    @Test
    fun belowEveryKeyTheSmallestLayoutIsUsed() {
        for (kind in WidgetKind.entries) assertEquals(kind.name, Tier.SMALL, tier(kind, SizeDp(20f, 20f)))
    }

    @Test
    fun growingNeverLosesContent() {
        // Under the platform's closest-fit rule, nested keys keep the chosen tier monotonic.
        for (kind in WidgetKind.entries) {
            for (w in 0..700 step 10) {
                for (h in 0..700 step 10) {
                    val here = tier(kind, SizeDp(w.toFloat(), h.toFloat())).ordinal
                    val wider = tier(kind, SizeDp(w + 10f, h.toFloat())).ordinal
                    val taller = tier(kind, SizeDp(w.toFloat(), h + 10f)).ordinal
                    assertTrue("$kind at ${w}x$h → wider", wider >= here)
                    assertTrue("$kind at ${w}x$h → taller", taller >= here)
                }
            }
        }
    }

    @Test
    fun keysNest() {
        for (kind in WidgetKind.entries) {
            kind.tiers.zipWithNext().forEach { (a, b) ->
                assertTrue(kind.name, b.minWidth >= a.minWidth && b.minHeight >= a.minHeight)
            }
        }
    }

    @Test
    fun tapTargetsFitTheirLayouts() {
        // Needs you rows and pads are 48 dp; their layouts' keys leave room for them (+ chrome).
        assertTrue(WidgetKind.NEEDS_YOU.key(Tier.MEDIUM).minHeight >= 3 * 48f)
        assertTrue(WidgetKind.NEEDS_YOU.key(Tier.LARGE).minHeight >= 5 * 48f)
        assertTrue(WidgetKind.PADS.key(Tier.MEDIUM).minWidth >= 4 * 48f)
        assertTrue(WidgetKind.PADS.key(Tier.LARGE).minHeight >= 2 * 48f)
        // One row of pads fills the widget's height, so its keys are the 48 dp itself.
        assertTrue(WidgetKind.PADS.key(Tier.SMALL).minHeight >= 48f)
        assertTrue(WidgetKind.PADS.key(Tier.MEDIUM).minHeight >= 48f)
        assertTrue(WidgetKind.NOW_PLAYING.key(Tier.LARGE).minWidth >= 3 * 48f)
    }

    // --- font scale ---

    private fun tier(kind: WidgetKind, size: SizeDp, fontScale: Float) =
        WidgetTiers.pick(WidgetTiers.atFontScale(kind.tiers, fontScale), size)

    @Test
    fun atFontScaleOneTheKeysAreUnchangedAndSmallerFontsNeverShrinkThem() {
        for (kind in WidgetKind.entries) {
            assertEquals(kind.tiers.map { it.minHeight }, WidgetTiers.atFontScale(kind.tiers, 1f).map { it.minHeight })
            assertEquals(kind.tiers.map { it.minHeight }, WidgetTiers.atFontScale(kind.tiers, 0.85f).map { it.minHeight })
            assertEquals(kind.tiers.map { it.minWidth }, WidgetTiers.atFontScale(kind.tiers, 2f).map { it.minWidth })
        }
    }

    @Test
    fun keysNestAndGrowingNeverLosesContentAtEveryFontScale() {
        for (scale in listOf(1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)) {
            for (kind in WidgetKind.entries) {
                val keys = WidgetTiers.atFontScale(kind.tiers, scale)
                keys.zipWithNext().forEach { (a, b) -> assertTrue("$kind ×$scale", b.minWidth >= a.minWidth && b.minHeight >= a.minHeight) }
                for (w in 0..700 step 20) {
                    for (h in 0..700 step 20) {
                        val here = WidgetTiers.pick(keys, SizeDp(w.toFloat(), h.toFloat())).ordinal
                        assertTrue("$kind ×$scale at ${w}x$h", WidgetTiers.pick(keys, SizeDp(w + 20f, h.toFloat())).ordinal >= here)
                        assertTrue("$kind ×$scale at ${w}x$h", WidgetTiers.pick(keys, SizeDp(w.toFloat(), h + 20f)).ordinal >= here)
                    }
                }
            }
        }
    }

    @Test
    fun largerTextPicksASmallerLayoutInsteadOfClipping() {
        // Needs you large: 20 padding + 23 header + 5 × 52 rows + 20 footer = 323 dp at 1×.
        assertTrue(WidgetKind.NEEDS_YOU.key(Tier.LARGE).minHeight >= 323f)
        assertEquals(Tier.LARGE, tier(WidgetKind.NEEDS_YOU, portrait4x3, 1f))
        assertEquals(Tier.LARGE, tier(WidgetKind.NEEDS_YOU, portrait4x3, 1.3f)) // 333.5 dp of content in 337
        assertEquals(Tier.MEDIUM, tier(WidgetKind.NEEDS_YOU, portrait4x3, 2f))
        // Now playing medium (about 102 dp at 1×, 124 at 1.3×) in a portrait 4×1 (102 dp).
        assertEquals(Tier.MEDIUM, tier(WidgetKind.NOW_PLAYING, portrait4x1, 1f))
        assertEquals(Tier.SMALL, tier(WidgetKind.NOW_PLAYING, portrait4x1, 1.3f))
        // Now playing large (48 dp controls under four lines of text) in a portrait 4×2 (220 dp):
        // about 182 dp at 1.3×, about 230 at 2×, so 2× shows the medium layout.
        assertEquals(Tier.LARGE, tier(WidgetKind.NOW_PLAYING, portrait4x2, 1.3f))
        assertEquals(Tier.MEDIUM, tier(WidgetKind.NOW_PLAYING, portrait4x2, 2f))
        // Pads have no text but the large layout's names.
        assertEquals(Tier.MEDIUM, tier(WidgetKind.PADS, landscape2x1, 2f))
        for (kind in WidgetKind.entries) assertEquals(kind.name, Tier.LARGE, tier(kind, portrait4x3, 1.3f))
    }

    @Test
    fun requestCodesAreUniquePerWidgetAndAction() {
        val codes = (1..200).flatMap { id -> WidgetAction.entries.map { WidgetAction.requestCode(id, it) } }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(WidgetAction.entries.all { it.slot < WidgetAction.STRIDE })
        assertEquals(WidgetAction.entries.size, WidgetAction.entries.map { it.slot }.toSet().size)
    }

    @Test
    fun mediaCommandsParseOnlyKnownNames() {
        assertEquals(MediaCommand.NEXT, MediaCommand.parse("NEXT"))
        assertEquals(null, MediaCommand.parse("next"))
        assertEquals(null, MediaCommand.parse(null))
        assertEquals(null, MediaCommand.parse("SEEK"))
    }
}
